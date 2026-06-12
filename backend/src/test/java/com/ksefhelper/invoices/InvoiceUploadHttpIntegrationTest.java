package com.ksefhelper.invoices;

import com.ksefhelper.audit.dto.AuditEventResponse;
import com.ksefhelper.audit.AuditEventType;
import com.ksefhelper.auth.dto.AuthResponse;
import com.ksefhelper.auth.dto.ResetPasswordRequest;
import com.ksefhelper.auth.dto.TokenRequest;
import com.ksefhelper.auth.dto.LoginRequest;
import com.ksefhelper.auth.dto.RegisterRequest;
import com.ksefhelper.companies.dto.CompanyRequest;
import com.ksefhelper.companies.dto.CompanyResponse;
import com.ksefhelper.invoices.dto.InvoiceItemResponse;
import com.ksefhelper.invoices.dto.InvoicePreviewResponse;
import com.ksefhelper.invoices.dto.InvoiceSummaryResponse;
import com.ksefhelper.invoices.dto.UploadInvoiceResponse;
import com.ksefhelper.invoices.entity.InvoiceStatus;
import com.ksefhelper.files.FileStorageService;
import com.ksefhelper.files.entity.StoredFile;
import com.ksefhelper.files.repository.StoredFileRepository;
import com.ksefhelper.files.storage.ObjectStorage;
import com.ksefhelper.organizations.dto.InviteMemberRequest;
import com.ksefhelper.organizations.dto.OrganizationDeletionRequest;
import com.ksefhelper.organizations.dto.OrganizationRequest;
import com.ksefhelper.organizations.dto.OrganizationResponse;
import com.ksefhelper.organizations.entity.MembershipRole;
import com.ksefhelper.organizations.entity.OrganizationType;
import com.ksefhelper.retention.RetentionCleanupService;
import com.ksefhelper.users.dto.AccountDeletionRequest;
import com.ksefhelper.validation.PythonTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@SuppressWarnings("SqlResolve")
class InvoiceUploadHttpIntegrationTest {
    private static final String EXEMPT_INVOICE_SAMPLE = "FA_3_Przykład_9.xml";
    private static final Path STORAGE_ROOT = createStorageRoot();

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("ksef_helper_test")
            .withUsername("ksef")
            .withPassword("ksef");

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StoredFileRepository storedFileRepository;

    @Autowired
    private FileStorageService fileStorageService;

    @Autowired
    private ObjectStorage objectStorage;

    @Autowired
    private RetentionCleanupService retentionCleanupService;

    @LocalServerPort
    private int serverPort;

    @DynamicPropertySource
    static void applicationProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("app.storage.local-path", STORAGE_ROOT::toString);
        registry.add("app.xml.validator-command", PythonTestSupport::command);
        registry.add("app.data.retention.invoice-days", () -> "30");
    }

    @Test
    void uploadsOfficialFa3XmlThroughHttpAndPersistsTextualVatRate() {
        String token = register();
        Path sample = officialSample();

        ResponseEntity<UploadInvoiceResponse> uploadResponse = upload(token, sample);

        assertThat(uploadResponse.getStatusCode().is2xxSuccessful()).isTrue();
        UploadInvoiceResponse upload = uploadResponse.getBody();
        assertThat(upload).isNotNull();
        assertThat(upload.status()).isNotEqualTo(InvoiceStatus.INVALID);

        HttpHeaders authorizedHeaders = new HttpHeaders();
        authorizedHeaders.setBearerAuth(token);
        ResponseEntity<InvoicePreviewResponse> previewResponse = restTemplate.exchange(
                "/api/invoices/{id}/preview",
                HttpMethod.GET,
                new HttpEntity<>(authorizedHeaders),
                InvoicePreviewResponse.class,
                upload.invoiceId()
        );

        assertThat(previewResponse.getStatusCode().is2xxSuccessful()).isTrue();
        InvoicePreviewResponse preview = previewResponse.getBody();
        assertThat(preview).isNotNull();
        assertThat(preview.items())
                .extracting(InvoiceItemResponse::vatRate)
                .contains("zw");

        // language=PostgreSQL
        String storagePath = jdbcTemplate.queryForObject(
                """
                SELECT stored_files.storage_path
                FROM stored_files
                JOIN invoices ON invoices.file_id = stored_files.id
                WHERE invoices.id = ?
                """,
                String.class,
                upload.invoiceId()
        );
        assertThat(storagePath).isNotBlank();
        assertThat(Files.isRegularFile(STORAGE_ROOT.resolve(storagePath))).isTrue();
        // language=PostgreSQL
        String textualVatQuery = "SELECT vat_rate FROM invoice_items WHERE invoice_id = ? AND vat_rate = 'zw'";
        assertThat(jdbcTemplate.queryForObject(
                textualVatQuery,
                String.class,
                upload.invoiceId()
        )).isEqualTo("zw");
    }

    @Test
    void returnsTechnicalValidationErrorsForMalformedXmlThroughHttp() throws IOException {
        String token = register();
        Path malformedXml = Files.createTempFile("ksef-malformed-", ".xml");
        Files.writeString(malformedXml, "<Faktura><broken></Faktura>");

        ResponseEntity<UploadInvoiceResponse> response = upload(token, malformedXml);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(InvoiceStatus.INVALID);
        assertThat(response.getBody().validationMessages())
                .anyMatch(message -> message.code().equals("XML_PARSE_FAILED"));
    }

    @Test
    void rejectsNonXmlUploadsBeforeCreatingStoredFileRecords() throws IOException {
        String token = register();
        long filesBefore = countStoredFiles();
        Path textFile = Files.createTempFile("not-an-invoice-", ".txt");
        Files.writeString(textFile, "not xml");

        ResponseEntity<String> response = uploadRaw(token, textFile, String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(countStoredFiles()).isEqualTo(filesBefore);
    }

    @Test
    void isolatesEveryOrganizationScopedResourceThroughHttp() {
        RegisteredUser first = registerUser("tenant-a");
        UploadInvoiceResponse upload = upload(first.token(), officialSample()).getBody();
        assertThat(upload).isNotNull();
        CompanyResponse company = createCompany(first.token(), "Tenant A Company");
        RegisteredUser second = registerUser("tenant-b");

        assertStatus(second.token(), HttpMethod.GET, "/api/invoices/" + upload.invoiceId(), null, 404);
        assertStatus(second.token(), HttpMethod.GET, "/api/invoices/" + upload.invoiceId() + "/preview", null, 404);
        assertStatus(second.token(), HttpMethod.GET, "/api/invoices/" + upload.invoiceId() + "/validation", null, 404);
        assertStatus(second.token(), HttpMethod.GET, "/api/invoices/" + upload.invoiceId() + "/download-original", null, 404);
        assertStatus(second.token(), HttpMethod.POST, "/api/invoices/" + upload.invoiceId() + "/revalidate", null, 404);
        assertStatus(second.token(), HttpMethod.DELETE, "/api/invoices/" + upload.invoiceId(), null, 404);
        assertStatus(second.token(), HttpMethod.GET, "/api/reports/invoices/" + upload.invoiceId() + "/validation-report", null, 404);
        assertStatus(second.token(), HttpMethod.GET, "/api/companies/" + company.id(), null, 404);
        assertStatus(second.token(), HttpMethod.PUT, "/api/companies/" + company.id(), companyRequest("Changed"), 404);
        assertStatus(second.token(), HttpMethod.DELETE, "/api/companies/" + company.id(), null, 404);
        assertStatus(second.token(), HttpMethod.GET, "/api/organizations/" + first.organizationId() + "/members", null, 403);
        assertStatus(second.token(), HttpMethod.POST, "/api/auth/switch-organization/" + first.organizationId(), null, 403);

        ResponseEntity<InvoiceSummaryResponse[]> invoices = restTemplate.exchange(
                "/api/invoices",
                HttpMethod.GET,
                authorizedEntity(second.token(), null),
                InvoiceSummaryResponse[].class
        );
        assertThat(invoices.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(invoices.getBody()).isEmpty();
    }

    @Test
    void requiresExplicitOrganizationSelectionAndSupportsSwitching() {
        RegisteredUser user = registerUser("switcher");
        OrganizationResponse secondOrganization = restTemplate.exchange(
                "/api/organizations",
                HttpMethod.POST,
                authorizedEntity(user.token(), new OrganizationRequest("Second Workspace", OrganizationType.ACCOUNTING_OFFICE)),
                OrganizationResponse.class
        ).getBody();
        assertThat(secondOrganization).isNotNull();

        ResponseEntity<AuthResponse> loginResponse = restTemplate.postForEntity(
                "/api/auth/login",
                new LoginRequest(user.email(), "strong-password"),
                AuthResponse.class
        );
        assertThat(loginResponse.getStatusCode().is2xxSuccessful()).isTrue();
        AuthResponse unscoped = loginResponse.getBody();
        assertThat(unscoped).isNotNull();
        assertThat(unscoped.organization()).isNull();
        assertThat(unscoped.organizations()).hasSize(2);
        assertStatus(unscoped.token(), HttpMethod.GET, "/api/invoices", null, 403);

        AuthResponse switched = switchOrganization(unscoped.token(), secondOrganization.id());
        assertThat(switched.organization()).isNotNull();
        assertThat(switched.organization().id()).isEqualTo(secondOrganization.id());
        createCompany(switched.token(), "Second Workspace Company");

        AuthResponse switchedBack = switchOrganization(switched.token(), user.organizationId());
        ResponseEntity<CompanyResponse[]> firstCompanies = restTemplate.exchange(
                "/api/companies",
                HttpMethod.GET,
                authorizedEntity(switchedBack.token(), null),
                CompanyResponse[].class
        );
        assertThat(firstCompanies.getBody()).isEmpty();
    }

    @Test
    void enforcesTheRolePermissionMatrixThroughHttp() {
        RegisteredUser owner = registerUser("matrix-owner");
        UploadInvoiceResponse ownerUpload = upload(owner.token(), officialSample()).getBody();
        assertThat(ownerUpload).isNotNull();
        CompanyResponse ownerCompany = createCompany(owner.token(), "Owner Company");

        RegisteredUser client = registerUser("matrix-client");
        invite(owner, client.email(), MembershipRole.CLIENT);
        String clientToken = switchOrganization(client.token(), owner.organizationId()).token();
        assertStatus(clientToken, HttpMethod.GET, "/api/invoices/" + ownerUpload.invoiceId() + "/preview", null, 200);
        assertStatus(clientToken, HttpMethod.GET, "/api/invoices/" + ownerUpload.invoiceId() + "/download-original", null, 200);
        assertStatus(clientToken, HttpMethod.GET, "/api/reports/invoices/" + ownerUpload.invoiceId() + "/validation-report", null, 200);
        assertStatus(clientToken, HttpMethod.GET, "/api/companies/" + ownerCompany.id(), null, 200);
        assertUploadStatus(clientToken, 403);
        assertStatus(clientToken, HttpMethod.POST, "/api/companies", companyRequest("Forbidden"), 403);
        assertStatus(clientToken, HttpMethod.POST, "/api/invoices/" + ownerUpload.invoiceId() + "/revalidate", null, 403);
        assertStatus(clientToken, HttpMethod.DELETE, "/api/invoices/" + ownerUpload.invoiceId(), null, 403);
        assertStatus(clientToken, HttpMethod.GET, "/api/organizations/current/members", null, 403);

        RegisteredUser employee = registerUser("matrix-employee");
        invite(owner, employee.email(), MembershipRole.EMPLOYEE);
        String employeeToken = switchOrganization(employee.token(), owner.organizationId()).token();
        assertUploadStatus(employeeToken, 200);
        assertStatus(employeeToken, HttpMethod.POST, "/api/invoices/" + ownerUpload.invoiceId() + "/revalidate", null, 200);
        assertStatus(employeeToken, HttpMethod.POST, "/api/companies", companyRequest("Forbidden"), 403);
        assertStatus(employeeToken, HttpMethod.DELETE, "/api/invoices/" + ownerUpload.invoiceId(), null, 403);
        assertStatus(employeeToken, HttpMethod.GET, "/api/organizations/current/members", null, 403);

        RegisteredUser accountant = registerUser("matrix-accountant");
        invite(owner, accountant.email(), MembershipRole.ACCOUNTANT);
        String accountantToken = switchOrganization(accountant.token(), owner.organizationId()).token();
        assertStatus(accountantToken, HttpMethod.GET, "/api/organizations/current/members", null, 200);
        assertStatus(accountantToken, HttpMethod.POST, "/api/companies", companyRequest("Accountant Company"), 201);

        RegisteredUser prospectiveAccountant = registerUser("matrix-next-accountant");
        assertStatus(
                accountantToken,
                HttpMethod.POST,
                "/api/organizations/" + owner.organizationId() + "/invite",
                new InviteMemberRequest(prospectiveAccountant.email(), MembershipRole.ACCOUNTANT),
                403
        );
        assertStatus(accountantToken, HttpMethod.DELETE, "/api/invoices/" + ownerUpload.invoiceId(), null, 204);
    }

    @Test
    void rotatesRefreshTokensAndRevokesTheFamilyWhenAnOldTokenIsReused() {
        ResponseEntity<AuthResponse> registration = registerResponse("refresh-rotation");
        String firstCookie = refreshCookie(registration);

        ResponseEntity<AuthResponse> refreshed = refresh(firstCookie);
        assertThat(refreshed.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(refreshed.getBody()).isNotNull();
        assertThat(refreshed.getBody().token()).isNotBlank();
        String secondCookie = refreshCookie(refreshed);
        assertThat(secondCookie).isNotEqualTo(firstCookie);

        assertThat(refresh(firstCookie).getStatusCode().value()).isEqualTo(400);
        assertThat(refresh(secondCookie).getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void logoutRevokesTheRefreshSession() {
        ResponseEntity<AuthResponse> registration = registerResponse("logout");
        String cookie = refreshCookie(registration);

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, cookie);
        ResponseEntity<Void> logout = restTemplate.exchange(
                "/api/auth/logout",
                HttpMethod.POST,
                new HttpEntity<>(headers),
                Void.class
        );

        assertThat(logout.getStatusCode().value()).isEqualTo(204);
        assertThat(refresh(cookie).getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void passwordResetRevokesSessionsAndChangesCredentials() {
        ResponseEntity<AuthResponse> registration = registerResponse("password-reset");
        AuthResponse auth = registration.getBody();
        assertThat(auth).isNotNull();
        String cookie = refreshCookie(registration);
        String resetToken = "known-password-reset-token";
        insertAccountToken(auth.user().id(), "PASSWORD_RESET", resetToken);

        ResponseEntity<String> reset = restTemplate.postForEntity(
                "/api/auth/reset-password",
                new ResetPasswordRequest(resetToken, "new-strong-password"),
                String.class
        );

        assertThat(reset.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(refresh(cookie).getStatusCode().value()).isEqualTo(400);
        assertThat(login(auth.user().email(), "strong-password").getStatusCode().value()).isEqualTo(401);
        assertThat(login(auth.user().email(), "new-strong-password").getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void emailVerificationTokenActivatesAnUnverifiedAccount() {
        ResponseEntity<AuthResponse> registration = registerResponse("verification");
        AuthResponse auth = registration.getBody();
        assertThat(auth).isNotNull();
        jdbcTemplate.update("UPDATE app_users SET email_verified = FALSE WHERE id = ?", auth.user().id());
        String verificationToken = "known-verification-token";
        insertAccountToken(auth.user().id(), "EMAIL_VERIFICATION", verificationToken);

        ResponseEntity<AuthResponse> verified = restTemplate.postForEntity(
                "/api/auth/verify-email",
                new TokenRequest(verificationToken),
                AuthResponse.class
        );

        assertThat(verified.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(verified.getBody()).isNotNull();
        assertThat(verified.getBody().user().emailVerified()).isTrue();
        assertThat(refreshCookie(verified)).isNotBlank();
    }

    @Test
    void platformAdminCanDisableAnAccountAndRevokeItsSessions() {
        RegisteredUser admin = registerUser("platform-admin");
        ResponseEntity<AuthResponse> targetRegistration = registerResponse("disabled-user");
        AuthResponse target = targetRegistration.getBody();
        assertThat(target).isNotNull();
        String targetCookie = refreshCookie(targetRegistration);
        jdbcTemplate.update("UPDATE app_users SET platform_admin = TRUE WHERE id = ?", userId(admin.email()));

        assertStatus(
                admin.token(),
                HttpMethod.POST,
                "/api/admin/users/" + target.user().id() + "/disable",
                null,
                204
        );

        assertThat(login(target.user().email(), "strong-password").getStatusCode().value()).isEqualTo(401);
        assertThat(refresh(targetCookie).getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void backsUpRestoresAndDeletesStoredObjectsThroughTheStorageBoundary() {
        RegisteredUser user = registerUser("storage-lifecycle");
        UploadInvoiceResponse upload = upload(user.token(), officialSample()).getBody();
        assertThat(upload).isNotNull();
        StoredFile storedFile = storedFile(upload.invoiceId());
        byte[] backup = fileStorageService.exportForBackup(storedFile);

        objectStorage.delete(storedFile.getStoragePath());
        assertStatus(
                user.token(),
                HttpMethod.GET,
                "/api/invoices/" + upload.invoiceId() + "/download-original",
                null,
                404
        );

        fileStorageService.restoreFromBackup(storedFile, backup);
        assertStatus(
                user.token(),
                HttpMethod.GET,
                "/api/invoices/" + upload.invoiceId() + "/download-original",
                null,
                200
        );

        assertStatus(user.token(), HttpMethod.DELETE, "/api/invoices/" + upload.invoiceId(), null, 204);
        assertThat(objectStorage.exists(storedFile.getStoragePath())).isFalse();
        // language=PostgreSQL
        Integer completedTasks = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM storage_deletion_tasks WHERE storage_key = ? AND completed_at IS NOT NULL",
                Integer.class,
                storedFile.getStoragePath()
        );
        assertThat(completedTasks).isEqualTo(1);
    }

    @Test
    void recordsAppendOnlyAuditEventsAndExportsOrganizationData() throws IOException {
        RegisteredUser owner = registerUser("audit-export");
        UploadInvoiceResponse upload = upload(owner.token(), officialSample()).getBody();
        assertThat(upload).isNotNull();
        assertStatus(
                owner.token(),
                HttpMethod.GET,
                "/api/invoices/" + upload.invoiceId() + "/download-original",
                null,
                200
        );

        ResponseEntity<AuditEventResponse[]> auditResponse = restTemplate.exchange(
                "/api/organizations/current/audit-events",
                HttpMethod.GET,
                authorizedEntity(owner.token(), null),
                AuditEventResponse[].class
        );
        assertThat(auditResponse.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(auditResponse.getBody())
                .extracting(AuditEventResponse::eventType)
                .contains(AuditEventType.INVOICE_UPLOADED, AuditEventType.INVOICE_DOWNLOADED);

        ResponseEntity<byte[]> exportResponse = restTemplate.exchange(
                "/api/organizations/current/export",
                HttpMethod.GET,
                authorizedEntity(owner.token(), null),
                byte[].class
        );
        assertThat(exportResponse.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(exportResponse.getHeaders().getContentType()).isEqualTo(MediaType.parseMediaType("application/zip"));
        Set<String> entries = zipEntries(java.util.Objects.requireNonNull(exportResponse.getBody()));
        assertThat(entries)
                .contains("manifest.json", "organization.json", "members.json", "invoices.json", "audit-events.json")
                .anyMatch(name -> name.startsWith("files/" + upload.invoiceId()));

        UUID auditId = jdbcTemplate.queryForObject(
                "SELECT id FROM audit_events WHERE organization_id = ? ORDER BY occurred_at DESC LIMIT 1",
                UUID.class,
                owner.organizationId()
        );
        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE audit_events SET metadata = '{}' WHERE id = ?",
                auditId
        )).hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbcTemplate.update(
                "DELETE FROM audit_events WHERE id = ?",
                auditId
        )).hasMessageContaining("append-only");
    }

    @Test
    void restrictsAuditExportAndOrganizationDeletionToOwners() {
        RegisteredUser owner = registerUser("lifecycle-owner");
        RegisteredUser client = registerUser("lifecycle-client");
        invite(owner, client.email(), MembershipRole.CLIENT);
        String clientToken = switchOrganization(client.token(), owner.organizationId()).token();

        assertStatus(clientToken, HttpMethod.GET, "/api/organizations/current/audit-events", null, 403);
        assertStatus(clientToken, HttpMethod.GET, "/api/organizations/current/export", null, 403);
        assertStatus(
                clientToken,
                HttpMethod.DELETE,
                "/api/organizations/current",
                new OrganizationDeletionRequest("strong-password", "Integration Organization"),
                403
        );
    }

    @Test
    void permanentlyDeletesAnOrganizationAndItsStoredObjects() {
        RegisteredUser owner = registerUser("organization-delete");
        UploadInvoiceResponse upload = upload(owner.token(), officialSample()).getBody();
        assertThat(upload).isNotNull();
        StoredFile file = storedFile(upload.invoiceId());

        assertStatus(
                owner.token(),
                HttpMethod.DELETE,
                "/api/organizations/current",
                new OrganizationDeletionRequest("strong-password", "Integration Organization"),
                204
        );

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM organizations WHERE id = ?",
                Integer.class,
                owner.organizationId()
        )).isZero();
        assertThat(objectStorage.exists(file.getStoragePath())).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_events WHERE organization_id = ? AND event_type = 'ORGANIZATION_DELETED'",
                Integer.class,
                owner.organizationId()
        )).isEqualTo(1);
        assertStatus(owner.token(), HttpMethod.GET, "/api/invoices", null, 403);
    }

    @Test
    void permanentlyDeletesAnAccountAndItsSoleOrganization() {
        RegisteredUser owner = registerUser("account-delete");
        UploadInvoiceResponse upload = upload(owner.token(), officialSample()).getBody();
        assertThat(upload).isNotNull();
        StoredFile file = storedFile(upload.invoiceId());
        UUID userId = userId(owner.email());

        assertStatus(
                owner.token(),
                HttpMethod.DELETE,
                "/api/account",
                new AccountDeletionRequest("strong-password", "DELETE"),
                204
        );

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM app_users WHERE id = ?",
                Integer.class,
                userId
        )).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM organizations WHERE id = ?",
                Integer.class,
                owner.organizationId()
        )).isZero();
        assertThat(objectStorage.exists(file.getStoragePath())).isFalse();
        assertThat(login(owner.email(), "strong-password").getStatusCode().value()).isEqualTo(401);
        assertStatus(owner.token(), HttpMethod.GET, "/api/invoices", null, 403);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_events WHERE actor_user_id = ? AND event_type = 'ACCOUNT_DELETED'",
                Integer.class,
                userId
        )).isEqualTo(1);
    }

    @Test
    void retentionCleanupDeletesExpiredInvoicesAndStoredObjects() {
        RegisteredUser owner = registerUser("retention");
        UploadInvoiceResponse upload = upload(owner.token(), officialSample()).getBody();
        assertThat(upload).isNotNull();
        StoredFile file = storedFile(upload.invoiceId());
        jdbcTemplate.update(
                "UPDATE invoices SET created_at = ? WHERE id = ?",
                Timestamp.from(Instant.now().minusSeconds(31L * 24 * 60 * 60)),
                upload.invoiceId()
        );

        retentionCleanupService.processExpired();

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM invoices WHERE id = ?",
                Integer.class,
                upload.invoiceId()
        )).isZero();
        assertThat(objectStorage.exists(file.getStoragePath())).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM audit_events
                WHERE organization_id = ? AND event_type = 'INVOICE_RETENTION_DELETED'
                """,
                Integer.class,
                owner.organizationId()
        )).isEqualTo(1);
        assertStatus(owner.token(), HttpMethod.GET, "/api/invoices/" + upload.invoiceId(), null, 404);
    }

    private String register() {
        return registerUser("integration").token();
    }

    private RegisteredUser registerUser(String prefix) {
        ResponseEntity<AuthResponse> response = registerResponse(prefix);
        AuthResponse body = java.util.Objects.requireNonNull(response.getBody());
        assertThat(body.organization()).isNotNull();
        return new RegisteredUser(body.user().email(), body.token(), body.organization().id());
    }

    private ResponseEntity<AuthResponse> registerResponse(String prefix) {
        String email = prefix + "-" + UUID.randomUUID() + "@example.com";
        RegisterRequest request = new RegisterRequest(
                email,
                "strong-password",
                "Integration",
                "Test",
                "Integration Organization",
                OrganizationType.BUSINESS
        );
        ResponseEntity<AuthResponse> response = restTemplate.postForEntity("/api/auth/register", request, AuthResponse.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        return response;
    }

    private AuthResponse switchOrganization(String token, UUID organizationId) {
        ResponseEntity<AuthResponse> response = restTemplate.exchange(
                "/api/auth/switch-organization/{organizationId}",
                HttpMethod.POST,
                authorizedEntity(token, null),
                AuthResponse.class,
                organizationId
        );
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }

    private void invite(RegisteredUser owner, String email, MembershipRole role) {
        assertStatus(
                owner.token(),
                HttpMethod.POST,
                "/api/organizations/" + owner.organizationId() + "/invite",
                new InviteMemberRequest(email, role),
                201
        );
    }

    private CompanyResponse createCompany(String token, String name) {
        ResponseEntity<CompanyResponse> response = restTemplate.exchange(
                "/api/companies",
                HttpMethod.POST,
                authorizedEntity(token, companyRequest(name)),
                CompanyResponse.class
        );
        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }

    private CompanyRequest companyRequest(String name) {
        return new CompanyRequest(name, "5250000000", null, "Main Street 1", "Warsaw", "00-001", "PL");
    }

    private void assertStatus(String token, HttpMethod method, String path, Object body, int expectedStatus) {
        ResponseEntity<String> response = restTemplate.exchange(
                path,
                method,
                authorizedEntity(token, body),
                String.class
        );
        assertThat(response.getStatusCode().value())
                .as("%s %s response=%s", method, path, response.getBody())
                .isEqualTo(expectedStatus);
    }

    private void assertUploadStatus(String token, int expectedStatus) {
        ResponseEntity<String> response = uploadRaw(token, officialSample(), String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(expectedStatus);
    }

    private <T> HttpEntity<T> authorizedEntity(String token, T body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(body, headers);
    }

    private ResponseEntity<UploadInvoiceResponse> upload(String token, Path path) {
        return uploadRaw(token, path, UploadInvoiceResponse.class);
    }

    private <T> ResponseEntity<T> uploadRaw(String token, Path path, Class<T> responseType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new FileSystemResource(path));
        return restTemplate.exchange(
                "/api/invoices/upload",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                responseType
        );
    }

    private long countStoredFiles() {
        // language=PostgreSQL
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM stored_files", Long.class);
        return count == null ? 0 : count;
    }

    private ResponseEntity<AuthResponse> refresh(String cookie) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, cookie);
        return new TestRestTemplate().exchange(
                "http://localhost:" + serverPort + "/api/auth/refresh",
                HttpMethod.POST,
                new HttpEntity<>(headers),
                AuthResponse.class
        );
    }

    private ResponseEntity<AuthResponse> login(String email, String password) {
        return restTemplate.postForEntity(
                "/api/auth/login",
                new LoginRequest(email, password),
                AuthResponse.class
        );
    }

    private String refreshCookie(ResponseEntity<?> response) {
        String setCookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).isNotBlank();
        return setCookie.substring(0, setCookie.indexOf(';'));
    }

    private void insertAccountToken(UUID userId, String type, String rawToken) {
        jdbcTemplate.update(
                """
                INSERT INTO account_tokens (
                    user_id, type, token_hash, expires_at, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?)
                """,
                userId,
                type,
                sha256(rawToken),
                Timestamp.from(Instant.now().plusSeconds(3600)),
                Timestamp.from(Instant.now()),
                Timestamp.from(Instant.now())
        );
    }

    private UUID userId(String email) {
        return jdbcTemplate.queryForObject("SELECT id FROM app_users WHERE email = ?", UUID.class, email);
    }

    private StoredFile storedFile(UUID invoiceId) {
        UUID fileId = jdbcTemplate.queryForObject(
                "SELECT file_id FROM invoices WHERE id = ?",
                UUID.class,
                invoiceId
        );
        return storedFileRepository.findById(java.util.Objects.requireNonNull(fileId)).orElseThrow();
    }

    private Set<String> zipEntries(byte[] bytes) throws IOException {
        Set<String> entries = new HashSet<>();
        try (ZipInputStream zip = new ZipInputStream(new java.io.ByteArrayInputStream(bytes))) {
            var entry = zip.getNextEntry();
            while (entry != null) {
                entries.add(entry.getName());
                entry = zip.getNextEntry();
            }
        }
        return entries;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private Path officialSample() {
        try (var paths = Files.walk(Path.of("src/test/resources/sample-invoices/fa3-official"))) {
            return paths
                    .filter(path -> path.getFileName().toString().equalsIgnoreCase(EXEMPT_INVOICE_SAMPLE))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Official sample was not found: " + EXEMPT_INVOICE_SAMPLE
                    ));
        } catch (IOException ex) {
            throw new IllegalStateException("Official samples could not be read.", ex);
        }
    }

    private static Path createStorageRoot() {
        try {
            return Files.createTempDirectory("ksef-upload-integration-");
        } catch (IOException ex) {
            throw new IllegalStateException("Integration-test storage could not be created.", ex);
        }
    }

    private record RegisteredUser(String email, String token, UUID organizationId) {
    }
}

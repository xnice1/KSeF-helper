package com.ksefhelper.invoices;

import com.ksefhelper.auth.dto.AuthResponse;
import com.ksefhelper.auth.dto.LoginRequest;
import com.ksefhelper.auth.dto.RegisterRequest;
import com.ksefhelper.companies.dto.CompanyRequest;
import com.ksefhelper.companies.dto.CompanyResponse;
import com.ksefhelper.invoices.dto.InvoiceItemResponse;
import com.ksefhelper.invoices.dto.InvoicePreviewResponse;
import com.ksefhelper.invoices.dto.InvoiceSummaryResponse;
import com.ksefhelper.invoices.dto.UploadInvoiceResponse;
import com.ksefhelper.invoices.entity.InvoiceStatus;
import com.ksefhelper.organizations.dto.InviteMemberRequest;
import com.ksefhelper.organizations.dto.OrganizationRequest;
import com.ksefhelper.organizations.dto.OrganizationResponse;
import com.ksefhelper.organizations.entity.MembershipRole;
import com.ksefhelper.organizations.entity.OrganizationType;
import com.ksefhelper.validation.PythonTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
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

    @DynamicPropertySource
    static void applicationProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("app.storage.local-path", STORAGE_ROOT::toString);
        registry.add("app.xml.validator-command", PythonTestSupport::command);
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
        assertThat(Files.isRegularFile(Path.of(storagePath))).isTrue();
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

    private String register() {
        return registerUser("integration").token();
    }

    private RegisteredUser registerUser(String prefix) {
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
        assertThat(response.getBody().organization()).isNotNull();
        return new RegisteredUser(email, response.getBody().token(), response.getBody().organization().id());
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
        assertThat(response.getStatusCode().value()).as("%s %s", method, path).isEqualTo(expectedStatus);
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

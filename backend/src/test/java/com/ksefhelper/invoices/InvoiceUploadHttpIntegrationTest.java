package com.ksefhelper.invoices;

import com.ksefhelper.auth.dto.AuthResponse;
import com.ksefhelper.auth.dto.RegisterRequest;
import com.ksefhelper.invoices.dto.InvoiceItemResponse;
import com.ksefhelper.invoices.dto.InvoicePreviewResponse;
import com.ksefhelper.invoices.dto.UploadInvoiceResponse;
import com.ksefhelper.invoices.entity.InvoiceStatus;
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

    private String register() {
        String email = "integration-" + UUID.randomUUID() + "@example.com";
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
        return response.getBody().token();
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
}

package com.ksefhelper.security.ratelimit;

import com.ksefhelper.auth.dto.AuthResponse;
import com.ksefhelper.auth.dto.LoginRequest;
import com.ksefhelper.auth.dto.RegisterRequest;
import com.ksefhelper.common.web.ApiError;
import com.ksefhelper.invoices.dto.UploadInvoiceResponse;
import com.ksefhelper.organizations.entity.OrganizationType;
import com.ksefhelper.validation.PythonTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
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
class RateLimitHttpIntegrationTest {
    private static final Path STORAGE_ROOT = createStorageRoot();

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("ksef_helper_rate_limit_test")
            .withUsername("ksef")
            .withPassword("ksef");

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void disableAutomaticHttpRetries() {
        restTemplate.getRestTemplate().setRequestFactory(new SimpleClientHttpRequestFactory());
    }

    @DynamicPropertySource
    static void applicationProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("app.storage.local-path", STORAGE_ROOT::toString);
        registry.add("app.xml.validator-command", PythonTestSupport::command);
        registry.add("app.rate-limit.login.max-attempts", () -> 2);
        registry.add("app.rate-limit.login.max-attempts-per-ip", () -> 100);
        registry.add("app.rate-limit.login.window", () -> "5m");
        registry.add("app.rate-limit.upload.max-requests", () -> 1);
        registry.add("app.rate-limit.upload.window", () -> "5m");
    }

    @Test
    void blocksRepeatedLoginAttemptsWithRetryAfter() {
        RegisteredUser user = register("login-limit");
        LoginRequest wrongPassword = new LoginRequest(user.email(), "wrong-password");

        assertThat(restTemplate.postForEntity("/api/auth/login", wrongPassword, ApiError.class).getStatusCode().value())
                .isEqualTo(401);
        assertThat(restTemplate.postForEntity("/api/auth/login", wrongPassword, ApiError.class).getStatusCode().value())
                .isEqualTo(401);

        ResponseEntity<ApiError> limited = restTemplate.postForEntity("/api/auth/login", wrongPassword, ApiError.class);

        assertThat(limited.getStatusCode().value()).isEqualTo(429);
        assertThat(limited.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isNotBlank();
        assertThat(limited.getBody()).isNotNull();
        assertThat(limited.getBody().message()).contains("Too many login attempts");
    }

    @Test
    void blocksRepeatedUploadsBeforeCreatingAnotherStoredFile() {
        RegisteredUser user = register("upload-limit");
        long before = countStoredFiles();

        ResponseEntity<UploadInvoiceResponse> first = upload(user.token(), UploadInvoiceResponse.class);
        assertThat(first.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(countStoredFiles()).isEqualTo(before + 1);

        ResponseEntity<ApiError> limited = upload(user.token(), ApiError.class);

        assertThat(limited.getStatusCode().value()).isEqualTo(429);
        assertThat(limited.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isNotBlank();
        assertThat(limited.getBody()).isNotNull();
        assertThat(limited.getBody().message()).contains("Too many invoice uploads");
        assertThat(countStoredFiles()).isEqualTo(before + 1);
    }

    private RegisteredUser register(String prefix) {
        String email = prefix + "-" + UUID.randomUUID() + "@example.com";
        RegisterRequest request = new RegisterRequest(
                email,
                "strong-password",
                "Rate",
                "Limit",
                "Rate Limit Organization",
                OrganizationType.BUSINESS
        );
        ResponseEntity<AuthResponse> response = restTemplate.postForEntity("/api/auth/register", request, AuthResponse.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        return new RegisteredUser(email, response.getBody().token());
    }

    private <T> ResponseEntity<T> upload(String token, Class<T> responseType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new FileSystemResource(officialSample()));
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
                    .filter(path -> path.getFileName().toString().equalsIgnoreCase("FA_3_Przykład_9.xml"))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Official FA(3) sample was not found."));
        } catch (IOException ex) {
            throw new IllegalStateException("Official samples could not be read.", ex);
        }
    }

    private static Path createStorageRoot() {
        try {
            return Files.createTempDirectory("ksef-rate-limit-integration-");
        } catch (IOException ex) {
            throw new IllegalStateException("Rate-limit integration storage could not be created.", ex);
        }
    }

    private record RegisteredUser(String email, String token) {
    }
}

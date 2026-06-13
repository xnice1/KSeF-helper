package com.ksefhelper.backup;

import com.ksefhelper.files.storage.LocalObjectStorage;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class PostgresBackupRestoreIntegrationTest {
    private static final String RESTORED_DATABASE = "ksef_helper_restored";

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("ksef_helper_backup_test")
            .withUsername("ksef")
            .withPassword("ksef");

    @TempDir
    Path temporaryDirectory;

    @Test
    void restoresDatabaseAndStoredObjectWithChecksumAndAuditImmutability() throws Exception {
        createDatabaseRoles();
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .placeholders(Map.of(
                        "runtimeRole", "ksef_runtime",
                        "auditMaintenanceRole", "ksef_audit_maintainer"
                ))
                .load()
                .migrate();

        UUID organizationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID auditId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();
        byte[] objectBytes = "<Faktura>restore-drill</Faktura>".getBytes(StandardCharsets.UTF_8);
        String checksum = sha256(objectBytes);
        String storageKey = organizationId + "/restore-drill.xml";
        LocalObjectStorage backupStorage = new LocalObjectStorage(
                temporaryDirectory.resolve("object-backup").toString()
        );
        backupStorage.put(storageKey, objectBytes, "application/xml", checksum);
        seedDatabase(userId, organizationId, auditId, fileId, invoiceId, storageKey, checksum, objectBytes.length);
        assertRuntimeRoleCannotMutateAudit(auditId);
        runPrivilegedAuditMaintenance();

        assertSuccessful(POSTGRES.execInContainer(
                "pg_dump",
                "-U", POSTGRES.getUsername(),
                "--format=custom",
                "--no-owner",
                "--file=/tmp/ksef-helper.dump",
                POSTGRES.getDatabaseName()
        ));
        assertSuccessful(POSTGRES.execInContainer(
                "createdb",
                "-U", POSTGRES.getUsername(),
                RESTORED_DATABASE
        ));
        assertSuccessful(POSTGRES.execInContainer(
                "pg_restore",
                "-U", POSTGRES.getUsername(),
                "--no-owner",
                "--dbname=" + RESTORED_DATABASE,
                "/tmp/ksef-helper.dump"
        ));

        String restoredUrl = "jdbc:postgresql://%s:%d/%s".formatted(
                POSTGRES.getHost(),
                POSTGRES.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT),
                RESTORED_DATABASE
        );
        try (var connection = DriverManager.getConnection(
                restoredUrl,
                POSTGRES.getUsername(),
                POSTGRES.getPassword()
        )) {
            try (var statement = connection.prepareStatement(
                    "SELECT name FROM organizations WHERE id = ?"
            )) {
                statement.setObject(1, organizationId);
                try (var result = statement.executeQuery()) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getString("name")).isEqualTo("Restore Drill Organization");
                }
            }
            try (var statement = connection.prepareStatement(
                    "SELECT event_type FROM audit_events WHERE id = ?"
            )) {
                statement.setObject(1, auditId);
                try (var result = statement.executeQuery()) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getString("event_type")).isEqualTo("ORGANIZATION_CREATED");
                }
            }
            assertThatThrownBy(() -> updateAuditEvent(connection, auditId))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("append-only");

            try (var statement = connection.prepareStatement(
                    """
                    SELECT file.storage_path, file.checksum
                    FROM invoices invoice
                    JOIN stored_files file ON file.id = invoice.file_id
                    WHERE invoice.id = ?
                    """
            )) {
                statement.setObject(1, invoiceId);
                try (var result = statement.executeQuery()) {
                    assertThat(result.next()).isTrue();
                    String restoredStorageKey = result.getString("storage_path");
                    String restoredChecksum = result.getString("checksum");
                    LocalObjectStorage restoredStorage = new LocalObjectStorage(
                            temporaryDirectory.resolve("restored-objects").toString()
                    );
                    restoredStorage.put(
                            restoredStorageKey,
                            backupStorage.read(restoredStorageKey),
                            "application/xml",
                            restoredChecksum
                    );
                    byte[] restoredBytes = restoredStorage.read(restoredStorageKey);
                    assertThat(restoredBytes).isEqualTo(objectBytes);
                    assertThat(sha256(restoredBytes)).isEqualTo(restoredChecksum);
                }
            }
        }
    }

    private void createDatabaseRoles() throws SQLException {
        try (var connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword()
        ); var statement = connection.createStatement()) {
            statement.execute("CREATE ROLE ksef_runtime LOGIN PASSWORD 'runtime-password'");
            statement.execute("CREATE ROLE ksef_audit_maintainer LOGIN PASSWORD 'maintenance-password'");
        }
    }

    private void assertRuntimeRoleCannotMutateAudit(UUID auditId) {
        assertThatThrownBy(() -> {
            try (var connection = DriverManager.getConnection(
                    POSTGRES.getJdbcUrl(),
                    "ksef_runtime",
                    "runtime-password"
            ); var statement = connection.createStatement()) {
                statement.execute("SELECT set_config('app.audit_redaction', 'on', FALSE)");
                statement.executeUpdate(
                        "UPDATE audit_events SET actor_email = NULL WHERE id = '" + auditId + "'"
                );
            }
        }).isInstanceOf(SQLException.class);
    }

    private void runPrivilegedAuditMaintenance() throws SQLException {
        try (var connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(),
                "ksef_audit_maintainer",
                "maintenance-password"
        ); var statement = connection.createStatement()) {
            statement.executeQuery(
                    "SELECT * FROM run_audit_retention(CURRENT_TIMESTAMP - INTERVAL '1 day', "
                            + "CURRENT_TIMESTAMP - INTERVAL '365 days')"
            );
        }
    }

    private void seedDatabase(
            UUID userId,
            UUID organizationId,
            UUID auditId,
            UUID fileId,
            UUID invoiceId,
            String storageKey,
            String checksum,
            long sizeBytes
    ) throws SQLException {
        try (var connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword()
        )) {
            try (var statement = connection.prepareStatement(
                    """
                    INSERT INTO app_users (
                        id, email, password_hash, first_name, last_name, created_at, updated_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?)
                    """
            )) {
                statement.setObject(1, userId);
                statement.setString(2, "restore@example.com");
                statement.setString(3, "not-a-real-password-hash");
                statement.setString(4, "Restore");
                statement.setString(5, "Test");
                statement.setTimestamp(6, Timestamp.from(Instant.now()));
                statement.setTimestamp(7, Timestamp.from(Instant.now()));
                statement.executeUpdate();
            }
            try (var statement = connection.prepareStatement(
                    """
                    INSERT INTO organizations (id, name, type, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?)
                    """
            )) {
                statement.setObject(1, organizationId);
                statement.setString(2, "Restore Drill Organization");
                statement.setString(3, "BUSINESS");
                statement.setTimestamp(4, Timestamp.from(Instant.now()));
                statement.setTimestamp(5, Timestamp.from(Instant.now()));
                statement.executeUpdate();
            }
            try (var statement = connection.prepareStatement(
                    """
                    INSERT INTO stored_files (
                        id, organization_id, original_filename, storage_path,
                        content_type, size_bytes, checksum, created_at, updated_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """
            )) {
                statement.setObject(1, fileId);
                statement.setObject(2, organizationId);
                statement.setString(3, "restore-drill.xml");
                statement.setString(4, storageKey);
                statement.setString(5, "application/xml");
                statement.setLong(6, sizeBytes);
                statement.setString(7, checksum);
                statement.setTimestamp(8, Timestamp.from(Instant.now()));
                statement.setTimestamp(9, Timestamp.from(Instant.now()));
                statement.executeUpdate();
            }
            try (var statement = connection.prepareStatement(
                    """
                    INSERT INTO invoices (
                        id, organization_id, file_id, invoice_number, status, created_at, updated_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?)
                    """
            )) {
                statement.setObject(1, invoiceId);
                statement.setObject(2, organizationId);
                statement.setObject(3, fileId);
                statement.setString(4, "RESTORE/1");
                statement.setString(5, "VALID");
                statement.setTimestamp(6, Timestamp.from(Instant.now()));
                statement.setTimestamp(7, Timestamp.from(Instant.now()));
                statement.executeUpdate();
            }
            try (var statement = connection.prepareStatement(
                    """
                    INSERT INTO memberships (
                        user_id, organization_id, role, created_at, updated_at
                    ) VALUES (?, ?, ?, ?, ?)
                    """
            )) {
                statement.setObject(1, userId);
                statement.setObject(2, organizationId);
                statement.setString(3, "OWNER");
                statement.setTimestamp(4, Timestamp.from(Instant.now()));
                statement.setTimestamp(5, Timestamp.from(Instant.now()));
                statement.executeUpdate();
            }
            try (var statement = connection.prepareStatement(
                    """
                    INSERT INTO audit_events (
                        id, occurred_at, actor_user_id, actor_email, organization_id,
                        event_type, target_type, target_id, metadata
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """
            )) {
                statement.setObject(1, auditId);
                statement.setTimestamp(2, Timestamp.from(Instant.now()));
                statement.setObject(3, userId);
                statement.setString(4, "restore@example.com");
                statement.setObject(5, organizationId);
                statement.setString(6, "ORGANIZATION_CREATED");
                statement.setString(7, "organization");
                statement.setString(8, organizationId.toString());
                statement.setString(9, "{\"source\":\"restore-test\"}");
                statement.executeUpdate();
            }
        }
    }

    private void updateAuditEvent(java.sql.Connection connection, UUID auditId) throws SQLException {
        try (var statement = connection.prepareStatement(
                "UPDATE audit_events SET metadata = '{}' WHERE id = ?"
        )) {
            statement.setObject(1, auditId);
            statement.executeUpdate();
        }
    }

    private String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private void assertSuccessful(org.testcontainers.containers.Container.ExecResult result) {
        assertThat(result.getExitCode())
                .withFailMessage("Command failed with exit %s:%n%s%n%s",
                        result.getExitCode(),
                        result.getStdout(),
                        result.getStderr())
                .isZero();
    }
}

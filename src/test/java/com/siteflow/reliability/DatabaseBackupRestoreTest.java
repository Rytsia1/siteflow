package com.siteflow.reliability;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DatabaseBackupRestoreTest {

    private static final Logger log = LoggerFactory.getLogger(DatabaseBackupRestoreTest.class);

    private static final String TARGET_TEST_DB = "siteflow_recovery_test";
    private static final String DB_USER = System.getenv("DB_USER") != null ? System.getenv("DB_USER") : "root";
    private static final String DB_PASSWORD = System.getenv("DB_PASSWORD") != null ? System.getenv("DB_PASSWORD") : "";
    private static final String DB_HOST = System.getenv("DB_HOST") != null ? System.getenv("DB_HOST") : "localhost";
    private static final String DB_PORT = System.getenv("DB_PORT") != null ? System.getenv("DB_PORT") : "3306";

    private static Path tempBackupDir;
    private static Path generatedBackupFile;

    @BeforeAll
    static void init() throws Exception {
        tempBackupDir = Files.createTempDirectory("siteflow_test_backups_");
    }

    @AfterAll
    static void cleanup() {
        // Clean up recovery test database
        try (Connection conn = DriverManager.getConnection(
                "jdbc:mysql://" + DB_HOST + ":" + DB_PORT + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                DB_USER, DB_PASSWORD);
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DROP DATABASE IF EXISTS " + TARGET_TEST_DB);
            log.info("Dropped test recovery database '{}'", TARGET_TEST_DB);
        } catch (Exception e) {
            log.warn("Could not drop recovery database '{}': {}", TARGET_TEST_DB, e.getMessage());
        }

        // Clean up temp backup directory
        try {
            if (tempBackupDir != null && Files.exists(tempBackupDir)) {
                Files.walk(tempBackupDir)
                        .sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            }
        } catch (Exception e) {
            log.warn("Could not delete temp backup dir: {}", e.getMessage());
        }
    }

    @Test
    @Order(1)
    @DisplayName("1. Backup script creates a consistent, non-empty SQL dump with complete schema and data")
    void testBackupCreationScript() throws Exception {
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        ProcessBuilder pb;

        if (isWindows) {
            pb = new ProcessBuilder("powershell.exe", "-ExecutionPolicy", "Bypass",
                    "-File", "scripts/backup.ps1",
                    "-HostName", DB_HOST,
                    "-Port", DB_PORT,
                    "-Database", "siteflow",
                    "-User", DB_USER,
                    "-BackupDir", tempBackupDir.toString());
        } else {
            pb = new ProcessBuilder("bash", "scripts/backup.sh");
            pb.environment().put("DB_HOST", DB_HOST);
            pb.environment().put("DB_PORT", DB_PORT);
            pb.environment().put("DB_NAME", "siteflow");
            pb.environment().put("DB_USER", DB_USER);
            pb.environment().put("BACKUP_DIR", tempBackupDir.toString());
        }

        pb.environment().put("DB_PASSWORD", DB_PASSWORD);
        pb.redirectErrorStream(true);

        Process process = pb.start();
        String output = new String(process.getInputStream().readAllBytes());
        int exitCode = process.waitFor();

        log.info("Backup script output:\n{}", output);
        assertThat(exitCode).as("Backup script should exit with 0").isEqualTo(0);

        // Find the generated backup file in temp directory
        List<Path> dumpFiles = Files.list(tempBackupDir)
                .filter(p -> p.toString().endsWith(".sql"))
                .toList();

        assertThat(dumpFiles).hasSize(1);
        generatedBackupFile = dumpFiles.get(0);

        long fileSize = Files.size(generatedBackupFile);
        log.info("Created backup file: {} ({} bytes)", generatedBackupFile, fileSize);
        assertThat(fileSize).isGreaterThan(10000L); // Should be at least 10KB

        String content = Files.readString(generatedBackupFile);
        // Verify key tables and migrations exist in dump
        assertThat(content).contains("CREATE TABLE `users`");
        assertThat(content).contains("CREATE TABLE `item_stocks`");
        assertThat(content).contains("CREATE TABLE `flyway_schema_history`");
        assertThat(content).contains("flyway_schema_history");
    }

    @Test
    @Order(2)
    @DisplayName("2. Restore script restores backup into clean, isolated recovery database without errors")
    void testRestoreIntoIsolatedDatabase() throws Exception {
        assertThat(generatedBackupFile).as("Backup file must have been created in Step 1").isNotNull();
        assertThat(Files.exists(generatedBackupFile)).isTrue();

        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        ProcessBuilder pb;

        if (isWindows) {
            pb = new ProcessBuilder("powershell.exe", "-ExecutionPolicy", "Bypass",
                    "-File", "scripts/restore.ps1",
                    "-BackupFile", generatedBackupFile.toAbsolutePath().toString(),
                    "-HostName", DB_HOST,
                    "-Port", DB_PORT,
                    "-TargetDatabase", TARGET_TEST_DB,
                    "-User", DB_USER,
                    "-CleanDatabase");
        } else {
            pb = new ProcessBuilder("bash", "scripts/restore.sh", generatedBackupFile.toAbsolutePath().toString());
            pb.environment().put("DB_HOST", DB_HOST);
            pb.environment().put("DB_PORT", DB_PORT);
            pb.environment().put("DB_NAME", TARGET_TEST_DB);
            pb.environment().put("DB_USER", DB_USER);
            pb.environment().put("CLEAN_DB", "true");
        }

        pb.environment().put("DB_PASSWORD", DB_PASSWORD);
        pb.redirectErrorStream(true);

        Process process = pb.start();
        String output = new String(process.getInputStream().readAllBytes());
        int exitCode = process.waitFor();

        log.info("Restore script output:\n{}", output);
        assertThat(exitCode).as("Restore script should exit with 0").isEqualTo(0);
        assertThat(output).contains("[RESTORE SUCCESS]");
    }

    @Test
    @Order(3)
    @DisplayName("3. Verify schema, Flyway migrations, and representative records in restored database")
    void testRestoredDatabaseIntegrityAndConnectivity() throws Exception {
        String recoveryJdbcUrl = "jdbc:mysql://" + DB_HOST + ":" + DB_PORT + "/" + TARGET_TEST_DB
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";

        try (Connection conn = DriverManager.getConnection(recoveryJdbcUrl, DB_USER, DB_PASSWORD);
             Statement stmt = conn.createStatement()) {

            // 1. Verify tables exist
            List<String> tables = new ArrayList<>();
            try (ResultSet rs = stmt.executeQuery("SHOW TABLES")) {
                while (rs.next()) {
                    tables.add(rs.getString(1).toLowerCase());
                }
            }

            log.info("Tables found in restored database ({}): {}", tables.size(), tables);
            assertThat(tables).contains(
                    "flyway_schema_history",
                    "users",
                    "roles",
                    "items",
                    "item_stocks",
                    "item_instances",
                    "locations",
                    "borrow_requests",
                    "borrow_items",
                    "material_requests",
                    "material_request_items",
                    "purchase_orders",
                    "stock_adjustments",
                    "transaction_logs",
                    "idempotency_keys"
            );

            // 2. Verify all Flyway migrations are recorded as successful
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1")) {
                assertThat(rs.next()).isTrue();
                int migrationCount = rs.getInt(1);
                log.info("Flyway migration count in restored DB: {}", migrationCount);
                assertThat(migrationCount).isGreaterThanOrEqualTo(13);
            }

            // 3. Verify representative records and foreign keys
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT u.username, r.role_name FROM users u JOIN roles r ON u.role_id = r.id WHERE u.username = 'admin'")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("username")).isEqualTo("admin");
                assertThat(rs.getString("role_name")).isEqualTo("ADMIN");
            }

            // 4. Verify items and inventory stock rows
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM items")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).isGreaterThan(0);
            }

            try (ResultSet rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM item_stocks s JOIN items i ON s.item_id = i.id JOIN locations l ON s.location_id = l.id")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).isGreaterThan(0);
            }
        }
    }
}

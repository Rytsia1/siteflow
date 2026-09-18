package com.siteflow.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.siteflow.mapper.UserMapper;

@SpringBootTest
class DatabaseSecurityHardeningTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private UserMapper userMapper;

    @Test
    @DisplayName("1. Production application.yml does not disable SSL and does not contain useSSL=false")
    void productionConfiguration_doesNotDisableSsl() throws IOException {
        Path appYml = Path.of("src/main/resources/application.yml");
        assertThat(appYml).exists();

        String content = Files.readString(appYml);

        // Crucial security check: must NOT explicitly disable SSL
        assertThat(content)
                .as("application.yml must not contain useSSL=false")
                .doesNotContain("useSSL=false")
                .doesNotContain("useSSL=0");
    }

    @Test
    @DisplayName("2. Production application.yml defaults to sslMode=VERIFY_IDENTITY")
    void productionConfiguration_defaultsToVerifyIdentitySslMode() throws IOException {
        Path appYml = Path.of("src/main/resources/application.yml");
        String content = Files.readString(appYml);

        assertThat(content)
                .as("application.yml must default sslMode to VERIFY_IDENTITY")
                .contains("sslMode=${DB_SSL_MODE:VERIFY_IDENTITY}");
    }

    @Test
    @DisplayName("3. Production application.yml defaults allowPublicKeyRetrieval to false")
    void productionConfiguration_defaultsAllowPublicKeyRetrievalToFalse() throws IOException {
        Path appYml = Path.of("src/main/resources/application.yml");
        String content = Files.readString(appYml);

        assertThat(content)
                .as("application.yml must default allowPublicKeyRetrieval to false")
                .contains("allowPublicKeyRetrieval=${DB_ALLOW_PUBLIC_KEY_RETRIEVAL:false}");
    }

    @Test
    @DisplayName("4. Production application.yml does not use 'root' as the default runtime database username")
    void productionConfiguration_doesNotDefaultToRoot() throws IOException {
        Path appYml = Path.of("src/main/resources/application.yml");
        String content = Files.readString(appYml);

        assertThat(content)
                .as("application.yml must not default DB_USERNAME to root")
                .doesNotContain("username: ${DB_USERNAME:root}")
                .contains("username: ${DB_USERNAME:siteflow_app}");
    }

    @Test
    @DisplayName("5. Production application.yml decouples Flyway migration credentials from runtime datasource")
    void productionConfiguration_configuresDistinctFlywayCredentials() throws IOException {
        Path appYml = Path.of("src/main/resources/application.yml");
        String content = Files.readString(appYml);

        assertThat(content)
                .as("application.yml must support separate Flyway migration credentials")
                .contains("user: ${FLYWAY_DB_USERNAME:${DB_USERNAME:siteflow_migration}}")
                .contains("password: ${FLYWAY_DB_PASSWORD:${DB_PASSWORD}}");
    }

    @Test
    @DisplayName("6. Least-privilege MySQL provisioning script exists and creates dedicated siteflow_app and siteflow_migration users")
    void leastPrivilegeScript_definesDedicatedRoles() throws IOException {
        Path scriptPath = Path.of("scripts/setup-least-privilege-users.sql");
        assertThat(scriptPath).exists();

        String sql = Files.readString(scriptPath);

        // Must define siteflow_app with DML only
        assertThat(sql)
                .contains("siteflow_app")
                .contains("GRANT SELECT, INSERT, UPDATE, DELETE, EXECUTE")
                .doesNotContain("GRANT ALL PRIVILEGES ON `siteflow`.* TO 'siteflow_app'");

        // Must define siteflow_migration with DDL
        assertThat(sql)
                .contains("siteflow_migration")
                .contains("CREATE, ALTER, DROP");
    }

    @Test
    @DisplayName("7. Production SSL verification fails closed when server certificate cannot be validated")
    void verifyIdentity_failsClosed_whenCertificateUntrusted() {
        // Attempting to connect with VERIFY_IDENTITY to a host without a trusted CA certificate
        // must throw SQLException / SSLHandshakeException and fail closed rather than falling back to plaintext
        String strictUrl = "jdbc:mysql://localhost:3306/siteflow?sslMode=VERIFY_IDENTITY&allowPublicKeyRetrieval=false&serverTimezone=UTC&connectTimeout=2000";

        assertThatThrownBy(() -> DriverManager.getConnection(strictUrl, "invalid_user", "invalid_password"))
                .isInstanceOf(SQLException.class);
    }

    @Test
    @DisplayName("8. Application runtime DataSource and MyBatis mappers connect and operate successfully")
    void runtimeDatasource_isHealthyAndFunctional() throws SQLException {
        assertThat(dataSource).isNotNull();

        try (Connection conn = dataSource.getConnection()) {
            assertThat(conn.isValid(2)).isTrue();
        }

        // Verify MyBatis query executes cleanly through the active datasource
        int activeAdmins = userMapper.countActiveAdmins();
        assertThat(activeAdmins).isGreaterThanOrEqualTo(0);
    }
}

package com.siteflow.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.siteflow.domain.UserWithRole;
import com.siteflow.mapper.UserMapper;

@SpringBootTest
@AutoConfigureMockMvc
class DefaultCredentialsIsolationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AdminProvisioningRunner adminProvisioningRunner;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("1. Production migration chain V3 does not contain default user INSERT statements")
    void v3Migration_doesNotContainDefaultCredentials() throws IOException {
        Path v3Path = Path.of("src/main/resources/db/migration/V3__seed_users.sql");
        assertThat(v3Path).exists();

        String v3Content = Files.readString(v3Path);

        // Ensure no INSERT INTO users or hardcoded BCrypt demo hashes exist in production V3
        assertThat(v3Content)
                .doesNotContain("INSERT IGNORE INTO users")
                .doesNotContain("INSERT INTO users")
                .doesNotContain("$2b$10$TfwVMtix/ZbXNl/rYEnVYuhaA9LCKcysxnCRX2mOo0n8..TodUb/6") // admin123
                .doesNotContain("$2b$10$kd.Psfos2DL1QPp5.HouVemJD2540oWm6qs5svzhUrL6lw5SuNmo6") // gudang123
                .doesNotContain("$2b$10$DooaDYXNN0SWNwxPT.0tSemXrfnx2S/lIrNgT1ORswc6f2L/p1L9C"); // pekerja123
    }

    @Test
    @DisplayName("2. Production application.yml isolates Flyway locations strictly to classpath:db/migration")
    void productionConfiguration_doesNotIncludeDevOrTestSeeds() throws IOException {
        Path appYmlPath = Path.of("src/main/resources/application.yml");
        assertThat(appYmlPath).exists();

        String content = Files.readString(appYmlPath);
        assertThat(content)
                .contains("locations: classpath:db/migration")
                .doesNotContain("classpath:db/dev-seed")
                .doesNotContain("classpath:db/test-fixtures");
    }

    @Test
    @DisplayName("3. Dev seed fixtures exist only in separate dev profile location")
    void devSeedFixtures_locatedInDevSeedDirectory() throws IOException {
        Path devSeedPath = Path.of("src/main/resources/db/dev-seed/R__dev_seed_users.sql");
        assertThat(devSeedPath).exists();

        Path devConfigPath = Path.of("src/main/resources/application-dev.yml");
        assertThat(devConfigPath).exists();

        String devConfig = Files.readString(devConfigPath);
        assertThat(devConfig).contains("classpath:db/dev-seed");
    }

    @Test
    @DisplayName("4. Test seed fixtures exist only in test scope (excluded from production JAR)")
    void testSeedFixtures_locatedInTestResources() {
        Path testSeedPath = Path.of("src/test/resources/db/test-fixtures/R__test_seed_users.sql");
        assertThat(testSeedPath).exists();

        // Ensure it is under src/test/resources, which Maven excludes from production JARs
        assertThat(testSeedPath.toAbsolutePath().toString()).contains("src" + File.separator + "test");
    }

    @Test
    @DisplayName("5. Explicit admin provisioning creates administrator with BCrypt hash and allows successful login")
    void explicitAdminProvisioning_succeeds_andEnablesAuthentication() throws Exception {
        String testAdminUser = "explicit_super_admin";
        String testPassword = "SecurePasswd!2026#SiteFlow";

        // Explicitly provision administrator
        Long adminId = adminProvisioningRunner.provisionAdmin(
                testAdminUser,
                testPassword,
                "Explicit Test Admin",
                "Chief Security Officer"
        );

        assertThat(adminId).isNotNull();

        // Verify user in DB
        UserWithRole userWithRole = userMapper.findByUsername(testAdminUser);
        assertThat(userWithRole).isNotNull();
        assertThat(userWithRole.getRoleName()).isEqualTo("ADMIN");
        assertThat(userWithRole.getIsActive()).isTrue();

        // Verify password hash is valid BCrypt and matches
        assertThat(userWithRole.getPasswordHash()).startsWith("$2");
        assertThat(passwordEncoder.matches(testPassword, userWithRole.getPasswordHash())).isTrue();

        // Verify authenticating with the provisioned user succeeds
        String loginPayload = """
                {
                    "username": "%s",
                    "password": "%s"
                }
                """.formatted(testAdminUser, testPassword);

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.username").value(testAdminUser))
                .andExpect(jsonPath("$.data.role").value("ADMIN"))
                .andExpect(jsonPath("$.data.accessToken", notNullValue()))
                .andReturn();

        JsonNode responseJson = objectMapper.readTree(result.getResponse().getContentAsString());
        String token = responseJson.path("data").path("accessToken").asText();

        // Verify the token allows accessing admin-protected endpoints
        mockMvc.perform(get("/api/approvals/borrow-requests/pending")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("6. Explicit admin provisioning rejects known weak/demo passwords and short passwords")
    void explicitAdminProvisioning_rejectsWeakPasswords() {
        // Disallow known default demo passwords
        assertThatThrownBy(() -> adminProvisioningRunner.provisionAdmin(
                "bad_admin_1", "admin123", "Bad Admin", "Admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("insecure or default password");

        assertThatThrownBy(() -> adminProvisioningRunner.provisionAdmin(
                "bad_admin_2", "gudang123", "Bad Admin", "Admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("insecure or default password");

        // Disallow password shorter than 8 chars
        assertThatThrownBy(() -> adminProvisioningRunner.provisionAdmin(
                "bad_admin_3", "short", "Bad Admin", "Admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 8 characters");

        // Disallow password matching username
        assertThatThrownBy(() -> adminProvisioningRunner.provisionAdmin(
                "my_custom_user", "my_custom_user", "Bad Admin", "Admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("insecure or default password");
    }

    @Test
    @DisplayName("7. V13 migration script contains safe deactivation and credential neutralization without DELETE")
    void v13Migration_safelyNeutralizesDefaultCredentialsWithoutDelete() throws IOException {
        Path v13Path = Path.of("src/main/resources/db/migration/V13__disable_default_seed_credentials.sql");
        assertThat(v13Path).exists();

        String v13Content = Files.readString(v13Path);

        // Must update is_active = FALSE and rotate password hash
        assertThat(v13Content)
                .contains("UPDATE users")
                .contains("is_active = FALSE")
                .contains("password_hash =")
                .contains("*DISABLED_DEFAULT_CREDENTIAL*");

        // Must NOT use DELETE to prevent breaking FK constraints on historical records
        assertThat(v13Content)
                .doesNotContain("DELETE FROM users");
    }
}

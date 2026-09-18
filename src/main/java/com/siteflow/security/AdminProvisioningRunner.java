package com.siteflow.security;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.siteflow.domain.User;
import com.siteflow.domain.enums.AuditEventType;
import com.siteflow.mapper.UserMapper;
import com.siteflow.service.AuditService;

import lombok.extern.slf4j.Slf4j;

/**
 * Explicit, fail-closed administrator provisioning runner.
 *
 * Security Contract:
 * 1. Fail closed by default: Does nothing on normal application startup.
 *    No default or backdoor accounts are ever silently created.
 * 2. Explicit opt-in only: Runs only when '--siteflow.provision-admin=true' or
 *    'SITEFLOW_PROVISION_ADMIN=true' is explicitly provided.
 * 3. Strong credentials enforcement: Rejects empty, short (<8 chars), or known default
 *    passwords (e.g. 'admin123', 'password').
 * 4. BCrypt hashing: Securely hashes credentials using the configured PasswordEncoder.
 * 5. Accountability: Records a USER_PROVISIONED audit event.
 */
@Slf4j
@Component
@Order(100)
public class AdminProvisioningRunner implements ApplicationRunner {

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9._-]{3,50}$");
    private static final Set<String> DISALLOWED_PASSWORDS = Set.of(
            "admin123", "gudang123", "pekerja123",
            "admin", "password", "12345678", "root", "qwerty"
    );

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public AdminProvisioningRunner(
            UserMapper userMapper,
            PasswordEncoder passwordEncoder,
            AuditService auditService) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean requested = isProvisioningRequested(args);
        if (!requested) {
            // Fail closed: no explicit flag -> do nothing
            return;
        }

        log.info("Explicit administrator provisioning requested via configuration/CLI flags.");

        String username = resolveOption(args, "siteflow.admin.username", "SITEFLOW_ADMIN_USERNAME");
        String password = resolveOption(args, "siteflow.admin.password", "SITEFLOW_ADMIN_PASSWORD");
        String fullName = resolveOption(args, "siteflow.admin.full-name", "SITEFLOW_ADMIN_FULL_NAME");
        String jobPosition = resolveOption(args, "siteflow.admin.job-position", "SITEFLOW_ADMIN_JOB_POSITION");
        boolean exitAfter = Boolean.parseBoolean(resolveOption(args, "siteflow.provision-admin.exit-after", "SITEFLOW_PROVISION_ADMIN_EXIT_AFTER"));

        if (fullName == null || fullName.isBlank()) {
            fullName = "System Administrator";
        }
        if (jobPosition == null || jobPosition.isBlank()) {
            jobPosition = "Site Administrator";
        }

        Long userId = provisionAdmin(username, password, fullName, jobPosition);
        log.info("Administrator user '{}' (ID: {}) successfully provisioned.", username, userId);

        if (exitAfter) {
            log.info("Provisioning complete. Exiting application as requested (--siteflow.provision-admin.exit-after=true).");
            System.exit(0);
        }
    }

    /**
     * Programmatically provisions an administrator user with explicit credential validation,
     * BCrypt hashing, and audit logging.
     *
     * @param username    the administrative username
     * @param rawPassword the raw plaintext password to hash and store
     * @param fullName    the display name
     * @param jobPosition the job position/title
     * @return the ID of the provisioned administrator
     */
    @Transactional
    public Long provisionAdmin(String username, String rawPassword, String fullName, String jobPosition) {
        validateInputs(username, rawPassword);

        Long adminRoleId = userMapper.findRoleIdByName("ADMIN");
        if (adminRoleId == null) {
            throw new IllegalStateException("Database integrity error: 'ADMIN' role does not exist in roles table.");
        }

        LocalDateTime now = LocalDateTime.now();
        String hashedPassword = passwordEncoder.encode(rawPassword);

        User existingUser = userMapper.findUserEntityByUsername(username);
        Long userId;

        if (existingUser != null) {
            log.warn("User '{}' already exists (ID: {}). Updating credentials and ensuring account is active.", username, existingUser.getId());
            existingUser.setPasswordHash(hashedPassword);
            existingUser.setFullName(fullName);
            existingUser.setJobPosition(jobPosition);
            existingUser.setIsActive(true);
            existingUser.setDeactivatedAt(null);
            existingUser.setUpdatedAt(now);
            userMapper.updateProvisionedUser(existingUser);
            userId = existingUser.getId();
        } else {
            User newUser = User.builder()
                    .roleId(adminRoleId)
                    .username(username)
                    .passwordHash(hashedPassword)
                    .fullName(fullName)
                    .jobPosition(jobPosition)
                    .isActive(true)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
            userMapper.insertUser(newUser);
            userId = newUser.getId();
        }

        if (auditService != null) {
            auditService.recordBusinessEvent(
                    AuditEventType.USER_PROVISIONED,
                    "USER",
                    userId,
                    "SUCCESS",
                    existingUser != null ? "INACTIVE" : "NONE",
                    "ACTIVE",
                    "Explicit administrator provisioning for username: " + username);
        }

        return userId;
    }

    private boolean isProvisioningRequested(ApplicationArguments args) {
        if (args.containsOption("siteflow.provision-admin")) {
            String val = args.getOptionValues("siteflow.provision-admin").stream().findFirst().orElse("false");
            return Boolean.parseBoolean(val);
        }
        String env = System.getenv("SITEFLOW_PROVISION_ADMIN");
        return Boolean.parseBoolean(env);
    }

    private String resolveOption(ApplicationArguments args, String optionName, String envName) {
        if (args.containsOption(optionName)) {
            return args.getOptionValues(optionName).stream().findFirst().orElse(null);
        }
        String env = System.getenv(envName);
        if (env != null && !env.isBlank()) {
            return env;
        }
        return null;
    }

    private void validateInputs(String username, String rawPassword) {
        if (username == null || !USERNAME_PATTERN.matcher(username).matches()) {
            throw new IllegalArgumentException(
                    "Invalid username. Must be between 3 and 50 characters, containing only letters, numbers, '.', '_', or '-'.");
        }
        if (rawPassword == null || rawPassword.length() < 8) {
            throw new IllegalArgumentException("Administrator password must be at least 8 characters long.");
        }
        String lowerPassword = rawPassword.toLowerCase();
        if (DISALLOWED_PASSWORDS.contains(lowerPassword) || lowerPassword.equals(username.toLowerCase())) {
            throw new IllegalArgumentException(
                    "Refusing to provision administrator with known insecure or default password. Choose a strong, unique password.");
        }
    }
}

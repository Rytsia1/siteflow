package com.siteflow.service;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.siteflow.domain.UserWithRole;
import com.siteflow.mapper.UserMapper;
import com.siteflow.web.ResourceNotFoundException;
import com.siteflow.web.dto.DeactivatedUserView;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class UserService {

    private final UserMapper userMapper;
    private final AuditService auditService;

    @org.springframework.beans.factory.annotation.Autowired
    public UserService(UserMapper userMapper, AuditService auditService) {
        this.userMapper = userMapper;
        this.auditService = auditService;
    }

    public UserService(UserMapper userMapper) {
        this(userMapper, null);
    }

    /**
     * Anonymizes personal information and deactivates the user account.
     * Prevents future authentication while preserving foreign key relational integrity
     * across borrow requests, approvals, and transaction logs.
     *
     * @param userId the ID of the user to deactivate and anonymize
     * @return a safe view representing the deactivated account
     */
    @Transactional
    public DeactivatedUserView deactivateAndAnonymizeUser(Long userId) {
        UserWithRole user = userMapper.findUserWithRoleById(userId);
        if (user == null) {
            throw new ResourceNotFoundException("User not found: " + userId);
        }

        if (Boolean.FALSE.equals(user.getIsActive())) {
            throw new IllegalStateException("User account is already deactivated.");
        }

        if ("ADMIN".equals(user.getRoleName()) || "ROLE_ADMIN".equals(user.getRoleName())) {
            int activeAdmins = userMapper.countActiveAdmins();
            if (activeAdmins <= 1) {
                throw new IllegalStateException("Cannot deactivate the sole remaining active administrator account.");
            }
        }

        String anonymizedName = "Anonymized User #" + userId;
        String scrambledHash = "DEACTIVATED_" + UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();

        userMapper.deactivateAndAnonymize(userId, anonymizedName, scrambledHash, now);

        if (auditService != null) {
            auditService.recordBusinessEvent(
                    com.siteflow.domain.enums.AuditEventType.USER_DEACTIVATED,
                    "USER",
                    userId,
                    "SUCCESS",
                    "ACTIVE",
                    "DEACTIVATED",
                    "Account deactivated and personal data anonymized");
        }

        log.info("User account ID {} successfully deactivated and personal data anonymized.", userId);

        return new DeactivatedUserView(userId, user.getUsername(), anonymizedName, false, now);
    }

    @Transactional(readOnly = true)
    public UserWithRole getUserById(Long userId) {
        UserWithRole user = userMapper.findUserWithRoleById(userId);
        if (user == null) {
            throw new ResourceNotFoundException("User not found: " + userId);
        }
        return user;
    }

    /**
     * Revokes all active JWT sessions for a user by incrementing their token_version.
     *
     * @param userId the ID of the user whose tokens to revoke
     */
    @Transactional
    public void revokeUserTokens(Long userId) {
        UserWithRole user = userMapper.findUserWithRoleById(userId);
        if (user == null) {
            throw new ResourceNotFoundException("User not found: " + userId);
        }
        userMapper.incrementTokenVersion(userId);

        if (auditService != null) {
            auditService.recordBusinessEvent(
                    com.siteflow.domain.enums.AuditEventType.TOKEN_REVOKED,
                    "USER",
                    userId,
                    "SUCCESS",
                    "ACTIVE_TOKENS",
                    "REVOKED",
                    "All active JWT tokens revoked for user: " + user.getUsername());
        }

        log.info("All active JWT sessions revoked (token_version incremented) for user ID {}", userId);
    }

    /**
     * Updates a user's role and immediately increments their token_version to invalidate
     * any previously issued tokens under their old role.
     *
     * @param userId      the ID of the user to update
     * @param newRoleName the new role name (e.g., 'ADMIN', 'FIELD_STAFF', 'WAREHOUSE_STAFF')
     */
    @Transactional
    public void updateUserRole(Long userId, String newRoleName) {
        UserWithRole user = userMapper.findUserWithRoleById(userId);
        if (user == null) {
            throw new ResourceNotFoundException("User not found: " + userId);
        }

        String normalizedRole = newRoleName.startsWith("ROLE_") ? newRoleName.substring(5) : newRoleName;
        Long roleId = userMapper.findRoleIdByName(normalizedRole);
        if (roleId == null) {
            throw new IllegalArgumentException("Role not found: " + newRoleName);
        }

        // Prevent demoting the sole remaining active administrator
        if (("ADMIN".equals(user.getRoleName()) || "ROLE_ADMIN".equals(user.getRoleName()))
                && !"ADMIN".equals(normalizedRole)) {
            int activeAdmins = userMapper.countActiveAdmins();
            if (activeAdmins <= 1) {
                throw new IllegalStateException("Cannot change the role of the sole remaining active administrator account.");
            }
        }

        userMapper.updateRoleAndIncrementTokenVersion(userId, roleId);

        if (auditService != null) {
            auditService.recordBusinessEvent(
                    com.siteflow.domain.enums.AuditEventType.USER_ROLE_CHANGED,
                    "USER",
                    userId,
                    "SUCCESS",
                    user.getRoleName(),
                    normalizedRole,
                    "Role changed from " + user.getRoleName() + " to " + normalizedRole + ". Previous tokens revoked.");
        }

        log.info("User ID {} role changed from {} to {}. Token version incremented.", userId, user.getRoleName(), normalizedRole);
    }
}

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
}

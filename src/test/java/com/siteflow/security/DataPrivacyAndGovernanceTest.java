package com.siteflow.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.siteflow.domain.BorrowRequest;
import com.siteflow.domain.MaterialRequest;
import com.siteflow.domain.UserWithRole;
import com.siteflow.domain.enums.ApprovalStatus;
import com.siteflow.domain.enums.BorrowStatus;
import com.siteflow.domain.enums.MaterialRequestStatus;
import com.siteflow.mapper.BorrowRequestMapper;
import com.siteflow.mapper.MaterialRequestMapper;
import com.siteflow.mapper.UserMapper;

@SpringBootTest
@AutoConfigureMockMvc
class DataPrivacyAndGovernanceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private UserMapper userMapper;

    @MockitoBean
    private BorrowRequestMapper borrowRequestMapper;

    @MockitoBean
    private MaterialRequestMapper materialRequestMapper;

    private String tokenFor(Long userId, String username, String role) {
        return jwtTokenProvider.generateToken(new UserPrincipal(userId, username, "", role));
    }

    @org.junit.jupiter.api.BeforeEach
    void setUpUserMapper() {
        when(userMapper.findUserWithRoleById(any())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            return UserWithRole.builder()
                    .id(id)
                    .username(id == 1L ? "admin" : (id == 5L ? "worker_dan" : (id == 25L ? "worker_leaving" : "user_" + id)))
                    .roleName(id == 1L ? "ADMIN" : "FIELD_STAFF")
                    .isActive(true)
                    .tokenVersion(1)
                    .build();
        });
        when(userMapper.findByUsername(any())).thenAnswer(inv -> {
            String uname = inv.getArgument(0);
            return UserWithRole.builder()
                    .id(1L)
                    .username(uname)
                    .roleName("admin".equals(uname) ? "ADMIN" : "FIELD_STAFF")
                    .isActive(!"deactivated_worker".equals(uname))
                    .tokenVersion(1)
                    .build();
        });
    }

    // =========================================================================
    // 1. SENSITIVE DATA EXPOSURE AUDIT
    // =========================================================================

    @Test
    @DisplayName("POST /api/auth/login never returns passwordHash, password, or jwtSecret")
    void loginResponse_neverExposesSensitiveCredentialsOrHashes() throws Exception {
        // BCrypt hash for "admin123"
        String hash = "$2a$10$wB9L/8R1qNqvBq/H4fF3aOuEomI6o0e4Csm6s0yN7lK2jJ9m/6a2i";
        UserWithRole mockUser = UserWithRole.builder()
                .id(1L)
                .username("admin")
                .passwordHash(hash)
                .roleName("ADMIN")
                .isActive(true)
                .build();

        when(userMapper.findByUsername("admin")).thenReturn(mockUser);

        // Even on failed login, credentials/secrets are never leaked
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"wrongpassword\"}"))
                .andExpect(status().isUnauthorized())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        assertThat(responseBody).doesNotContain("passwordHash");
        assertThat(responseBody).doesNotContain("password_hash");
        assertThat(responseBody).doesNotContain("jwtSecret");
        assertThat(responseBody).doesNotContain(hash);
    }

    @Test
    @DisplayName("GET /api/auth/me returns only minimal identity view and no secrets")
    void meEndpoint_exposesOnlyMinimalIdentityData() throws Exception {
        String token = tokenFor(5L, "worker_dan", "FIELD_STAFF");

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(5))
                .andExpect(jsonPath("$.data.username").value("worker_dan"))
                .andExpect(jsonPath("$.data.role").value("FIELD_STAFF"))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.secret").doesNotExist());
    }

    // =========================================================================
    // 2. PRIVACY-PRESERVING IDOR AUTHORIZATION
    // =========================================================================

    @Test
    @DisplayName("FIELD_STAFF cannot access another user's BorrowRequest by ID (IDOR protection)")
    void getBorrowRequest_otherUserBorrowRequest_deniedForFieldStaff() throws Exception {
        Long ownerId = 42L;
        Long attackerId = 99L;

        BorrowRequest request = BorrowRequest.builder()
                .id(100L)
                .userId(ownerId)
                .locationId(1L)
                .status(BorrowStatus.BORROWED)
                .approvalStatus(ApprovalStatus.APPROVED)
                .requestDate(LocalDateTime.now())
                .build();

        when(borrowRequestMapper.findById(100L)).thenReturn(request);

        String attackerToken = tokenFor(attackerId, "attacker_worker", "FIELD_STAFF");

        mockMvc.perform(get("/api/borrow-requests/100")
                        .header("Authorization", "Bearer " + attackerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Access denied."));
    }

    @Test
    @DisplayName("FIELD_STAFF can access their own BorrowRequest by ID")
    void getBorrowRequest_ownBorrowRequest_allowedForFieldStaff() throws Exception {
        Long ownerId = 42L;

        BorrowRequest request = BorrowRequest.builder()
                .id(100L)
                .userId(ownerId)
                .locationId(1L)
                .status(BorrowStatus.BORROWED)
                .approvalStatus(ApprovalStatus.APPROVED)
                .requestDate(LocalDateTime.now())
                .build();

        when(borrowRequestMapper.findById(100L)).thenReturn(request);

        String ownerToken = tokenFor(ownerId, "legitimate_worker", "FIELD_STAFF");

        mockMvc.perform(get("/api/borrow-requests/100")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(100))
                .andExpect(jsonPath("$.data.userId").value(ownerId));
    }

    @Test
    @DisplayName("FIELD_STAFF cannot access another user's MaterialRequest by ID (IDOR protection)")
    void getMaterialRequest_otherUserMaterialRequest_deniedForFieldStaff() throws Exception {
        Long ownerId = 50L;
        Long attackerId = 99L;

        MaterialRequest mr = MaterialRequest.builder()
                .id(200L)
                .requestedBy(ownerId)
                .status(MaterialRequestStatus.SUBMITTED)
                .justification("Site foundation reinforcement")
                .requestDate(LocalDateTime.now())
                .build();

        when(materialRequestMapper.findById(200L)).thenReturn(mr);

        String attackerToken = tokenFor(attackerId, "attacker_worker", "FIELD_STAFF");

        mockMvc.perform(get("/api/procurement/material-requests/200")
                        .header("Authorization", "Bearer " + attackerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Access denied."));
    }

    // =========================================================================
    // 3. ACCOUNT DEACTIVATION, DATA ANONYMIZATION & LIFECYCLE
    // =========================================================================

    @Test
    @DisplayName("POST /api/users/me/deactivate requires explicit confirmation")
    void deactivateOwnAccount_missingConfirmation_rejected() throws Exception {
        String token = tokenFor(20L, "worker_resigning", "FIELD_STAFF");

        mockMvc.perform(post("/api/users/me/deactivate")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirm\": false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed."));
    }

    @Test
    @DisplayName("POST /api/users/me/deactivate anonymizes personal info and deactivates account")
    void deactivateOwnAccount_validConfirmation_anonymizesAndDeactivates() throws Exception {
        Long userId = 25L;
        String token = tokenFor(userId, "worker_leaving", "FIELD_STAFF");

        UserWithRole existingUser = UserWithRole.builder()
                .id(userId)
                .username("worker_leaving")
                .passwordHash("$2a$10$existingHash")
                .roleName("FIELD_STAFF")
                .isActive(true)
                .build();

        when(userMapper.findUserWithRoleById(userId)).thenReturn(existingUser);
        when(userMapper.deactivateAndAnonymize(eq(userId), eq("Anonymized User #25"), any(), any())).thenReturn(1);

        mockMvc.perform(post("/api/users/me/deactivate")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirm\": true, \"reason\": \"Resigned from construction site\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(25))
                .andExpect(jsonPath("$.data.username").value("worker_leaving"))
                .andExpect(jsonPath("$.data.anonymizedName").value("Anonymized User #25"))
                .andExpect(jsonPath("$.data.isActive").value(false));

        verify(userMapper).deactivateAndAnonymize(eq(userId), eq("Anonymized User #25"), any(), any());
    }

    @Test
    @DisplayName("ADMIN deactivating sole remaining administrator is blocked to prevent lockout")
    void deactivateAdmin_soleActiveAdmin_blockedWithConflict() throws Exception {
        Long adminId = 1L;
        String adminToken = tokenFor(adminId, "admin", "ADMIN");

        UserWithRole soleAdmin = UserWithRole.builder()
                .id(adminId)
                .username("admin")
                .passwordHash("$2a$10$hash")
                .roleName("ADMIN")
                .isActive(true)
                .build();

        when(userMapper.findUserWithRoleById(adminId)).thenReturn(soleAdmin);
        when(userMapper.countActiveAdmins()).thenReturn(1);

        mockMvc.perform(post("/api/users/1/deactivate")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirm\": true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Cannot deactivate the sole remaining active administrator account."));
    }

    @Test
    @DisplayName("Deactivated user authentication is rejected with clear safe message")
    void deactivatedUser_loginAttempt_rejected() throws Exception {
        UserWithRole deactivatedUser = UserWithRole.builder()
                .id(30L)
                .username("deactivated_worker")
                .passwordHash("DEACTIVATED_UUID_STRING")
                .roleName("FIELD_STAFF")
                .isActive(false)
                .build();

        when(userMapper.findByUsername("deactivated_worker")).thenReturn(deactivatedUser);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"deactivated_worker\",\"password\":\"anyPassword123\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("User account is deactivated."));
    }

    // =========================================================================
    // 4. DATABASE & INTERNAL EXCEPTION MASKING
    // =========================================================================

    @Test
    @DisplayName("Server error never leaks database schema, SQL queries, or stack traces")
    void serverError_masksInternalsAndReturnsSafeEnvelope() throws Exception {
        String token = tokenFor(1L, "admin", "ADMIN");

        // Requesting nonexistent endpoint or bad data
        mockMvc.perform(get("/api/items/invalid-id/stocks")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.data.requestId").isNotEmpty())
                .andExpect(jsonPath("$.data.error").isNotEmpty())
                .andExpect(jsonPath("$.message", not(containsString("SELECT"))))
                .andExpect(jsonPath("$.message", not(containsString("Exception"))))
                .andExpect(jsonPath("$.message", not(containsString("siteflow"))));
    }
}

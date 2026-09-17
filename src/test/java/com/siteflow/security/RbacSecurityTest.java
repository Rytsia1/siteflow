package com.siteflow.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.siteflow.domain.BorrowRequest;
import com.siteflow.domain.StockAdjustment;
import com.siteflow.domain.enums.AdjustmentType;
import com.siteflow.domain.enums.ApprovalStatus;
import com.siteflow.domain.enums.BorrowStatus;
import com.siteflow.service.AnalyticsService;
import com.siteflow.service.ApprovalService;
import com.siteflow.service.BorrowService;
import com.siteflow.service.InventoryService;
import com.siteflow.service.StockAdjustmentService;
import com.siteflow.web.dto.DashboardSummaryView;
import com.siteflow.web.dto.ItemStockView;

@SpringBootTest
@AutoConfigureMockMvc
class RbacSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private AnalyticsService analyticsService;

    @MockitoBean
    private ApprovalService approvalService;

    @MockitoBean
    private InventoryService inventoryService;

    @MockitoBean
    private BorrowService borrowService;

    @MockitoBean
    private StockAdjustmentService stockAdjustmentService;

    private String createBearerToken(Long userId, String username, String role) {
        UserPrincipal principal = new UserPrincipal(userId, username, "", role);
        return "Bearer " + jwtTokenProvider.generateToken(principal);
    }

    // =========================================================================
    // 1. ADMIN can access ADMIN-only endpoints
    // =========================================================================
    @Test
    @DisplayName("1. ADMIN can access ADMIN-only endpoint (e.g. /api/analytics/summary and pending approvals)")
    void admin_canAccessAdminOnlyEndpoints() throws Exception {
        String adminToken = createBearerToken(1L, "admin", "ADMIN");

        when(analyticsService.getDashboardSummary()).thenReturn(new DashboardSummaryView(3, 1, "Hammer"));
        when(approvalService.listPendingBorrowRequests()).thenReturn(List.of());

        mockMvc.perform(get("/api/analytics/summary")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.totalActiveBorrows").value(3));

        mockMvc.perform(get("/api/approvals/borrow-requests/pending")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));
    }

    // =========================================================================
    // 2. STAFF cannot access ADMIN-only endpoint
    // =========================================================================
    @Test
    @DisplayName("2. STAFF (WAREHOUSE_STAFF) cannot access ADMIN-only endpoint (returns 403 Forbidden)")
    void staff_cannotAccessAdminOnlyEndpoint() throws Exception {
        String staffToken = createBearerToken(2L, "gudang", "WAREHOUSE_STAFF");

        mockMvc.perform(get("/api/analytics/summary")
                        .header("Authorization", staffToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Access denied."))
                .andExpect(jsonPath("$.data.status").value(403))
                .andExpect(jsonPath("$.data.error").value("Forbidden"));

        mockMvc.perform(get("/api/approvals/borrow-requests/pending")
                        .header("Authorization", staffToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Access denied."));
    }

    // =========================================================================
    // 3. USER cannot access ADMIN-only endpoint
    // =========================================================================
    @Test
    @DisplayName("3. USER (FIELD_STAFF) cannot access ADMIN-only endpoint (returns 403 Forbidden)")
    void user_cannotAccessAdminOnlyEndpoint() throws Exception {
        String userToken = createBearerToken(3L, "pekerja", "FIELD_STAFF");

        mockMvc.perform(get("/api/analytics/summary")
                        .header("Authorization", userToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Access denied."))
                .andExpect(jsonPath("$.data.status").value(403));

        mockMvc.perform(get("/api/approvals/borrow-requests/pending")
                        .header("Authorization", userToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Access denied."));
    }

    // =========================================================================
    // 4. STAFF can access endpoints intended for STAFF
    // =========================================================================
    @Test
    @DisplayName("4. STAFF (WAREHOUSE_STAFF) can access endpoints intended for STAFF (e.g. stock adjustments, location stocks)")
    void staff_canAccessStaffEndpoints() throws Exception {
        String staffToken = createBearerToken(2L, "gudang", "WAREHOUSE_STAFF");

        // 4a. Location stock breakdown is accessible by STAFF
        when(inventoryService.getStockByItem(10L)).thenReturn(List.of(new ItemStockView(1L, "Main Bay", 5)));
        mockMvc.perform(get("/api/items/10/stocks")
                        .header("Authorization", staffToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data[0].locationName").value("Main Bay"));

        // 4b. Stock adjustments are accessible by STAFF
        StockAdjustment adj = StockAdjustment.builder()
                .id(1L)
                .itemId(10L)
                .locationId(1L)
                .adjustedBy(2L)
                .adjustmentType(AdjustmentType.IN)
                .qty(5)
                .build();
        when(stockAdjustmentService.createAdjustment(eq(10L), eq(1L), eq(AdjustmentType.IN), eq(5), any(), eq(2L)))
                .thenReturn(adj);

        mockMvc.perform(post("/api/stock-adjustments")
                        .header("Authorization", staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "itemId": 10,
                                    "locationId": 1,
                                    "adjustmentType": "IN",
                                    "qty": 5,
                                    "reason": "Restock"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.qty").value(5));
    }

    // =========================================================================
    // 5. USER can access endpoints intended for USER
    // =========================================================================
    @Test
    @DisplayName("5. USER (FIELD_STAFF) can access endpoints intended for USER (e.g. borrow requests, item catalog)")
    void user_canAccessUserEndpoints() throws Exception {
        String userToken = createBearerToken(3L, "pekerja", "FIELD_STAFF");

        // 5a. Item catalog is accessible by USER
        when(inventoryService.listItems()).thenReturn(List.of());
        mockMvc.perform(get("/api/items")
                        .header("Authorization", userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));

        // 5b. Create borrow request is accessible by USER
        BorrowRequest request = BorrowRequest.builder()
                .id(42L)
                .userId(3L)
                .locationId(1L)
                .requestDate(LocalDateTime.now())
                .status(BorrowStatus.PENDING)
                .approvalStatus(ApprovalStatus.PENDING_APPROVAL)
                .build();
        when(borrowService.createBorrowRequest(eq(3L), eq(1L), any())).thenReturn(request);

        mockMvc.perform(post("/api/borrow-requests")
                        .header("Authorization", userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "locationId": 1,
                                    "items": [
                                        {"itemId": 5, "qty": 1}
                                    ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.id").value(42));
    }

    // =========================================================================
    // 6. Unauthenticated requests receive 401
    // =========================================================================
    @Test
    @DisplayName("6. Unauthenticated requests receive 401 Unauthorized with standard ApiResponse and WWW-Authenticate")
    void unauthenticated_receives401() throws Exception {
        mockMvc.perform(get("/api/analytics/summary"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", containsString("Bearer realm=\"siteflow\"")))
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Authentication required."))
                .andExpect(jsonPath("$.data.status").value(401))
                .andExpect(jsonPath("$.data.error").value("Unauthorized"));

        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.data.status").value(401));
    }

    // =========================================================================
    // 7. Authenticated users with insufficient roles receive 403
    // =========================================================================
    @Test
    @DisplayName("7. Authenticated users with insufficient roles receive 403 Forbidden with standard ApiResponse envelope")
    void insufficientRole_receives403() throws Exception {
        String userToken = createBearerToken(3L, "pekerja", "FIELD_STAFF");

        // FIELD_STAFF cannot view item detailed stocks per location (STAFF/ADMIN only)
        mockMvc.perform(get("/api/items/10/stocks")
                        .header("Authorization", userToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Access denied."))
                .andExpect(jsonPath("$.data.status").value(403))
                .andExpect(jsonPath("$.data.error").value("Forbidden"))
                .andExpect(jsonPath("$.data.path").value("/api/items/10/stocks"));

        // FIELD_STAFF cannot record stock adjustments (STAFF/ADMIN only)
        mockMvc.perform(post("/api/stock-adjustments")
                        .header("Authorization", userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "itemId": 10,
                                    "locationId": 1,
                                    "adjustmentType": "IN",
                                    "qty": 5,
                                    "reason": "Forbidden attempt"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Access denied."))
                .andExpect(jsonPath("$.data.status").value(403));
    }

    // =========================================================================
    // 8. JWT roles are correctly converted into Spring Security authorities
    // =========================================================================
    @Test
    @DisplayName("8a. Standard role 'ADMIN' converts to authority 'ROLE_ADMIN' and satisfies hasRole('ADMIN')")
    void jwtRole_convertedToAuthority_hasRoleMatches() throws Exception {
        UserPrincipal principal = new UserPrincipal(1L, "admin_user", "", "ADMIN");
        String token = jwtTokenProvider.generateToken(principal);

        Authentication auth = jwtTokenProvider.getAuthentication(token);
        assertThat(auth.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_ADMIN");

        when(analyticsService.getDashboardSummary()).thenReturn(new DashboardSummaryView(1, 0, "Drill"));
        mockMvc.perform(get("/api/analytics/summary")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("8b. Pre-prefixed role 'ROLE_ADMIN' does not duplicate prefix and satisfies hasRole('ADMIN')")
    void jwtRole_alreadyPrefixed_avoidsDuplication() throws Exception {
        UserPrincipal principal = new UserPrincipal(1L, "admin_user", "", "ROLE_ADMIN");
        String token = jwtTokenProvider.generateToken(principal);

        Authentication auth = jwtTokenProvider.getAuthentication(token);
        assertThat(auth.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_ADMIN");

        when(analyticsService.getDashboardSummary()).thenReturn(new DashboardSummaryView(1, 0, "Drill"));
        mockMvc.perform(get("/api/analytics/summary")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("8c. Null or blank role produces no authorities and is denied on role-protected endpoints with 403")
    void jwtRole_blankOrNull_producesNoAuthorities_deniedWith403() throws Exception {
        UserPrincipal blankRolePrincipal = new UserPrincipal(99L, "norole", "", "");
        String token = jwtTokenProvider.generateToken(blankRolePrincipal);

        Authentication auth = jwtTokenProvider.getAuthentication(token);
        assertThat(auth.getAuthorities()).isEmpty();

        mockMvc.perform(get("/api/analytics/summary")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Access denied."));
    }
}

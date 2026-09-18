package com.siteflow.security;

import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.siteflow.domain.BorrowItem;
import com.siteflow.domain.BorrowRequest;
import com.siteflow.domain.ItemStock;
import com.siteflow.domain.MaterialRequest;
import com.siteflow.domain.enums.ApprovalStatus;
import com.siteflow.domain.enums.BorrowStatus;
import com.siteflow.domain.enums.MaterialRequestStatus;
import com.siteflow.mapper.BorrowItemMapper;
import com.siteflow.mapper.BorrowRequestMapper;
import com.siteflow.mapper.ItemStockMapper;
import com.siteflow.mapper.MaterialRequestItemMapper;
import com.siteflow.mapper.MaterialRequestMapper;
import com.siteflow.mapper.PurchaseOrderMapper;
import com.siteflow.mapper.TransactionLogMapper;

@SpringBootTest
@AutoConfigureMockMvc
class ResourceAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private BorrowRequestMapper borrowRequestMapper;

    @MockitoBean
    private BorrowItemMapper borrowItemMapper;

    @MockitoBean
    private ItemStockMapper itemStockMapper;

    @MockitoBean
    private TransactionLogMapper transactionLogMapper;

    @MockitoBean
    private MaterialRequestMapper materialRequestMapper;

    @MockitoBean
    private MaterialRequestItemMapper materialRequestItemMapper;

    @MockitoBean
    private PurchaseOrderMapper purchaseOrderMapper;

    @MockitoBean
    private com.siteflow.mapper.UserMapper userMapper;

    @org.junit.jupiter.api.BeforeEach
    void setUpUserMapper() {
        when(userMapper.findUserWithRoleById(any())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            String role = (id == 1L) ? "ADMIN" : ((id == 30L) ? "WAREHOUSE_STAFF" : ((id == 40L) ? "PROCUREMENT" : "FIELD_STAFF"));
            String username = (id == 1L) ? "admin" : ((id == 10L) ? "userA" : ((id == 20L) ? "userB" : ((id == 30L) ? "gudang" : ((id == 40L) ? "buyer" : "user_" + id))));
            return com.siteflow.domain.UserWithRole.builder()
                    .id(id)
                    .username(username)
                    .roleName(role)
                    .isActive(true)
                    .tokenVersion(1)
                    .build();
        });
        when(userMapper.findByUsername(any())).thenAnswer(inv -> {
            String uname = inv.getArgument(0);
            String role = "gudang".equals(uname) ? "WAREHOUSE_STAFF" : ("buyer".equals(uname) ? "PROCUREMENT" : "FIELD_STAFF");
            return com.siteflow.domain.UserWithRole.builder()
                    .id(1L)
                    .username(uname)
                    .roleName(role)
                    .isActive(true)
                    .tokenVersion(1)
                    .build();
        });
    }

    private String bearerToken(Long userId, String username, String role) {
        UserPrincipal principal = new UserPrincipal(userId, username, "", role);
        return "Bearer " + jwtTokenProvider.generateToken(principal);
    }

    // =========================================================================
    // 1. User can access their own resource
    // =========================================================================
    @Test
    @DisplayName("1. User can access their own BorrowRequest and MaterialRequest")
    void user_canAccessOwnResource() throws Exception {
        String userAToken = bearerToken(10L, "userA", "FIELD_STAFF");

        // 1a. User A accessing own borrow request
        BorrowRequest ownBorrow = BorrowRequest.builder()
                .id(100L)
                .userId(10L)
                .locationId(1L)
                .status(BorrowStatus.PENDING)
                .approvalStatus(ApprovalStatus.PENDING_APPROVAL)
                .build();
        when(borrowRequestMapper.findById(100L)).thenReturn(ownBorrow);

        mockMvc.perform(get("/api/borrow-requests/100")
                        .header("Authorization", userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.id").value(100))
                .andExpect(jsonPath("$.data.userId").value(10));

        // 1b. User A accessing own material request
        MaterialRequest ownMr = MaterialRequest.builder()
                .id(200L)
                .requestedBy(10L)
                .status(MaterialRequestStatus.SUBMITTED)
                .justification("Field repairs")
                .build();
        when(materialRequestMapper.findById(200L)).thenReturn(ownMr);

        mockMvc.perform(get("/api/procurement/material-requests/200")
                        .header("Authorization", userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.id").value(200))
                .andExpect(jsonPath("$.data.requestedBy").value(10));

        // 1c. User A listing own requests (/my)
        when(borrowRequestMapper.findByUserId(10L)).thenReturn(List.of(ownBorrow));
        mockMvc.perform(get("/api/borrow-requests/my")
                        .header("Authorization", userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(100));

        when(materialRequestMapper.findByRequestedBy(10L)).thenReturn(List.of(ownMr));
        mockMvc.perform(get("/api/procurement/material-requests/my")
                        .header("Authorization", userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(200));
    }

    // =========================================================================
    // 2. User cannot access another user's resource
    // =========================================================================
    @Test
    @DisplayName("2. User cannot access another user's BorrowRequest or MaterialRequest (403 Forbidden)")
    void user_cannotAccessAnotherUserResource() throws Exception {
        String userAToken = bearerToken(10L, "userA", "FIELD_STAFF");

        // BorrowRequest belonging to User B (id=20L)
        BorrowRequest userBBorrow = BorrowRequest.builder()
                .id(101L)
                .userId(20L)
                .locationId(1L)
                .status(BorrowStatus.PENDING)
                .approvalStatus(ApprovalStatus.PENDING_APPROVAL)
                .build();
        when(borrowRequestMapper.findById(101L)).thenReturn(userBBorrow);

        mockMvc.perform(get("/api/borrow-requests/101")
                        .header("Authorization", userAToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Access denied."))
                .andExpect(jsonPath("$.data.status").value(403));

        // MaterialRequest belonging to User B (id=20L)
        MaterialRequest userBMr = MaterialRequest.builder()
                .id(201L)
                .requestedBy(20L)
                .status(MaterialRequestStatus.SUBMITTED)
                .justification("Confidential project budget")
                .build();
        when(materialRequestMapper.findById(201L)).thenReturn(userBMr);

        mockMvc.perform(get("/api/procurement/material-requests/201")
                        .header("Authorization", userAToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Access denied."))
                .andExpect(jsonPath("$.data.status").value(403));
    }

    // =========================================================================
    // 3. User cannot modify another user's resource
    // =========================================================================
    @Test
    @DisplayName("3. User cannot modify another user's BorrowRequest or MaterialRequest (403 Forbidden)")
    void user_cannotModifyAnotherUserResource() throws Exception {
        String userAToken = bearerToken(10L, "userA", "FIELD_STAFF");

        // BorrowRequest belonging to User B (id=20L) in BORROWED state
        BorrowRequest userBBorrow = BorrowRequest.builder()
                .id(101L)
                .userId(20L)
                .locationId(1L)
                .status(BorrowStatus.BORROWED)
                .approvalStatus(ApprovalStatus.APPROVED)
                .build();
        when(borrowRequestMapper.findById(101L)).thenReturn(userBBorrow);

        // Attempting to return User B's borrow request
        mockMvc.perform(post("/api/borrow-requests/101/returns")
                        .header("Authorization", userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "items": [
                                        {"borrowItemId": 501, "qty": 1}
                                    ]
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Access denied."));

        // Attempting to cancel User B's borrow request
        BorrowRequest userBPendingBorrow = BorrowRequest.builder()
                .id(103L)
                .userId(20L)
                .locationId(1L)
                .status(BorrowStatus.PENDING)
                .approvalStatus(ApprovalStatus.PENDING_APPROVAL)
                .build();
        when(borrowRequestMapper.findById(103L)).thenReturn(userBPendingBorrow);

        mockMvc.perform(post("/api/borrow-requests/103/cancel")
                        .header("Authorization", userAToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Access denied."));

        // Attempting to cancel User B's material request
        MaterialRequest userBMr = MaterialRequest.builder()
                .id(201L)
                .requestedBy(20L)
                .status(MaterialRequestStatus.SUBMITTED)
                .build();
        when(materialRequestMapper.findById(201L)).thenReturn(userBMr);

        mockMvc.perform(post("/api/procurement/material-requests/201/cancel")
                        .header("Authorization", userAToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Access denied."));
    }

    // =========================================================================
    // 4. User can modify their own resource when the state allows it
    // =========================================================================
    @Test
    @DisplayName("4. User can modify their own resource when business state allows it")
    void user_canModifyOwnResource_whenStateAllows() throws Exception {
        String userAToken = bearerToken(10L, "userA", "FIELD_STAFF");

        // 4a. Cancelling own pending borrow request
        BorrowRequest ownPending = BorrowRequest.builder()
                .id(100L)
                .userId(10L)
                .locationId(1L)
                .status(BorrowStatus.PENDING)
                .approvalStatus(ApprovalStatus.PENDING_APPROVAL)
                .build();
        when(borrowRequestMapper.findById(100L)).thenReturn(ownPending);
        when(borrowItemMapper.findByBorrowRequestId(100L)).thenReturn(List.of(
                BorrowItem.builder().id(50L).itemId(1L).qtyBorrowed(2).build()
        ));
        when(itemStockMapper.findByItemIdAndLocationId(1L, 1L)).thenReturn(
                ItemStock.builder().id(99L).currentQty(5).build()
        );
        when(borrowRequestMapper.updateApproval(eq(100L), eq(ApprovalStatus.PENDING_APPROVAL), eq(ApprovalStatus.REJECTED), eq(10L), any()))
                .thenReturn(1);

        mockMvc.perform(post("/api/borrow-requests/100/cancel")
                        .header("Authorization", userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.message").value("Borrow request cancelled."));

        // 4b. Returning items on own active borrow request in BORROWED state
        BorrowRequest ownActive = BorrowRequest.builder()
                .id(102L)
                .userId(10L)
                .locationId(1L)
                .status(BorrowStatus.BORROWED)
                .approvalStatus(ApprovalStatus.APPROVED)
                .build();
        BorrowItem item = BorrowItem.builder()
                .id(502L)
                .borrowRequestId(102L)
                .itemId(2L)
                .qtyBorrowed(1)
                .qtyReturned(0)
                .build();
        when(borrowRequestMapper.findById(102L)).thenReturn(ownActive);
        when(borrowItemMapper.findById(502L)).thenReturn(item);
        when(borrowItemMapper.recordReturn(eq(502L), eq(1), any())).thenReturn(1);
        when(itemStockMapper.findByItemIdAndLocationId(2L, 1L)).thenReturn(
                ItemStock.builder().id(98L).currentQty(3).build()
        );
        when(borrowItemMapper.findByBorrowRequestId(102L)).thenReturn(List.of(
                BorrowItem.builder().id(502L).qtyBorrowed(1).qtyReturned(1).build()
        ));

        mockMvc.perform(post("/api/borrow-requests/102/returns")
                        .header("Authorization", userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "items": [
                                        {"borrowItemId": 502, "qty": 1}
                                    ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.message").value("Return processed."));

        // 4c. Cancelling own SUBMITTED material request
        MaterialRequest ownSubmittedMr = MaterialRequest.builder()
                .id(200L)
                .requestedBy(10L)
                .status(MaterialRequestStatus.SUBMITTED)
                .build();
        when(materialRequestMapper.findById(200L)).thenReturn(ownSubmittedMr);
        when(materialRequestMapper.updateApproval(eq(200L), eq(MaterialRequestStatus.SUBMITTED), eq(MaterialRequestStatus.REJECTED), eq(10L), any()))
                .thenReturn(1);

        mockMvc.perform(post("/api/procurement/material-requests/200/cancel")
                        .header("Authorization", userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.message").value("Material request cancelled."));
    }

    // =========================================================================
    // 5. User cannot modify their own resource when the state does not allow it
    // =========================================================================
    @Test
    @DisplayName("5. User cannot modify their own resource when state does not allow it (409 Conflict)")
    void user_cannotModifyOwnResource_whenStateDoesNotAllow() throws Exception {
        String userAToken = bearerToken(10L, "userA", "FIELD_STAFF");

        // 5a. Cannot cancel borrow request if already BORROWED
        BorrowRequest ownBorrowed = BorrowRequest.builder()
                .id(102L)
                .userId(10L)
                .locationId(1L)
                .status(BorrowStatus.BORROWED)
                .approvalStatus(ApprovalStatus.APPROVED)
                .build();
        when(borrowRequestMapper.findById(102L)).thenReturn(ownBorrowed);

        mockMvc.perform(post("/api/borrow-requests/102/cancel")
                        .header("Authorization", userAToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("error"));

        // 5b. Cannot process returns on borrow request still in PENDING status
        BorrowRequest ownPending = BorrowRequest.builder()
                .id(100L)
                .userId(10L)
                .locationId(1L)
                .status(BorrowStatus.PENDING)
                .approvalStatus(ApprovalStatus.PENDING_APPROVAL)
                .build();
        when(borrowRequestMapper.findById(100L)).thenReturn(ownPending);

        mockMvc.perform(post("/api/borrow-requests/100/returns")
                        .header("Authorization", userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "items": [
                                        {"borrowItemId": 501, "qty": 1}
                                    ]
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("error"));

        // 5c. Cannot cancel material request once APPROVED
        MaterialRequest ownApprovedMr = MaterialRequest.builder()
                .id(202L)
                .requestedBy(10L)
                .status(MaterialRequestStatus.APPROVED)
                .build();
        when(materialRequestMapper.findById(202L)).thenReturn(ownApprovedMr);

        mockMvc.perform(post("/api/procurement/material-requests/202/cancel")
                        .header("Authorization", userAToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("error"));
    }

    // =========================================================================
    // 6. ADMIN can access resources according to RBAC rules
    // =========================================================================
    @Test
    @DisplayName("6. ADMIN can access and action resources according to administrative RBAC rules")
    void admin_canAccessAllResources() throws Exception {
        String adminToken = bearerToken(1L, "admin", "ADMIN");

        // Admin can inspect User A's borrow request
        BorrowRequest userABorrow = BorrowRequest.builder()
                .id(100L)
                .userId(10L)
                .locationId(1L)
                .status(BorrowStatus.PENDING)
                .approvalStatus(ApprovalStatus.PENDING_APPROVAL)
                .build();
        when(borrowRequestMapper.findById(100L)).thenReturn(userABorrow);

        mockMvc.perform(get("/api/borrow-requests/100")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(100));

        // Admin can inspect User A's material request
        MaterialRequest userAMr = MaterialRequest.builder()
                .id(200L)
                .requestedBy(10L)
                .status(MaterialRequestStatus.SUBMITTED)
                .build();
        when(materialRequestMapper.findById(200L)).thenReturn(userAMr);

        mockMvc.perform(get("/api/procurement/material-requests/200")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(200));

        // Admin can process returns on any user's active borrow request
        BorrowRequest activeBorrow = BorrowRequest.builder()
                .id(102L)
                .userId(10L)
                .locationId(1L)
                .status(BorrowStatus.BORROWED)
                .approvalStatus(ApprovalStatus.APPROVED)
                .build();
        BorrowItem item = BorrowItem.builder().id(502L).borrowRequestId(102L).itemId(2L).qtyBorrowed(1).qtyReturned(0).build();
        when(borrowRequestMapper.findById(102L)).thenReturn(activeBorrow);
        when(borrowItemMapper.findById(502L)).thenReturn(item);
        when(borrowItemMapper.recordReturn(eq(502L), eq(1), any())).thenReturn(1);
        when(itemStockMapper.findByItemIdAndLocationId(2L, 1L)).thenReturn(ItemStock.builder().id(98L).currentQty(3).build());
        when(borrowItemMapper.findByBorrowRequestId(102L)).thenReturn(List.of(
                BorrowItem.builder().id(502L).qtyBorrowed(1).qtyReturned(1).build()
        ));

        mockMvc.perform(post("/api/borrow-requests/102/returns")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "items": [{"borrowItemId": 502, "qty": 1}]
                                }
                                """))
                .andExpect(status().isOk());
    }

    // =========================================================================
    // 7. STAFF can access resources according to RBAC rules
    // =========================================================================
    @Test
    @DisplayName("7. STAFF can access resources within their operational duties")
    void staff_canAccessOperationalResources() throws Exception {
        String warehouseToken = bearerToken(30L, "gudang", "WAREHOUSE_STAFF");
        String procurementToken = bearerToken(40L, "buyer", "PROCUREMENT");

        // 7a. Warehouse staff can view any user's borrow request for inventory checks
        BorrowRequest userABorrow = BorrowRequest.builder()
                .id(100L)
                .userId(10L)
                .locationId(1L)
                .status(BorrowStatus.PENDING)
                .build();
        when(borrowRequestMapper.findById(100L)).thenReturn(userABorrow);

        mockMvc.perform(get("/api/borrow-requests/100")
                        .header("Authorization", warehouseToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(100));

        // 7b. Warehouse staff can process returns for any borrow request
        BorrowRequest activeBorrow = BorrowRequest.builder()
                .id(102L)
                .userId(10L)
                .locationId(1L)
                .status(BorrowStatus.BORROWED)
                .build();
        BorrowItem item = BorrowItem.builder().id(502L).borrowRequestId(102L).itemId(2L).qtyBorrowed(1).qtyReturned(0).build();
        when(borrowRequestMapper.findById(102L)).thenReturn(activeBorrow);
        when(borrowItemMapper.findById(502L)).thenReturn(item);
        when(borrowItemMapper.recordReturn(eq(502L), eq(1), any())).thenReturn(1);
        when(itemStockMapper.findByItemIdAndLocationId(2L, 1L)).thenReturn(ItemStock.builder().id(98L).currentQty(3).build());
        when(borrowItemMapper.findByBorrowRequestId(102L)).thenReturn(List.of(
                BorrowItem.builder().id(502L).qtyBorrowed(1).qtyReturned(1).build()
        ));

        mockMvc.perform(post("/api/borrow-requests/102/returns")
                        .header("Authorization", warehouseToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "items": [{"borrowItemId": 502, "qty": 1}]
                                }
                                """))
                .andExpect(status().isOk());

        // 7c. Procurement staff can view any material request
        MaterialRequest mr = MaterialRequest.builder()
                .id(200L)
                .requestedBy(10L)
                .status(MaterialRequestStatus.APPROVED)
                .build();
        when(materialRequestMapper.findById(200L)).thenReturn(mr);

        mockMvc.perform(get("/api/procurement/material-requests/200")
                        .header("Authorization", procurementToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(200));

        // 7d. Procurement staff cannot process borrow returns (role forbidden)
        mockMvc.perform(post("/api/borrow-requests/102/returns")
                        .header("Authorization", procurementToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "items": [{"borrowItemId": 502, "qty": 1}]
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // 8. Unauthorized resource access does not leak sensitive information
    // =========================================================================
    @Test
    @DisplayName("8. Unauthorized access returns standard 403 envelope without leaking entity details")
    void unauthorizedAccess_doesNotLeakSensitiveInfo() throws Exception {
        String userAToken = bearerToken(10L, "userA", "FIELD_STAFF");

        MaterialRequest sensitiveMr = MaterialRequest.builder()
                .id(205L)
                .requestedBy(99L)
                .status(MaterialRequestStatus.SUBMITTED)
                .justification("Top Secret Defense Project Reinforcements")
                .approvalNote("Classified notes")
                .build();
        when(materialRequestMapper.findById(205L)).thenReturn(sensitiveMr);

        mockMvc.perform(get("/api/procurement/material-requests/205")
                        .header("Authorization", userAToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Access denied."))
                .andExpect(jsonPath("$.data.status").value(403))
                .andExpect(jsonPath("$.data.error").value("Forbidden"))
                .andExpect(jsonPath("$.data.path").value("/api/procurement/material-requests/205"))
                .andExpect(jsonPath("$.data.justification").doesNotExist())
                .andExpect(jsonPath("$.data.approvalNote").doesNotExist())
                .andExpect(jsonPath("$.data.requestedBy").doesNotExist());

        // Non-existent request returns 404
        when(borrowRequestMapper.findById(9999L)).thenReturn(null);
        mockMvc.perform(get("/api/borrow-requests/9999")
                        .header("Authorization", userAToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Borrow request not found: 9999"));
    }

    // =========================================================================
    // 9. Changing the resource ID in the request cannot bypass authorization (IDOR)
    // =========================================================================
    @Test
    @DisplayName("9. Tampering resource ID in URL (IDOR attempt) is intercepted and rejected with 403")
    void idor_tamperingResourceId_isBlocked() throws Exception {
        String userAToken = bearerToken(10L, "userA", "FIELD_STAFF");

        // User A's legitimate request ID is 100
        BorrowRequest ownBorrow = BorrowRequest.builder()
                .id(100L)
                .userId(10L)
                .status(BorrowStatus.PENDING)
                .build();
        when(borrowRequestMapper.findById(100L)).thenReturn(ownBorrow);

        // User B's request ID is 101
        BorrowRequest victimBorrow = BorrowRequest.builder()
                .id(101L)
                .userId(20L)
                .status(BorrowStatus.PENDING)
                .build();
        when(borrowRequestMapper.findById(101L)).thenReturn(victimBorrow);

        // Legitimate access to ID 100 succeeds
        mockMvc.perform(get("/api/borrow-requests/100")
                        .header("Authorization", userAToken))
                .andExpect(status().isOk());

        // Attacker modifies path variable to 101 -> Blocked
        mockMvc.perform(get("/api/borrow-requests/101")
                        .header("Authorization", userAToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Access denied."));
    }
}

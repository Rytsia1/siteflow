package com.siteflow.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.siteflow.domain.BorrowRequest;
import com.siteflow.domain.ItemStock;
import com.siteflow.domain.MaterialRequest;
import com.siteflow.domain.TransactionLog;
import com.siteflow.domain.UserWithRole;
import com.siteflow.domain.enums.AdjustmentType;
import com.siteflow.domain.enums.ApprovalStatus;
import com.siteflow.domain.enums.AuditEventType;
import com.siteflow.domain.enums.BorrowStatus;
import com.siteflow.domain.enums.MaterialRequestStatus;
import com.siteflow.mapper.BorrowRequestMapper;
import com.siteflow.mapper.ItemStockMapper;
import com.siteflow.mapper.MaterialRequestItemMapper;
import com.siteflow.mapper.MaterialRequestMapper;
import com.siteflow.mapper.PurchaseOrderMapper;
import com.siteflow.mapper.StockAdjustmentMapper;
import com.siteflow.mapper.TransactionLogMapper;
import com.siteflow.mapper.UserMapper;
import com.siteflow.security.UserPrincipal;
import com.siteflow.service.ApprovalService;
import com.siteflow.service.AuditService;
import com.siteflow.service.ProcurementService;
import com.siteflow.service.StockAdjustmentService;
import com.siteflow.service.UserService;
import com.siteflow.web.dto.AuditLogView;
import com.siteflow.web.dto.PagedAuditLogView;

@SpringBootTest
@AutoConfigureMockMvc
class AuditTrailTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuditService auditService;

    private UserPrincipal adminPrincipal() {
        return new UserPrincipal(1L, "admin", "hash", "ADMIN");
    }

    private UserPrincipal fieldStaffPrincipal() {
        return new UserPrincipal(2L, "pekerja", "hash", "FIELD_STAFF");
    }

    private UserPrincipal warehouseStaffPrincipal() {
        return new UserPrincipal(3L, "gudang", "hash", "WAREHOUSE_STAFF");
    }

    @Nested
    @DisplayName("Audit API Access Control & Immutability")
    class AuditApiAccessControlTests {

        @Test
        @DisplayName("Unauthenticated request to /api/audit-logs is rejected with 401")
        void unauthenticated_auditLogAccess_isRejected() throws Exception {
            mockMvc.perform(get("/api/audit-logs"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Field staff is forbidden from accessing /api/audit-logs with 403")
        void fieldStaff_auditLogAccess_isForbidden() throws Exception {
            mockMvc.perform(get("/api/audit-logs")
                            .with(user(fieldStaffPrincipal())))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Warehouse staff is forbidden from accessing /api/audit-logs with 403")
        void warehouseStaff_auditLogAccess_isForbidden() throws Exception {
            mockMvc.perform(get("/api/audit-logs")
                            .with(user(warehouseStaffPrincipal())))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Admin can view audit logs with server-side pagination headers and safe payload")
        void admin_auditLogAccess_succeedsWithPagination() throws Exception {
            AuditLogView logItem = new AuditLogView(
                    101L,
                    LocalDateTime.of(2026, 9, 18, 10, 30, 0),
                    AuditEventType.BORROW_REQUEST_APPROVED.name(),
                    "BORROW_REQUEST",
                    42L,
                    "admin",
                    1L,
                    "SUCCESS",
                    "PENDING_APPROVAL",
                    "APPROVED",
                    "Approved by administrator",
                    "127.0.0.1"
            );

            PagedAuditLogView pagedView = new PagedAuditLogView(
                    List.of(logItem),
                    1,
                    0,
                    20,
                    1
            );

            when(auditService.listAuditLogsPaged(eq(0), eq(20), any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(pagedView);

            mockMvc.perform(get("/api/audit-logs?page=0&size=20")
                            .with(user(adminPrincipal())))
                    .andExpect(status().isOk())
                    .andExpect(header().string("X-Total-Count", "1"))
                    .andExpect(header().string("X-Page-Number", "0"))
                    .andExpect(header().string("X-Page-Size", "20"))
                    .andExpect(header().string("X-Total-Pages", "1"))
                    .andExpect(jsonPath("$.data.items[0].id").value(101))
                    .andExpect(jsonPath("$.data.items[0].action").value("BORROW_REQUEST_APPROVED"))
                    .andExpect(jsonPath("$.data.items[0].actorUsername").value("admin"))
                    .andExpect(jsonPath("$.data.items[0].beforeState").value("PENDING_APPROVAL"))
                    .andExpect(jsonPath("$.data.items[0].afterState").value("APPROVED"));
        }

        @Test
        @DisplayName("Append-only integrity: mutation endpoints (POST/PUT/DELETE) on /api/audit-logs are rejected")
        void auditLogMutation_isRejected() throws Exception {
            mockMvc.perform(post("/api/audit-logs").with(user(adminPrincipal())))
                    .andExpect(status().isMethodNotAllowed());

            mockMvc.perform(put("/api/audit-logs/101").with(user(adminPrincipal())))
                    .andExpect(status().isMethodNotAllowed());

            mockMvc.perform(delete("/api/audit-logs/101").with(user(adminPrincipal())))
                    .andExpect(status().isMethodNotAllowed());
        }
    }

    @Nested
    @DisplayName("Actor Identity Resolution & Spoofing Resistance")
    class ActorResolutionTests {

        @Mock
        private TransactionLogMapper transactionLogMapper;

        private AuditService realAuditService;
        private AutoCloseable mocks;

        @BeforeEach
        void setUp() {
            mocks = MockitoAnnotations.openMocks(this);
            realAuditService = new AuditService(transactionLogMapper, null);
        }

        @AfterEach
        void tearDown() throws Exception {
            SecurityContextHolder.clearContext();
            if (mocks != null) {
                mocks.close();
            }
        }

        @Test
        @DisplayName("Authenticated principal identity is used as actor; cannot be spoofed")
        void authenticatedUser_actorIsResolvedFromSecurityContext() {
            UserPrincipal principal = new UserPrincipal(77L, "field_supervisor", "hash", "FIELD_STAFF");
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(principal, "cred", principal.getAuthorities()));

            realAuditService.recordBusinessEvent(
                    AuditEventType.BORROW_REQUEST_CREATED,
                    "BORROW_REQUEST",
                    50L,
                    "SUCCESS",
                    null,
                    "PENDING_APPROVAL",
                    "Borrow requested");

            ArgumentCaptor<TransactionLog> captor = ArgumentCaptor.forClass(TransactionLog.class);
            verify(transactionLogMapper).insert(captor.capture());

            TransactionLog recorded = captor.getValue();
            assertThat(recorded.getUserId()).isEqualTo(77L);
            assertThat(recorded.getActorUsername()).isEqualTo("field_supervisor");
            assertThat(recorded.getAction()).isEqualTo(AuditEventType.BORROW_REQUEST_CREATED.name());
            assertThat(recorded.getStatus()).isEqualTo("SUCCESS");
        }

        @Test
        @DisplayName("Automated/unauthenticated execution is attributed to SYSTEM")
        void unauthenticated_actorIsResolvedToSystem() {
            SecurityContextHolder.clearContext();

            realAuditService.recordBusinessEvent(
                    AuditEventType.STOCK_ADJUSTED,
                    "ITEM",
                    10L,
                    "SUCCESS",
                    "qty: 100",
                    "qty: 110",
                    "System batch rebalance");

            ArgumentCaptor<TransactionLog> captor = ArgumentCaptor.forClass(TransactionLog.class);
            verify(transactionLogMapper).insert(captor.capture());

            TransactionLog recorded = captor.getValue();
            assertThat(recorded.getUserId()).isNull();
            assertThat(recorded.getActorUsername()).isEqualTo("SYSTEM");
        }

        @Test
        @DisplayName("Failed login records sanitized username and zero password/secret data")
        void loginFailure_recordsSanitizedUsernameAndNoCredentials() {
            realAuditService.recordAuthFailure("hacker\n\r_user", "192.168.1.50", "Invalid credentials");

            ArgumentCaptor<TransactionLog> captor = ArgumentCaptor.forClass(TransactionLog.class);
            verify(transactionLogMapper).insert(captor.capture());

            TransactionLog recorded = captor.getValue();
            assertThat(recorded.getAction()).isEqualTo(AuditEventType.LOGIN_FAILURE.name());
            assertThat(recorded.getResourceType()).isEqualTo("AUTH");
            assertThat(recorded.getActorUsername()).isEqualTo("hacker_user");
            assertThat(recorded.getStatus()).isEqualTo("FAILURE");
            assertThat(recorded.getIpAddress()).isEqualTo("192.168.1.50");
            assertThat(recorded.getBeforeState()).isNull();
            assertThat(recorded.getAfterState()).isEqualTo("UNAUTHENTICATED");
        }
    }

    @Nested
    @DisplayName("Business Workflow State Transitions & Rollback Integrity")
    class WorkflowAuditTests {

        @Mock
        private BorrowRequestMapper borrowRequestMapper;
        @Mock
        private TransactionLogMapper transactionLogMapper;
        @Mock
        private ItemStockMapper itemStockMapper;
        @Mock
        private StockAdjustmentMapper stockAdjustmentMapper;
        @Mock
        private MaterialRequestMapper materialRequestMapper;
        @Mock
        private MaterialRequestItemMapper materialRequestItemMapper;
        @Mock
        private PurchaseOrderMapper purchaseOrderMapper;
        @Mock
        private UserMapper userMapper;

        private AutoCloseable mocks;
        private AuditService localAuditService;

        @BeforeEach
        void setUp() {
            mocks = MockitoAnnotations.openMocks(this);
            localAuditService = new AuditService(transactionLogMapper, null);
        }

        @AfterEach
        void tearDown() throws Exception {
            SecurityContextHolder.clearContext();
            if (mocks != null) {
                mocks.close();
            }
        }

        @Test
        @DisplayName("ApprovalService: approve records BORROW_REQUEST_APPROVED with before/after state")
        void approvalService_approve_recordsAuditEvent() {
            ApprovalService approvalService = new ApprovalService(borrowRequestMapper, localAuditService);

            BorrowRequest request = BorrowRequest.builder()
                    .id(42L)
                    .userId(2L)
                    .status(BorrowStatus.PENDING)
                    .approvalStatus(ApprovalStatus.PENDING_APPROVAL)
                    .build();

            when(borrowRequestMapper.findById(42L)).thenReturn(request);
            when(borrowRequestMapper.updateApproval(eq(42L), eq(ApprovalStatus.PENDING_APPROVAL), eq(ApprovalStatus.APPROVED), eq(1L), any()))
                    .thenReturn(1);

            approvalService.approveBorrowRequest(42L, 1L, "Looks good to dispense");

            ArgumentCaptor<TransactionLog> captor = ArgumentCaptor.forClass(TransactionLog.class);
            verify(transactionLogMapper).insert(captor.capture());

            TransactionLog log = captor.getValue();
            assertThat(log.getAction()).isEqualTo(AuditEventType.BORROW_REQUEST_APPROVED.name());
            assertThat(log.getResourceType()).isEqualTo("BORROW_REQUEST");
            assertThat(log.getResourceId()).isEqualTo(42L);
            assertThat(log.getBeforeState()).isEqualTo("PENDING_APPROVAL");
            assertThat(log.getAfterState()).isEqualTo("APPROVED");
            assertThat(log.getDetails()).contains("Looks good to dispense");
            assertThat(log.getStatus()).isEqualTo("SUCCESS");
        }

        @Test
        @DisplayName("ApprovalService: reject records BORROW_REQUEST_REJECTED with REJECTED status")
        void approvalService_reject_recordsAuditEvent() {
            ApprovalService approvalService = new ApprovalService(borrowRequestMapper, localAuditService);

            BorrowRequest request = BorrowRequest.builder()
                    .id(42L)
                    .userId(2L)
                    .status(BorrowStatus.PENDING)
                    .approvalStatus(ApprovalStatus.PENDING_APPROVAL)
                    .build();

            when(borrowRequestMapper.findById(42L)).thenReturn(request);
            when(borrowRequestMapper.updateApproval(eq(42L), eq(ApprovalStatus.PENDING_APPROVAL), eq(ApprovalStatus.REJECTED), eq(1L), any()))
                    .thenReturn(1);

            approvalService.rejectBorrowRequest(42L, 1L, "Tool unavailable on requested date");

            ArgumentCaptor<TransactionLog> captor = ArgumentCaptor.forClass(TransactionLog.class);
            verify(transactionLogMapper).insert(captor.capture());

            TransactionLog log = captor.getValue();
            assertThat(log.getAction()).isEqualTo(AuditEventType.BORROW_REQUEST_REJECTED.name());
            assertThat(log.getResourceId()).isEqualTo(42L);
            assertThat(log.getBeforeState()).isEqualTo("PENDING_APPROVAL");
            assertThat(log.getAfterState()).isEqualTo("REJECTED");
            assertThat(log.getStatus()).isEqualTo("REJECTED");
            assertThat(log.getDetails()).isEqualTo("Tool unavailable on requested date");
        }

        @Test
        @DisplayName("StockAdjustmentService: failed adjustment rolls back without writing audit record")
        void stockAdjustmentService_insufficientStock_noAuditRecordWritten() {
            StockAdjustmentService service = new StockAdjustmentService(
                    itemStockMapper, stockAdjustmentMapper, transactionLogMapper);

            ItemStock currentStock = ItemStock.builder()
                    .id(5L)
                    .itemId(10L)
                    .locationId(1L)
                    .currentQty(2)
                    .build();

            when(itemStockMapper.findByItemIdAndLocationIdForUpdate(10L, 1L)).thenReturn(currentStock);
            // Simulate atomic check failure (negative stock rejected)
            when(itemStockMapper.adjustQty(5L, -10)).thenReturn(0);

            assertThatThrownBy(() -> service.createAdjustment(10L, 1L, AdjustmentType.OUT, 10, "Defective items", 1L))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("negative stock");

            // Verify no audit log was inserted
            verify(transactionLogMapper, never()).insert(any());
        }

        @Test
        @DisplayName("ProcurementService: submitMaterialRequest records MATERIAL_REQUEST_CREATED")
        void procurementService_submit_recordsAuditEvent() {
            ProcurementService procurementService = new ProcurementService(
                    materialRequestMapper, materialRequestItemMapper, purchaseOrderMapper, null, localAuditService);

            MaterialRequest savedMr = MaterialRequest.builder()
                    .id(99L)
                    .requestedBy(2L)
                    .justification("Site drainage pipes")
                    .status(MaterialRequestStatus.SUBMITTED)
                    .build();

            procurementService.submitMaterialRequest(2L, "Site drainage pipes", List.of(new ProcurementService.MrLineItem(10L, 5)));

            ArgumentCaptor<TransactionLog> captor = ArgumentCaptor.forClass(TransactionLog.class);
            verify(transactionLogMapper).insert(captor.capture());

            TransactionLog log = captor.getValue();
            assertThat(log.getAction()).isEqualTo(AuditEventType.MATERIAL_REQUEST_CREATED.name());
            assertThat(log.getResourceType()).isEqualTo("MATERIAL_REQUEST");
            assertThat(log.getAfterState()).isEqualTo("SUBMITTED");
            assertThat(log.getDetails()).isEqualTo("Site drainage pipes");
        }

        @Test
        @DisplayName("UserService: deactivateAndAnonymizeUser records USER_DEACTIVATED")
        void userService_deactivate_recordsAuditEvent() {
            UserService userService = new UserService(userMapper, localAuditService);

            UserWithRole user = UserWithRole.builder()
                    .id(50L)
                    .username("john_doe")
                    .roleName("FIELD_STAFF")
                    .isActive(true)
                    .build();

            when(userMapper.findUserWithRoleById(50L)).thenReturn(user);

            userService.deactivateAndAnonymizeUser(50L);

            ArgumentCaptor<TransactionLog> captor = ArgumentCaptor.forClass(TransactionLog.class);
            verify(transactionLogMapper).insert(captor.capture());

            TransactionLog log = captor.getValue();
            assertThat(log.getAction()).isEqualTo(AuditEventType.USER_DEACTIVATED.name());
            assertThat(log.getResourceType()).isEqualTo("USER");
            assertThat(log.getResourceId()).isEqualTo(50L);
            assertThat(log.getBeforeState()).isEqualTo("ACTIVE");
            assertThat(log.getAfterState()).isEqualTo("DEACTIVATED");
        }
    }
}

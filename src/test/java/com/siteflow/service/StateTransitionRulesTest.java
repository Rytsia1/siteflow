package com.siteflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.siteflow.domain.BorrowItem;
import com.siteflow.domain.BorrowRequest;
import com.siteflow.domain.ItemInstance;
import com.siteflow.domain.MaterialRequest;
import com.siteflow.domain.PurchaseOrder;
import com.siteflow.domain.enums.ApprovalStatus;
import com.siteflow.domain.enums.BorrowStatus;
import com.siteflow.domain.enums.MaterialRequestStatus;
import com.siteflow.domain.enums.PurchaseOrderStatus;
import com.siteflow.domain.enums.ToolCondition;
import com.siteflow.mapper.BorrowItemMapper;
import com.siteflow.mapper.BorrowRequestMapper;
import com.siteflow.mapper.ItemInstanceMapper;
import com.siteflow.mapper.MaterialRequestItemMapper;
import com.siteflow.mapper.MaterialRequestMapper;
import com.siteflow.mapper.PurchaseOrderMapper;

class StateTransitionRulesTest {

    @Nested
    @DisplayName("MaterialRequestStatus Transition Rules")
    class MaterialRequestStatusRules {

        @Test
        @DisplayName("Valid forward transitions are permitted")
        void validTransitions() {
            assertThat(MaterialRequestStatus.DRAFT.canTransitionTo(MaterialRequestStatus.SUBMITTED)).isTrue();
            assertThat(MaterialRequestStatus.SUBMITTED.canTransitionTo(MaterialRequestStatus.APPROVED)).isTrue();
            assertThat(MaterialRequestStatus.SUBMITTED.canTransitionTo(MaterialRequestStatus.REJECTED)).isTrue();
            assertThat(MaterialRequestStatus.APPROVED.canTransitionTo(MaterialRequestStatus.PO_CREATED)).isTrue();
            assertThat(MaterialRequestStatus.PO_CREATED.canTransitionTo(MaterialRequestStatus.COMPLETED)).isTrue();
        }

        @Test
        @DisplayName("Invalid backward or jumping transitions are rejected")
        void invalidTransitions() {
            // Regressions
            assertThat(MaterialRequestStatus.APPROVED.canTransitionTo(MaterialRequestStatus.SUBMITTED)).isFalse();
            assertThat(MaterialRequestStatus.APPROVED.canTransitionTo(MaterialRequestStatus.DRAFT)).isFalse();
            assertThat(MaterialRequestStatus.PO_CREATED.canTransitionTo(MaterialRequestStatus.APPROVED)).isFalse();
            assertThat(MaterialRequestStatus.PO_CREATED.canTransitionTo(MaterialRequestStatus.SUBMITTED)).isFalse();

            // From terminal states
            assertThat(MaterialRequestStatus.REJECTED.canTransitionTo(MaterialRequestStatus.APPROVED)).isFalse();
            assertThat(MaterialRequestStatus.REJECTED.canTransitionTo(MaterialRequestStatus.SUBMITTED)).isFalse();
            assertThat(MaterialRequestStatus.COMPLETED.canTransitionTo(MaterialRequestStatus.PO_CREATED)).isFalse();
            assertThat(MaterialRequestStatus.COMPLETED.canTransitionTo(MaterialRequestStatus.SUBMITTED)).isFalse();

            // Self-transitions
            assertThat(MaterialRequestStatus.SUBMITTED.canTransitionTo(MaterialRequestStatus.SUBMITTED)).isFalse();
            assertThat(MaterialRequestStatus.APPROVED.canTransitionTo(MaterialRequestStatus.APPROVED)).isFalse();
            assertThat(MaterialRequestStatus.REJECTED.canTransitionTo(MaterialRequestStatus.REJECTED)).isFalse();
            assertThat(MaterialRequestStatus.COMPLETED.canTransitionTo(MaterialRequestStatus.COMPLETED)).isFalse();

            // Null check
            assertThat(MaterialRequestStatus.SUBMITTED.canTransitionTo(null)).isFalse();
        }

        @Test
        @DisplayName("Terminal states are correctly identified")
        void terminalStates() {
            assertThat(MaterialRequestStatus.REJECTED.isTerminal()).isTrue();
            assertThat(MaterialRequestStatus.COMPLETED.isTerminal()).isTrue();
            assertThat(MaterialRequestStatus.SUBMITTED.isTerminal()).isFalse();
            assertThat(MaterialRequestStatus.APPROVED.isTerminal()).isFalse();
            assertThat(MaterialRequestStatus.PO_CREATED.isTerminal()).isFalse();
        }
    }

    @Nested
    @DisplayName("ApprovalStatus Transition Rules")
    class ApprovalStatusRules {

        @Test
        @DisplayName("Valid forward transitions from PENDING_APPROVAL are permitted")
        void validTransitions() {
            assertThat(ApprovalStatus.PENDING_APPROVAL.canTransitionTo(ApprovalStatus.APPROVED)).isTrue();
            assertThat(ApprovalStatus.PENDING_APPROVAL.canTransitionTo(ApprovalStatus.REJECTED)).isTrue();
        }

        @Test
        @DisplayName("Transitions out of decision states are rejected")
        void invalidTransitions() {
            // Reversing or switching decisions
            assertThat(ApprovalStatus.APPROVED.canTransitionTo(ApprovalStatus.PENDING_APPROVAL)).isFalse();
            assertThat(ApprovalStatus.APPROVED.canTransitionTo(ApprovalStatus.REJECTED)).isFalse();
            assertThat(ApprovalStatus.REJECTED.canTransitionTo(ApprovalStatus.PENDING_APPROVAL)).isFalse();
            assertThat(ApprovalStatus.REJECTED.canTransitionTo(ApprovalStatus.APPROVED)).isFalse();

            // Self-transitions
            assertThat(ApprovalStatus.APPROVED.canTransitionTo(ApprovalStatus.APPROVED)).isFalse();
            assertThat(ApprovalStatus.REJECTED.canTransitionTo(ApprovalStatus.REJECTED)).isFalse();

            // Null
            assertThat(ApprovalStatus.PENDING_APPROVAL.canTransitionTo(null)).isFalse();
        }

        @Test
        @DisplayName("Terminal decision states are correctly identified")
        void terminalStates() {
            assertThat(ApprovalStatus.APPROVED.isTerminal()).isTrue();
            assertThat(ApprovalStatus.REJECTED.isTerminal()).isTrue();
            assertThat(ApprovalStatus.PENDING_APPROVAL.isTerminal()).isFalse();
        }
    }

    @Nested
    @DisplayName("BorrowStatus Transition Rules")
    class BorrowStatusRules {

        @Test
        @DisplayName("Valid lifecycle transitions are permitted")
        void validTransitions() {
            assertThat(BorrowStatus.PENDING.canTransitionTo(BorrowStatus.BORROWED)).isTrue();
            assertThat(BorrowStatus.BORROWED.canTransitionTo(BorrowStatus.PARTIAL_RETURN)).isTrue();
            assertThat(BorrowStatus.BORROWED.canTransitionTo(BorrowStatus.COMPLETED)).isTrue();
            assertThat(BorrowStatus.PARTIAL_RETURN.canTransitionTo(BorrowStatus.PARTIAL_RETURN)).isTrue();
            assertThat(BorrowStatus.PARTIAL_RETURN.canTransitionTo(BorrowStatus.COMPLETED)).isTrue();
        }

        @Test
        @DisplayName("Invalid regressions and shortcuts are rejected")
        void invalidTransitions() {
            // Regressions
            assertThat(BorrowStatus.BORROWED.canTransitionTo(BorrowStatus.PENDING)).isFalse();
            assertThat(BorrowStatus.PARTIAL_RETURN.canTransitionTo(BorrowStatus.PENDING)).isFalse();
            assertThat(BorrowStatus.PARTIAL_RETURN.canTransitionTo(BorrowStatus.BORROWED)).isFalse();

            // From terminal COMPLETED
            assertThat(BorrowStatus.COMPLETED.canTransitionTo(BorrowStatus.PENDING)).isFalse();
            assertThat(BorrowStatus.COMPLETED.canTransitionTo(BorrowStatus.BORROWED)).isFalse();
            assertThat(BorrowStatus.COMPLETED.canTransitionTo(BorrowStatus.PARTIAL_RETURN)).isFalse();
            assertThat(BorrowStatus.COMPLETED.canTransitionTo(BorrowStatus.COMPLETED)).isFalse();

            // Skipping BORROWED directly to return
            assertThat(BorrowStatus.PENDING.canTransitionTo(BorrowStatus.PARTIAL_RETURN)).isFalse();
            assertThat(BorrowStatus.PENDING.canTransitionTo(BorrowStatus.COMPLETED)).isFalse();

            // Null
            assertThat(BorrowStatus.BORROWED.canTransitionTo(null)).isFalse();
        }

        @Test
        @DisplayName("COMPLETED is marked as terminal")
        void terminalStates() {
            assertThat(BorrowStatus.COMPLETED.isTerminal()).isTrue();
            assertThat(BorrowStatus.PENDING.isTerminal()).isFalse();
            assertThat(BorrowStatus.BORROWED.isTerminal()).isFalse();
            assertThat(BorrowStatus.PARTIAL_RETURN.isTerminal()).isFalse();
        }
    }

    @Nested
    @DisplayName("PurchaseOrderStatus Transition Rules")
    class PurchaseOrderStatusRules {

        @Test
        @DisplayName("Valid PO lifecycle transitions are permitted")
        void validTransitions() {
            assertThat(PurchaseOrderStatus.ISSUED.canTransitionTo(PurchaseOrderStatus.PARTIAL_RECEIVED)).isTrue();
            assertThat(PurchaseOrderStatus.ISSUED.canTransitionTo(PurchaseOrderStatus.FULFILLED)).isTrue();
            assertThat(PurchaseOrderStatus.PARTIAL_RECEIVED.canTransitionTo(PurchaseOrderStatus.PARTIAL_RECEIVED)).isTrue();
            assertThat(PurchaseOrderStatus.PARTIAL_RECEIVED.canTransitionTo(PurchaseOrderStatus.FULFILLED)).isTrue();
        }

        @Test
        @DisplayName("Invalid regressions and terminal transitions are rejected")
        void invalidTransitions() {
            // Regressions
            assertThat(PurchaseOrderStatus.PARTIAL_RECEIVED.canTransitionTo(PurchaseOrderStatus.ISSUED)).isFalse();
            assertThat(PurchaseOrderStatus.FULFILLED.canTransitionTo(PurchaseOrderStatus.ISSUED)).isFalse();
            assertThat(PurchaseOrderStatus.FULFILLED.canTransitionTo(PurchaseOrderStatus.PARTIAL_RECEIVED)).isFalse();
            assertThat(PurchaseOrderStatus.FULFILLED.canTransitionTo(PurchaseOrderStatus.FULFILLED)).isFalse();

            // Null
            assertThat(PurchaseOrderStatus.ISSUED.canTransitionTo(null)).isFalse();
        }

        @Test
        @DisplayName("FULFILLED is marked as terminal")
        void terminalStates() {
            assertThat(PurchaseOrderStatus.FULFILLED.isTerminal()).isTrue();
            assertThat(PurchaseOrderStatus.ISSUED.isTerminal()).isFalse();
            assertThat(PurchaseOrderStatus.PARTIAL_RECEIVED.isTerminal()).isFalse();
        }
    }

    @Nested
    @DisplayName("ProcurementService State Transition Enforcement")
    class ProcurementServiceEnforcement {

        private MaterialRequestMapper materialRequestMapper;
        private MaterialRequestItemMapper materialRequestItemMapper;
        private PurchaseOrderMapper purchaseOrderMapper;
        private ProcurementService procurementService;

        @BeforeEach
        void setUp() {
            materialRequestMapper = mock(MaterialRequestMapper.class);
            materialRequestItemMapper = mock(MaterialRequestItemMapper.class);
            purchaseOrderMapper = mock(PurchaseOrderMapper.class);
            procurementService = new ProcurementService(
                    materialRequestMapper, materialRequestItemMapper, purchaseOrderMapper);
        }

        @Test
        @DisplayName("Approving an already APPROVED request is rejected")
        void reApprove_fails() {
            MaterialRequest approved = MaterialRequest.builder().id(1L).status(MaterialRequestStatus.APPROVED).build();
            when(materialRequestMapper.findById(1L)).thenReturn(approved);

            assertThatThrownBy(() -> procurementService.approveMaterialRequest(1L, 99L, "note"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("must be SUBMITTED");
        }

        @Test
        @DisplayName("Approving a REJECTED request is rejected")
        void approveRejected_fails() {
            MaterialRequest rejected = MaterialRequest.builder().id(1L).status(MaterialRequestStatus.REJECTED).build();
            when(materialRequestMapper.findById(1L)).thenReturn(rejected);

            assertThatThrownBy(() -> procurementService.approveMaterialRequest(1L, 99L, "note"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("must be SUBMITTED");
        }

        @Test
        @DisplayName("Completing an already COMPLETED request is rejected")
        void reComplete_fails() {
            MaterialRequest completed = MaterialRequest.builder().id(1L).status(MaterialRequestStatus.COMPLETED).build();
            when(materialRequestMapper.findById(1L)).thenReturn(completed);

            assertThatThrownBy(() -> procurementService.markMaterialRequestCompleted(1L))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("must be PO_CREATED");
        }

        @Test
        @DisplayName("Modifying PurchaseOrder after FULFILLED is rejected")
        void modifyFulfilledPo_fails() {
            PurchaseOrder fulfilled = PurchaseOrder.builder().id(10L).poStatus(PurchaseOrderStatus.FULFILLED).build();
            when(purchaseOrderMapper.findById(10L)).thenReturn(fulfilled);

            assertThatThrownBy(() -> procurementService.updatePurchaseOrderStatus(10L, PurchaseOrderStatus.ISSUED))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("terminal status");
        }

        @Test
        @DisplayName("Regressing PurchaseOrder from PARTIAL_RECEIVED to ISSUED is rejected")
        void regressPo_fails() {
            PurchaseOrder po = PurchaseOrder.builder().id(10L).poStatus(PurchaseOrderStatus.PARTIAL_RECEIVED).build();
            when(purchaseOrderMapper.findById(10L)).thenReturn(po);

            assertThatThrownBy(() -> procurementService.updatePurchaseOrderStatus(10L, PurchaseOrderStatus.ISSUED))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot transition purchase order");
        }

        @Test
        @DisplayName("Valid PO status transition succeeds")
        void validPoTransition_succeeds() {
            PurchaseOrder po = PurchaseOrder.builder().id(10L).poStatus(PurchaseOrderStatus.ISSUED).build();
            when(purchaseOrderMapper.findById(10L)).thenReturn(po);
            when(purchaseOrderMapper.updateStatusGuarded(10L, PurchaseOrderStatus.ISSUED, PurchaseOrderStatus.PARTIAL_RECEIVED))
                    .thenReturn(1);

            PurchaseOrder updated = PurchaseOrder.builder().id(10L).poStatus(PurchaseOrderStatus.PARTIAL_RECEIVED).build();
            when(purchaseOrderMapper.findById(10L)).thenReturn(po, updated);

            PurchaseOrder result = procurementService.updatePurchaseOrderStatus(10L, PurchaseOrderStatus.PARTIAL_RECEIVED);
            assertThat(result.getPoStatus()).isEqualTo(PurchaseOrderStatus.PARTIAL_RECEIVED);
        }
    }

    @Nested
    @DisplayName("ApprovalService State Transition Enforcement")
    class ApprovalServiceEnforcement {

        private BorrowRequestMapper borrowRequestMapper;
        private ApprovalService approvalService;

        @BeforeEach
        void setUp() {
            borrowRequestMapper = mock(BorrowRequestMapper.class);
            approvalService = new ApprovalService(borrowRequestMapper);
        }

        @Test
        @DisplayName("Re-approving an already APPROVED borrow request is rejected")
        void reApproveBorrowRequest_fails() {
            BorrowRequest approved = BorrowRequest.builder()
                    .id(5L)
                    .approvalStatus(ApprovalStatus.APPROVED)
                    .status(BorrowStatus.BORROWED)
                    .build();
            when(borrowRequestMapper.findById(5L)).thenReturn(approved);

            assertThatThrownBy(() -> approvalService.approveBorrowRequest(5L, 1L, "note"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("is not pending approval");
        }

        @Test
        @DisplayName("Approving a REJECTED borrow request is rejected")
        void approveRejectedBorrowRequest_fails() {
            BorrowRequest rejected = BorrowRequest.builder()
                    .id(5L)
                    .approvalStatus(ApprovalStatus.REJECTED)
                    .status(BorrowStatus.PENDING)
                    .build();
            when(borrowRequestMapper.findById(5L)).thenReturn(rejected);

            assertThatThrownBy(() -> approvalService.approveBorrowRequest(5L, 1L, "note"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("is not pending approval");
        }

        @Test
        @DisplayName("Deciding on a COMPLETED borrow request is rejected")
        void decideOnCompletedRequest_fails() {
            BorrowRequest completed = BorrowRequest.builder()
                    .id(5L)
                    .approvalStatus(ApprovalStatus.PENDING_APPROVAL)
                    .status(BorrowStatus.COMPLETED)
                    .build();
            when(borrowRequestMapper.findById(5L)).thenReturn(completed);

            assertThatThrownBy(() -> approvalService.approveBorrowRequest(5L, 1L, "note"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already completed");
        }
    }

    @Nested
    @DisplayName("BorrowService & AssetTrackingService Lifecycle Enforcement")
    class BorrowAndReturnEnforcement {

        private BorrowRequestMapper borrowRequestMapper;
        private BorrowItemMapper borrowItemMapper;
        private BorrowService borrowService;
        private AssetTrackingService assetTrackingService;
        private ItemInstanceMapper itemInstanceMapper;

        @BeforeEach
        void setUp() {
            borrowRequestMapper = mock(BorrowRequestMapper.class);
            borrowItemMapper = mock(BorrowItemMapper.class);
            itemInstanceMapper = mock(ItemInstanceMapper.class);
            borrowService = new BorrowService(borrowRequestMapper, borrowItemMapper, null, null);
            assetTrackingService = new AssetTrackingService(itemInstanceMapper, borrowRequestMapper, borrowItemMapper, null, null);
        }

        @Test
        @DisplayName("Returning on an already COMPLETED borrow request is rejected")
        void returnOnCompletedRequest_fails() {
            BorrowItem item = BorrowItem.builder().id(100L).borrowRequestId(1L).itemId(10L).qtyBorrowed(1).qtyReturned(1).build();
            BorrowRequest req = BorrowRequest.builder().id(1L).status(BorrowStatus.COMPLETED).build();

            when(borrowItemMapper.findById(100L)).thenReturn(item);
            when(borrowRequestMapper.findById(1L)).thenReturn(req);

            assertThatThrownBy(() -> borrowService.processReturn(100L, 1, 99L))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Must be BORROWED or PARTIAL_RETURN");
        }

        @Test
        @DisplayName("Batch return on COMPLETED borrow request is rejected")
        void batchReturnOnCompletedRequest_fails() {
            BorrowRequest req = BorrowRequest.builder().id(1L).status(BorrowStatus.COMPLETED).build();
            when(borrowRequestMapper.findById(1L)).thenReturn(req);

            BorrowService.ReturnLine line = new BorrowService.ReturnLine(100L, 1);
            assertThatThrownBy(() -> borrowService.processReturnsForRequest(1L, List.of(line), 99L))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("terminal status");
        }

        @Test
        @DisplayName("Checking out a tool when request is in PARTIAL_RETURN is rejected")
        void checkoutOnPartialReturnRequest_fails() {
            ItemInstance instance = ItemInstance.builder()
                    .id(10L)
                    .itemId(100L)
                    .serialNumber("SN-1")
                    .isAvailable(true)
                    .toolCondition(ToolCondition.GOOD)
                    .build();
            BorrowRequest req = BorrowRequest.builder()
                    .id(1L)
                    .approvalStatus(ApprovalStatus.APPROVED)
                    .status(BorrowStatus.PARTIAL_RETURN)
                    .build();

            when(itemInstanceMapper.findBySerialNumber("SN-1")).thenReturn(instance);
            when(borrowRequestMapper.findById(1L)).thenReturn(req);

            assertThatThrownBy(() -> assetTrackingService.checkoutItemInstance("SN-1", 1L, 99L))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Must be PENDING or BORROWED");
        }

        @Test
        @DisplayName("Checking out a tool when request is in terminal COMPLETED state is rejected")
        void checkoutOnCompletedRequest_fails() {
            ItemInstance instance = ItemInstance.builder()
                    .id(10L)
                    .itemId(100L)
                    .serialNumber("SN-1")
                    .isAvailable(true)
                    .toolCondition(ToolCondition.GOOD)
                    .build();
            BorrowRequest req = BorrowRequest.builder()
                    .id(1L)
                    .approvalStatus(ApprovalStatus.APPROVED)
                    .status(BorrowStatus.COMPLETED)
                    .build();

            when(itemInstanceMapper.findBySerialNumber("SN-1")).thenReturn(instance);
            when(borrowRequestMapper.findById(1L)).thenReturn(req);

            assertThatThrownBy(() -> assetTrackingService.checkoutItemInstance("SN-1", 1L, 99L))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("terminal state");
        }
    }
}

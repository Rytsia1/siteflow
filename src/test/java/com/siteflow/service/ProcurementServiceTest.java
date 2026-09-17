package com.siteflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.siteflow.domain.MaterialRequest;
import com.siteflow.domain.PurchaseOrder;
import com.siteflow.domain.enums.MaterialRequestStatus;
import com.siteflow.domain.enums.PurchaseOrderStatus;
import com.siteflow.mapper.MaterialRequestItemMapper;
import com.siteflow.mapper.MaterialRequestMapper;
import com.siteflow.mapper.PurchaseOrderMapper;
import com.siteflow.web.ResourceNotFoundException;
import com.siteflow.service.ProcurementService.MrLineItem;

@ExtendWith(MockitoExtension.class)
class ProcurementServiceTest {

    @Mock
    private MaterialRequestMapper materialRequestMapper;
    @Mock
    private MaterialRequestItemMapper materialRequestItemMapper;
    @Mock
    private PurchaseOrderMapper purchaseOrderMapper;

    private ProcurementService procurementService;

    @BeforeEach
    void setUp() {
        procurementService = new ProcurementService(materialRequestMapper, materialRequestItemMapper,
                purchaseOrderMapper);
    }

    @Test
    @DisplayName("submitMaterialRequest rejects an empty item list without inserting anything")
    void submit_emptyItems_throwsWithoutSideEffects() {
        assertThatThrownBy(() -> procurementService.submitMaterialRequest(1L, "why", List.of()))
                .isInstanceOf(IllegalArgumentException.class);

        verify(materialRequestMapper, never()).insert(any());
        verify(materialRequestItemMapper, never()).insert(any());
    }

    @Test
    @DisplayName("submitMaterialRequest creates the MR directly in SUBMITTED status")
    void submit_valid_createsInSubmittedStatus() {
        MaterialRequest created = procurementService.submitMaterialRequest(
                1L, "need drills", List.of(new MrLineItem(5L, 2)));

        assertThat(created.getStatus()).isEqualTo(MaterialRequestStatus.SUBMITTED);
        verify(materialRequestItemMapper).insert(any());
    }

    @Test
    @DisplayName("approveMaterialRequest writes the approval and returns the updated MR")
    void approve_submittedMr_writesApprovalAndReturnsUpdated() {
        MaterialRequest submitted = MaterialRequest.builder().id(1L).status(MaterialRequestStatus.SUBMITTED).build();
        MaterialRequest approved = MaterialRequest.builder().id(1L).status(MaterialRequestStatus.APPROVED)
                .approvedBy(7L).approvalNote("ok").build();
        when(materialRequestMapper.findById(1L)).thenReturn(submitted, approved);
        when(materialRequestMapper.updateApproval(1L, MaterialRequestStatus.SUBMITTED, MaterialRequestStatus.APPROVED,
                7L, "ok")).thenReturn(1);

        MaterialRequest result = procurementService.approveMaterialRequest(1L, 7L, "ok");

        assertThat(result.getStatus()).isEqualTo(MaterialRequestStatus.APPROVED);
        verify(materialRequestMapper).updateApproval(1L, MaterialRequestStatus.SUBMITTED,
                MaterialRequestStatus.APPROVED, 7L, "ok");
    }

    @Test
    @DisplayName("approveMaterialRequest fails with 404-mapped exception when the MR id doesn't exist")
    void approve_unknownMr_throwsResourceNotFound() {
        when(materialRequestMapper.findById(404L)).thenReturn(null);

        assertThatThrownBy(() -> procurementService.approveMaterialRequest(404L, 7L, null))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(materialRequestMapper, never()).updateApproval(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("approveMaterialRequest fails without writing when the MR isn't SUBMITTED (already decided)")
    void approve_alreadyApproved_throwsWithoutWriting() {
        MaterialRequest approved = MaterialRequest.builder().id(1L).status(MaterialRequestStatus.APPROVED).build();
        when(materialRequestMapper.findById(1L)).thenReturn(approved);

        assertThatThrownBy(() -> procurementService.approveMaterialRequest(1L, 7L, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must be SUBMITTED");

        verify(materialRequestMapper, never()).updateApproval(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("approveMaterialRequest fails when a concurrent decision already actioned the MR")
    void approve_concurrentlyActioned_throws() {
        MaterialRequest submitted = MaterialRequest.builder().id(1L).status(MaterialRequestStatus.SUBMITTED).build();
        when(materialRequestMapper.findById(1L)).thenReturn(submitted);
        when(materialRequestMapper.updateApproval(1L, MaterialRequestStatus.SUBMITTED, MaterialRequestStatus.APPROVED,
                7L, null)).thenReturn(0);

        assertThatThrownBy(() -> procurementService.approveMaterialRequest(1L, 7L, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("concurrently modified");
    }

    @Test
    @DisplayName("rejectMaterialRequest writes REJECTED with the admin id and note")
    void reject_submittedMr_writesRejected() {
        MaterialRequest submitted = MaterialRequest.builder().id(1L).status(MaterialRequestStatus.SUBMITTED).build();
        MaterialRequest rejected = MaterialRequest.builder().id(1L).status(MaterialRequestStatus.REJECTED).build();
        when(materialRequestMapper.findById(1L)).thenReturn(submitted, rejected);
        when(materialRequestMapper.updateApproval(1L, MaterialRequestStatus.SUBMITTED, MaterialRequestStatus.REJECTED,
                7L, "out of budget")).thenReturn(1);

        MaterialRequest result = procurementService.rejectMaterialRequest(1L, 7L, "out of budget");

        assertThat(result.getStatus()).isEqualTo(MaterialRequestStatus.REJECTED);
        verify(materialRequestMapper).updateApproval(1L, MaterialRequestStatus.SUBMITTED,
                MaterialRequestStatus.REJECTED, 7L, "out of budget");
    }

    @Test
    @DisplayName("rejectMaterialRequest fails without writing when the MR was already rejected (terminal)")
    void reject_alreadyRejected_throwsWithoutWriting() {
        MaterialRequest rejected = MaterialRequest.builder().id(1L).status(MaterialRequestStatus.REJECTED).build();
        when(materialRequestMapper.findById(1L)).thenReturn(rejected);

        assertThatThrownBy(() -> procurementService.rejectMaterialRequest(1L, 7L, "note"))
                .isInstanceOf(IllegalStateException.class);

        verify(materialRequestMapper, never()).updateApproval(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("generatePurchaseOrder fails for a rejected request without creating a PO")
    void generatePo_rejectedMr_throwsWithoutCreatingPo() {
        MaterialRequest rejected = MaterialRequest.builder().id(1L).status(MaterialRequestStatus.REJECTED).build();
        when(materialRequestMapper.findById(1L)).thenReturn(rejected);

        assertThatThrownBy(() -> procurementService.generatePurchaseOrder(1L, "Acme", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must be APPROVED");

        verify(purchaseOrderMapper, never()).insert(any());
    }

    @Test
    @DisplayName("generatePurchaseOrder succeeds for an APPROVED request and moves it to PO_CREATED")
    void generatePo_approvedMr_succeeds() {
        MaterialRequest approved = MaterialRequest.builder().id(1L).status(MaterialRequestStatus.APPROVED).build();
        when(materialRequestMapper.findById(1L)).thenReturn(approved);
        when(materialRequestMapper.updateStatus(1L, MaterialRequestStatus.APPROVED, MaterialRequestStatus.PO_CREATED))
                .thenReturn(1);

        PurchaseOrder po = procurementService.generatePurchaseOrder(1L, "Acme Supplies", null);

        assertThat(po.getPoStatus()).isEqualTo(PurchaseOrderStatus.ISSUED);
        verify(purchaseOrderMapper).insert(any());
    }

    @Test
    @DisplayName("generatePurchaseOrder fails with 404-mapped exception when the MR id doesn't exist")
    void generatePo_unknownMr_throwsResourceNotFound() {
        when(materialRequestMapper.findById(404L)).thenReturn(null);

        assertThatThrownBy(() -> procurementService.generatePurchaseOrder(404L, "Acme", null))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(purchaseOrderMapper, never()).insert(any());
    }

    @Test
    @DisplayName("generatePurchaseOrder fails for a request that isn't APPROVED yet (missing prerequisite)")
    void generatePo_notApproved_throwsWithoutCreatingPo() {
        MaterialRequest submitted = MaterialRequest.builder().id(1L).status(MaterialRequestStatus.SUBMITTED).build();
        when(materialRequestMapper.findById(1L)).thenReturn(submitted);

        assertThatThrownBy(() -> procurementService.generatePurchaseOrder(1L, "Acme", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must be APPROVED");

        verify(purchaseOrderMapper, never()).insert(any());
        verify(materialRequestMapper, never()).updateStatus(any(), any(), any());
    }

    @Test
    @DisplayName("generatePurchaseOrder fails when a concurrent request already advanced the MR (repeated transition)")
    void generatePo_concurrentlyAdvanced_throwsBeforeCreatingPo() {
        MaterialRequest approved = MaterialRequest.builder().id(1L).status(MaterialRequestStatus.APPROVED).build();
        when(materialRequestMapper.findById(1L)).thenReturn(approved);
        when(materialRequestMapper.updateStatus(1L, MaterialRequestStatus.APPROVED, MaterialRequestStatus.PO_CREATED))
                .thenReturn(0);

        assertThatThrownBy(() -> procurementService.generatePurchaseOrder(1L, "Acme", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("concurrently modified");

        // The MR status claim is atomic and happens before the PO is built, so losing the
        // race means no PO is ever inserted for this call.
        verify(purchaseOrderMapper, never()).insert(any());
    }

    @Test
    @DisplayName("markMaterialRequestCompleted fails for a request that hasn't reached PO_CREATED")
    void markCompleted_wrongStatus_throws() {
        MaterialRequest approved = MaterialRequest.builder().id(1L).status(MaterialRequestStatus.APPROVED).build();
        when(materialRequestMapper.findById(1L)).thenReturn(approved);

        assertThatThrownBy(() -> procurementService.markMaterialRequestCompleted(1L))
                .isInstanceOf(IllegalStateException.class);

        verify(materialRequestMapper, never()).updateStatus(any(), any(), any());
    }
}

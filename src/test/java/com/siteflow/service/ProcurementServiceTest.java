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

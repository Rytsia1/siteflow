package com.siteflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.siteflow.domain.BorrowItem;
import com.siteflow.domain.BorrowRequest;
import com.siteflow.domain.ItemInstance;
import com.siteflow.domain.ItemStock;
import com.siteflow.domain.TransactionLog;
import com.siteflow.domain.enums.ApprovalStatus;
import com.siteflow.domain.enums.BorrowStatus;
import com.siteflow.domain.enums.ToolCondition;
import com.siteflow.domain.enums.TransactionType;
import com.siteflow.mapper.BorrowItemMapper;
import com.siteflow.mapper.BorrowRequestMapper;
import com.siteflow.mapper.ItemInstanceMapper;
import com.siteflow.mapper.ItemStockMapper;
import com.siteflow.mapper.TransactionLogMapper;
import com.siteflow.web.ResourceNotFoundException;

@ExtendWith(MockitoExtension.class)
class AssetTrackingServiceTest {

    @Mock
    private ItemInstanceMapper itemInstanceMapper;
    @Mock
    private BorrowRequestMapper borrowRequestMapper;
    @Mock
    private BorrowItemMapper borrowItemMapper;
    @Mock
    private ItemStockMapper itemStockMapper;
    @Mock
    private TransactionLogMapper transactionLogMapper;

    private AssetTrackingService assetTrackingService;

    @BeforeEach
    void setUp() {
        assetTrackingService = new AssetTrackingService(
                itemInstanceMapper,
                borrowRequestMapper,
                borrowItemMapper,
                itemStockMapper,
                transactionLogMapper);
    }

    @Test
    @DisplayName("checkoutItemInstance succeeds for a GOOD tool against an APPROVED borrow request and logs audit")
    void checkout_goodToolApprovedRequest_succeeds() {
        ItemInstance instance = ItemInstance.builder().id(1L).itemId(50L).serialNumber("SN-1").toolCondition(ToolCondition.GOOD)
                .isAvailable(true).build();
        BorrowRequest approved = BorrowRequest.builder().id(5L).userId(7L).locationId(10L).status(BorrowStatus.PENDING)
                .approvalStatus(ApprovalStatus.APPROVED).build();
        when(itemInstanceMapper.findBySerialNumber("SN-1")).thenReturn(instance);
        when(borrowRequestMapper.findById(5L)).thenReturn(approved);
        when(borrowItemMapper.findByBorrowRequestId(5L)).thenReturn(List.of(
                BorrowItem.builder().id(101L).borrowRequestId(5L).itemId(50L).qtyBorrowed(1).build()
        ));
        when(itemInstanceMapper.assignToBorrowRequest(1L, 5L)).thenReturn(1);
        when(itemInstanceMapper.findById(1L)).thenReturn(instance);

        ItemInstance result = assetTrackingService.checkoutItemInstance("SN-1", 5L, 7L);

        assertThat(result.getToolCondition()).isEqualTo(ToolCondition.GOOD);
        verify(itemInstanceMapper).assignToBorrowRequest(1L, 5L);
        verify(borrowRequestMapper).updateStatus(5L, BorrowStatus.BORROWED);

        ArgumentCaptor<TransactionLog> logCaptor = ArgumentCaptor.forClass(TransactionLog.class);
        verify(transactionLogMapper).insert(logCaptor.capture());
        TransactionLog log = logCaptor.getValue();
        assertThat(log.getItemId()).isEqualTo(50L);
        assertThat(log.getLocationId()).isEqualTo(10L);
        assertThat(log.getUserId()).isEqualTo(7L);
        assertThat(log.getTransactionType()).isEqualTo(TransactionType.BORROW);
        assertThat(log.getQtyChange()).isEqualTo(-1);
        assertThat(log.getReferenceId()).isEqualTo(5L);
    }

    @Test
    @DisplayName("checkoutItemInstance rejects a damaged tool — it cannot become immediately available")
    void checkout_brokenTool_throwsWithoutCheckingBorrowRequest() {
        ItemInstance instance = ItemInstance.builder().id(1L).serialNumber("SN-1").toolCondition(ToolCondition.BROKEN)
                .isAvailable(true).build();
        when(itemInstanceMapper.findBySerialNumber("SN-1")).thenReturn(instance);

        assertThatThrownBy(() -> assetTrackingService.checkoutItemInstance("SN-1", 5L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BROKEN");

        verify(borrowRequestMapper, never()).findById(any());
    }

    @Test
    @DisplayName("checkoutItemInstance rejects checkout if tool is already borrowed by another request")
    void checkout_alreadyBorrowed_throwsIllegalState() {
        ItemInstance instance = ItemInstance.builder().id(1L).serialNumber("SN-1").toolCondition(ToolCondition.GOOD)
                .isAvailable(false).currentBorrowRequestId(99L).build();
        when(itemInstanceMapper.findBySerialNumber("SN-1")).thenReturn(instance);

        assertThatThrownBy(() -> assetTrackingService.checkoutItemInstance("SN-1", 5L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already borrowed");
    }

    @Test
    @DisplayName("checkoutItemInstance rejects checkout against a borrow request that isn't approved yet")
    void checkout_borrowRequestNotApproved_throws() {
        ItemInstance instance = ItemInstance.builder().id(1L).serialNumber("SN-1").toolCondition(ToolCondition.GOOD)
                .isAvailable(true).build();
        BorrowRequest pending = BorrowRequest.builder().id(5L).status(BorrowStatus.PENDING)
                .approvalStatus(ApprovalStatus.PENDING_APPROVAL).build();
        when(itemInstanceMapper.findBySerialNumber("SN-1")).thenReturn(instance);
        when(borrowRequestMapper.findById(5L)).thenReturn(pending);

        assertThatThrownBy(() -> assetTrackingService.checkoutItemInstance("SN-1", 5L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not approved");
    }

    @Test
    @DisplayName("checkoutItemInstance rejects checkout if tool item does not match any requested item")
    void checkout_toolNotPartOfRequest_throwsIllegalState() {
        ItemInstance instance = ItemInstance.builder().id(1L).itemId(50L).serialNumber("SN-1").toolCondition(ToolCondition.GOOD)
                .isAvailable(true).build();
        BorrowRequest approved = BorrowRequest.builder().id(5L).status(BorrowStatus.PENDING)
                .approvalStatus(ApprovalStatus.APPROVED).build();

        when(itemInstanceMapper.findBySerialNumber("SN-1")).thenReturn(instance);
        when(borrowRequestMapper.findById(5L)).thenReturn(approved);
        when(borrowItemMapper.findByBorrowRequestId(5L)).thenReturn(List.of(
                BorrowItem.builder().id(101L).borrowRequestId(5L).itemId(999L).build() // Different itemId
        ));

        assertThatThrownBy(() -> assetTrackingService.checkoutItemInstance("SN-1", 5L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("is not part of borrow request 5");
    }

    @Test
    @DisplayName("checkoutItemInstance fails when concurrent assignment loses the race (zero rows updated)")
    void checkout_concurrencyRace_throwsIllegalState() {
        ItemInstance instance = ItemInstance.builder().id(1L).itemId(50L).serialNumber("SN-1").toolCondition(ToolCondition.GOOD)
                .isAvailable(true).build();
        BorrowRequest approved = BorrowRequest.builder().id(5L).status(BorrowStatus.PENDING)
                .approvalStatus(ApprovalStatus.APPROVED).build();

        when(itemInstanceMapper.findBySerialNumber("SN-1")).thenReturn(instance);
        when(borrowRequestMapper.findById(5L)).thenReturn(approved);
        when(borrowItemMapper.findByBorrowRequestId(5L)).thenReturn(List.of(
                BorrowItem.builder().id(101L).borrowRequestId(5L).itemId(50L).build()
        ));
        when(itemInstanceMapper.assignToBorrowRequest(1L, 5L)).thenReturn(0);

        assertThatThrownBy(() -> assetTrackingService.checkoutItemInstance("SN-1", 5L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already checked out or unavailable");
    }

    @Test
    @DisplayName("checkoutItemInstance falls back to QR code lookup when serial number doesn't match")
    void checkout_unknownSerialNumber_fallsBackToQrCode() {
        ItemInstance instance = ItemInstance.builder().id(1L).itemId(50L).qrCodeValue("QR-1").toolCondition(ToolCondition.GOOD)
                .isAvailable(true).build();
        BorrowRequest approved = BorrowRequest.builder().id(5L).status(BorrowStatus.PENDING)
                .approvalStatus(ApprovalStatus.APPROVED).build();
        when(itemInstanceMapper.findBySerialNumber("QR-1")).thenReturn(null);
        when(itemInstanceMapper.findByQrCodeValue("QR-1")).thenReturn(instance);
        when(borrowRequestMapper.findById(5L)).thenReturn(approved);
        when(borrowItemMapper.findByBorrowRequestId(5L)).thenReturn(List.of(
                BorrowItem.builder().id(101L).borrowRequestId(5L).itemId(50L).build()
        ));
        when(itemInstanceMapper.assignToBorrowRequest(1L, 5L)).thenReturn(1);
        when(itemInstanceMapper.findById(1L)).thenReturn(instance);

        ItemInstance result = assetTrackingService.checkoutItemInstance("QR-1", 5L);

        assertThat(result.getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("checkoutItemInstance fails with 404-mapped exception when no tool matches either identifier")
    void checkout_unknownIdentifier_throwsResourceNotFound() {
        when(itemInstanceMapper.findBySerialNumber("MISSING")).thenReturn(null);
        when(itemInstanceMapper.findByQrCodeValue("MISSING")).thenReturn(null);

        assertThatThrownBy(() -> assetTrackingService.checkoutItemInstance("MISSING", 5L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("checkoutItemInstance fails with 404-mapped exception when the borrow request id doesn't exist")
    void checkout_unknownBorrowRequest_throwsResourceNotFound() {
        ItemInstance instance = ItemInstance.builder().id(1L).serialNumber("SN-1").toolCondition(ToolCondition.GOOD)
                .isAvailable(true).build();
        when(itemInstanceMapper.findBySerialNumber("SN-1")).thenReturn(instance);
        when(borrowRequestMapper.findById(99L)).thenReturn(null);

        assertThatThrownBy(() -> assetTrackingService.checkoutItemInstance("SN-1", 99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("returnItemInstance in GOOD condition restores stock, makes tool available, and logs audit")
    void returnItemInstance_goodCondition_restoresStockAndLogs() {
        ItemInstance before = ItemInstance.builder().id(1L).itemId(50L).serialNumber("SN-1").toolCondition(ToolCondition.GOOD)
                .isAvailable(false).currentBorrowRequestId(5L).build();
        ItemInstance after = ItemInstance.builder().id(1L).itemId(50L).serialNumber("SN-1").toolCondition(ToolCondition.GOOD)
                .isAvailable(true).build();
        BorrowRequest request = BorrowRequest.builder().id(5L).userId(7L).locationId(10L).status(BorrowStatus.BORROWED).build();
        BorrowItem item = BorrowItem.builder().id(101L).borrowRequestId(5L).itemId(50L).qtyBorrowed(1).qtyReturned(0).build();
        ItemStock stock = ItemStock.builder().id(200L).itemId(50L).locationId(10L).currentQty(3).build();

        when(itemInstanceMapper.findBySerialNumber("SN-1")).thenReturn(before);
        when(borrowRequestMapper.findById(5L)).thenReturn(request);
        when(itemInstanceMapper.releaseReturn(1L, ToolCondition.GOOD, true)).thenReturn(1);
        when(borrowItemMapper.findByBorrowRequestId(5L)).thenReturn(
                List.of(item),
                List.of(BorrowItem.builder().id(101L).borrowRequestId(5L).itemId(50L).qtyBorrowed(1).qtyReturned(1).build())
        );
        when(itemStockMapper.findByItemIdAndLocationId(50L, 10L)).thenReturn(stock);
        when(itemInstanceMapper.findById(1L)).thenReturn(after);

        ItemInstance result = assetTrackingService.returnItemInstance("SN-1", ToolCondition.GOOD, 7L);

        assertThat(result.getToolCondition()).isEqualTo(ToolCondition.GOOD);
        verify(itemStockMapper).adjustQty(200L, 1);
        verify(borrowRequestMapper).updateStatus(5L, BorrowStatus.COMPLETED);

        ArgumentCaptor<TransactionLog> logCaptor = ArgumentCaptor.forClass(TransactionLog.class);
        verify(transactionLogMapper).insert(logCaptor.capture());
        TransactionLog log = logCaptor.getValue();
        assertThat(log.getTransactionType()).isEqualTo(TransactionType.RETURN);
        assertThat(log.getQtyChange()).isEqualTo(1);
    }

    @Test
    @DisplayName("returnItemInstance in NEEDS_REPAIR condition quarantines tool and does NOT restore stock")
    void returnItemInstance_needsRepair_quarantinesToolNoStockRestored() {
        ItemInstance before = ItemInstance.builder().id(1L).itemId(50L).serialNumber("SN-1").toolCondition(ToolCondition.GOOD)
                .isAvailable(false).currentBorrowRequestId(5L).build();
        ItemInstance after = ItemInstance.builder().id(1L).itemId(50L).serialNumber("SN-1").toolCondition(ToolCondition.NEEDS_REPAIR)
                .isAvailable(false).build();
        BorrowRequest request = BorrowRequest.builder().id(5L).userId(7L).locationId(10L).status(BorrowStatus.BORROWED).build();
        BorrowItem item = BorrowItem.builder().id(101L).borrowRequestId(5L).itemId(50L).qtyBorrowed(1).qtyReturned(0).build();

        when(itemInstanceMapper.findBySerialNumber("SN-1")).thenReturn(before);
        when(borrowRequestMapper.findById(5L)).thenReturn(request);
        // Quarantined: isAvailable must be false
        when(itemInstanceMapper.releaseReturn(1L, ToolCondition.NEEDS_REPAIR, false)).thenReturn(1);
        when(borrowItemMapper.findByBorrowRequestId(5L)).thenReturn(
                List.of(item),
                List.of(BorrowItem.builder().id(101L).borrowRequestId(5L).itemId(50L).qtyBorrowed(1).qtyReturned(1).build())
        );
        when(itemInstanceMapper.findById(1L)).thenReturn(after);

        ItemInstance result = assetTrackingService.returnItemInstance("SN-1", ToolCondition.NEEDS_REPAIR, 7L);

        assertThat(result.getToolCondition()).isEqualTo(ToolCondition.NEEDS_REPAIR);
        // Stock must NOT be adjusted back into available pool
        verify(itemStockMapper, never()).adjustQty(any(), anyInt());
    }

    @Test
    @DisplayName("returnItemInstance rejects tool that is not currently borrowed")
    void returnItemInstance_notCurrentlyBorrowed_throwsIllegalState() {
        ItemInstance available = ItemInstance.builder().id(1L).serialNumber("SN-1").toolCondition(ToolCondition.GOOD)
                .isAvailable(true).currentBorrowRequestId(null).build();
        when(itemInstanceMapper.findBySerialNumber("SN-1")).thenReturn(available);

        assertThatThrownBy(() -> assetTrackingService.returnItemInstance("SN-1", ToolCondition.GOOD))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not currently borrowed");
    }

    @Test
    @DisplayName("returnItemInstance fails when concurrent release fails (zero rows updated)")
    void returnItemInstance_concurrentReleaseFails_throwsIllegalState() {
        ItemInstance before = ItemInstance.builder().id(1L).itemId(50L).serialNumber("SN-1").toolCondition(ToolCondition.GOOD)
                .isAvailable(false).currentBorrowRequestId(5L).build();
        BorrowRequest request = BorrowRequest.builder().id(5L).status(BorrowStatus.BORROWED).build();

        when(itemInstanceMapper.findBySerialNumber("SN-1")).thenReturn(before);
        when(borrowRequestMapper.findById(5L)).thenReturn(request);
        when(itemInstanceMapper.releaseReturn(1L, ToolCondition.GOOD, true)).thenReturn(0);

        assertThatThrownBy(() -> assetTrackingService.returnItemInstance("SN-1", ToolCondition.GOOD))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("return failed");
    }
}

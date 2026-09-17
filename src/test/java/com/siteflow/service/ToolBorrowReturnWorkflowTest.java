package com.siteflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.siteflow.domain.BorrowItem;
import com.siteflow.domain.BorrowRequest;
import com.siteflow.domain.ItemInstance;
import com.siteflow.domain.ItemStock;
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
class ToolBorrowReturnWorkflowTest {

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

    // -------------------------------------------------------------------------
    // 1. Normal Borrow
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Normal borrow: approved request successfully checks out available tool in GOOD condition")
    void normalBorrow_success() {
        Long requestId = 100L;
        Long toolInstanceId = 1L;
        Long itemId = 50L;
        String serialNumber = "SN-DRILL-001";

        ItemInstance availableTool = ItemInstance.builder()
                .id(toolInstanceId)
                .itemId(itemId)
                .serialNumber(serialNumber)
                .toolCondition(ToolCondition.GOOD)
                .isAvailable(true)
                .currentBorrowRequestId(null)
                .build();

        BorrowRequest approvedRequest = BorrowRequest.builder()
                .id(requestId)
                .userId(10L)
                .locationId(1L)
                .status(BorrowStatus.PENDING)
                .approvalStatus(ApprovalStatus.APPROVED)
                .build();

        BorrowItem borrowItem = BorrowItem.builder()
                .id(1L)
                .borrowRequestId(requestId)
                .itemId(itemId)
                .qtyBorrowed(1)
                .qtyReturned(0)
                .build();

        ItemInstance checkedOutTool = ItemInstance.builder()
                .id(toolInstanceId)
                .itemId(itemId)
                .serialNumber(serialNumber)
                .toolCondition(ToolCondition.GOOD)
                .isAvailable(false)
                .currentBorrowRequestId(requestId)
                .build();

        when(itemInstanceMapper.findBySerialNumber(serialNumber)).thenReturn(availableTool);
        when(borrowRequestMapper.findById(requestId)).thenReturn(approvedRequest);
        when(borrowItemMapper.findByBorrowRequestId(requestId)).thenReturn(List.of(borrowItem));
        when(itemInstanceMapper.assignToBorrowRequest(toolInstanceId, requestId)).thenReturn(1);
        when(itemInstanceMapper.findById(toolInstanceId)).thenReturn(checkedOutTool);

        ItemInstance result = assetTrackingService.checkoutItemInstance(serialNumber, requestId, 2L);

        assertThat(result.getIsAvailable()).isFalse();
        assertThat(result.getCurrentBorrowRequestId()).isEqualTo(requestId);
        verify(itemInstanceMapper).assignToBorrowRequest(toolInstanceId, requestId);
        verify(borrowRequestMapper).updateStatus(requestId, BorrowStatus.BORROWED);
        verify(transactionLogMapper).insert(any());
    }

    // -------------------------------------------------------------------------
    // 2. Normal Return
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Normal return: borrowed tool returned in GOOD condition restores stock and completes request")
    void normalReturn_success() {
        Long requestId = 100L;
        Long toolInstanceId = 1L;
        Long itemId = 50L;
        String serialNumber = "SN-DRILL-001";

        ItemInstance borrowedTool = ItemInstance.builder()
                .id(toolInstanceId)
                .itemId(itemId)
                .serialNumber(serialNumber)
                .toolCondition(ToolCondition.GOOD)
                .isAvailable(false)
                .currentBorrowRequestId(requestId)
                .build();

        BorrowRequest activeRequest = BorrowRequest.builder()
                .id(requestId)
                .userId(10L)
                .locationId(1L)
                .status(BorrowStatus.BORROWED)
                .approvalStatus(ApprovalStatus.APPROVED)
                .build();

        BorrowItem line = BorrowItem.builder()
                .id(5L)
                .borrowRequestId(requestId)
                .itemId(itemId)
                .qtyBorrowed(1)
                .qtyReturned(0)
                .build();

        BorrowItem completedLine = BorrowItem.builder()
                .id(5L)
                .borrowRequestId(requestId)
                .itemId(itemId)
                .qtyBorrowed(1)
                .qtyReturned(1)
                .build();

        ItemStock stock = ItemStock.builder()
                .id(20L)
                .itemId(itemId)
                .locationId(1L)
                .currentQty(4)
                .build();

        ItemInstance returnedTool = ItemInstance.builder()
                .id(toolInstanceId)
                .itemId(itemId)
                .serialNumber(serialNumber)
                .toolCondition(ToolCondition.GOOD)
                .isAvailable(true)
                .currentBorrowRequestId(null)
                .build();

        when(itemInstanceMapper.findBySerialNumber(serialNumber)).thenReturn(borrowedTool);
        when(borrowRequestMapper.findById(requestId)).thenReturn(activeRequest);
        when(itemInstanceMapper.releaseReturn(toolInstanceId, ToolCondition.GOOD, true)).thenReturn(1);
        when(borrowItemMapper.findByBorrowRequestId(requestId)).thenReturn(List.of(line), List.of(completedLine));
        when(itemStockMapper.findByItemIdAndLocationId(itemId, 1L)).thenReturn(stock);
        when(itemInstanceMapper.findById(toolInstanceId)).thenReturn(returnedTool);

        ItemInstance result = assetTrackingService.returnItemInstance(serialNumber, ToolCondition.GOOD, 2L);

        assertThat(result.getIsAvailable()).isTrue();
        assertThat(result.getCurrentBorrowRequestId()).isNull();
        verify(itemInstanceMapper).releaseReturn(toolInstanceId, ToolCondition.GOOD, true);
        verify(borrowItemMapper).recordReturn(eq(5L), eq(1), any());
        verify(borrowRequestMapper).updateStatus(requestId, BorrowStatus.COMPLETED);
        verify(itemStockMapper).adjustQty(20L, 1);
        verify(transactionLogMapper).insert(any());
    }

    // -------------------------------------------------------------------------
    // 3. Duplicate Borrow
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Duplicate borrow: attempting to borrow an already borrowed tool is rejected")
    void duplicateBorrow_fails() {
        String serialNumber = "SN-DRILL-001";

        ItemInstance alreadyBorrowed = ItemInstance.builder()
                .id(1L)
                .serialNumber(serialNumber)
                .toolCondition(ToolCondition.GOOD)
                .isAvailable(false)
                .currentBorrowRequestId(99L)
                .build();

        when(itemInstanceMapper.findBySerialNumber(serialNumber)).thenReturn(alreadyBorrowed);

        assertThatThrownBy(() -> assetTrackingService.checkoutItemInstance(serialNumber, 100L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already borrowed");

        verify(itemInstanceMapper, never()).assignToBorrowRequest(any(), any());
        verify(borrowRequestMapper, never()).updateStatus(any(), any());
    }

    // -------------------------------------------------------------------------
    // 4. Duplicate Return
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Duplicate return: attempting to return a tool that is not borrowed is rejected")
    void duplicateReturn_fails() {
        String serialNumber = "SN-DRILL-001";

        ItemInstance notBorrowed = ItemInstance.builder()
                .id(1L)
                .serialNumber(serialNumber)
                .toolCondition(ToolCondition.GOOD)
                .isAvailable(true)
                .currentBorrowRequestId(null)
                .build();

        when(itemInstanceMapper.findBySerialNumber(serialNumber)).thenReturn(notBorrowed);

        assertThatThrownBy(() -> assetTrackingService.returnItemInstance(serialNumber, ToolCondition.GOOD))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not currently borrowed");

        verify(itemInstanceMapper, never()).releaseReturn(any(), any(), anyBoolean());
        verify(borrowItemMapper, never()).recordReturn(any(), anyInt(), any());
    }

    // -------------------------------------------------------------------------
    // 5. Invalid Status Transitions
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Invalid status transition: cannot checkout against PENDING_APPROVAL request")
    void invalidStatusTransition_pendingApproval_fails() {
        String serialNumber = "SN-001";
        ItemInstance tool = ItemInstance.builder()
                .id(1L).serialNumber(serialNumber).toolCondition(ToolCondition.GOOD).isAvailable(true).build();
        BorrowRequest unapproved = BorrowRequest.builder()
                .id(10L).approvalStatus(ApprovalStatus.PENDING_APPROVAL).build();

        when(itemInstanceMapper.findBySerialNumber(serialNumber)).thenReturn(tool);
        when(borrowRequestMapper.findById(10L)).thenReturn(unapproved);

        assertThatThrownBy(() -> assetTrackingService.checkoutItemInstance(serialNumber, 10L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not approved");

        verify(itemInstanceMapper, never()).assignToBorrowRequest(any(), any());
    }

    @Test
    @DisplayName("Invalid status transition: cannot checkout against REJECTED request")
    void invalidStatusTransition_rejectedRequest_fails() {
        String serialNumber = "SN-001";
        ItemInstance tool = ItemInstance.builder()
                .id(1L).serialNumber(serialNumber).toolCondition(ToolCondition.GOOD).isAvailable(true).build();
        BorrowRequest rejected = BorrowRequest.builder()
                .id(10L).approvalStatus(ApprovalStatus.REJECTED).build();

        when(itemInstanceMapper.findBySerialNumber(serialNumber)).thenReturn(tool);
        when(borrowRequestMapper.findById(10L)).thenReturn(rejected);

        assertThatThrownBy(() -> assetTrackingService.checkoutItemInstance(serialNumber, 10L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not approved");

        verify(itemInstanceMapper, never()).assignToBorrowRequest(any(), any());
    }

    @Test
    @DisplayName("Invalid status transition: cannot checkout against COMPLETED request")
    void invalidStatusTransition_completedRequest_fails() {
        String serialNumber = "SN-001";
        ItemInstance tool = ItemInstance.builder()
                .id(1L).serialNumber(serialNumber).toolCondition(ToolCondition.GOOD).isAvailable(true).build();
        BorrowRequest completed = BorrowRequest.builder()
                .id(10L).status(BorrowStatus.COMPLETED).approvalStatus(ApprovalStatus.APPROVED).build();

        when(itemInstanceMapper.findBySerialNumber(serialNumber)).thenReturn(tool);
        when(borrowRequestMapper.findById(10L)).thenReturn(completed);

        assertThatThrownBy(() -> assetTrackingService.checkoutItemInstance(serialNumber, 10L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already completed");

        verify(itemInstanceMapper, never()).assignToBorrowRequest(any(), any());
    }

    @Test
    @DisplayName("Invalid status transition: cannot checkout BROKEN tool")
    void invalidStatusTransition_brokenTool_fails() {
        String serialNumber = "SN-001";
        ItemInstance brokenTool = ItemInstance.builder()
                .id(1L).serialNumber(serialNumber).toolCondition(ToolCondition.BROKEN).isAvailable(true).build();

        when(itemInstanceMapper.findBySerialNumber(serialNumber)).thenReturn(brokenTool);

        assertThatThrownBy(() -> assetTrackingService.checkoutItemInstance(serialNumber, 10L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BROKEN");

        verify(borrowRequestMapper, never()).findById(any());
        verify(itemInstanceMapper, never()).assignToBorrowRequest(any(), any());
    }

    // -------------------------------------------------------------------------
    // 6. Nonexistent Tool
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Nonexistent tool: checkout of missing serial number/QR code throws 404 ResourceNotFound")
    void nonexistentTool_checkout_throwsNotFound() {
        when(itemInstanceMapper.findBySerialNumber("UNKNOWN-SN")).thenReturn(null);
        when(itemInstanceMapper.findByQrCodeValue("UNKNOWN-SN")).thenReturn(null);

        assertThatThrownBy(() -> assetTrackingService.checkoutItemInstance("UNKNOWN-SN", 10L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("No tool found");
    }

    @Test
    @DisplayName("Nonexistent tool: return of missing serial number/QR code throws 404 ResourceNotFound")
    void nonexistentTool_return_throwsNotFound() {
        when(itemInstanceMapper.findBySerialNumber("UNKNOWN-SN")).thenReturn(null);
        when(itemInstanceMapper.findByQrCodeValue("UNKNOWN-SN")).thenReturn(null);

        assertThatThrownBy(() -> assetTrackingService.returnItemInstance("UNKNOWN-SN", ToolCondition.GOOD))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("No tool found");
    }

    // -------------------------------------------------------------------------
    // 7. Concurrent / Conflicting Borrow Scenario
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Concurrent borrow: atomic conditional update guarantees only one racer succeeds")
    void concurrentBorrow_conflictResolution() throws InterruptedException {
        String serialNumber = "SN-CONCURRENT-001";
        Long toolId = 42L;

        ItemInstance tool = ItemInstance.builder()
                .id(toolId)
                .itemId(10L)
                .serialNumber(serialNumber)
                .toolCondition(ToolCondition.GOOD)
                .isAvailable(true)
                .build();

        BorrowRequest req1 = BorrowRequest.builder()
                .id(101L).userId(1L).locationId(1L).status(BorrowStatus.PENDING).approvalStatus(ApprovalStatus.APPROVED).build();
        BorrowRequest req2 = BorrowRequest.builder()
                .id(102L).userId(2L).locationId(1L).status(BorrowStatus.PENDING).approvalStatus(ApprovalStatus.APPROVED).build();

        when(itemInstanceMapper.findBySerialNumber(serialNumber)).thenReturn(tool);
        when(borrowRequestMapper.findById(101L)).thenReturn(req1);
        when(borrowRequestMapper.findById(102L)).thenReturn(req2);

        // Simulate database atomic locking: exactly one caller gets rowsAffected = 1, subsequent gets 0
        AtomicInteger databaseLock = new AtomicInteger(1);
        when(itemInstanceMapper.assignToBorrowRequest(eq(toolId), any())).thenAnswer(invocation -> {
            return databaseLock.getAndDecrement() == 1 ? 1 : 0;
        });
        when(itemInstanceMapper.findById(toolId)).thenReturn(tool);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(2);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        executor.submit(() -> {
            try {
                startLatch.await();
                assetTrackingService.checkoutItemInstance(serialNumber, 101L, 1L);
                successCount.incrementAndGet();
            } catch (IllegalStateException ex) {
                conflictCount.incrementAndGet();
            } catch (Exception ignored) {
            } finally {
                finishLatch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                startLatch.await();
                assetTrackingService.checkoutItemInstance(serialNumber, 102L, 2L);
                successCount.incrementAndGet();
            } catch (IllegalStateException ex) {
                conflictCount.incrementAndGet();
            } catch (Exception ignored) {
            } finally {
                finishLatch.countDown();
            }
        });

        // Trigger both threads simultaneously
        startLatch.countDown();
        finishLatch.await();
        executor.shutdown();

        // Exactly one should succeed, and exactly one should detect the conflict and fail safely
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(1);
        verify(itemInstanceMapper, times(2)).assignToBorrowRequest(eq(toolId), any());
    }
}

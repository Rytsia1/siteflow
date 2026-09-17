package com.siteflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.siteflow.domain.ItemStock;
import com.siteflow.domain.TransactionLog;
import com.siteflow.domain.enums.ApprovalStatus;
import com.siteflow.domain.enums.BorrowStatus;
import com.siteflow.domain.enums.TransactionType;
import com.siteflow.mapper.BorrowItemMapper;
import com.siteflow.mapper.BorrowRequestMapper;
import com.siteflow.mapper.ItemStockMapper;
import com.siteflow.mapper.TransactionLogMapper;
import com.siteflow.service.BorrowService.BorrowItemRequest;
import com.siteflow.service.BorrowService.ReturnLine;
import com.siteflow.web.ResourceNotFoundException;

@ExtendWith(MockitoExtension.class)
class BorrowServiceTest {

    @Mock
    private BorrowRequestMapper borrowRequestMapper;
    @Mock
    private BorrowItemMapper borrowItemMapper;
    @Mock
    private ItemStockMapper itemStockMapper;
    @Mock
    private TransactionLogMapper transactionLogMapper;

    private BorrowService borrowService;

    @BeforeEach
    void setUp() {
        borrowService = new BorrowService(borrowRequestMapper, borrowItemMapper, itemStockMapper,
                transactionLogMapper);
    }

    @Test
    @DisplayName("createBorrowRequest succeeds and decrements stock when availability is sufficient, recording audit log")
    void createBorrowRequest_sufficientStock_succeeds() {
        ItemStock stock = ItemStock.builder().id(50L).itemId(1L).locationId(10L).currentQty(5).build();
        when(itemStockMapper.findByItemIdAndLocationId(1L, 10L)).thenReturn(stock);
        when(itemStockMapper.adjustQty(50L, -2)).thenReturn(1);

        BorrowRequest created = borrowService.createBorrowRequest(7L, 10L, List.of(new BorrowItemRequest(1L, 2)));

        assertThat(created.getStatus()).isEqualTo(BorrowStatus.PENDING);
        assertThat(created.getApprovalStatus()).isEqualTo(ApprovalStatus.PENDING_APPROVAL);
        verify(borrowItemMapper).insert(any());

        ArgumentCaptor<TransactionLog> logCaptor = ArgumentCaptor.forClass(TransactionLog.class);
        verify(transactionLogMapper).insert(logCaptor.capture());
        TransactionLog log = logCaptor.getValue();
        assertThat(log.getItemId()).isEqualTo(1L);
        assertThat(log.getLocationId()).isEqualTo(10L);
        assertThat(log.getUserId()).isEqualTo(7L);
        assertThat(log.getTransactionType()).isEqualTo(TransactionType.BORROW);
        assertThat(log.getQtyChange()).isEqualTo(-2);
    }

    @Test
    @DisplayName("createBorrowRequest rejects a non-positive quantity without inserting anything")
    void createBorrowRequest_nonPositiveQty_throwsWithoutSideEffects() {
        assertThatThrownBy(() -> borrowService.createBorrowRequest(7L, 10L, List.of(new BorrowItemRequest(1L, 0))))
                .isInstanceOf(IllegalArgumentException.class);

        verify(borrowRequestMapper, never()).insert(any());
        verify(itemStockMapper, never()).adjustQty(any(), anyInt());
        verify(transactionLogMapper, never()).insert(any());
    }

    @Test
    @DisplayName("createBorrowRequest fails when requested quantity exceeds available stock, inserting nothing")
    void createBorrowRequest_insufficientStock_throwsWithoutSideEffects() {
        ItemStock stock = ItemStock.builder().id(50L).itemId(1L).locationId(10L).currentQty(1).build();
        when(itemStockMapper.findByItemIdAndLocationId(1L, 10L)).thenReturn(stock);

        assertThatThrownBy(() -> borrowService.createBorrowRequest(7L, 10L, List.of(new BorrowItemRequest(1L, 2))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Insufficient stock");

        verify(borrowRequestMapper, never()).insert(any());
        verify(borrowItemMapper, never()).insert(any());
        verify(transactionLogMapper, never()).insert(any());
    }

    @Test
    @DisplayName("createBorrowRequest fails when cumulative item deduction exceeds available stock")
    void createBorrowRequest_cumulativeDepletion_throwsIllegalState() {
        ItemStock stock = ItemStock.builder().id(50L).itemId(1L).locationId(10L).currentQty(5).build();
        when(itemStockMapper.findByItemIdAndLocationId(1L, 10L)).thenReturn(stock);
        when(itemStockMapper.adjustQty(50L, -3)).thenReturn(1);
        when(itemStockMapper.adjustQty(50L, -3)).thenReturn(0); // second deduction fails due to insufficient balance

        assertThatThrownBy(() -> borrowService.createBorrowRequest(7L, 10L,
                List.of(new BorrowItemRequest(1L, 3), new BorrowItemRequest(1L, 3))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Insufficient stock");
    }

    @Test
    @DisplayName("processReturn restores stock and logs a transaction for a valid partial return")
    void processReturn_valid_restoresStockAndLogsTransaction() {
        BorrowItem item = BorrowItem.builder().id(1L).borrowRequestId(9L).itemId(1L).qtyBorrowed(5).qtyReturned(0)
                .build();
        BorrowRequest request = BorrowRequest.builder().id(9L).locationId(10L).status(BorrowStatus.BORROWED).build();
        ItemStock stock = ItemStock.builder().id(50L).itemId(1L).locationId(10L).currentQty(3).build();

        when(borrowItemMapper.findById(1L)).thenReturn(item);
        when(borrowItemMapper.recordReturn(eq(1L), eq(2), any())).thenReturn(1);
        when(borrowRequestMapper.findById(9L)).thenReturn(request);
        when(itemStockMapper.findByItemIdAndLocationId(1L, 10L)).thenReturn(stock);
        when(borrowItemMapper.findByBorrowRequestId(9L))
                .thenReturn(List.of(BorrowItem.builder().id(1L).qtyBorrowed(5).qtyReturned(2).build()));

        borrowService.processReturn(1L, 2, 3L);

        verify(itemStockMapper).adjustQty(50L, 2);

        ArgumentCaptor<TransactionLog> logCaptor = ArgumentCaptor.forClass(TransactionLog.class);
        verify(transactionLogMapper).insert(logCaptor.capture());
        TransactionLog log = logCaptor.getValue();
        assertThat(log.getItemId()).isEqualTo(1L);
        assertThat(log.getTransactionType()).isEqualTo(TransactionType.RETURN);
        assertThat(log.getQtyChange()).isEqualTo(2);
        assertThat(log.getUserId()).isEqualTo(3L);

        verify(borrowRequestMapper).updateStatus(9L, BorrowStatus.PARTIAL_RETURN);
    }

    @Test
    @DisplayName("processReturn marks the request COMPLETED once the last outstanding line is fully returned")
    void processReturn_lastOutstandingLine_marksRequestCompleted() {
        BorrowItem item = BorrowItem.builder().id(1L).borrowRequestId(9L).itemId(1L).qtyBorrowed(5).qtyReturned(3)
                .build();
        BorrowRequest request = BorrowRequest.builder().id(9L).locationId(10L).status(BorrowStatus.PARTIAL_RETURN)
                .build();
        ItemStock stock = ItemStock.builder().id(50L).itemId(1L).locationId(10L).currentQty(3).build();

        when(borrowItemMapper.findById(1L)).thenReturn(item);
        when(borrowItemMapper.recordReturn(eq(1L), eq(2), any())).thenReturn(1);
        when(borrowRequestMapper.findById(9L)).thenReturn(request);
        when(itemStockMapper.findByItemIdAndLocationId(1L, 10L)).thenReturn(stock);
        when(borrowItemMapper.findByBorrowRequestId(9L))
                .thenReturn(List.of(BorrowItem.builder().id(1L).qtyBorrowed(5).qtyReturned(5).build()));

        borrowService.processReturn(1L, 2, 3L);

        verify(borrowRequestMapper).updateStatus(9L, BorrowStatus.COMPLETED);
    }

    @Test
    @DisplayName("processReturn fails with 404-mapped exception when the borrow item id doesn't exist")
    void processReturn_unknownBorrowItem_throwsResourceNotFound() {
        when(borrowItemMapper.findById(404L)).thenReturn(null);

        assertThatThrownBy(() -> borrowService.processReturn(404L, 1, 3L))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(itemStockMapper, never()).adjustQty(any(), anyInt());
        verify(transactionLogMapper, never()).insert(any());
    }

    @Test
    @DisplayName("processReturn fails with 404 when associated borrow request does not exist")
    void processReturn_unknownBorrowRequest_throwsResourceNotFound() {
        BorrowItem item = BorrowItem.builder().id(1L).borrowRequestId(999L).itemId(1L).qtyBorrowed(5).qtyReturned(0).build();
        when(borrowItemMapper.findById(1L)).thenReturn(item);
        when(borrowRequestMapper.findById(999L)).thenReturn(null);

        assertThatThrownBy(() -> borrowService.processReturn(1L, 2, 3L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("processReturn rejects processing return when request is in PENDING status")
    void processReturn_pendingRequest_throwsIllegalState() {
        BorrowItem item = BorrowItem.builder().id(1L).borrowRequestId(9L).itemId(1L).qtyBorrowed(5).qtyReturned(0).build();
        BorrowRequest request = BorrowRequest.builder().id(9L).locationId(10L).status(BorrowStatus.PENDING).build();
        when(borrowItemMapper.findById(1L)).thenReturn(item);
        when(borrowRequestMapper.findById(9L)).thenReturn(request);

        assertThatThrownBy(() -> borrowService.processReturn(1L, 2, 3L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Must be BORROWED or PARTIAL_RETURN");
    }

    @Test
    @DisplayName("processReturn rejects a non-positive quantity without touching stock")
    void processReturn_nonPositiveQty_throwsWithoutSideEffects() {
        BorrowItem item = BorrowItem.builder().id(1L).borrowRequestId(9L).itemId(1L).qtyBorrowed(5).qtyReturned(0)
                .build();
        when(borrowItemMapper.findById(1L)).thenReturn(item);

        assertThatThrownBy(() -> borrowService.processReturn(1L, 0, 3L))
                .isInstanceOf(IllegalArgumentException.class);

        verify(borrowItemMapper, never()).recordReturn(any(), anyInt(), any());
        verify(itemStockMapper, never()).adjustQty(any(), anyInt());
    }

    @Test
    @DisplayName("processReturn rejects a repeated/over return once the line is already fully returned")
    void processReturn_alreadyFullyReturned_throwsWithoutSideEffects() {
        BorrowItem item = BorrowItem.builder().id(1L).borrowRequestId(9L).itemId(1L).qtyBorrowed(5).qtyReturned(5)
                .build();
        BorrowRequest request = BorrowRequest.builder().id(9L).locationId(10L).status(BorrowStatus.BORROWED).build();
        when(borrowItemMapper.findById(1L)).thenReturn(item);
        when(borrowRequestMapper.findById(9L)).thenReturn(request);
        when(borrowItemMapper.recordReturn(eq(1L), eq(1), any())).thenReturn(0);

        assertThatThrownBy(() -> borrowService.processReturn(1L, 1, 3L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exceeds the outstanding balance");

        verify(itemStockMapper, never()).adjustQty(any(), anyInt());
        verify(transactionLogMapper, never()).insert(any());
        verify(borrowRequestMapper, never()).updateStatus(any(), any());
    }

    @Test
    @DisplayName("processReturnsForRequest successfully processes a batch of return lines")
    void processReturnsForRequest_validBatch_succeeds() {
        BorrowRequest request = BorrowRequest.builder().id(9L).locationId(10L).status(BorrowStatus.BORROWED).build();
        BorrowItem item1 = BorrowItem.builder().id(101L).borrowRequestId(9L).itemId(1L).qtyBorrowed(2).qtyReturned(0).build();
        BorrowItem item2 = BorrowItem.builder().id(102L).borrowRequestId(9L).itemId(2L).qtyBorrowed(3).qtyReturned(0).build();
        ItemStock stock1 = ItemStock.builder().id(51L).itemId(1L).locationId(10L).currentQty(1).build();
        ItemStock stock2 = ItemStock.builder().id(52L).itemId(2L).locationId(10L).currentQty(2).build();

        when(borrowRequestMapper.findById(9L)).thenReturn(request);
        when(borrowItemMapper.findById(101L)).thenReturn(item1);
        when(borrowItemMapper.findById(102L)).thenReturn(item2);

        when(borrowItemMapper.recordReturn(eq(101L), eq(2), any())).thenReturn(1);
        when(borrowItemMapper.recordReturn(eq(102L), eq(3), any())).thenReturn(1);
        when(itemStockMapper.findByItemIdAndLocationId(1L, 10L)).thenReturn(stock1);
        when(itemStockMapper.findByItemIdAndLocationId(2L, 10L)).thenReturn(stock2);

        when(borrowItemMapper.findByBorrowRequestId(9L)).thenReturn(
                List.of(
                        BorrowItem.builder().id(101L).qtyBorrowed(2).qtyReturned(2).build(),
                        BorrowItem.builder().id(102L).qtyBorrowed(3).qtyReturned(3).build()
                )
        );

        borrowService.processReturnsForRequest(9L, List.of(new ReturnLine(101L, 2), new ReturnLine(102L, 3)), 7L);

        verify(itemStockMapper).adjustQty(51L, 2);
        verify(itemStockMapper).adjustQty(52L, 3);
        verify(borrowRequestMapper, times(2)).updateStatus(9L, BorrowStatus.COMPLETED);
    }

    @Test
    @DisplayName("processReturnsForRequest fails when request does not exist")
    void processReturnsForRequest_unknownRequest_throwsResourceNotFound() {
        when(borrowRequestMapper.findById(999L)).thenReturn(null);

        assertThatThrownBy(() -> borrowService.processReturnsForRequest(999L, List.of(new ReturnLine(101L, 1)), 7L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("processReturnsForRequest fails when request is in terminal COMPLETED status")
    void processReturnsForRequest_terminalStatus_throwsIllegalState() {
        BorrowRequest request = BorrowRequest.builder().id(9L).status(BorrowStatus.COMPLETED).build();
        when(borrowRequestMapper.findById(9L)).thenReturn(request);

        assertThatThrownBy(() -> borrowService.processReturnsForRequest(9L, List.of(new ReturnLine(101L, 1)), 7L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("terminal status");
    }

    @Test
    @DisplayName("processReturnsForRequest fails when request is still PENDING")
    void processReturnsForRequest_pendingStatus_throwsIllegalState() {
        BorrowRequest request = BorrowRequest.builder().id(9L).status(BorrowStatus.PENDING).build();
        when(borrowRequestMapper.findById(9L)).thenReturn(request);

        assertThatThrownBy(() -> borrowService.processReturnsForRequest(9L, List.of(new ReturnLine(101L, 1)), 7L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Must be BORROWED or PARTIAL_RETURN");
    }

    @Test
    @DisplayName("processReturnsForRequest rejects batch if an item belongs to a different request")
    void processReturnsForRequest_itemBelongsToOtherRequest_throwsIllegalArgument() {
        BorrowRequest request = BorrowRequest.builder().id(9L).status(BorrowStatus.BORROWED).build();
        BorrowItem itemForeign = BorrowItem.builder().id(101L).borrowRequestId(888L).itemId(1L).build();

        when(borrowRequestMapper.findById(9L)).thenReturn(request);
        when(borrowItemMapper.findById(101L)).thenReturn(itemForeign);

        assertThatThrownBy(() -> borrowService.processReturnsForRequest(9L, List.of(new ReturnLine(101L, 1)), 7L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not belong to request 9");

        verify(borrowItemMapper, never()).recordReturn(any(), anyInt(), any());
    }
}

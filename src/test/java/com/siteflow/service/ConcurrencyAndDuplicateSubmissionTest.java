package com.siteflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.siteflow.domain.BorrowRequest;
import com.siteflow.domain.ItemStock;
import com.siteflow.domain.MaterialRequest;
import com.siteflow.domain.MaterialRequestItem;
import com.siteflow.domain.PurchaseOrder;
import com.siteflow.domain.enums.PurchaseOrderStatus;
import com.siteflow.mapper.BorrowRequestMapper;
import com.siteflow.mapper.IdempotencyKeyMapper;
import com.siteflow.mapper.ItemStockMapper;
import com.siteflow.mapper.MaterialRequestItemMapper;
import com.siteflow.mapper.MaterialRequestMapper;
import com.siteflow.mapper.PurchaseOrderMapper;
import com.siteflow.security.UserPrincipal;

@SpringBootTest
@AutoConfigureMockMvc
class ConcurrencyAndDuplicateSubmissionTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IdempotencyKeyMapper idempotencyKeyMapper;

    @Autowired
    private ItemStockMapper itemStockMapper;

    @Autowired
    private BorrowRequestMapper borrowRequestMapper;

    @Autowired
    private PurchaseOrderMapper purchaseOrderMapper;

    @Autowired
    private MaterialRequestItemMapper materialRequestItemMapper;

    @Autowired
    private MaterialRequestMapper materialRequestMapper;

    private UserPrincipal adminPrincipal() {
        return new UserPrincipal(1L, "admin", "hash", "ADMIN");
    }

    private UserPrincipal warehousePrincipal() {
        return new UserPrincipal(2L, "gudang", "hash", "WAREHOUSE_STAFF");
    }

    private UserPrincipal fieldStaffPrincipal() {
        return new UserPrincipal(3L, "pekerja", "hash", "FIELD_STAFF");
    }

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanIdempotencyKeys() {
        idempotencyKeyMapper.deleteAll();
        jdbcTemplate.update("UPDATE item_stocks SET current_qty = 50 WHERE item_id IN (1, 2) AND location_id = 1");
    }

    @Test
    @DisplayName("1. Submitting the same request twice with Idempotency-Key returns 409 Conflict on the second")
    void test1_submittingSameRequestTwice_returns409Conflict() throws Exception {
        String key = "test-key-" + UUID.randomUUID();
        String json = """
                {
                    "locationId": 1,
                    "items": [{"itemId": 1, "qty": 1}]
                }
                """;

        // First submission succeeds (201 Created)
        mockMvc.perform(post("/api/borrow-requests")
                        .with(user(fieldStaffPrincipal()))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("success"));

        // Second submission with the exact same Idempotency-Key is rejected with 409 Conflict
        mockMvc.perform(post("/api/borrow-requests")
                        .with(user(fieldStaffPrincipal()))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message", containsString("Duplicate submission detected")));
    }

    @Test
    @DisplayName("2. Two identical requests arriving concurrently: exactly one succeeds, the other receives 409 Conflict")
    void test2_twoIdenticalRequestsArrivingConcurrently_onlyOneSucceeds() throws Exception {
        String key = "concurrent-key-" + UUID.randomUUID();
        String json = """
                {
                    "justification": "Concurrent test material request",
                    "items": [{"itemId": 2, "qty": 1}]
                }
                """;

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(threadCount);
        List<Integer> responseStatuses = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startGate.await(); // wait for both threads to be ready
                    MvcResult result = mockMvc.perform(post("/api/procurement/material-requests")
                                    .with(user(fieldStaffPrincipal()))
                                    .header("Idempotency-Key", key)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(json))
                            .andReturn();
                    responseStatuses.add(result.getResponse().getStatus());
                } catch (Exception e) {
                    responseStatuses.add(500);
                } finally {
                    endGate.countDown();
                }
            });
        }

        startGate.countDown(); // fire both requests concurrently
        boolean finished = endGate.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(finished).isTrue();
        assertThat(responseStatuses).hasSize(2);
        // Exactly one must succeed (201) and one must be rejected (409 Conflict)
        assertThat(responseStatuses).containsExactlyInAnyOrder(201, 409);
    }

    @Test
    @DisplayName("3. Duplicate borrow submission without explicit key is caught by automatic fingerprinting")
    void test3_duplicateBorrowSubmission_fingerprintPreventsDuplicate() throws Exception {
        String json = """
                {
                    "locationId": 1,
                    "items": [{"itemId": 3, "qty": 1}]
                }
                """;

        // First call succeeds
        mockMvc.perform(post("/api/borrow-requests")
                        .with(user(fieldStaffPrincipal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated());

        // Identical call within duplicate window is caught by automatic fingerprinting
        mockMvc.perform(post("/api/borrow-requests")
                        .with(user(fieldStaffPrincipal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("Duplicate submission detected")));
    }

    @Test
    @DisplayName("4. Duplicate material request submission returns 409 Conflict")
    void test4_duplicateMaterialRequestSubmission_returns409Conflict() throws Exception {
        String key = "mr-key-" + UUID.randomUUID();
        String json = """
                {
                    "justification": "Procurement for project site alpha",
                    "items": [{"itemId": 1, "qty": 2}]
                }
                """;

        mockMvc.perform(post("/api/procurement/material-requests")
                        .with(user(fieldStaffPrincipal()))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/procurement/material-requests")
                        .with(user(fieldStaffPrincipal()))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("Duplicate submission detected")));
    }

    @Test
    @DisplayName("5. Duplicate stock adjustment submission returns 409 Conflict")
    void test5_duplicateStockAdjustmentSubmission_returns409Conflict() throws Exception {
        String key = "adj-key-" + UUID.randomUUID();
        String json = """
                {
                    "itemId": 4,
                    "locationId": 1,
                    "adjustmentType": "IN",
                    "qty": 5,
                    "reason": "Restock shipment batch A"
                }
                """;

        mockMvc.perform(post("/api/stock-adjustments")
                        .with(user(warehousePrincipal()))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/stock-adjustments")
                        .with(user(warehousePrincipal()))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("Duplicate submission detected")));
    }

    @Test
    @DisplayName("6. Repeating an approval action returns 409 Conflict")
    void test6_repeatingApprovalAction_returns409Conflict() throws Exception {
        // Create a pending borrow request first
        String key = "borrow-for-approval-" + UUID.randomUUID();
        String json = """
                {
                    "locationId": 1,
                    "items": [{"itemId": 1, "qty": 1}]
                }
                """;
        MvcResult createResult = mockMvc.perform(post("/api/borrow-requests")
                        .with(user(fieldStaffPrincipal()))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn();

        // Extract created borrow request ID
        String responseBody = createResult.getResponse().getContentAsString();
        long requestId = Long.parseLong(responseBody.replaceAll(".*\"id\":(\\d+).*", "$1"));

        // Approve it the first time -> 200 OK
        mockMvc.perform(post("/api/approvals/borrow-requests/" + requestId + "/approve")
                        .with(user(adminPrincipal())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));

        // Repeating the approval action -> 409 Conflict
        mockMvc.perform(post("/api/approvals/borrow-requests/" + requestId + "/approve")
                        .with(user(adminPrincipal())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("is not pending approval")));
    }

    @Test
    @DisplayName("7. Repeating an already-completed state transition returns 409 Conflict")
    void test7_repeatingAlreadyCompletedStateTransition_returns409Conflict() throws Exception {
        // Create a pending borrow request
        String key = "borrow-for-reject-" + UUID.randomUUID();
        String json = """
                {
                    "locationId": 1,
                    "items": [{"itemId": 2, "qty": 1}]
                }
                """;
        MvcResult createResult = mockMvc.perform(post("/api/borrow-requests")
                        .with(user(fieldStaffPrincipal()))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn();

        long requestId = Long.parseLong(createResult.getResponse().getContentAsString().replaceAll(".*\"id\":(\\d+).*", "$1"));

        // Reject it the first time -> 200 OK
        mockMvc.perform(post("/api/approvals/borrow-requests/" + requestId + "/reject")
                        .with(user(adminPrincipal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\": \"Rejected due to inspection\"}"))
                .andExpect(status().isOk());

        // Attempt to reject it a second time -> 409 Conflict
        mockMvc.perform(post("/api/approvals/borrow-requests/" + requestId + "/reject")
                        .with(user(adminPrincipal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\": \"Second rejection attempt\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("is not pending approval")));
    }

    @Test
    @DisplayName("8. Concurrent attempts to borrow stock beyond availability: pessimistic locking prevents negative stock")
    void test8_concurrentBorrowAttempts_pessimisticLockPreventsNegativeStock() throws Exception {
        // Item 5 (CHN-001) at location 1: check current stock
        ItemStock initialStock = itemStockMapper.findByItemIdAndLocationId(5L, 1L);
        int currentQty = initialStock.getCurrentQty();

        // Explicitly set current stock to 5 for this test
        jdbcTemplate.update("UPDATE item_stocks SET current_qty = 5 WHERE item_id = 5 AND location_id = 1");
        currentQty = 5;

        // We have 5 available. We launch two concurrent requests each asking for 3 units.
        // Total requested = 6 > 5. Exactly 1 must succeed and 1 must fail.
        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final String threadKey = "concurrent-stock-key-" + i + "-" + UUID.randomUUID();
            executor.submit(() -> {
                try {
                    startGate.await();
                    MvcResult result = mockMvc.perform(post("/api/borrow-requests")
                                    .with(user(fieldStaffPrincipal()))
                                    .header("Idempotency-Key", threadKey)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"locationId\": 1, \"items\": [{\"itemId\": 5, \"qty\": 3}]}"))
                            .andReturn();
                    int status = result.getResponse().getStatus();
                    if (status == 201) {
                        successCount.incrementAndGet();
                    } else if (status == 409) {
                        conflictCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    // unexpected
                } finally {
                    endGate.countDown();
                }
            });
        }

        startGate.countDown();
        boolean finished = endGate.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(finished).isTrue();
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(1);

        // Verify remaining stock in database: was 5, 3 borrowed => remaining must be 2, NEVER negative
        ItemStock postStock = itemStockMapper.findByItemIdAndLocationId(5L, 1L);
        assertThat(postStock.getCurrentQty()).isEqualTo(currentQty - 3);
        assertThat(postStock.getCurrentQty()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("9. Database uniqueness constraints: purchase_orders(mr_id) and material_request_items(mr_id, item_id)")
    void test9_databaseUniquenessConstraints_enforceDbLevelIntegrity() {
        // Create a real MaterialRequest so foreign key checks pass
        com.siteflow.domain.MaterialRequest mr = com.siteflow.domain.MaterialRequest.builder()
                .requestedBy(1L)
                .requestDate(LocalDateTime.now())
                .status(com.siteflow.domain.enums.MaterialRequestStatus.APPROVED)
                .justification("For DB uniqueness constraint test")
                .build();
        materialRequestMapper.insert(mr);
        Long mrId = mr.getId();

        // Test 9a: Unique constraint on purchase_orders.mr_id
        PurchaseOrder po1 = PurchaseOrder.builder()
                .mrId(mrId)
                .poNumber("PO-TEST-" + System.currentTimeMillis() + "-1")
                .supplierName("Supplier A")
                .orderDate(LocalDateTime.now())
                .poStatus(PurchaseOrderStatus.ISSUED)
                .build();
        purchaseOrderMapper.insert(po1);

        PurchaseOrder poDuplicate = PurchaseOrder.builder()
                .mrId(mrId) // Duplicate mr_id!
                .poNumber("PO-TEST-" + System.currentTimeMillis() + "-2")
                .supplierName("Supplier B")
                .orderDate(LocalDateTime.now())
                .poStatus(PurchaseOrderStatus.ISSUED)
                .build();

        assertThatThrownBy(() -> purchaseOrderMapper.insert(poDuplicate))
                .isInstanceOf(DataIntegrityViolationException.class);

        // Test 9b: Unique constraint on material_request_items (mr_id, item_id)
        MaterialRequestItem item1 = MaterialRequestItem.builder()
                .mrId(mrId)
                .itemId(1L)
                .requestedQty(5)
                .build();
        materialRequestItemMapper.insert(item1);

        MaterialRequestItem duplicateItem = MaterialRequestItem.builder()
                .mrId(mrId)
                .itemId(1L) // Duplicate (mr_id, item_id)!
                .requestedQty(10)
                .build();

        assertThatThrownBy(() -> materialRequestItemMapper.insert(duplicateItem))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}

package com.siteflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.siteflow.domain.BorrowRequest;
import com.siteflow.domain.ItemStock;
import com.siteflow.domain.enums.ApprovalStatus;
import com.siteflow.domain.enums.BorrowStatus;
import com.siteflow.mapper.BorrowRequestMapper;
import com.siteflow.mapper.IdempotencyKeyMapper;
import com.siteflow.mapper.ItemStockMapper;
import com.siteflow.security.UserPrincipal;

@SpringBootTest
@AutoConfigureMockMvc
class BorrowStockReservationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ItemStockMapper itemStockMapper;

    @Autowired
    private BorrowRequestMapper borrowRequestMapper;

    @Autowired
    private IdempotencyKeyMapper idempotencyKeyMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UserPrincipal adminPrincipal() {
        return new UserPrincipal(1L, "admin", "hash", "ADMIN");
    }

    private UserPrincipal warehousePrincipal() {
        return new UserPrincipal(2L, "gudang", "hash", "WAREHOUSE_STAFF");
    }

    private UserPrincipal fieldStaffPrincipal() {
        return new UserPrincipal(3L, "pekerja", "hash", "FIELD_STAFF");
    }

    private static final Long TEST_ITEM_ID = 1L; // DRL-001
    private static final Long TEST_LOCATION_ID = 1L; // Main Warehouse

    @BeforeEach
    void resetInventory() {
        idempotencyKeyMapper.deleteAll();
        // Reset Item 1 (DRL-001) at Location 1 to current_qty = 10, reserved_qty = 0
        jdbcTemplate.update("UPDATE item_stocks SET current_qty = 10, reserved_qty = 0 WHERE item_id = ? AND location_id = ?",
                TEST_ITEM_ID, TEST_LOCATION_ID);
    }

    private long extractId(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString();
        return Long.parseLong(body.replaceAll(".*\"id\":(\\d+).*", "$1"));
    }

    @Test
    @DisplayName("1. Create pending request: reserves stock, physical stock remains unchanged")
    void test1_createPendingRequest_reservesStock() throws Exception {
        String json = """
                {
                    "locationId": 1,
                    "items": [{"itemId": 1, "qty": 4}]
                }
                """;

        MvcResult result = mockMvc.perform(post("/api/borrow-requests")
                        .with(user(fieldStaffPrincipal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.approvalStatus").value("PENDING_APPROVAL"))
                .andReturn();

        long requestId = extractId(result);
        assertThat(requestId).isPositive();

        ItemStock stock = itemStockMapper.findByItemIdAndLocationId(TEST_ITEM_ID, TEST_LOCATION_ID);
        assertThat(stock.getCurrentQty()).isEqualTo(6); // 10 - 4 available
        assertThat(stock.getReservedQty()).isEqualTo(4); // 4 reserved
        assertThat(stock.getPhysicalQty()).isEqualTo(10); // Physical stock 6 + 4 = 10 intact!
    }

    @Test
    @DisplayName("2. Approve request: approval sign-off, reservation held, physical stock not double-consumed")
    void test2_approveRequest_preservesReservationWithoutDoubleConsumption() throws Exception {
        String json = """
                {
                    "locationId": 1,
                    "items": [{"itemId": 1, "qty": 3}]
                }
                """;

        MvcResult createResult = mockMvc.perform(post("/api/borrow-requests")
                        .with(user(fieldStaffPrincipal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn();

        long requestId = extractId(createResult);

        // Approve by Admin
        mockMvc.perform(post("/api/approvals/borrow-requests/" + requestId + "/approve")
                        .with(user(adminPrincipal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\": \"Approved for job site\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.approvalStatus").value("APPROVED"));

        ItemStock stock = itemStockMapper.findByItemIdAndLocationId(TEST_ITEM_ID, TEST_LOCATION_ID);
        assertThat(stock.getCurrentQty()).isEqualTo(7); // 10 - 3 available
        assertThat(stock.getReservedQty()).isEqualTo(3); // 3 reserved
        assertThat(stock.getPhysicalQty()).isEqualTo(10); // Physical stock still 10, not double-consumed!
    }

    @Test
    @DisplayName("3. Reject request: releases reservation, available stock restored, physical stock unchanged")
    void test3_rejectRequest_releasesReservationAndRestoresAvailability() throws Exception {
        String json = """
                {
                    "locationId": 1,
                    "items": [{"itemId": 1, "qty": 4}]
                }
                """;

        MvcResult createResult = mockMvc.perform(post("/api/borrow-requests")
                        .with(user(fieldStaffPrincipal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn();

        long requestId = extractId(createResult);

        // Verify reserved
        ItemStock midStock = itemStockMapper.findByItemIdAndLocationId(TEST_ITEM_ID, TEST_LOCATION_ID);
        assertThat(midStock.getCurrentQty()).isEqualTo(6);
        assertThat(midStock.getReservedQty()).isEqualTo(4);

        // Reject by Admin
        mockMvc.perform(post("/api/approvals/borrow-requests/" + requestId + "/reject")
                        .with(user(adminPrincipal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\": \"Excessive quantity requested\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.approvalStatus").value("REJECTED"));

        // Verify 100% restored
        ItemStock postStock = itemStockMapper.findByItemIdAndLocationId(TEST_ITEM_ID, TEST_LOCATION_ID);
        assertThat(postStock.getCurrentQty()).isEqualTo(10); // Full 10 available again!
        assertThat(postStock.getReservedQty()).isEqualTo(0); // 0 reserved
        assertThat(postStock.getPhysicalQty()).isEqualTo(10); // Physical stock 10
    }

    @Test
    @DisplayName("4. Cancel request: requester cancellation releases reservation")
    void test4_cancelRequest_releasesReservation() throws Exception {
        String json = """
                {
                    "locationId": 1,
                    "items": [{"itemId": 1, "qty": 5}]
                }
                """;

        MvcResult createResult = mockMvc.perform(post("/api/borrow-requests")
                        .with(user(fieldStaffPrincipal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn();

        long requestId = extractId(createResult);

        // Requester cancels
        mockMvc.perform(post("/api/borrow-requests/" + requestId + "/cancel")
                        .with(user(fieldStaffPrincipal())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.approvalStatus").value("REJECTED"));

        ItemStock postStock = itemStockMapper.findByItemIdAndLocationId(TEST_ITEM_ID, TEST_LOCATION_ID);
        assertThat(postStock.getCurrentQty()).isEqualTo(10);
        assertThat(postStock.getReservedQty()).isEqualTo(0);
        assertThat(postStock.getPhysicalQty()).isEqualTo(10);
    }

    @Test
    @DisplayName("5. Checkout: items leave warehouse, reservation fulfilled, physical stock decreases")
    void test5_checkout_fulfillsReservationAndDecreasesPhysicalStock() throws Exception {
        String json = """
                {
                    "locationId": 1,
                    "items": [{"itemId": 1, "qty": 3}]
                }
                """;

        MvcResult createResult = mockMvc.perform(post("/api/borrow-requests")
                        .with(user(fieldStaffPrincipal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn();

        long requestId = extractId(createResult);

        // Approve first
        mockMvc.perform(post("/api/approvals/borrow-requests/" + requestId + "/approve")
                        .with(user(adminPrincipal())))
                .andExpect(status().isOk());

        // Checkout by warehouse staff
        mockMvc.perform(post("/api/borrow-requests/" + requestId + "/checkout")
                        .with(user(warehousePrincipal())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("BORROWED"));

        ItemStock postStock = itemStockMapper.findByItemIdAndLocationId(TEST_ITEM_ID, TEST_LOCATION_ID);
        assertThat(postStock.getCurrentQty()).isEqualTo(7); // Available is 7
        assertThat(postStock.getReservedQty()).isEqualTo(0); // Reservation fulfilled!
        assertThat(postStock.getPhysicalQty()).isEqualTo(7); // Physical on-hand stock is now 7
    }

    @Test
    @DisplayName("6. Return: physical items restored to shelf, available stock increases")
    void test6_return_restoresPhysicalAndAvailableStock() throws Exception {
        String json = """
                {
                    "locationId": 1,
                    "items": [{"itemId": 1, "qty": 2}]
                }
                """;

        MvcResult createResult = mockMvc.perform(post("/api/borrow-requests")
                        .with(user(fieldStaffPrincipal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn();

        long requestId = extractId(createResult);

        // Approve & Checkout
        mockMvc.perform(post("/api/approvals/borrow-requests/" + requestId + "/approve")
                .with(user(adminPrincipal()))).andExpect(status().isOk());
        mockMvc.perform(post("/api/borrow-requests/" + requestId + "/checkout")
                .with(user(warehousePrincipal()))).andExpect(status().isOk());

        // Get borrow_item ID
        Long borrowItemId = jdbcTemplate.queryForObject(
                "SELECT id FROM borrow_items WHERE borrow_request_id = ? AND item_id = ?",
                Long.class, requestId, TEST_ITEM_ID);
        assertThat(borrowItemId).isNotNull();

        // Process return of 2 units
        String returnJson = "{\"items\": [{\"borrowItemId\": " + borrowItemId + ", \"qty\": 2}]}";
        mockMvc.perform(post("/api/borrow-requests/" + requestId + "/returns")
                        .with(user(warehousePrincipal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(returnJson))
                .andExpect(status().isOk());

        ItemStock postStock = itemStockMapper.findByItemIdAndLocationId(TEST_ITEM_ID, TEST_LOCATION_ID);
        assertThat(postStock.getCurrentQty()).isEqualTo(10); // Back to 10!
        assertThat(postStock.getReservedQty()).isEqualTo(0);
        assertThat(postStock.getPhysicalQty()).isEqualTo(10);

        BorrowRequest req = borrowRequestMapper.findById(requestId);
        assertThat(req.getStatus()).isEqualTo(BorrowStatus.COMPLETED);
    }

    @Test
    @DisplayName("7. Repeated approval: rejected with 409 Conflict, 0 stock mutation")
    void test7_repeatedApproval_rejectedWithoutMutatingStock() throws Exception {
        String json = """
                {
                    "locationId": 1,
                    "items": [{"itemId": 1, "qty": 2}]
                }
                """;

        MvcResult createResult = mockMvc.perform(post("/api/borrow-requests")
                        .with(user(fieldStaffPrincipal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn();

        long requestId = extractId(createResult);

        // First approval
        mockMvc.perform(post("/api/approvals/borrow-requests/" + requestId + "/approve")
                        .with(user(adminPrincipal())))
                .andExpect(status().isOk());

        // Second approval attempt
        mockMvc.perform(post("/api/approvals/borrow-requests/" + requestId + "/approve")
                        .with(user(adminPrincipal())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("is not pending approval")));

        ItemStock postStock = itemStockMapper.findByItemIdAndLocationId(TEST_ITEM_ID, TEST_LOCATION_ID);
        assertThat(stockHasAvailableAndReserved(postStock, 8, 2)).isTrue();
    }

    @Test
    @DisplayName("8. Repeated rejection: rejected with 409 Conflict, 0 stock mutation")
    void test8_repeatedRejection_rejectedWithoutMutatingStock() throws Exception {
        String json = """
                {
                    "locationId": 1,
                    "items": [{"itemId": 1, "qty": 2}]
                }
                """;

        MvcResult createResult = mockMvc.perform(post("/api/borrow-requests")
                        .with(user(fieldStaffPrincipal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn();

        long requestId = extractId(createResult);

        // First rejection
        mockMvc.perform(post("/api/approvals/borrow-requests/" + requestId + "/reject")
                        .with(user(adminPrincipal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\": \"Rejection 1\"}"))
                .andExpect(status().isOk());

        // Second rejection attempt
        mockMvc.perform(post("/api/approvals/borrow-requests/" + requestId + "/reject")
                        .with(user(adminPrincipal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\": \"Rejection 2\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("is not pending approval")));

        ItemStock postStock = itemStockMapper.findByItemIdAndLocationId(TEST_ITEM_ID, TEST_LOCATION_ID);
        // Stock was restored once to 10/0, never over-restored!
        assertThat(postStock.getCurrentQty()).isEqualTo(10);
        assertThat(postStock.getReservedQty()).isEqualTo(0);
    }

    @Test
    @DisplayName("9. Simultaneous requests competing for stock: atomic CAS prevents overselling")
    void test9_simultaneousRequests_atomicCasPreventsOverselling() throws Exception {
        // Stock is 5
        jdbcTemplate.update("UPDATE item_stocks SET current_qty = 5, reserved_qty = 0 WHERE item_id = ? AND location_id = ?",
                TEST_ITEM_ID, TEST_LOCATION_ID);

        int threadCount = 4;
        // 4 concurrent requests each asking for 3 units (total requested = 12, available = 5)
        // Exactly 1 must succeed (1 * 3 = 3 <= 5), remaining 3 must fail due to insufficient stock
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger rejectedCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final String idemKey = "simul-race-" + i + "-" + UUID.randomUUID();
            executor.submit(() -> {
                try {
                    startGate.await();
                    String json = "{\"locationId\": 1, \"items\": [{\"itemId\": 1, \"qty\": 3}]}";
                    MvcResult result = mockMvc.perform(post("/api/borrow-requests")
                                    .with(user(fieldStaffPrincipal()))
                                    .header("Idempotency-Key", idemKey)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(json))
                            .andReturn();
                    int status = result.getResponse().getStatus();
                    if (status == 201) {
                        successCount.incrementAndGet();
                    } else if (status == 400 || status == 409) {
                        rejectedCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    // unexpected
                } finally {
                    endGate.countDown();
                }
            });
        }

        startGate.countDown();
        boolean completed = endGate.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(rejectedCount.get()).isEqualTo(3);

        ItemStock postStock = itemStockMapper.findByItemIdAndLocationId(TEST_ITEM_ID, TEST_LOCATION_ID);
        assertThat(postStock.getCurrentQty()).isEqualTo(2); // 5 - 3 = 2
        assertThat(postStock.getReservedQty()).isEqualTo(3);
        assertThat(postStock.getCurrentQty()).isNotNegative();
    }

    @Test
    @DisplayName("10. Insufficient stock: requests exceeding availability are rejected before inserting rows")
    void test10_insufficientStock_rejectedWithoutSideEffects() throws Exception {
        // Available is 10, request 15
        String json = """
                {
                    "locationId": 1,
                    "items": [{"itemId": 1, "qty": 15}]
                }
                """;

        mockMvc.perform(post("/api/borrow-requests")
                        .with(user(fieldStaffPrincipal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("Insufficient stock")));

        ItemStock postStock = itemStockMapper.findByItemIdAndLocationId(TEST_ITEM_ID, TEST_LOCATION_ID);
        assertThat(postStock.getCurrentQty()).isEqualTo(10);
        assertThat(postStock.getReservedQty()).isEqualTo(0);
    }

    @Test
    @DisplayName("11. Exact stock boundary: request for all remaining units succeeds, next request for 1 unit fails")
    void test11_exactStockBoundary() throws Exception {
        // Stock is exactly 3
        jdbcTemplate.update("UPDATE item_stocks SET current_qty = 3, reserved_qty = 0 WHERE item_id = ? AND location_id = ?",
                TEST_ITEM_ID, TEST_LOCATION_ID);

        // Request exactly 3 -> succeeds
        mockMvc.perform(post("/api/borrow-requests")
                        .with(user(fieldStaffPrincipal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"locationId\": 1, \"items\": [{\"itemId\": 1, \"qty\": 3}]}"))
                .andExpect(status().isCreated());

        ItemStock midStock = itemStockMapper.findByItemIdAndLocationId(TEST_ITEM_ID, TEST_LOCATION_ID);
        assertThat(midStock.getCurrentQty()).isEqualTo(0); // Exactly 0 available
        assertThat(midStock.getReservedQty()).isEqualTo(3);

        // Request 1 more unit -> fails immediately
        mockMvc.perform(post("/api/borrow-requests")
                        .with(user(fieldStaffPrincipal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"locationId\": 1, \"items\": [{\"itemId\": 1, \"qty\": 1}]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("Insufficient stock")));
    }

    @Test
    @DisplayName("12. Stock cannot become negative: database constraints and CAS updates verify invariant")
    void test12_stockCannotBecomeNegative() {
        ItemStock stock = itemStockMapper.findByItemIdAndLocationId(TEST_ITEM_ID, TEST_LOCATION_ID);

        // Attempting to reserve more than available via mapper CAS returns 0
        int rows = itemStockMapper.reserveStock(stock.getId(), 999);
        assertThat(rows).isEqualTo(0);

        // Attempting to release more than reserved returns 0
        int releaseRows = itemStockMapper.releaseReservation(stock.getId(), 999);
        assertThat(releaseRows).isEqualTo(0);

        // Attempting to fulfill more than reserved returns 0
        int fulfillRows = itemStockMapper.fulfillReservation(stock.getId(), 999);
        assertThat(fulfillRows).isEqualTo(0);

        ItemStock after = itemStockMapper.findByItemIdAndLocationId(TEST_ITEM_ID, TEST_LOCATION_ID);
        assertThat(after.getCurrentQty()).isGreaterThanOrEqualTo(0);
        assertThat(after.getReservedQty()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("13. Rejected request does not permanently consume inventory (verified complete cycle)")
    void test13_rejectedRequestDoesNotPermanentlyConsumeInventory() throws Exception {
        // Initial: 10 available
        String requestJson = "{\"locationId\": 1, \"items\": [{\"itemId\": 1, \"qty\": 10}]}";

        // User A requests all 10 available units
        MvcResult resultA = mockMvc.perform(post("/api/borrow-requests")
                        .with(user(fieldStaffPrincipal()))
                        .header("Idempotency-Key", "idem-user-a-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andReturn();
        long requestAId = extractId(resultA);

        // Verify availability is 0 now
        ItemStock duringStock = itemStockMapper.findByItemIdAndLocationId(TEST_ITEM_ID, TEST_LOCATION_ID);
        assertThat(duringStock.getCurrentQty()).isEqualTo(0);
        assertThat(duringStock.getReservedQty()).isEqualTo(10);

        // User B tries to request 1 unit while pending -> fails with Insufficient stock
        mockMvc.perform(post("/api/borrow-requests")
                        .with(user(fieldStaffPrincipal()))
                        .header("Idempotency-Key", "idem-user-b-1-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"locationId\": 1, \"items\": [{\"itemId\": 1, \"qty\": 1}]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("Insufficient stock")));

        // Admin REJECTS User A's request
        mockMvc.perform(post("/api/approvals/borrow-requests/" + requestAId + "/reject")
                        .with(user(adminPrincipal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\": \"Rejected due to warehouse inventory audit\"}"))
                .andExpect(status().isOk());

        // Verify inventory availability is 100% restored
        ItemStock afterRejectStock = itemStockMapper.findByItemIdAndLocationId(TEST_ITEM_ID, TEST_LOCATION_ID);
        assertThat(afterRejectStock.getCurrentQty()).isEqualTo(10);
        assertThat(afterRejectStock.getReservedQty()).isEqualTo(0);
        assertThat(afterRejectStock.getPhysicalQty()).isEqualTo(10);

        // User B can now immediately request all 10 units and SUCCEEDS!
        mockMvc.perform(post("/api/borrow-requests")
                        .with(user(fieldStaffPrincipal()))
                        .header("Idempotency-Key", "idem-user-b-2-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("success"));
    }

    private boolean stockHasAvailableAndReserved(ItemStock stock, int expectedAvail, int expectedReserved) {
        return stock.getCurrentQty() == expectedAvail && stock.getReservedQty() == expectedReserved;
    }
}

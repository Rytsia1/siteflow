package com.siteflow.reliability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.siteflow.domain.ItemStock;
import com.siteflow.domain.MaterialRequest;
import com.siteflow.domain.enums.AdjustmentType;
import com.siteflow.domain.enums.MaterialRequestStatus;
import com.siteflow.mapper.IdempotencyKeyMapper;
import com.siteflow.mapper.ItemStockMapper;
import com.siteflow.mapper.MaterialRequestMapper;
import com.siteflow.security.UserPrincipal;
import com.siteflow.service.StockAdjustmentService;

@SpringBootTest
@AutoConfigureMockMvc
class ReliabilityAndConcurrencyTest {

    private static final Logger log = LoggerFactory.getLogger(ReliabilityAndConcurrencyTest.class);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ItemStockMapper itemStockMapper;

    @Autowired
    private StockAdjustmentService stockAdjustmentService;

    @Autowired
    private MaterialRequestMapper materialRequestMapper;

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

    @BeforeEach
    void setupTestData() {
        idempotencyKeyMapper.deleteAll();
        // Reset stock for Item 1 (Drill) and Item 2 (Hammer) at Location 1
        jdbcTemplate.update("UPDATE item_stocks SET current_qty = 50 WHERE item_id IN (1, 2) AND location_id = 1");
    }

    @Test
    @DisplayName("1. Concurrent stock adjustments maintain consistency with zero lost updates")
    void testConcurrentStockAdjustments_noLostUpdates() throws Exception {
        // Given: Item 1 at Location 1 has current_qty = 100
        jdbcTemplate.update("UPDATE item_stocks SET current_qty = 100 WHERE item_id = 1 AND location_id = 1");

        int threadCount = 6;
        // 3 threads perform adjustment IN +10 (= +30)
        // 3 threads perform adjustment OUT -4 (= -12)
        // Net expected stock = 100 + 30 - 12 = 118
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startGate.await();
                    if (index % 2 == 0) {
                        stockAdjustmentService.createAdjustment(1L, 1L, AdjustmentType.IN, 10, "Concurrent IN test", 1L, "adj-in-" + index);
                    } else {
                        stockAdjustmentService.createAdjustment(1L, 1L, AdjustmentType.OUT, 4, "Concurrent OUT test", 1L, "adj-out-" + index);
                    }
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    log.error("Stock adjustment failed concurrently", e);
                } finally {
                    endGate.countDown();
                }
            });
        }

        startGate.countDown();
        boolean completed = endGate.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        assertThat(successCount.get()).isEqualTo(threadCount);

        ItemStock finalStock = itemStockMapper.findByItemIdAndLocationId(1L, 1L);
        assertThat(finalStock).isNotNull();
        // 100 + (3 * 10) - (3 * 4) = 118
        assertThat(finalStock.getCurrentQty()).isEqualTo(118);
    }

    @Test
    @DisplayName("2. High concurrency borrow requests prevent overselling and negative stock")
    void testConcurrentBorrowRequests_preventsNegativeStock() throws Exception {
        // Given: Item 2 has only 10 units in stock
        jdbcTemplate.update("UPDATE item_stocks SET current_qty = 10 WHERE item_id = 2 AND location_id = 1");

        int threadCount = 4;
        // 4 concurrent requests each asking for 5 units (total requested = 20, but only 10 available)
        // Exactly 2 must succeed (2 * 5 = 10), and the other 2 must be rejected due to insufficient stock
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(threadCount);
        List<Integer> statuses = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threadCount; i++) {
            final String idemKey = "borrow-race-" + i + "-" + UUID.randomUUID();
            executor.submit(() -> {
                try {
                    startGate.await();
                    String json = """
                            {
                                "locationId": 1,
                                "items": [{"itemId": 2, "qty": 5}]
                            }
                            """;
                    MvcResult result = mockMvc.perform(post("/api/borrow-requests")
                                    .with(user(fieldStaffPrincipal()))
                                    .header("Idempotency-Key", idemKey)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(json))
                            .andReturn();
                    statuses.add(result.getResponse().getStatus());
                } catch (Exception e) {
                    statuses.add(500);
                } finally {
                    endGate.countDown();
                }
            });
        }

        startGate.countDown();
        boolean completed = endGate.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        assertThat(statuses).hasSize(4);

        long successCount = statuses.stream().filter(s -> s == 201).count();
        long rejectedCount = statuses.stream().filter(s -> s == 400 || s == 409).count();

        // Exactly 2 could claim the 10 units (2 * 5 = 10), remaining 2 must be rejected
        assertThat(successCount).isEqualTo(2);
        assertThat(rejectedCount).isEqualTo(2);

        // Stock must be exactly 0 and NEVER negative
        ItemStock finalStock = itemStockMapper.findByItemIdAndLocationId(2L, 1L);
        assertThat(finalStock).isNotNull();
        assertThat(finalStock.getCurrentQty()).isEqualTo(0);
        assertThat(finalStock.getCurrentQty()).isNotNegative();
    }

    @Test
    @DisplayName("3. Concurrent approval attempts: exactly one approves, others are rejected")
    void testConcurrentApprovals_exactlyOneSucceeds() throws Exception {
        // Given: Create a Material Request in SUBMITTED state
        MaterialRequest req = MaterialRequest.builder()
                .requestedBy(3L)
                .requestDate(java.time.LocalDateTime.now())
                .status(MaterialRequestStatus.SUBMITTED)
                .justification("Approval race condition test")
                .build();
        materialRequestMapper.insert(req);
        Long mrId = req.getId();

        int threadCount = 3;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(threadCount);
        List<Integer> statuses = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startGate.await();
                    MvcResult result = mockMvc.perform(post("/api/procurement/material-requests/{id}/approve", mrId)
                                    .with(user(adminPrincipal()))
                                    .contentType(MediaType.APPLICATION_JSON))
                            .andReturn();
                    statuses.add(result.getResponse().getStatus());
                } catch (Exception e) {
                    statuses.add(500);
                } finally {
                    endGate.countDown();
                }
            });
        }

        startGate.countDown();
        boolean completed = endGate.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        assertThat(statuses).hasSize(3);

        // Exactly one approval succeeds (200 OK), the other 2 attempts are rejected (400 or 409)
        long successCount = statuses.stream().filter(s -> s == 200).count();
        long rejectedCount = statuses.stream().filter(s -> s == 400 || s == 409).count();

        assertThat(successCount).isEqualTo(1);
        assertThat(rejectedCount).isEqualTo(2);

        // Verify status in DB is APPROVED
        MaterialRequest after = materialRequestMapper.findById(mrId);
        assertThat(after.getStatus()).isEqualTo(MaterialRequestStatus.APPROVED);
    }

    @Test
    @DisplayName("4. Lightweight load benchmark: 20 concurrent threads running 100 requests")
    void testLightweightLoadBenchmark() throws Exception {
        int totalRequests = 100;
        int concurrencyLevel = 20;

        ExecutorService executor = Executors.newFixedThreadPool(concurrencyLevel);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(totalRequests);

        List<Long> latenciesMs = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger errorCount = new AtomicInteger(0);

        long overallStart = System.currentTimeMillis();

        for (int i = 0; i < totalRequests; i++) {
            final int reqIndex = i;
            executor.submit(() -> {
                try {
                    startGate.await();
                    long reqStart = System.currentTimeMillis();

                    MvcResult result;
                    if (reqIndex % 2 == 0) {
                        // Read endpoint (Health check)
                        result = mockMvc.perform(get("/actuator/health"))
                                .andReturn();
                    } else {
                        // Read endpoint (Locations list with Auth)
                        result = mockMvc.perform(get("/api/locations")
                                        .with(user(fieldStaffPrincipal())))
                                .andReturn();
                    }

                    long reqEnd = System.currentTimeMillis();
                    latenciesMs.add(reqEnd - reqStart);

                    if (result.getResponse().getStatus() != 200) {
                        errorCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                } finally {
                    endGate.countDown();
                }
            });
        }

        startGate.countDown();
        boolean completed = endGate.await(30, TimeUnit.SECONDS);
        long overallEnd = System.currentTimeMillis();
        executor.shutdown();

        assertThat(completed).isTrue();

        long totalDurationMs = overallEnd - overallStart;
        double throughputRps = (totalRequests / (double) totalDurationMs) * 1000.0;
        double avgLatencyMs = latenciesMs.stream().mapToLong(Long::longValue).average().orElse(0.0);
        long minLatencyMs = latenciesMs.stream().mapToLong(Long::longValue).min().orElse(0L);
        long maxLatencyMs = latenciesMs.stream().mapToLong(Long::longValue).max().orElse(0L);
        double errorRatePct = (errorCount.get() / (double) totalRequests) * 100.0;

        log.info("=== LIGHTWEIGHT LOAD BENCHMARK RESULTS ===");
        log.info("Total Requests   : {}", totalRequests);
        log.info("Concurrency Level: {}", concurrencyLevel);
        log.info("Total Duration   : {} ms", totalDurationMs);
        log.info("Throughput       : {} req/sec", String.format("%.2f", throughputRps));
        log.info("Latency Min      : {} ms", minLatencyMs);
        log.info("Latency Max      : {} ms", maxLatencyMs);
        log.info("Latency Avg      : {} ms", String.format("%.2f", avgLatencyMs));
        log.info("Error Rate       : {} % ({} failures)", String.format("%.2f", errorRatePct), errorCount.get());
        log.info("==========================================");

        assertThat(errorCount.get()).isEqualTo(0);
        assertThat(errorRatePct).isEqualTo(0.0);
    }
}

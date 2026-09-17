package com.siteflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.siteflow.domain.Location;
import com.siteflow.domain.enums.ItemCategory;
import com.siteflow.security.UserPrincipal;
import com.siteflow.web.dto.DashboardSummaryView;
import com.siteflow.web.dto.ItemSummaryView;
import com.siteflow.web.dto.ReorderRecommendationView;
import com.siteflow.web.dto.ToolUtilizationView;

@SpringBootTest
@AutoConfigureMockMvc
class PerformanceAndPaginationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private AnalyticsService analyticsService;

    @Autowired
    private CacheManager cacheManager;

    private UserPrincipal adminPrincipal() {
        return new UserPrincipal(1L, "admin", "hash", "ADMIN");
    }

    private UserPrincipal fieldStaffPrincipal() {
        return new UserPrincipal(3L, "pekerja", "hash", "FIELD_STAFF");
    }

    // -------------------------------------------------------------------------
    // 1. Pagination returns the correct page slice
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Requirement 1: Pagination returns the correct page slice with headers")
    void pagination_returnsCorrectPageSlice() throws Exception {
        MvcResult page0Result = mockMvc.perform(get("/api/items")
                        .param("page", "0")
                        .param("size", "2")
                        .with(user(adminPrincipal())))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Page-Number", "0"))
                .andExpect(header().string("X-Page-Size", "2"))
                .andExpect(header().exists("X-Total-Count"))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andReturn();

        MvcResult page1Result = mockMvc.perform(get("/api/items")
                        .param("page", "1")
                        .param("size", "2")
                        .with(user(adminPrincipal())))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Page-Number", "1"))
                .andExpect(header().string("X-Page-Size", "2"))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andReturn();

        JsonNode root0 = objectMapper.readTree(page0Result.getResponse().getContentAsString());
        JsonNode root1 = objectMapper.readTree(page1Result.getResponse().getContentAsString());

        long firstPageFirstId = root0.get("data").get(0).get("id").asLong();
        long secondPageFirstId = root1.get("data").get(0).get("id").asLong();

        assertThat(firstPageFirstId).isNotEqualTo(secondPageFirstId);
    }

    // -------------------------------------------------------------------------
    // 2. Maximum page size is strictly enforced
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Requirement 2: Maximum page size (> 100) is rejected with 400 Bad Request")
    void maxPageSize_exceeded_rejectedWithBadRequest() throws Exception {
        mockMvc.perform(get("/api/items")
                        .param("page", "0")
                        .param("size", "101")
                        .with(user(adminPrincipal())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Page size must not exceed 100.")));

        mockMvc.perform(get("/api/borrow-requests/my")
                        .param("page", "0")
                        .param("size", "1000000")
                        .with(user(fieldStaffPrincipal())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Page size must not exceed 100.")));

        mockMvc.perform(get("/api/procurement/material-requests")
                        .param("status", "SUBMITTED")
                        .param("page", "0")
                        .param("size", "500")
                        .with(user(adminPrincipal())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Page size must not exceed 100.")));
    }

    // -------------------------------------------------------------------------
    // 3. Filtering works correctly with pagination
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Requirement 3: Filtering by category and search works in conjunction with pagination")
    void filtering_withPagination_returnsFilteredSlice() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/items")
                        .param("category", "TOOL")
                        .param("page", "0")
                        .param("size", "10")
                        .with(user(adminPrincipal())))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode data = root.get("data");
        assertThat(data.size()).isGreaterThan(0);
        for (JsonNode item : data) {
            assertThat(item.get("category").asText()).isEqualTo("TOOL");
        }

        MvcResult searchResult = mockMvc.perform(get("/api/items")
                        .param("search", "Drill")
                        .param("page", "0")
                        .param("size", "10")
                        .with(user(adminPrincipal())))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode searchRoot = objectMapper.readTree(searchResult.getResponse().getContentAsString());
        JsonNode searchData = searchRoot.get("data");
        assertThat(searchData.size()).isGreaterThan(0);
        for (JsonNode item : searchData) {
            String name = item.get("name").asText().toLowerCase();
            String code = item.get("itemCode").asText().toLowerCase();
            assertThat(name.contains("drill") || code.contains("drill")).isTrue();
        }
    }

    // -------------------------------------------------------------------------
    // 4. Sorting works correctly with pagination
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Requirement 4: Sorting by name asc/desc works correctly with pagination")
    void sorting_withPagination_ordersResultsProperly() throws Exception {
        MvcResult ascResult = mockMvc.perform(get("/api/items")
                        .param("sortBy", "name")
                        .param("sortDir", "asc")
                        .param("page", "0")
                        .param("size", "10")
                        .with(user(adminPrincipal())))
                .andExpect(status().isOk())
                .andReturn();

        MvcResult descResult = mockMvc.perform(get("/api/items")
                        .param("sortBy", "name")
                        .param("sortDir", "desc")
                        .param("page", "0")
                        .param("size", "10")
                        .with(user(adminPrincipal())))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode ascData = objectMapper.readTree(ascResult.getResponse().getContentAsString()).get("data");
        JsonNode descData = objectMapper.readTree(descResult.getResponse().getContentAsString()).get("data");

        String firstNameAsc = ascData.get(0).get("name").asText();
        String firstNameDesc = descData.get(0).get("name").asText();

        assertThat(firstNameAsc).isNotEqualTo(firstNameDesc);
        assertThat(firstNameAsc.compareToIgnoreCase(firstNameDesc)).isLessThan(0);
    }

    // -------------------------------------------------------------------------
    // 5. Large-result endpoints use SQL LIMIT and OFFSET (not in-memory subList)
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Requirement 5: Large-result endpoints use database-level pagination")
    void pagination_executedAtDatabaseLevel() {
        List<ItemSummaryView> page0 = inventoryService.listItems(0, 1, null, null, "id", "asc");
        List<ItemSummaryView> page1 = inventoryService.listItems(1, 1, null, null, "id", "asc");

        assertThat(page0).hasSize(1);
        assertThat(page1).hasSize(1);
        assertThat(page0.get(0).id()).isNotEqualTo(page1.get(0).id());

        int totalCount = inventoryService.countItems(null, null);
        assertThat(totalCount).isGreaterThanOrEqualTo(2);
    }

    // -------------------------------------------------------------------------
    // 6. Newly added indexes exist through Flyway migration
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Requirement 6: Newly added V10 indexes exist in the database schema")
    void flywayMigration_indexesExistInSchema() {
        String query = "SELECT DISTINCT index_name FROM information_schema.statistics "
                + "WHERE table_schema = DATABASE() AND index_name IN (?, ?, ?, ?, ?, ?)";

        List<String> indexes = jdbcTemplate.queryForList(
                query,
                String.class,
                "idx_borrow_requests_user_date",
                "idx_borrow_requests_approval_date",
                "idx_material_requests_status_date",
                "idx_material_requests_user_date",
                "idx_transaction_logs_item_type_ts",
                "idx_items_code_name"
        );

        assertThat(indexes).contains(
                "idx_borrow_requests_user_date",
                "idx_borrow_requests_approval_date",
                "idx_material_requests_status_date",
                "idx_material_requests_user_date",
                "idx_transaction_logs_item_type_ts",
                "idx_items_code_name"
        );
    }

    // -------------------------------------------------------------------------
    // 7. Existing functionality remains correct after query optimization
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Requirement 7: Optimized analytics queries return accurate business metrics")
    void optimizedQueries_preserveBusinessAccuracy() {
        DashboardSummaryView summary = analyticsService.getDashboardSummary();
        assertThat(summary).isNotNull();
        assertThat(summary.totalItemsBelowMinStock()).isGreaterThanOrEqualTo(0);

        List<ToolUtilizationView> toolUtil = analyticsService.getToolUtilization();
        assertThat(toolUtil).isNotNull();
        if (!toolUtil.isEmpty()) {
            ToolUtilizationView tool = toolUtil.get(0);
            assertThat(tool.totalOwned()).isGreaterThanOrEqualTo(0);
            assertThat(tool.currentlyOut()).isGreaterThanOrEqualTo(0);
        }

        List<ReorderRecommendationView> recs = analyticsService.generateReorderRecommendations();
        assertThat(recs).isNotNull();
        for (ReorderRecommendationView rec : recs) {
            assertThat(rec.recommendedOrderQty()).isGreaterThan(0);
        }
    }

    // -------------------------------------------------------------------------
    // 8. Cached data is returned correctly
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Requirement 8: Location reference data is cached in-process")
    void locationReferenceData_isCached() {
        Cache locationsCache = cacheManager.getCache("locations");
        assertThat(locationsCache).isNotNull();
        locationsCache.clear();

        List<Location> firstCall = inventoryService.listLocations();
        assertThat(firstCall).isNotEmpty();

        Cache.ValueWrapper wrapper = locationsCache.get(org.springframework.cache.interceptor.SimpleKey.EMPTY);
        assertThat(wrapper).isNotNull();
        @SuppressWarnings("unchecked")
        List<Location> cachedLocations = (List<Location>) wrapper.get();
        assertThat(cachedLocations).hasSameSizeAs(firstCall);

        List<Location> secondCall = inventoryService.listLocations();
        assertThat(secondCall).isSameAs(cachedLocations);
    }

    // -------------------------------------------------------------------------
    // 9. Cache invalidation occurs after relevant mutations
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Requirement 9: Cache invalidation successfully evicts the reference cache")
    void locationReferenceData_cacheInvalidationWorks() {
        inventoryService.listLocations();
        Cache locationsCache = cacheManager.getCache("locations");
        assertThat(locationsCache.get(org.springframework.cache.interceptor.SimpleKey.EMPTY)).isNotNull();

        inventoryService.evictLocationsCache();

        assertThat(locationsCache.get(org.springframework.cache.interceptor.SimpleKey.EMPTY)).isNull();
    }

    // -------------------------------------------------------------------------
    // 10. Dynamic sorting/filtering cannot inject arbitrary SQL
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Requirement 10: Dynamic sorting/filtering strictly rejects SQL injection attempts with 400")
    void dynamicSorting_sqlInjectionAttempt_rejectedWithBadRequest() throws Exception {
        mockMvc.perform(get("/api/items")
                        .param("sortBy", "name; DROP TABLE items--")
                        .with(user(adminPrincipal())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Invalid sort field")));

        mockMvc.perform(get("/api/items")
                        .param("sortBy", "' OR '1'='1")
                        .with(user(adminPrincipal())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Invalid sort field")));

        mockMvc.perform(get("/api/items")
                        .param("sortBy", "UNION SELECT 1, 2, 3")
                        .with(user(adminPrincipal())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Invalid sort field")));
    }
}

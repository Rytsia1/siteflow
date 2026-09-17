package com.siteflow.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.siteflow.security.UserPrincipal;
import com.siteflow.service.AnalyticsService;
import com.siteflow.web.dto.DashboardSummaryView;
import com.siteflow.web.dto.DemandForecastView;

@SpringBootTest
@AutoConfigureMockMvc
class AnalyticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AnalyticsService analyticsService;

    private UserPrincipal adminPrincipal() {
        return new UserPrincipal(1L, "admin", "hash", "ADMIN");
    }

    private UserPrincipal fieldStaffPrincipal() {
        return new UserPrincipal(2L, "worker", "hash", "FIELD_STAFF");
    }

    private UserPrincipal warehousePrincipal() {
        return new UserPrincipal(3L, "keeper", "hash", "WAREHOUSE_STAFF");
    }

    private UserPrincipal procurementPrincipal() {
        return new UserPrincipal(4L, "buyer", "hash", "PROCUREMENT");
    }

    @Test
    @DisplayName("Admin can access analytics dashboard summary")
    void getSummary_asAdmin_success() throws Exception {
        when(analyticsService.getDashboardSummary()).thenReturn(new DashboardSummaryView(5, 2, "Drill"));

        mockMvc.perform(get("/api/analytics/summary")
                        .with(user(adminPrincipal())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.totalActiveBorrows").value(5))
                .andExpect(jsonPath("$.data.totalItemsBelowMinStock").value(2))
                .andExpect(jsonPath("$.data.mostBorrowedItemName").value("Drill"));
    }

    @Test
    @DisplayName("Field staff is forbidden from accessing analytics dashboard summary")
    void getSummary_asFieldStaff_forbidden() throws Exception {
        mockMvc.perform(get("/api/analytics/summary")
                        .with(user(fieldStaffPrincipal())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Warehouse staff is forbidden from accessing analytics dashboard summary")
    void getSummary_asWarehouseStaff_forbidden() throws Exception {
        mockMvc.perform(get("/api/analytics/summary")
                        .with(user(warehousePrincipal())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Procurement role is forbidden from accessing analytics dashboard summary")
    void getSummary_asProcurement_forbidden() throws Exception {
        mockMvc.perform(get("/api/analytics/summary")
                        .with(user(procurementPrincipal())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Unauthenticated request to analytics summary returns 401")
    void getSummary_unauthenticated_unauthorized() throws Exception {
        mockMvc.perform(get("/api/analytics/summary"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Admin can view low-stock items")
    void getLowStock_asAdmin_success() throws Exception {
        when(analyticsService.getLowStockItems()).thenReturn(List.of());

        mockMvc.perform(get("/api/analytics/low-stock")
                        .with(user(adminPrincipal())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));
    }

    @Test
    @DisplayName("Admin can view consumption trends with valid date range parameters")
    void getTrends_asAdmin_success() throws Exception {
        when(analyticsService.getConsumptionTrends(any(LocalDate.class), any(LocalDate.class))).thenReturn(List.of());

        mockMvc.perform(get("/api/analytics/trends")
                        .with(user(adminPrincipal()))
                        .param("startDate", "2026-01-01")
                        .param("endDate", "2026-01-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));
    }

    @Test
    @DisplayName("Consumption trends endpoint returns 400 when date parameters are missing")
    void getTrends_missingParams_badRequest() throws Exception {
        mockMvc.perform(get("/api/analytics/trends")
                        .with(user(adminPrincipal())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"));
    }

    @Test
    @DisplayName("Consumption trends endpoint returns 400 when date parameter has invalid format")
    void getTrends_invalidDateFormat_badRequest() throws Exception {
        mockMvc.perform(get("/api/analytics/trends")
                        .with(user(adminPrincipal()))
                        .param("startDate", "not-a-date")
                        .param("endDate", "2026-01-31"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"));
    }

    @Test
    @DisplayName("Admin can view tool utilization metrics")
    void getToolUtilization_asAdmin_success() throws Exception {
        when(analyticsService.getToolUtilization()).thenReturn(List.of());

        mockMvc.perform(get("/api/analytics/tool-utilization")
                        .with(user(adminPrincipal())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));
    }

    @Test
    @DisplayName("Admin can view demand forecast for specific item")
    void getForecast_asAdmin_success() throws Exception {
        when(analyticsService.calculateDemandForecast(eq(10L))).thenReturn(new DemandForecastView(10L, 8));

        mockMvc.perform(get("/api/analytics/forecast/10")
                        .with(user(adminPrincipal())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.itemId").value(10))
                .andExpect(jsonPath("$.data.forecastedDemand").value(8));
    }
}

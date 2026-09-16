package com.siteflow.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.siteflow.service.AnalyticsService;
import com.siteflow.web.ApiResponse;
import com.siteflow.web.dto.ConsumptionTrendView;
import com.siteflow.web.dto.DashboardSummaryView;
import com.siteflow.web.dto.DemandForecastView;
import com.siteflow.web.dto.ItemSummaryView;

@RestController
@RequestMapping("/api/analytics")
@PreAuthorize("hasRole('ADMIN')")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/summary")
    public ApiResponse<DashboardSummaryView> getSummary() {
        return ApiResponse.success("Dashboard summary retrieved.", analyticsService.getDashboardSummary());
    }

    @GetMapping("/low-stock")
    public ApiResponse<List<ItemSummaryView>> getLowStock() {
        return ApiResponse.success("Low-stock items retrieved.", analyticsService.getLowStockItems());
    }

    @GetMapping("/trends")
    public ApiResponse<List<ConsumptionTrendView>> getTrends(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ApiResponse.success("Consumption trends retrieved.",
                analyticsService.getConsumptionTrends(startDate, endDate));
    }

    @GetMapping("/forecast/{itemId}")
    public ApiResponse<DemandForecastView> getForecast(@PathVariable Long itemId) {
        return ApiResponse.success("Demand forecast calculated.", analyticsService.calculateDemandForecast(itemId));
    }
}

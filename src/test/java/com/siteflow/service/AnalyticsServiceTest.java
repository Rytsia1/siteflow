package com.siteflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.siteflow.domain.enums.ItemCategory;
import com.siteflow.mapper.AnalyticsMapper;
import com.siteflow.web.dto.ConsumptionTrendView;
import com.siteflow.web.dto.DashboardSummaryView;
import com.siteflow.web.dto.DemandForecastView;
import com.siteflow.web.dto.ItemMonthlyConsumptionView;
import com.siteflow.web.dto.ItemSummaryView;
import com.siteflow.web.dto.MonthlyConsumptionView;
import com.siteflow.web.dto.MostBorrowedItemView;
import com.siteflow.web.dto.ReorderRecommendationView;
import com.siteflow.web.dto.ToolUtilizationView;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    @Mock
    private AnalyticsMapper analyticsMapper;

    private AnalyticsService analyticsService;

    @BeforeEach
    void setUp() {
        analyticsService = new AnalyticsService(analyticsMapper);
    }

    @Test
    @DisplayName("getDashboardSummary calculates active borrows, low stock items, and top borrowed item")
    void getDashboardSummary_withTopBorrowed_returnsSummary() {
        when(analyticsMapper.countActiveBorrows()).thenReturn(4);
        when(analyticsMapper.countLowStockItems()).thenReturn(1);
        when(analyticsMapper.findMostBorrowedItemForPeriod(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(new MostBorrowedItemView(1L, "Drill", 15));

        DashboardSummaryView summary = analyticsService.getDashboardSummary();

        assertThat(summary.totalActiveBorrows()).isEqualTo(4);
        assertThat(summary.totalItemsBelowMinStock()).isEqualTo(1);
        assertThat(summary.mostBorrowedItemName()).isEqualTo("Drill");
        verify(analyticsMapper).countLowStockItems();
    }

    @Test
    @DisplayName("getDashboardSummary handles case when no items were borrowed in period")
    void getDashboardSummary_noBorrowsInPeriod_returnsNullItemName() {
        when(analyticsMapper.countActiveBorrows()).thenReturn(0);
        when(analyticsMapper.countLowStockItems()).thenReturn(0);
        when(analyticsMapper.findMostBorrowedItemForPeriod(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(null);

        DashboardSummaryView summary = analyticsService.getDashboardSummary();

        assertThat(summary.totalActiveBorrows()).isEqualTo(0);
        assertThat(summary.totalItemsBelowMinStock()).isEqualTo(0);
        assertThat(summary.mostBorrowedItemName()).isNull();
    }

    @Test
    @DisplayName("getLowStockItems delegates to mapper")
    void getLowStockItems_delegatesToMapper() {
        ItemSummaryView item = new ItemSummaryView(2L, "HELM-01", "Helmet", ItemCategory.CONSUMABLE, "pcs", 2, 10);
        when(analyticsMapper.findLowStockItems()).thenReturn(List.of(item));

        List<ItemSummaryView> result = analyticsService.getLowStockItems();

        assertThat(result).containsExactly(item);
    }

    @Test
    @DisplayName("getConsumptionTrends queries mapper spanning start of first day to end of last day")
    void getConsumptionTrends_queriesFullDateRange() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 1, 31);
        ConsumptionTrendView trend = new ConsumptionTrendView("2026-01", 25);
        when(analyticsMapper.findConsumptionTrends(start.atStartOfDay(), end.atTime(23, 59, 59)))
                .thenReturn(List.of(trend));

        List<ConsumptionTrendView> result = analyticsService.getConsumptionTrends(start, end);

        assertThat(result).containsExactly(trend);
        verify(analyticsMapper).findConsumptionTrends(start.atStartOfDay(), end.atTime(23, 59, 59));
    }

    @Test
    @DisplayName("getToolUtilization delegates to mapper")
    void getToolUtilization_delegatesToMapper() {
        ToolUtilizationView util = new ToolUtilizationView(1L, "Heavy Drill", 10, 8);
        when(analyticsMapper.findToolUtilization()).thenReturn(List.of(util));

        List<ToolUtilizationView> result = analyticsService.getToolUtilization();

        assertThat(result).containsExactly(util);
        assertThat(util.utilizationRate()).isEqualTo(0.8);
    }

    @Test
    @DisplayName("generateReorderRecommendations calculates shortfall plus consumption buffer using batch query")
    void generateReorderRecommendations_computesShortfallAndBuffer() {
        ItemSummaryView item = new ItemSummaryView(1L, "DRL-001", "Hammer Drill", ItemCategory.TOOL, "units", 10, 2);
        when(analyticsMapper.findLowStockItems()).thenReturn(List.of(item));
        // 3 recent months consumption: 6, 8, 4 -> average = 6.0 -> buffer = ceil(6.0) = 6
        // Shortfall = 10 - 2 = 8. Recommended = 8 + 6 = 14.
        when(analyticsMapper.findRecentMonthlyConsumptionForItems(any(), any())).thenReturn(List.of(
                new ItemMonthlyConsumptionView(1L, "2026-08", 6),
                new ItemMonthlyConsumptionView(1L, "2026-07", 8),
                new ItemMonthlyConsumptionView(1L, "2026-06", 4)
        ));

        List<ReorderRecommendationView> recommendations = analyticsService.generateReorderRecommendations();

        assertThat(recommendations).hasSize(1);
        ReorderRecommendationView rec = recommendations.get(0);
        assertThat(rec.itemId()).isEqualTo(1L);
        assertThat(rec.itemName()).isEqualTo("Hammer Drill");
        assertThat(rec.currentQty()).isEqualTo(2);
        assertThat(rec.minStockThreshold()).isEqualTo(10);
        assertThat(rec.recommendedOrderQty()).isEqualTo(14);
    }

    @Test
    @DisplayName("generateReorderRecommendations handles zero monthly consumption history")
    void generateReorderRecommendations_noConsumptionHistory_recommendsJustShortfall() {
        ItemSummaryView item = new ItemSummaryView(1L, "DRL-001", "Hammer Drill", ItemCategory.TOOL, "units", 10, 3);
        when(analyticsMapper.findLowStockItems()).thenReturn(List.of(item));
        when(analyticsMapper.findRecentMonthlyConsumptionForItems(any(), any())).thenReturn(List.of());

        List<ReorderRecommendationView> recommendations = analyticsService.generateReorderRecommendations();

        assertThat(recommendations).hasSize(1);
        ReorderRecommendationView rec = recommendations.get(0);
        // Shortfall = 10 - 3 = 7, Buffer = 0 -> Recommended = 7
        assertThat(rec.recommendedOrderQty()).isEqualTo(7);
    }

    @Test
    @DisplayName("calculateDemandForecast calculates 3-month Simple Moving Average with rounding")
    void calculateDemandForecast_calculatesRoundedSMA() {
        when(analyticsMapper.findMonthlyConsumptionByItem(10L)).thenReturn(List.of(
                new MonthlyConsumptionView("2026-08", 10),
                new MonthlyConsumptionView("2026-07", 12),
                new MonthlyConsumptionView("2026-06", 11)
        )); // average = (10 + 12 + 11) / 3 = 11.0 -> round = 11

        DemandForecastView forecast = analyticsService.calculateDemandForecast(10L);

        assertThat(forecast.itemId()).isEqualTo(10L);
        assertThat(forecast.forecastedDemand()).isEqualTo(11);
    }

    @Test
    @DisplayName("calculateDemandForecast returns zero when no historical data exists")
    void calculateDemandForecast_emptyHistory_returnsZero() {
        when(analyticsMapper.findMonthlyConsumptionByItem(10L)).thenReturn(List.of());

        DemandForecastView forecast = analyticsService.calculateDemandForecast(10L);

        assertThat(forecast.itemId()).isEqualTo(10L);
        assertThat(forecast.forecastedDemand()).isEqualTo(0);
    }
}

package com.siteflow.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

@Service
public class AnalyticsService {

    /** Number of most-recent months averaged for both the SMA forecast and the reorder buffer. */
    private static final int SMA_WINDOW_MONTHS = 3;

    private final AnalyticsMapper analyticsMapper;

    public AnalyticsService(AnalyticsMapper analyticsMapper) {
        this.analyticsMapper = analyticsMapper;
    }

    @Transactional(readOnly = true)
    public DashboardSummaryView getDashboardSummary() {
        LocalDateTime monthStart = YearMonth.now().atDay(1).atStartOfDay();
        LocalDateTime monthEnd = LocalDateTime.now();

        int activeBorrows = analyticsMapper.countActiveBorrows();
        int belowMinStock = analyticsMapper.countLowStockItems();
        MostBorrowedItemView mostBorrowed = analyticsMapper.findMostBorrowedItemForPeriod(monthStart, monthEnd);

        return new DashboardSummaryView(activeBorrows, belowMinStock,
                mostBorrowed == null ? null : mostBorrowed.itemName());
    }

    @Transactional(readOnly = true)
    public List<ItemSummaryView> getLowStockItems() {
        return analyticsMapper.findLowStockItems();
    }

    @Transactional(readOnly = true)
    public List<ConsumptionTrendView> getConsumptionTrends(LocalDate startDate, LocalDate endDate) {
        return analyticsMapper.findConsumptionTrends(startDate.atStartOfDay(), endDate.atTime(23, 59, 59));
    }

    @Transactional(readOnly = true)
    public List<ToolUtilizationView> getToolUtilization() {
        return analyticsMapper.findToolUtilization();
    }

    /**
     * For each low-stock item, recommends ordering enough to clear the shortfall against its
     * minimum threshold plus a one-month buffer sized to its recent average consumption.
     * Uses a single batch lookup to eliminate N+1 queries.
     */
    @Transactional(readOnly = true)
    public List<ReorderRecommendationView> generateReorderRecommendations() {
        List<ItemSummaryView> lowStockItems = analyticsMapper.findLowStockItems();
        if (lowStockItems.isEmpty()) {
            return List.of();
        }

        List<Long> itemIds = lowStockItems.stream().map(ItemSummaryView::id).toList();
        LocalDateTime since = YearMonth.now().minusMonths(6).atDay(1).atStartOfDay();

        List<ItemMonthlyConsumptionView> batchConsumption = analyticsMapper.findRecentMonthlyConsumptionForItems(itemIds, since);

        Map<Long, List<ItemMonthlyConsumptionView>> consumptionByItem = batchConsumption.stream()
                .collect(Collectors.groupingBy(ItemMonthlyConsumptionView::itemId));

        return lowStockItems.stream()
                .map(item -> {
                    int shortfall = Math.max(item.minStockThreshold() - item.totalQty(), 0);
                    List<ItemMonthlyConsumptionView> recentMonths = consumptionByItem.getOrDefault(item.id(), List.of())
                            .stream()
                            .limit(SMA_WINDOW_MONTHS)
                            .toList();
                    double avg = recentMonths.isEmpty() ? 0.0
                            : recentMonths.stream().mapToInt(ItemMonthlyConsumptionView::qty).average().orElse(0.0);
                    int buffer = (int) Math.ceil(avg);
                    return new ReorderRecommendationView(item.id(), item.name(), item.totalQty(),
                            item.minStockThreshold(), shortfall + buffer);
                })
                .toList();
    }

    /** Simple Moving Average over the last {@value #SMA_WINDOW_MONTHS} months of borrow outflow. */
    @Transactional(readOnly = true)
    public DemandForecastView calculateDemandForecast(Long itemId) {
        return new DemandForecastView(itemId, (int) Math.round(averageMonthlyConsumption(itemId)));
    }

    private double averageMonthlyConsumption(Long itemId) {
        List<MonthlyConsumptionView> recentMonths = analyticsMapper.findMonthlyConsumptionByItem(itemId).stream()
                .limit(SMA_WINDOW_MONTHS)
                .toList();
        return recentMonths.isEmpty() ? 0.0
                : recentMonths.stream().mapToInt(MonthlyConsumptionView::qty).average().orElse(0.0);
    }
}

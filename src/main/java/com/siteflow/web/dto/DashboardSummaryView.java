package com.siteflow.web.dto;

public record DashboardSummaryView(
        Integer totalActiveBorrows,
        Integer totalItemsBelowMinStock,
        String mostBorrowedItemName) {
}

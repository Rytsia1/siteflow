package com.siteflow.web.dto;

import com.siteflow.domain.enums.ItemCategory;

public record ItemSummaryView(
        Long id,
        String itemCode,
        String name,
        ItemCategory category,
        String unit,
        Integer minStockThreshold,
        Integer totalQty) {
}

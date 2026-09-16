package com.siteflow.web.dto;

public record ReorderRecommendationView(
        Long itemId,
        String itemName,
        Integer currentQty,
        Integer minStockThreshold,
        Integer recommendedOrderQty) {
}

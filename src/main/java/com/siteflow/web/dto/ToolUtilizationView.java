package com.siteflow.web.dto;

public record ToolUtilizationView(Long itemId, String itemName, Integer totalOwned, Integer currentlyOut) {

    public double utilizationRate() {
        return totalOwned == null || totalOwned == 0 ? 0.0 : (double) currentlyOut / totalOwned;
    }
}

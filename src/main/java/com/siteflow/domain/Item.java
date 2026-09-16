package com.siteflow.domain;

import java.time.LocalDateTime;

import com.siteflow.domain.enums.ItemCategory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Item {
    private Long id;
    private String itemCode;
    private String name;
    private ItemCategory category;
    private String unit;
    private Integer minStockThreshold;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

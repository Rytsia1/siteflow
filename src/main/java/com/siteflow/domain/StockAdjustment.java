package com.siteflow.domain;

import java.time.LocalDateTime;

import com.siteflow.domain.enums.AdjustmentType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockAdjustment {
    private Long id;
    private Long itemId;
    private Long locationId;
    private Long adjustedBy;
    private AdjustmentType adjustmentType;
    private Integer qty;
    private String reason;
    private LocalDateTime createdAt;
}

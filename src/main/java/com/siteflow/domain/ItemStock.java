package com.siteflow.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ItemStock {
    private Long id;
    private Long itemId;
    private Long locationId;
    private Integer currentQty;
    @Builder.Default
    private Integer reservedQty = 0;
    private LocalDateTime updatedAt;

    /**
     * Quantity available for new reservations or borrowing.
     */
    public int getAvailableQty() {
        return currentQty != null ? currentQty : 0;
    }

    /**
     * Total physical stock physically on-hand in warehouse (available + reserved).
     */
    public int getPhysicalQty() {
        int avail = currentQty != null ? currentQty : 0;
        int reserved = reservedQty != null ? reservedQty : 0;
        return avail + reserved;
    }
}

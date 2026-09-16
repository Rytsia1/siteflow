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
    private LocalDateTime updatedAt;
}

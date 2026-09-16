package com.siteflow.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MaterialRequestItem {
    private Long id;
    private Long mrId;
    private Long itemId;
    private Integer requestedQty;
}

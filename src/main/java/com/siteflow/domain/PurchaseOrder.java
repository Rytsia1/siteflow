package com.siteflow.domain;

import java.time.LocalDateTime;

import com.siteflow.domain.enums.PurchaseOrderStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseOrder {
    private Long id;
    private Long mrId;
    private String poNumber;
    private String supplierName;
    private LocalDateTime orderDate;
    private LocalDateTime expectedDeliveryDate;
    private PurchaseOrderStatus poStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

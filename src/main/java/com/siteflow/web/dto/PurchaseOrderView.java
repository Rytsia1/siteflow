package com.siteflow.web.dto;

import java.time.LocalDateTime;

import com.siteflow.domain.PurchaseOrder;
import com.siteflow.domain.enums.PurchaseOrderStatus;

/**
 * Read-only projection of a {@link PurchaseOrder} for the Purchase Orders list endpoint.
 *
 * <p>The frontend reads {@code row.status} (not {@code row.poStatus}), so this view exposes
 * {@code poStatus} under the JSON key {@code status} to match the existing frontend contract
 * without renaming the domain field.
 */
public record PurchaseOrderView(
        Long id,
        Long mrId,
        String poNumber,
        String supplierName,
        LocalDateTime orderDate,
        LocalDateTime expectedDeliveryDate,
        PurchaseOrderStatus status) {

    /** Converts a domain {@link PurchaseOrder} to this view. */
    public static PurchaseOrderView from(PurchaseOrder po) {
        return new PurchaseOrderView(
                po.getId(),
                po.getMrId(),
                po.getPoNumber(),
                po.getSupplierName(),
                po.getOrderDate(),
                po.getExpectedDeliveryDate(),
                po.getPoStatus());
    }
}

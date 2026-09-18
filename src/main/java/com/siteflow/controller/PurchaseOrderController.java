package com.siteflow.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.siteflow.service.ProcurementService;
import com.siteflow.web.ApiResponse;
import com.siteflow.web.dto.PurchaseOrderView;

/**
 * Read-only endpoints for the Purchase Order resource.
 *
 * <p>Purchase Orders are created by the existing
 * {@code POST /api/procurement/material-requests/{id}/generate-po} endpoint in
 * {@link ProcurementController}. This controller exposes the complementary
 * read operations so the frontend Purchase Orders tab can list them.
 *
 * <p>URL base: {@code /api/procurement/purchase-orders}
 */
@RestController
@RequestMapping("/api/procurement/purchase-orders")
public class PurchaseOrderController {

    private final ProcurementService procurementService;

    public PurchaseOrderController(ProcurementService procurementService) {
        this.procurementService = procurementService;
    }

    /**
     * Returns all purchase orders, newest first.
     *
     * <p>Authorization: ADMIN and PROCUREMENT roles only. FIELD_STAFF and WAREHOUSE_STAFF
     * do not have a business need to view the full PO register.
     *
     * <p>Response: {@code 200 OK} with an {@link ApiResponse} wrapping a list of
     * {@link PurchaseOrderView}. Returns an empty list when no POs exist — never 404.
     *
     * @return paginated or full list of purchase orders
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'PROCUREMENT')")
    public ResponseEntity<ApiResponse<List<PurchaseOrderView>>> listPurchaseOrders() {
        List<PurchaseOrderView> views = procurementService.listAllPurchaseOrders()
                .stream()
                .map(PurchaseOrderView::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success("Purchase orders retrieved.", views));
    }
}

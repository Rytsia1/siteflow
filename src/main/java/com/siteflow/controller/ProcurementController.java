package com.siteflow.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.siteflow.domain.MaterialRequest;
import com.siteflow.domain.PurchaseOrder;
import com.siteflow.security.UserPrincipal;
import com.siteflow.service.ProcurementService;
import com.siteflow.web.ApiResponse;
import com.siteflow.web.dto.GeneratePoDto;
import com.siteflow.web.dto.MaterialRequestDto;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/procurement/material-requests")
public class ProcurementController {

    private final ProcurementService procurementService;

    public ProcurementController(ProcurementService procurementService) {
        this.procurementService = procurementService;
    }

    /**
     * Submits a new material request for procurement.
     * Accessible by authenticated users/supervisors.
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'FIELD_STAFF', 'WAREHOUSE_STAFF')")
    public ResponseEntity<ApiResponse<MaterialRequest>> submitMaterialRequest(
            @RequestBody @Valid MaterialRequestDto dto,
            @AuthenticationPrincipal UserPrincipal principal) {
        List<ProcurementService.MrLineItem> lines = dto.items().stream()
                .map(line -> new ProcurementService.MrLineItem(line.itemId(), line.getQuantity()))
                .toList();

        MaterialRequest created = procurementService.submitMaterialRequest(
                principal.getUserId(),
                dto.justification(),
                lines);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Material request submitted successfully.", created));
    }

    /**
     * Generates a purchase order from an approved material request.
     * Accessible only by users with ADMIN or PROCUREMENT role.
     */
    @PostMapping("/{id}/generate-po")
    @PreAuthorize("hasAnyRole('ADMIN', 'PROCUREMENT')")
    public ResponseEntity<ApiResponse<PurchaseOrder>> generatePurchaseOrder(
            @PathVariable Long id,
            @RequestBody @Valid GeneratePoDto dto) {
        PurchaseOrder po = procurementService.generatePurchaseOrder(
                id,
                dto.supplierName(),
                dto.expectedDeliveryDate());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Purchase order generated successfully.", po));
    }
}

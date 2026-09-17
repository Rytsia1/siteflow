package com.siteflow.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.siteflow.domain.MaterialRequest;
import com.siteflow.domain.PurchaseOrder;
import com.siteflow.domain.enums.MaterialRequestStatus;
import com.siteflow.security.UserPrincipal;
import com.siteflow.service.ProcurementService;
import com.siteflow.web.ApiResponse;
import com.siteflow.web.dto.ApproveRequestDto;
import com.siteflow.web.dto.GeneratePoDto;
import com.siteflow.web.dto.MaterialRequestDto;
import com.siteflow.web.dto.MaterialRequestView;
import com.siteflow.web.dto.RejectRequestDto;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/procurement/material-requests")
public class ProcurementController {

    private final ProcurementService procurementService;

    public ProcurementController(ProcurementService procurementService) {
        this.procurementService = procurementService;
    }

    /**
     * Lists material requests in the given status (e.g. APPROVED, awaiting PO generation).
     * Accessible only by users with ADMIN or PROCUREMENT role.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'PROCUREMENT')")
    public ApiResponse<List<MaterialRequestView>> listMaterialRequests(
            @RequestParam MaterialRequestStatus status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            jakarta.servlet.http.HttpServletResponse response) {
        if (page == null && size == null) {
            return ApiResponse.success("Material requests retrieved.",
                    procurementService.listMaterialRequestsByStatus(status));
        }
        int pageIndex = (page != null) ? page : 0;
        int pageSize = (size != null) ? size : 20;
        List<MaterialRequestView> requests = procurementService.listMaterialRequestsByStatus(status, pageIndex, pageSize);
        int total = procurementService.countMaterialRequestsByStatus(status);
        int totalPages = (int) Math.ceil((double) total / pageSize);
        if (response != null) {
            response.setHeader("X-Total-Count", String.valueOf(total));
            response.setHeader("X-Page-Number", String.valueOf(pageIndex));
            response.setHeader("X-Page-Size", String.valueOf(pageSize));
            response.setHeader("X-Total-Pages", String.valueOf(totalPages));
        }
        return ApiResponse.success("Material requests retrieved.", requests);
    }

    /**
     * Submits a new material request for procurement.
     * Accessible by authenticated users/supervisors.
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'FIELD_STAFF', 'WAREHOUSE_STAFF')")
    public ResponseEntity<ApiResponse<MaterialRequest>> submitMaterialRequest(
            @RequestBody @Valid MaterialRequestDto dto,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal UserPrincipal principal) {
        List<ProcurementService.MrLineItem> lines = dto.items().stream()
                .map(line -> new ProcurementService.MrLineItem(line.itemId(), line.getQuantity()))
                .toList();

        MaterialRequest created = (idempotencyKey != null && !idempotencyKey.isBlank())
                ? procurementService.submitMaterialRequest(principal.getUserId(), dto.justification(), lines, idempotencyKey)
                : procurementService.submitMaterialRequest(principal.getUserId(), dto.justification(), lines);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Material request submitted successfully.", created));
    }

    /**
     * Approves a submitted material request, making it eligible for PO generation.
     * Accessible only by administrators.
     */
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<MaterialRequest> approveMaterialRequest(
            @PathVariable Long id,
            @RequestBody(required = false) ApproveRequestDto dto,
            @AuthenticationPrincipal UserPrincipal principal) {
        MaterialRequest approved = procurementService.approveMaterialRequest(
                id, principal.getUserId(), ApproveRequestDto.noteOf(dto));
        return ApiResponse.success("Material request approved.", approved);
    }

    /**
     * Rejects a submitted material request, terminating it. A required note explains
     * why so the requester can amend and resubmit if appropriate.
     * Accessible only by administrators.
     */
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<MaterialRequest> rejectMaterialRequest(
            @PathVariable Long id,
            @RequestBody @Valid RejectRequestDto dto,
            @AuthenticationPrincipal UserPrincipal principal) {
        MaterialRequest rejected = procurementService.rejectMaterialRequest(id, principal.getUserId(), dto.note());
        return ApiResponse.success("Material request rejected.", rejected);
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

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'PROCUREMENT', 'FIELD_STAFF', 'WAREHOUSE_STAFF')")
    public ApiResponse<MaterialRequest> getMaterialRequest(@PathVariable Long id) {
        return ApiResponse.success("Material request retrieved.", procurementService.getMaterialRequest(id));
    }

    @GetMapping("/my")
    @PreAuthorize("hasAnyRole('ADMIN', 'FIELD_STAFF', 'WAREHOUSE_STAFF')")
    public ApiResponse<List<MaterialRequest>> listMyMaterialRequests(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @AuthenticationPrincipal UserPrincipal principal,
            jakarta.servlet.http.HttpServletResponse response) {
        if (page == null && size == null) {
            return ApiResponse.success("Material requests retrieved.", procurementService.listMyMaterialRequests(principal.getUserId()));
        }
        int pageIndex = (page != null) ? page : 0;
        int pageSize = (size != null) ? size : 20;
        List<MaterialRequest> requests = procurementService.listMyMaterialRequests(principal.getUserId(), pageIndex, pageSize);
        int total = procurementService.countMyMaterialRequests(principal.getUserId());
        int totalPages = (int) Math.ceil((double) total / pageSize);
        if (response != null) {
            response.setHeader("X-Total-Count", String.valueOf(total));
            response.setHeader("X-Page-Number", String.valueOf(pageIndex));
            response.setHeader("X-Page-Size", String.valueOf(pageSize));
            response.setHeader("X-Total-Pages", String.valueOf(totalPages));
        }
        return ApiResponse.success("Material requests retrieved.", requests);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN', 'FIELD_STAFF', 'WAREHOUSE_STAFF')")
    public ApiResponse<MaterialRequest> cancelMaterialRequest(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        MaterialRequest cancelled = procurementService.cancelMaterialRequest(id, principal.getUserId());
        return ApiResponse.success("Material request cancelled.", cancelled);
    }
}

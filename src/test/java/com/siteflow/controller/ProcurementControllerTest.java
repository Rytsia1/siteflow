package com.siteflow.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.siteflow.domain.MaterialRequest;
import com.siteflow.domain.PurchaseOrder;
import com.siteflow.domain.enums.MaterialRequestStatus;
import com.siteflow.domain.enums.PurchaseOrderStatus;
import com.siteflow.security.UserPrincipal;
import com.siteflow.service.ProcurementService;

@SpringBootTest
@AutoConfigureMockMvc
class ProcurementControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProcurementService procurementService;

    private UserPrincipal adminPrincipal() {
        return new UserPrincipal(1L, "admin", "hash", "ADMIN");
    }

    private UserPrincipal fieldStaffPrincipal() {
        return new UserPrincipal(2L, "pekerja", "hash", "FIELD_STAFF");
    }

    private UserPrincipal procurementPrincipal() {
        return new UserPrincipal(3L, "proc", "hash", "PROCUREMENT");
    }

    @Test
    @DisplayName("Field staff / supervisor can submit a new material request")
    void submitMaterialRequest_success() throws Exception {
        MaterialRequest created = MaterialRequest.builder()
                .id(100L)
                .requestedBy(2L)
                .requestDate(LocalDateTime.now())
                .status(MaterialRequestStatus.SUBMITTED)
                .justification("Bridge foundation reinforcements")
                .build();

        when(procurementService.submitMaterialRequest(eq(2L), eq("Bridge foundation reinforcements"), any()))
                .thenReturn(created);

        mockMvc.perform(post("/api/procurement/material-requests")
                        .with(user(fieldStaffPrincipal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "justification": "Bridge foundation reinforcements",
                                    "items": [
                                        {"itemId": 1, "qty": 10},
                                        {"itemId": 2, "requestedQty": 5}
                                    ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.message").value("Material request submitted successfully."))
                .andExpect(jsonPath("$.data.id").value(100))
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"));
    }

    @Test
    @DisplayName("Submitting material request without items returns 400 Bad Request")
    void submitMaterialRequest_emptyItems_badRequest() throws Exception {
        mockMvc.perform(post("/api/procurement/material-requests")
                        .with(user(fieldStaffPrincipal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "justification": "Empty request",
                                    "items": []
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Admin can approve a submitted material request")
    void approveMaterialRequest_asAdmin_success() throws Exception {
        MaterialRequest approved = MaterialRequest.builder()
                .id(100L)
                .status(MaterialRequestStatus.APPROVED)
                .approvedBy(1L)
                .approvalNote("ok")
                .build();

        when(procurementService.approveMaterialRequest(eq(100L), eq(1L), eq("ok"))).thenReturn(approved);

        mockMvc.perform(post("/api/procurement/material-requests/100/approve")
                        .with(user(adminPrincipal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "note": "ok"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.message").value("Material request approved."))
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
    }

    @Test
    @DisplayName("Admin can approve a material request without a note")
    void approveMaterialRequest_withoutBody_success() throws Exception {
        MaterialRequest approved = MaterialRequest.builder().id(100L).status(MaterialRequestStatus.APPROVED).build();

        when(procurementService.approveMaterialRequest(eq(100L), eq(1L), eq(null))).thenReturn(approved);

        mockMvc.perform(post("/api/procurement/material-requests/100/approve")
                        .with(user(adminPrincipal())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
    }

    @Test
    @DisplayName("Field staff is forbidden from approving material requests")
    void approveMaterialRequest_asFieldStaff_forbidden() throws Exception {
        mockMvc.perform(post("/api/procurement/material-requests/100/approve")
                        .with(user(fieldStaffPrincipal())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PROCUREMENT role is forbidden from approving material requests (ADMIN-only, unlike its sibling endpoints)")
    void approveMaterialRequest_asProcurement_forbidden() throws Exception {
        mockMvc.perform(post("/api/procurement/material-requests/100/approve")
                        .with(user(procurementPrincipal())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Admin can reject a submitted material request with a note")
    void rejectMaterialRequest_withNote_success() throws Exception {
        MaterialRequest rejected = MaterialRequest.builder()
                .id(100L)
                .status(MaterialRequestStatus.REJECTED)
                .approvedBy(1L)
                .approvalNote("Not in budget")
                .build();

        when(procurementService.rejectMaterialRequest(eq(100L), eq(1L), eq("Not in budget"))).thenReturn(rejected);

        mockMvc.perform(post("/api/procurement/material-requests/100/reject")
                        .with(user(adminPrincipal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "note": "Not in budget"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Material request rejected."))
                .andExpect(jsonPath("$.data.status").value("REJECTED"));
    }

    @Test
    @DisplayName("Rejecting with a blank note returns 400 Bad Request")
    void rejectMaterialRequest_blankNote_badRequest() throws Exception {
        mockMvc.perform(post("/api/procurement/material-requests/100/reject")
                        .with(user(adminPrincipal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "note": ""
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Field staff is forbidden from rejecting material requests")
    void rejectMaterialRequest_asFieldStaff_forbidden() throws Exception {
        mockMvc.perform(post("/api/procurement/material-requests/100/reject")
                        .with(user(fieldStaffPrincipal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "note": "no"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Admin can generate purchase order from approved material request")
    void generatePurchaseOrder_asAdmin_success() throws Exception {
        PurchaseOrder po = PurchaseOrder.builder()
                .id(50L)
                .mrId(100L)
                .poNumber("PO-100-20260916-170000")
                .supplierName("PT Sumber Jaya Teknik")
                .orderDate(LocalDateTime.now())
                .poStatus(PurchaseOrderStatus.ISSUED)
                .build();

        when(procurementService.generatePurchaseOrder(eq(100L), eq("PT Sumber Jaya Teknik"), any()))
                .thenReturn(po);

        mockMvc.perform(post("/api/procurement/material-requests/100/generate-po")
                        .with(user(adminPrincipal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "supplierName": "PT Sumber Jaya Teknik",
                                    "expectedDeliveryDate": "2026-09-30T10:00:00"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.message").value("Purchase order generated successfully."))
                .andExpect(jsonPath("$.data.id").value(50))
                .andExpect(jsonPath("$.data.poNumber").value("PO-100-20260916-170000"))
                .andExpect(jsonPath("$.data.poStatus").value("ISSUED"));
    }

    @Test
    @DisplayName("Field staff is forbidden from generating purchase orders")
    void generatePurchaseOrder_asFieldStaff_forbidden() throws Exception {
        mockMvc.perform(post("/api/procurement/material-requests/100/generate-po")
                        .with(user(fieldStaffPrincipal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "supplierName": "PT Sumber Jaya Teknik"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Generating PO with blank supplier name returns 400 Bad Request")
    void generatePurchaseOrder_blankSupplier_badRequest() throws Exception {
        mockMvc.perform(post("/api/procurement/material-requests/100/generate-po")
                        .with(user(adminPrincipal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "supplierName": ""
                                }
                                """))
                .andExpect(status().isBadRequest());
    }
}

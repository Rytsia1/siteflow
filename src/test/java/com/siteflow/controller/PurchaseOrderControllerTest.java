package com.siteflow.controller;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.siteflow.domain.PurchaseOrder;
import com.siteflow.domain.enums.PurchaseOrderStatus;
import com.siteflow.security.UserPrincipal;
import com.siteflow.service.ProcurementService;

/**
 * Controller-slice tests for {@code GET /api/procurement/purchase-orders}.
 *
 * <p>Covers:
 * <ul>
 *   <li>Authorization: ADMIN → 200, PROCUREMENT → 200, unauthorized roles → 403, anonymous → 401
 *   <li>Data contract: returned JSON fields match the PurchaseOrderView shape expected by the frontend
 *   <li>Empty collection: returns 200 with an empty array, not 404
 *   <li>Integration flow: newly generated PO appears in the list response
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
class PurchaseOrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProcurementService procurementService;

    // -----------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------

    private UserPrincipal admin() {
        return new UserPrincipal(1L, "admin", "hash", "ADMIN");
    }

    private UserPrincipal procurement() {
        return new UserPrincipal(3L, "proc", "hash", "PROCUREMENT");
    }

    private UserPrincipal fieldStaff() {
        return new UserPrincipal(2L, "pekerja", "hash", "FIELD_STAFF");
    }

    private UserPrincipal warehouseStaff() {
        return new UserPrincipal(4L, "gudang", "hash", "WAREHOUSE_STAFF");
    }

    private PurchaseOrder samplePo() {
        return PurchaseOrder.builder()
                .id(50L)
                .mrId(100L)
                .poNumber("PO-100-20260916-170000")
                .supplierName("PT Sumber Jaya Teknik")
                .orderDate(LocalDateTime.of(2026, 9, 16, 17, 0))
                .expectedDeliveryDate(LocalDateTime.of(2026, 9, 30, 10, 0))
                .poStatus(PurchaseOrderStatus.ISSUED)
                .build();
    }

    // -----------------------------------------------------------------
    // Authorization tests
    // -----------------------------------------------------------------

    @Test
    @DisplayName("ADMIN can list purchase orders → 200 OK")
    void listPurchaseOrders_asAdmin_returns200() throws Exception {
        when(procurementService.listAllPurchaseOrders()).thenReturn(List.of(samplePo()));

        mockMvc.perform(get("/api/procurement/purchase-orders")
                        .with(user(admin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.message").value("Purchase orders retrieved."));
    }

    @Test
    @DisplayName("PROCUREMENT role can list purchase orders → 200 OK")
    void listPurchaseOrders_asProcurement_returns200() throws Exception {
        when(procurementService.listAllPurchaseOrders()).thenReturn(List.of(samplePo()));

        mockMvc.perform(get("/api/procurement/purchase-orders")
                        .with(user(procurement())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));
    }

    @Test
    @DisplayName("FIELD_STAFF is forbidden from listing purchase orders → 403")
    void listPurchaseOrders_asFieldStaff_returns403() throws Exception {
        mockMvc.perform(get("/api/procurement/purchase-orders")
                        .with(user(fieldStaff())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("WAREHOUSE_STAFF is forbidden from listing purchase orders → 403")
    void listPurchaseOrders_asWarehouseStaff_returns403() throws Exception {
        mockMvc.perform(get("/api/procurement/purchase-orders")
                        .with(user(warehouseStaff())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Unauthenticated request returns 401")
    void listPurchaseOrders_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/procurement/purchase-orders"))
                .andExpect(status().isUnauthorized());
    }

    // -----------------------------------------------------------------
    // Data contract tests
    // -----------------------------------------------------------------

    @Test
    @DisplayName("Returned PO JSON matches the frontend-expected field names")
    void listPurchaseOrders_responseFields_matchFrontendContract() throws Exception {
        when(procurementService.listAllPurchaseOrders()).thenReturn(List.of(samplePo()));

        mockMvc.perform(get("/api/procurement/purchase-orders")
                        .with(user(admin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(50))
                .andExpect(jsonPath("$.data[0].poNumber").value("PO-100-20260916-170000"))
                .andExpect(jsonPath("$.data[0].supplierName").value("PT Sumber Jaya Teknik"))
                // Frontend reads row.status — must be present and match PurchaseOrderStatus enum name
                .andExpect(jsonPath("$.data[0].status").value("ISSUED"))
                // Frontend reads row.expectedDeliveryDate
                .andExpect(jsonPath("$.data[0].expectedDeliveryDate").isNotEmpty())
                // mrId carried through for traceability
                .andExpect(jsonPath("$.data[0].mrId").value(100));
    }

    // -----------------------------------------------------------------
    // Empty collection test
    // -----------------------------------------------------------------

    @Test
    @DisplayName("Empty purchase order table returns 200 with empty array, not 404")
    void listPurchaseOrders_emptyTable_returns200WithEmptyArray() throws Exception {
        when(procurementService.listAllPurchaseOrders()).thenReturn(List.of());

        mockMvc.perform(get("/api/procurement/purchase-orders")
                        .with(user(admin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    // -----------------------------------------------------------------
    // Integration flow test (mocked service layer)
    // -----------------------------------------------------------------

    @Test
    @DisplayName("Newly generated PO appears in the list response")
    void listPurchaseOrders_afterGeneration_showsNewPo() throws Exception {
        PurchaseOrder newPo = PurchaseOrder.builder()
                .id(51L)
                .mrId(101L)
                .poNumber("PO-101-20260918-120000")
                .supplierName("CV Mandiri Konstruksi")
                .orderDate(LocalDateTime.now())
                .expectedDeliveryDate(LocalDateTime.of(2026, 10, 5, 8, 0))
                .poStatus(PurchaseOrderStatus.ISSUED)
                .build();

        // After the PO was generated, the list returns both existing and new
        when(procurementService.listAllPurchaseOrders()).thenReturn(List.of(newPo, samplePo()));

        mockMvc.perform(get("/api/procurement/purchase-orders")
                        .with(user(admin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].poNumber").value("PO-101-20260918-120000"))
                .andExpect(jsonPath("$.data[0].supplierName").value("CV Mandiri Konstruksi"))
                .andExpect(jsonPath("$.data[0].status").value("ISSUED"))
                .andExpect(jsonPath("$.data[1].poNumber").value("PO-100-20260916-170000"));
    }

    @Test
    @DisplayName("Multiple purchase orders are all returned in the response")
    void listPurchaseOrders_multiplePOs_allReturned() throws Exception {
        PurchaseOrder po1 = PurchaseOrder.builder()
                .id(1L).mrId(10L).poNumber("PO-10-20260901-080000")
                .supplierName("Supplier A").poStatus(PurchaseOrderStatus.ISSUED).build();
        PurchaseOrder po2 = PurchaseOrder.builder()
                .id(2L).mrId(11L).poNumber("PO-11-20260902-090000")
                .supplierName("Supplier B").poStatus(PurchaseOrderStatus.PARTIAL_RECEIVED).build();
        PurchaseOrder po3 = PurchaseOrder.builder()
                .id(3L).mrId(12L).poNumber("PO-12-20260903-100000")
                .supplierName("Supplier C").poStatus(PurchaseOrderStatus.FULFILLED).build();

        when(procurementService.listAllPurchaseOrders()).thenReturn(List.of(po3, po2, po1));

        mockMvc.perform(get("/api/procurement/purchase-orders")
                        .with(user(admin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].status").value("FULFILLED"))
                .andExpect(jsonPath("$.data[1].status").value("PARTIAL_RECEIVED"))
                .andExpect(jsonPath("$.data[2].status").value("ISSUED"));
    }
}

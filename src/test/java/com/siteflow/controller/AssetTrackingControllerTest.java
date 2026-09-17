package com.siteflow.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.siteflow.domain.ItemInstance;
import com.siteflow.domain.enums.ToolCondition;
import com.siteflow.security.UserPrincipal;
import com.siteflow.service.AssetTrackingService;

@SpringBootTest
@AutoConfigureMockMvc
class AssetTrackingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AssetTrackingService assetTrackingService;

    private UserPrincipal warehousePrincipal() {
        return new UserPrincipal(2L, "gudang", "hash", "WAREHOUSE_STAFF");
    }

    private UserPrincipal adminPrincipal() {
        return new UserPrincipal(1L, "admin", "hash", "ADMIN");
    }

    private UserPrincipal fieldStaffPrincipal() {
        return new UserPrincipal(3L, "pekerja", "hash", "FIELD_STAFF");
    }

    @Test
    @DisplayName("Warehouse staff can check out a tool by serial number")
    void checkout_bySerialNumber_success() throws Exception {
        ItemInstance instance = ItemInstance.builder()
                .id(1L)
                .itemId(100L)
                .serialNumber("SN-DRILL-001")
                .qrCodeValue("QR-DRILL-001")
                .toolCondition(ToolCondition.GOOD)
                .build();

        when(assetTrackingService.checkoutItemInstance(eq("SN-DRILL-001"), eq(5L), any()))
                .thenReturn(instance);

        mockMvc.perform(post("/api/assets/checkout")
                        .with(user(warehousePrincipal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "serialNumber": "SN-DRILL-001",
                                    "borrowRequestId": 5
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.message").value("Asset checked out successfully."))
                .andExpect(jsonPath("$.data.serialNumber").value("SN-DRILL-001"))
                .andExpect(jsonPath("$.data.toolCondition").value("GOOD"));
    }

    @Test
    @DisplayName("Admin can check out a tool by QR code value")
    void checkout_byQrCode_success() throws Exception {
        ItemInstance instance = ItemInstance.builder()
                .id(2L)
                .itemId(101L)
                .serialNumber("SN-GRINDER-002")
                .qrCodeValue("QR-GRINDER-002")
                .toolCondition(ToolCondition.GOOD)
                .build();

        when(assetTrackingService.checkoutItemInstance(eq("QR-GRINDER-002"), eq(5L), any()))
                .thenReturn(instance);

        mockMvc.perform(post("/api/assets/checkout")
                        .with(user(adminPrincipal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "qrCodeValue": "QR-GRINDER-002",
                                    "borrowRequestId": 5
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.qrCodeValue").value("QR-GRINDER-002"));
    }

    @Test
    @DisplayName("Checkout fails when neither serial number nor QR code is provided")
    void checkout_missingIdentifier_badRequest() throws Exception {
        mockMvc.perform(post("/api/assets/checkout")
                        .with(user(warehousePrincipal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "borrowRequestId": 5
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Field staff is forbidden from checking out tools directly")
    void checkout_asFieldStaff_forbidden() throws Exception {
        mockMvc.perform(post("/api/assets/checkout")
                        .with(user(fieldStaffPrincipal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "serialNumber": "SN-001",
                                    "borrowRequestId": 1
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Warehouse staff can process tool return with updated condition")
    void processReturn_success() throws Exception {
        ItemInstance returned = ItemInstance.builder()
                .id(1L)
                .itemId(100L)
                .serialNumber("SN-DRILL-001")
                .qrCodeValue("QR-DRILL-001")
                .toolCondition(ToolCondition.NEEDS_REPAIR)
                .build();

        when(assetTrackingService.returnItemInstance(eq("SN-DRILL-001"), eq(ToolCondition.NEEDS_REPAIR), any()))
                .thenReturn(returned);

        mockMvc.perform(post("/api/assets/return")
                        .with(user(warehousePrincipal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "serialNumber": "SN-DRILL-001",
                                    "toolCondition": "NEEDS_REPAIR"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.message").value("Asset return processed successfully."))
                .andExpect(jsonPath("$.data.toolCondition").value("NEEDS_REPAIR"));
    }

    @Test
    @DisplayName("Field staff is forbidden from processing returns")
    void processReturn_asFieldStaff_forbidden() throws Exception {
        mockMvc.perform(post("/api/assets/return")
                        .with(user(fieldStaffPrincipal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "serialNumber": "SN-DRILL-001",
                                    "toolCondition": "GOOD"
                                }
                                """))
                .andExpect(status().isForbidden());
    }
}

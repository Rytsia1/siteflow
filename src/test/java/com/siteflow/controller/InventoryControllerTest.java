package com.siteflow.controller;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.siteflow.domain.enums.ItemCategory;
import com.siteflow.security.UserPrincipal;
import com.siteflow.service.InventoryService;
import com.siteflow.web.dto.ItemStockView;
import com.siteflow.web.dto.ItemSummaryView;

@SpringBootTest
@AutoConfigureMockMvc
class InventoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InventoryService inventoryService;

    private UserPrincipal adminPrincipal() {
        return new UserPrincipal(1L, "admin", "hash", "ADMIN");
    }

    private UserPrincipal warehousePrincipal() {
        return new UserPrincipal(2L, "keeper", "hash", "WAREHOUSE_STAFF");
    }

    private UserPrincipal fieldStaffPrincipal() {
        return new UserPrincipal(3L, "worker", "hash", "FIELD_STAFF");
    }

    private UserPrincipal procurementPrincipal() {
        return new UserPrincipal(4L, "buyer", "hash", "PROCUREMENT");
    }

    @Test
    @DisplayName("Admin can list items with aggregate stock")
    void listItems_asAdmin_success() throws Exception {
        ItemSummaryView item = new ItemSummaryView(1L, "DRL-001", "Drill", ItemCategory.TOOL, "units", 5, 2);
        when(inventoryService.listItems()).thenReturn(List.of(item));

        mockMvc.perform(get("/api/items")
                        .with(user(adminPrincipal())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data[0].itemCode").value("DRL-001"));
    }

    @Test
    @DisplayName("Field staff can view item list")
    void listItems_asFieldStaff_success() throws Exception {
        when(inventoryService.listItems()).thenReturn(List.of());

        mockMvc.perform(get("/api/items")
                        .with(user(fieldStaffPrincipal())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));
    }

    @Test
    @DisplayName("Procurement role can view item list")
    void listItems_asProcurement_success() throws Exception {
        when(inventoryService.listItems()).thenReturn(List.of());

        mockMvc.perform(get("/api/items")
                        .with(user(procurementPrincipal())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));
    }

    @Test
    @DisplayName("Warehouse staff can view detailed stock breakdown per location")
    void getStockByItem_asWarehouseStaff_success() throws Exception {
        ItemStockView stock = new ItemStockView(10L, "Main Warehouse", 8);
        when(inventoryService.getStockByItem(1L)).thenReturn(List.of(stock));

        mockMvc.perform(get("/api/items/1/stocks")
                        .with(user(warehousePrincipal())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data[0].locationName").value("Main Warehouse"));
    }

    @Test
    @DisplayName("Field staff is forbidden from viewing detailed stock breakdown per location")
    void getStockByItem_asFieldStaff_forbidden() throws Exception {
        mockMvc.perform(get("/api/items/1/stocks")
                        .with(user(fieldStaffPrincipal())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Procurement role is forbidden from viewing detailed stock breakdown per location")
    void getStockByItem_asProcurement_forbidden() throws Exception {
        mockMvc.perform(get("/api/items/1/stocks")
                        .with(user(procurementPrincipal())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Unauthenticated request to items returns 401")
    void listItems_unauthenticated_unauthorized() throws Exception {
        mockMvc.perform(get("/api/items"))
                .andExpect(status().isUnauthorized());
    }
}

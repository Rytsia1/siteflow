package com.siteflow.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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

import com.siteflow.domain.StockAdjustment;
import com.siteflow.domain.enums.AdjustmentType;
import com.siteflow.security.UserPrincipal;
import com.siteflow.service.StockAdjustmentService;

@SpringBootTest
@AutoConfigureMockMvc
class StockAdjustmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StockAdjustmentService stockAdjustmentService;

    private UserPrincipal adminPrincipal() {
        return new UserPrincipal(1L, "admin", "hash", "ADMIN");
    }

    private UserPrincipal warehousePrincipal() {
        return new UserPrincipal(2L, "gudang", "hash", "WAREHOUSE_STAFF");
    }

    private UserPrincipal fieldStaffPrincipal() {
        return new UserPrincipal(3L, "pekerja", "hash", "FIELD_STAFF");
    }

    @Test
    @DisplayName("Admin can record a stock adjustment")
    void createAdjustment_asAdmin_success() throws Exception {
        StockAdjustment created = StockAdjustment.builder()
                .id(1L)
                .itemId(1L)
                .locationId(10L)
                .adjustedBy(1L)
                .adjustmentType(AdjustmentType.IN)
                .qty(5)
                .reason("cycle count")
                .createdAt(LocalDateTime.now())
                .build();

        when(stockAdjustmentService.createAdjustment(eq(1L), eq(10L), eq(AdjustmentType.IN), eq(5),
                eq("cycle count"), eq(1L))).thenReturn(created);

        mockMvc.perform(post("/api/stock-adjustments")
                        .with(user(adminPrincipal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "itemId": 1,
                                    "locationId": 10,
                                    "adjustmentType": "IN",
                                    "qty": 5,
                                    "reason": "cycle count"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.message").value("Stock adjustment recorded."))
                .andExpect(jsonPath("$.data.adjustmentType").value("IN"))
                .andExpect(jsonPath("$.data.qty").value(5));
    }

    @Test
    @DisplayName("Warehouse staff can record a stock adjustment")
    void createAdjustment_asWarehouseStaff_success() throws Exception {
        StockAdjustment created = StockAdjustment.builder()
                .id(2L)
                .itemId(1L)
                .locationId(10L)
                .adjustedBy(2L)
                .adjustmentType(AdjustmentType.OUT)
                .qty(2)
                .build();

        when(stockAdjustmentService.createAdjustment(any(), any(), any(), anyInt(), any(), eq(2L)))
                .thenReturn(created);

        mockMvc.perform(post("/api/stock-adjustments")
                        .with(user(warehousePrincipal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "itemId": 1,
                                    "locationId": 10,
                                    "adjustmentType": "OUT",
                                    "qty": 2
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.adjustmentType").value("OUT"));
    }

    @Test
    @DisplayName("Field staff is forbidden from recording stock adjustments")
    void createAdjustment_asFieldStaff_forbidden() throws Exception {
        mockMvc.perform(post("/api/stock-adjustments")
                        .with(user(fieldStaffPrincipal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "itemId": 1,
                                    "locationId": 10,
                                    "adjustmentType": "IN",
                                    "qty": 5
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("A non-positive quantity returns 400 Bad Request")
    void createAdjustment_nonPositiveQty_badRequest() throws Exception {
        mockMvc.perform(post("/api/stock-adjustments")
                        .with(user(adminPrincipal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "itemId": 1,
                                    "locationId": 10,
                                    "adjustmentType": "IN",
                                    "qty": 0
                                }
                                """))
                .andExpect(status().isBadRequest());
    }
}

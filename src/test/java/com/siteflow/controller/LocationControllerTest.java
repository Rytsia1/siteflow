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

import com.siteflow.domain.Location;
import com.siteflow.security.UserPrincipal;
import com.siteflow.service.InventoryService;

@SpringBootTest
@AutoConfigureMockMvc
class LocationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InventoryService inventoryService;

    private UserPrincipal adminPrincipal() {
        return new UserPrincipal(1L, "admin", "hash", "ADMIN");
    }

    private UserPrincipal fieldStaffPrincipal() {
        return new UserPrincipal(2L, "worker", "hash", "FIELD_STAFF");
    }

    private UserPrincipal procurementPrincipal() {
        return new UserPrincipal(3L, "buyer", "hash", "PROCUREMENT");
    }

    @Test
    @DisplayName("Admin can retrieve list of locations")
    void listLocations_asAdmin_success() throws Exception {
        Location loc = Location.builder().id(1L).locationName("Central Depot").build();
        when(inventoryService.listLocations()).thenReturn(List.of(loc));

        mockMvc.perform(get("/api/locations")
                        .with(user(adminPrincipal())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data[0].locationName").value("Central Depot"));
    }

    @Test
    @DisplayName("Field staff can retrieve list of locations")
    void listLocations_asFieldStaff_success() throws Exception {
        when(inventoryService.listLocations()).thenReturn(List.of());

        mockMvc.perform(get("/api/locations")
                        .with(user(fieldStaffPrincipal())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));
    }

    @Test
    @DisplayName("Procurement role is forbidden from retrieving locations")
    void listLocations_asProcurement_forbidden() throws Exception {
        mockMvc.perform(get("/api/locations")
                        .with(user(procurementPrincipal())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Unauthenticated request to locations returns 401")
    void listLocations_unauthenticated_unauthorized() throws Exception {
        mockMvc.perform(get("/api/locations"))
                .andExpect(status().isUnauthorized());
    }
}

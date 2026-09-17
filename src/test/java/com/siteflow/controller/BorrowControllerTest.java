package com.siteflow.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
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

import com.siteflow.domain.BorrowRequest;
import com.siteflow.domain.enums.ApprovalStatus;
import com.siteflow.domain.enums.BorrowStatus;
import com.siteflow.security.UserPrincipal;
import com.siteflow.service.BorrowService;

@SpringBootTest
@AutoConfigureMockMvc
class BorrowControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BorrowService borrowService;

    private UserPrincipal adminPrincipal() {
        return new UserPrincipal(1L, "admin", "hash", "ADMIN");
    }

    private UserPrincipal fieldStaffPrincipal() {
        return new UserPrincipal(2L, "field_worker", "hash", "FIELD_STAFF");
    }

    private UserPrincipal warehouseStaffPrincipal() {
        return new UserPrincipal(3L, "warehouse_worker", "hash", "WAREHOUSE_STAFF");
    }

    private UserPrincipal procurementPrincipal() {
        return new UserPrincipal(4L, "buyer", "hash", "PROCUREMENT");
    }

    @Test
    @DisplayName("Field staff can create a borrow request")
    void createBorrowRequest_asFieldStaff_success() throws Exception {
        BorrowRequest created = BorrowRequest.builder()
                .id(10L)
                .userId(2L)
                .locationId(1L)
                .requestDate(LocalDateTime.now())
                .status(BorrowStatus.PENDING)
                .approvalStatus(ApprovalStatus.PENDING_APPROVAL)
                .build();

        when(borrowService.createBorrowRequest(eq(2L), eq(1L), any())).thenReturn(created);

        mockMvc.perform(post("/api/borrow-requests")
                        .with(user(fieldStaffPrincipal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "locationId": 1,
                                    "items": [
                                        {"itemId": 5, "qty": 2}
                                    ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.message").value("Borrow request created."))
                .andExpect(jsonPath("$.data.id").value(10))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    @DisplayName("Admin can also create a borrow request")
    void createBorrowRequest_asAdmin_success() throws Exception {
        BorrowRequest created = BorrowRequest.builder()
                .id(11L)
                .userId(1L)
                .locationId(1L)
                .requestDate(LocalDateTime.now())
                .status(BorrowStatus.PENDING)
                .approvalStatus(ApprovalStatus.PENDING_APPROVAL)
                .build();

        when(borrowService.createBorrowRequest(eq(1L), eq(1L), any())).thenReturn(created);

        mockMvc.perform(post("/api/borrow-requests")
                        .with(user(adminPrincipal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "locationId": 1,
                                    "items": [
                                        {"itemId": 5, "qty": 1}
                                    ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.id").value(11));
    }

    @Test
    @DisplayName("Warehouse staff is forbidden from creating borrow requests")
    void createBorrowRequest_asWarehouseStaff_forbidden() throws Exception {
        mockMvc.perform(post("/api/borrow-requests")
                        .with(user(warehouseStaffPrincipal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "locationId": 1,
                                    "items": [{"itemId": 5, "qty": 1}]
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Procurement role is forbidden from creating borrow requests")
    void createBorrowRequest_asProcurement_forbidden() throws Exception {
        mockMvc.perform(post("/api/borrow-requests")
                        .with(user(procurementPrincipal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "locationId": 1,
                                    "items": [{"itemId": 5, "qty": 1}]
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Unauthenticated request to create borrow request returns 401")
    void createBorrowRequest_unauthenticated_unauthorized() throws Exception {
        mockMvc.perform(post("/api/borrow-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "locationId": 1,
                                    "items": [{"itemId": 5, "qty": 1}]
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Creating borrow request with empty items returns 400 Bad Request")
    void createBorrowRequest_emptyItems_badRequest() throws Exception {
        mockMvc.perform(post("/api/borrow-requests")
                        .with(user(fieldStaffPrincipal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "locationId": 1,
                                    "items": []
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Validation failed."));
    }

    @Test
    @DisplayName("Creating borrow request with negative or zero quantity returns 400 Bad Request")
    void createBorrowRequest_zeroQty_badRequest() throws Exception {
        mockMvc.perform(post("/api/borrow-requests")
                        .with(user(fieldStaffPrincipal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "locationId": 1,
                                    "items": [{"itemId": 5, "qty": 0}]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"));
    }

    @Test
    @DisplayName("Warehouse staff can process returns for a request")
    void processReturns_asWarehouseStaff_success() throws Exception {
        mockMvc.perform(post("/api/borrow-requests/10/returns")
                        .with(user(warehouseStaffPrincipal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "items": [
                                        {"borrowItemId": 101, "qty": 2}
                                    ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.message").value("Return processed."));

        verify(borrowService).processReturnsForRequest(eq(10L), any(), eq(3L));
    }

    @Test
    @DisplayName("Field staff can process returns for a request")
    void processReturns_asFieldStaff_success() throws Exception {
        mockMvc.perform(post("/api/borrow-requests/10/returns")
                        .with(user(fieldStaffPrincipal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "items": [
                                        {"borrowItemId": 101, "qty": 1}
                                    ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));

        verify(borrowService).processReturnsForRequest(eq(10L), any(), eq(2L));
    }

    @Test
    @DisplayName("Procurement role is forbidden from processing returns")
    void processReturns_asProcurement_forbidden() throws Exception {
        mockMvc.perform(post("/api/borrow-requests/10/returns")
                        .with(user(procurementPrincipal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "items": [{"borrowItemId": 101, "qty": 1}]
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Processing return with empty items list returns 400 Bad Request")
    void processReturns_emptyItems_badRequest() throws Exception {
        mockMvc.perform(post("/api/borrow-requests/10/returns")
                        .with(user(warehouseStaffPrincipal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "items": []
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"));
    }
}

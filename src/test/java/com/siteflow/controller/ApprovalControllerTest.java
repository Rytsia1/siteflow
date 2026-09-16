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

import com.siteflow.domain.BorrowRequest;
import com.siteflow.domain.enums.ApprovalStatus;
import com.siteflow.domain.enums.BorrowStatus;
import com.siteflow.security.UserPrincipal;
import com.siteflow.service.ApprovalService;

@SpringBootTest
@AutoConfigureMockMvc
class ApprovalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ApprovalService approvalService;

    private UserPrincipal adminPrincipal() {
        return new UserPrincipal(1L, "admin", "hash", "ADMIN");
    }

    private UserPrincipal fieldStaffPrincipal() {
        return new UserPrincipal(2L, "pekerja", "hash", "FIELD_STAFF");
    }

    @Test
    @DisplayName("Admin can approve a borrow request with optional note")
    void approveBorrowRequest_asAdmin_success() throws Exception {
        BorrowRequest approved = BorrowRequest.builder()
                .id(10L)
                .userId(2L)
                .locationId(1L)
                .requestDate(LocalDateTime.now())
                .status(BorrowStatus.BORROWED)
                .approvalStatus(ApprovalStatus.APPROVED)
                .approvedBy(1L)
                .approvalNote("Approved for field operations")
                .build();

        when(approvalService.approveBorrowRequest(eq(10L), eq(1L), eq("Approved for field operations")))
                .thenReturn(approved);

        mockMvc.perform(post("/api/approvals/borrow-requests/10/approve")
                        .with(user(adminPrincipal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"Approved for field operations\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.message").value("Borrow request approved."))
                .andExpect(jsonPath("$.data.id").value(10))
                .andExpect(jsonPath("$.data.approvalStatus").value("APPROVED"));
    }

    @Test
    @DisplayName("Admin can approve without request body")
    void approveBorrowRequest_withoutBody_success() throws Exception {
        BorrowRequest approved = BorrowRequest.builder()
                .id(10L)
                .userId(2L)
                .locationId(1L)
                .requestDate(LocalDateTime.now())
                .status(BorrowStatus.BORROWED)
                .approvalStatus(ApprovalStatus.APPROVED)
                .approvedBy(1L)
                .build();

        when(approvalService.approveBorrowRequest(eq(10L), eq(1L), any()))
                .thenReturn(approved);

        mockMvc.perform(post("/api/approvals/borrow-requests/10/approve")
                        .with(user(adminPrincipal())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.approvalStatus").value("APPROVED"));
    }

    @Test
    @DisplayName("Non-admin user is forbidden from approving borrow requests")
    void approveBorrowRequest_asFieldStaff_forbidden() throws Exception {
        mockMvc.perform(post("/api/approvals/borrow-requests/10/approve")
                        .with(user(fieldStaffPrincipal())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Admin can reject a borrow request with a note")
    void rejectBorrowRequest_withNote_success() throws Exception {
        BorrowRequest rejected = BorrowRequest.builder()
                .id(10L)
                .userId(2L)
                .locationId(1L)
                .requestDate(LocalDateTime.now())
                .status(BorrowStatus.BORROWED)
                .approvalStatus(ApprovalStatus.REJECTED)
                .approvedBy(1L)
                .approvalNote("Insufficient reason provided")
                .build();

        when(approvalService.rejectBorrowRequest(eq(10L), eq(1L), eq("Insufficient reason provided")))
                .thenReturn(rejected);

        mockMvc.perform(post("/api/approvals/borrow-requests/10/reject")
                        .with(user(adminPrincipal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"Insufficient reason provided\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.message").value("Borrow request rejected."))
                .andExpect(jsonPath("$.data.approvalStatus").value("REJECTED"));
    }

    @Test
    @DisplayName("Admin can reject a borrow request without a note")
    void rejectBorrowRequest_withoutNote_success() throws Exception {
        BorrowRequest rejected = BorrowRequest.builder()
                .id(10L)
                .userId(2L)
                .locationId(1L)
                .requestDate(LocalDateTime.now())
                .status(BorrowStatus.BORROWED)
                .approvalStatus(ApprovalStatus.REJECTED)
                .approvedBy(1L)
                .build();

        when(approvalService.rejectBorrowRequest(eq(10L), eq(1L), any()))
                .thenReturn(rejected);

        mockMvc.perform(post("/api/approvals/borrow-requests/10/reject")
                        .with(user(adminPrincipal())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.approvalStatus").value("REJECTED"));
    }

    @Test
    @DisplayName("Non-admin user is forbidden from rejecting borrow requests")
    void rejectBorrowRequest_asFieldStaff_forbidden() throws Exception {
        mockMvc.perform(post("/api/approvals/borrow-requests/10/reject")
                        .with(user(fieldStaffPrincipal())))
                .andExpect(status().isForbidden());
    }
}

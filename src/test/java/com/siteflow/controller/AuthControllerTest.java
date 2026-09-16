package com.siteflow.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import com.siteflow.security.UserPrincipal;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Returns the authenticated user's id, username, and role")
    void me_returnsCurrentUser() throws Exception {
        UserPrincipal principal = new UserPrincipal(7L, "gudang", "hash", "WAREHOUSE_STAFF");

        mockMvc.perform(get("/api/auth/me").with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.userId").value(7))
                .andExpect(jsonPath("$.data.username").value("gudang"))
                .andExpect(jsonPath("$.data.role").value("WAREHOUSE_STAFF"));
    }
}

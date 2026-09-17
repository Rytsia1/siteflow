package com.siteflow.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.siteflow.security.UserPrincipal;

@SpringBootTest
@AutoConfigureMockMvc
class ErrorHandlingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private UserPrincipal adminPrincipal() {
        return new UserPrincipal(1L, "admin", "hash", "ADMIN");
    }

    private UserPrincipal fieldStaffPrincipal() {
        return new UserPrincipal(2L, "pekerja", "hash", "FIELD_STAFF");
    }

    @Test
    @DisplayName("Unauthenticated request returns 401 with standard ApiResponse envelope")
    void unauthenticated_returns401StandardEnvelope() throws Exception {
        mockMvc.perform(get("/api/items"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Authentication required."))
                .andExpect(jsonPath("$.data.status").value(401))
                .andExpect(jsonPath("$.data.error").value("Unauthorized"))
                .andExpect(jsonPath("$.data.path").value("/api/items"))
                .andExpect(jsonPath("$.data.timestamp", notNullValue()));
    }

    @Test
    @DisplayName("Unauthorized role returns 403 with standard ApiResponse envelope")
    void forbiddenRole_returns403StandardEnvelope() throws Exception {
        mockMvc.perform(get("/api/analytics/summary")
                        .with(user(fieldStaffPrincipal())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Access denied."))
                .andExpect(jsonPath("$.data.status").value(403))
                .andExpect(jsonPath("$.data.error").value("Forbidden"))
                .andExpect(jsonPath("$.data.path").value("/api/analytics/summary"))
                .andExpect(jsonPath("$.data.timestamp", notNullValue()));
    }

    @Test
    @DisplayName("Nonexistent endpoint returns 404 with standard ApiResponse envelope")
    void nonexistentEndpoint_returns404StandardEnvelope() throws Exception {
        mockMvc.perform(get("/api/nonexistent-endpoint-xyz")
                        .with(user(adminPrincipal())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("The requested endpoint does not exist."))
                .andExpect(jsonPath("$.data.status").value(404))
                .andExpect(jsonPath("$.data.error").value("Not Found"))
                .andExpect(jsonPath("$.data.path").value("/api/nonexistent-endpoint-xyz"))
                .andExpect(jsonPath("$.data.timestamp", notNullValue()));
    }

    @Test
    @DisplayName("Unsupported HTTP method returns 405 with standard ApiResponse envelope")
    void unsupportedMethod_returns405StandardEnvelope() throws Exception {
        mockMvc.perform(delete("/api/items")
                        .with(user(adminPrincipal())))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("HTTP method not supported for this endpoint."))
                .andExpect(jsonPath("$.data.status").value(405))
                .andExpect(jsonPath("$.data.error").value("Method Not Allowed"));
    }

    @Test
    @DisplayName("Unsupported media type returns 415 with standard ApiResponse envelope")
    void unsupportedMediaType_returns415StandardEnvelope() throws Exception {
        mockMvc.perform(post("/api/stock-adjustments")
                        .with(user(adminPrincipal()))
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("raw text body"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Unsupported media type."))
                .andExpect(jsonPath("$.data.status").value(415))
                .andExpect(jsonPath("$.data.error").value("Unsupported Media Type"));
    }

    @Test
    @DisplayName("Malformed JSON body returns 400 with standard ApiResponse envelope")
    void malformedJson_returns400StandardEnvelope() throws Exception {
        mockMvc.perform(post("/api/borrow-requests")
                        .with(user(adminPrincipal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid json body"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Malformed request body."))
                .andExpect(jsonPath("$.data.status").value(400))
                .andExpect(jsonPath("$.data.error").value("Bad Request"));
    }

    @Test
    @DisplayName("Method argument type mismatch returns 400 with parameter name")
    void typeMismatch_returns400WithParamName() throws Exception {
        mockMvc.perform(get("/api/analytics/forecast/not-a-number")
                        .with(user(adminPrincipal())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message", containsString("itemId")))
                .andExpect(jsonPath("$.data.status").value(400));
    }

    @Test
    @DisplayName("Missing query parameter returns 400 with missing parameter name")
    void missingParameter_returns400WithParamName() throws Exception {
        mockMvc.perform(get("/api/analytics/trends")
                        .with(user(adminPrincipal())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message", containsString("startDate")))
                .andExpect(jsonPath("$.data.status").value(400));
    }

    @Test
    @DisplayName("Bean validation failure returns 400 with structured fieldErrors map")
    void validationError_returns400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/procurement/material-requests")
                        .with(user(adminPrincipal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "justification": "test",
                                    "items": []
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Validation failed."))
                .andExpect(jsonPath("$.data.status").value(400))
                .andExpect(jsonPath("$.data.fieldErrors.items", notNullValue()));
    }
}

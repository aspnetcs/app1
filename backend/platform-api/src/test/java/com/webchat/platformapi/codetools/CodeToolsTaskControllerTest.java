package com.webchat.platformapi.codetools;

import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.common.api.ErrorCodes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CodeToolsTaskControllerTest {

    @Mock
    private CodeToolsTaskService service;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new CodeToolsTaskController(service)).build();
    }

    @Test
    void createRejectsUnauthenticatedRequests() throws Exception {
        mockMvc.perform(post("/api/v1/code-tools/tasks").contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.UNAUTHORIZED));
    }

    @Test
    void createCallsServiceWithParsedPayload() throws Exception {
        UUID userId = UUID.randomUUID();
        when(service.createTask(eq(userId), eq("shell"), eq(Map.of("cmd", "echo 1"))))
                .thenReturn(Map.of("id", UUID.randomUUID().toString(), "status", "pending"));

        mockMvc.perform(
                        post("/api/v1/code-tools/tasks")
                                .requestAttr(JwtAuthFilter.ATTR_USER_ID, userId)
                                .contentType(APPLICATION_JSON)
                                .content("{\"kind\":\"shell\",\"input\":{\"cmd\":\"echo 1\"}}")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("pending"));

        verify(service).createTask(userId, "shell", Map.of("cmd", "echo 1"));
    }

    @Test
    void listRejectsUnauthenticatedRequests() throws Exception {
        mockMvc.perform(get("/api/v1/code-tools/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.UNAUTHORIZED));
    }
}


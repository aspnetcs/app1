package com.webchat.platformapi.infra.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.webchat.platformapi.common.api.ErrorCodes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class JobV1ControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        JobV1Controller controller = new JobV1Controller(jdbcTemplate, new ObjectMapper());
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void enqueueUsesExactRouteAndRejectsUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/v1/job/enqueue").contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.UNAUTHORIZED));
    }

    @Test
    void getUsesExactRouteAndRejectsUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/job/{jobId}", UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.UNAUTHORIZED));
    }

    @Test
    void legacyJobStatusRouteIsNotMapped() throws Exception {
        mockMvc.perform(get("/api/v1/job/status/{jobId}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }
}

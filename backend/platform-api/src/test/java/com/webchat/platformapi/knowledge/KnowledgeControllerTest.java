package com.webchat.platformapi.knowledge;

import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.common.api.ErrorCodes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class KnowledgeControllerTest {

    @Mock private KnowledgeService knowledgeService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new KnowledgeController(knowledgeService)).build();
    }

    @Test
    void searchReturnsCitationPayload() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID baseId = UUID.randomUUID();
        when(knowledgeService.search(eq(userId), eq(baseId), any())).thenReturn(Map.of(
                "items", List.of(Map.of("index", 1, "title", "Doc", "content", "Chunk")),
                "citationBlock", Map.of("type", "citation")
        ));

        mockMvc.perform(post("/api/v1/knowledge/bases/{baseId}/search", baseId)
                        .contentType("application/json")
                        .content("{\"query\":\"hello\"}")
                        .requestAttr(JwtAuthFilter.ATTR_USER_ID, userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.citationBlock.type").value("citation"))
                .andExpect(jsonPath("$.data.items[0].title").value("Doc"));
    }

    @Test
    void createBaseRejectsUnauthenticatedRequests() throws Exception {
        mockMvc.perform(post("/api/v1/knowledge/bases")
                        .contentType("application/json")
                        .content("{\"name\":\"Docs\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.UNAUTHORIZED));
    }
}

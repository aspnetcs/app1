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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class KnowledgeBindingCompatControllerTest {

    @Mock
    private KnowledgeService knowledgeService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new KnowledgeBindingCompatController(knowledgeService)).build();
    }

    @Test
    void getBindingRejectsAnonymousUser() throws Exception {
        mockMvc.perform(get("/api/v1/knowledge/conversations/{conversationId}", UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.UNAUTHORIZED));
    }

    @Test
    void getBindingWrapsBindingListIntoCompatPayload() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        when(knowledgeService.listBindings(eq(userId), eq(conversationId))).thenReturn(List.of(
                Map.of("baseId", "kb-1", "name", "Docs")
        ));

        mockMvc.perform(get("/api/v1/knowledge/conversations/{conversationId}", conversationId)
                        .requestAttr(JwtAuthFilter.ATTR_USER_ID, userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.conversationId").value(conversationId.toString()))
                .andExpect(jsonPath("$.data.knowledgeBaseIds[0]").value("kb-1"));
    }

    @Test
    void updateBindingAddsAndRemovesDifferences() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID currentId = UUID.randomUUID();
        UUID nextId = UUID.randomUUID();
        when(knowledgeService.listBindings(eq(userId), eq(conversationId)))
                .thenReturn(List.of(Map.of("baseId", currentId.toString())))
                .thenReturn(List.of(Map.of("baseId", nextId.toString())));

        mockMvc.perform(put("/api/v1/knowledge/conversations/{conversationId}", conversationId)
                        .requestAttr(JwtAuthFilter.ATTR_USER_ID, userId)
                        .contentType("application/json")
                        .content("""
                                {
                                  "knowledgeBaseIds": ["%s"]
                                }
                                """.formatted(nextId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.knowledgeBaseIds[0]").value(nextId.toString()));

        verify(knowledgeService).bindConversation(userId, currentId, conversationId, false);
        verify(knowledgeService).bindConversation(userId, nextId, conversationId, true);
    }
}

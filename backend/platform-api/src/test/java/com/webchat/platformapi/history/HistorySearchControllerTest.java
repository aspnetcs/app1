package com.webchat.platformapi.history;

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

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class HistorySearchControllerTest {

    @Mock
    private HistorySearchService historySearchService;

    private MockMvc mockMvc;
    private UUID userId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new HistorySearchController(historySearchService)).build();
        userId = UUID.randomUUID();
    }

    @Test
    void historyRoutesRejectUnauthenticatedRequests() throws Exception {
        mockMvc.perform(get("/api/v1/history/topics").param("keyword", "road"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.UNAUTHORIZED));

        mockMvc.perform(get("/api/v1/history/messages").param("keyword", "road"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.UNAUTHORIZED));

        mockMvc.perform(get("/api/v1/history/files").param("keyword", "road"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.UNAUTHORIZED));
    }

    @Test
    void topicsRouteReturnsPagedSearchResults() throws Exception {
        when(historySearchService.searchTopics(userId, "road", 0, 20)).thenReturn(Map.of(
                "items", List.of(Map.of(
                        "conversationId", "conv-1",
                        "title", "Roadmap",
                        "snippet", "Roadmap"
                )),
                "total", 1,
                "page", 0,
                "size", 20
        ));

        mockMvc.perform(
                        get("/api/v1/history/topics")
                                .param("keyword", " road ")
                                .requestAttr(JwtAuthFilter.ATTR_USER_ID, userId)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.items[0].title").value("Roadmap"));

        verify(historySearchService).searchTopics(userId, "road", 0, 20);
    }

    @Test
    void messagesRoutePassesTopicFilter() throws Exception {
        UUID topicId = UUID.randomUUID();
        when(historySearchService.searchMessages(userId, "hello", topicId, 1, 10)).thenReturn(Map.of(
                "items", List.of(Map.of(
                        "conversationId", topicId.toString(),
                        "messageId", "msg-1",
                        "snippet", "hello world"
                )),
                "total", 1,
                "page", 1,
                "size", 10
        ));

        mockMvc.perform(
                        get("/api/v1/history/messages")
                                .param("keyword", "hello")
                                .param("topicId", topicId.toString())
                                .param("page", "1")
                                .param("size", "10")
                                .requestAttr(JwtAuthFilter.ATTR_USER_ID, userId)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.items[0].messageId").value("msg-1"));

        verify(historySearchService).searchMessages(userId, "hello", topicId, 1, 10);
    }

    @Test
    void filesRouteRejectsInvalidTopicId() throws Exception {
        mockMvc.perform(
                        get("/api/v1/history/files")
                                .param("keyword", "invoice")
                                .param("topicId", "bad-uuid")
                                .requestAttr(JwtAuthFilter.ATTR_USER_ID, userId)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.PARAM_MISSING))
                .andExpect(jsonPath("$.message").value("topicId is invalid"));
    }
}
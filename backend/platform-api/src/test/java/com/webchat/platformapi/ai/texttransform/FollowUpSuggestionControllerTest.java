package com.webchat.platformapi.ai.texttransform;

import com.webchat.platformapi.audit.AuditService;
import com.webchat.platformapi.auth.group.UserGroupService;
import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.common.api.ErrorCodes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class FollowUpSuggestionControllerTest {

    @Mock
    private FollowUpSuggestionService service;

    @Mock
    private AuditService auditService;

    @Mock
    private UserGroupService userGroupService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        FollowUpSuggestionController controller = new FollowUpSuggestionController(
                service,
                auditService,
                userGroupService,
                false
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void suggestUsesFollowUpSuggestionsRouteAndReturnsSuggestions() throws Exception {
        UUID userId = UUID.randomUUID();
        when(service.suggest(userId, "gpt-4o", "Need next steps")).thenReturn(
                FollowUpSuggestionService.SuggestionResult.ok(List.of("Ask scope", "Ask outcome"))
        );

        mockMvc.perform(
                        post("/api/v1/follow-up/suggestions")
                                .requestAttr(JwtAuthFilter.ATTR_USER_ID, userId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "context": "Need next steps",
                                          "model": "gpt-4o"
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.suggestions[0]").value("Ask scope"))
                .andExpect(jsonPath("$.data.suggestions[1]").value("Ask outcome"));

        verify(service).suggest(userId, "gpt-4o", "Need next steps");
        verify(auditService).log(eq(userId), eq("followup.suggest"), org.mockito.ArgumentMatchers.anyMap(), eq("127.0.0.1"), isNull());
    }

    @Test
    void suggestRejectsUnauthenticatedRequests() throws Exception {
        mockMvc.perform(
                        post("/api/v1/follow-up/suggestions")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "context": "Need next steps"
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.UNAUTHORIZED));
    }

    @Test
    void suggestRejectsMissingBody() throws Exception {
        mockMvc.perform(
                        post("/api/v1/follow-up/suggestions")
                                .requestAttr(JwtAuthFilter.ATTR_USER_ID, UUID.randomUUID())
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.PARAM_MISSING))
                .andExpect(jsonPath("$.message").value("context is required"));
    }

    @Test
    void legacyFollowUpSuggestRouteIsNotMapped() throws Exception {
        mockMvc.perform(
                        post("/api/v1/follow-up/suggest")
                                .requestAttr(JwtAuthFilter.ATTR_USER_ID, UUID.randomUUID())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"context\":\"Need next steps\"}")
                )
                .andExpect(status().isNotFound());
    }
}

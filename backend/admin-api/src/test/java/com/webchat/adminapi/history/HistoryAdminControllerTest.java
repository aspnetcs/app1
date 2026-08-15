package com.webchat.adminapi.history;

import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.common.api.ErrorCodes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
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
class HistoryAdminControllerTest {

    @Mock
    private HistoryAdminService historyAdminService;

    private MockMvc mockMvc;
    private UUID adminUserId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new HistoryAdminController(historyAdminService)).build();
        adminUserId = UUID.randomUUID();
    }

    @Test
    void searchRejectsAnonymousRequests() throws Exception {
        mockMvc.perform(get("/api/v1/admin/history/search").param("keyword", "road"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.UNAUTHORIZED));
    }

    @Test
    void searchReturnsAdminHistoryPayload() throws Exception {
        when(historyAdminService.search("files", "invoice", null, null, 0, 20)).thenReturn(Map.of(
                "items", List.of(Map.of(
                        "userId", UUID.randomUUID().toString(),
                        "conversationTitle", "Invoice thread",
                        "fileLabel", "invoice.pdf"
                )),
                "total", 1,
                "page", 0,
                "size", 20
        ));

        mockMvc.perform(admin(get("/api/v1/admin/history/search").param("keyword", "invoice").param("type", "files")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.items[0].fileLabel").value("invoice.pdf"));

        verify(historyAdminService).search("files", "invoice", null, null, 0, 20);
    }

    @Test
    void searchRejectsInvalidUserId() throws Exception {
        mockMvc.perform(admin(
                        get("/api/v1/admin/history/search")
                                .param("keyword", "road")
                                .param("userId", "bad-uuid")
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.PARAM_MISSING))
                .andExpect(jsonPath("$.message").value("userId is invalid"));
    }

    private MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder builder) {
        return builder
                .requestAttr(JwtAuthFilter.ATTR_USER_ID, adminUserId)
                .requestAttr(JwtAuthFilter.ATTR_USER_ROLE, "admin");
    }
}
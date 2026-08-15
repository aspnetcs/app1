package com.webchat.platformapi.ai.extension.tool;

import com.webchat.platformapi.ai.extension.ToolCatalogService;
import com.webchat.platformapi.auth.group.UserGroupService;
import com.webchat.platformapi.common.api.ErrorCodes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ToolV1ControllerTest {

    @Mock
    private ToolCatalogService toolCatalogService;

    @Mock
    private UserGroupService userGroupService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ToolV1Controller controller = new ToolV1Controller(toolCatalogService, userGroupService, false);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void configUsesExactRouteAndRejectsUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/tools/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.UNAUTHORIZED));
    }

    @Test
    void legacyToolConfigRouteIsNotMapped() throws Exception {
        mockMvc.perform(get("/api/v1/tool/config"))
                .andExpect(status().isNotFound());
    }

    @Test
    void configUsesUserScopedCatalog() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/tools/config")
                        .requestAttr(com.webchat.platformapi.auth.jwt.JwtAuthFilter.ATTR_USER_ID, userId))
                .andExpect(status().isOk());

        verify(toolCatalogService).userConfig(eq(userId));
    }
}

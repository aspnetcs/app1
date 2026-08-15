package com.webchat.adminapi.tool;

import com.webchat.platformapi.ai.extension.BuiltinToolRegistry;
import com.webchat.platformapi.ai.extension.ToolConfigProperties;
import com.webchat.platformapi.ai.extension.ToolRuntimeConfigService;
import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.common.api.ErrorCodes;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.webchat.platformapi.config.SysConfigService;
import java.util.Optional;

class ToolAdminControllerTest {

    private MockMvc mockMvc;
    private UUID adminUserId;
    @Mock
    private ObjectProvider<SysConfigService> sysConfigServiceProvider;
    @Mock
    private SysConfigService sysConfigService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        ToolConfigProperties properties = new ToolConfigProperties();
        properties.setEnabled(true);
        properties.setMaxSteps(6);
        properties.getTools().getCurrentTime().setEnabled(true);
        properties.getTools().getCalculator().setEnabled(false);
        properties.getTools().getWebPageSummary().setEnabled(true);
        when(sysConfigServiceProvider.getIfAvailable()).thenReturn(sysConfigService);
        when(sysConfigService.get(anyString())).thenReturn(Optional.empty());
        ToolRuntimeConfigService runtimeConfigService = new ToolRuntimeConfigService(properties, sysConfigServiceProvider);
        BuiltinToolRegistry builtinToolRegistry = new BuiltinToolRegistry(properties);
        mockMvc = MockMvcBuilders.standaloneSetup(new ToolAdminController(runtimeConfigService, builtinToolRegistry)).build();
        adminUserId = UUID.randomUUID();
    }

    @Test
    void configRejectsAnonymousRequests() throws Exception {
        mockMvc.perform(get("/api/v1/admin/tools/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.UNAUTHORIZED));
    }

    @Test
    void configReturnsNestedToolEnvelope() throws Exception {
        mockMvc.perform(admin(get("/api/v1/admin/tools/config")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.featureKey").value("platform.function-calling.enabled"))
                .andExpect(jsonPath("$.data.maxSteps").value(6))
                .andExpect(jsonPath("$.data.tools.current_time.enabled").value(true))
                .andExpect(jsonPath("$.data.tools.calculator.enabled").value(false))
                .andExpect(jsonPath("$.data.tools.web_page_summary.enabled").value(true));
    }

    @Test
    void updateConfigPersistsValidatedValues() throws Exception {
        mockMvc.perform(admin(put("/api/v1/admin/tools/config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "enabled": false,
                                  "maxSteps": 9,
                                  "tools": {
                                    "current_time": { "enabled": false },
                                    "calculator": { "enabled": true },
                                    "web_page_summary": { "enabled": false }
                                  }
                                }
                                """)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.enabled").value(false))
                .andExpect(jsonPath("$.data.maxSteps").value(9))
                .andExpect(jsonPath("$.data.tools.current_time.enabled").value(false))
                .andExpect(jsonPath("$.data.tools.calculator.enabled").value(true))
                .andExpect(jsonPath("$.data.tools.web_page_summary.enabled").value(false));

        verify(sysConfigService).set("function_calling.enabled", "false");
        verify(sysConfigService).set("function_calling.max_steps", "9");
        verify(sysConfigService).set("function_calling.tools.current_time.enabled", "false");
        verify(sysConfigService).set("function_calling.tools.calculator.enabled", "true");
        verify(sysConfigService).set("function_calling.tools.web_page_summary.enabled", "false");
    }

    private MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder builder) {
        return builder
                .requestAttr(JwtAuthFilter.ATTR_USER_ID, adminUserId)
                .requestAttr(JwtAuthFilter.ATTR_USER_ROLE, "admin");
    }
}

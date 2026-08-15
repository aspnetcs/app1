package com.webchat.adminapi.mcp;

import com.webchat.platformapi.ai.extension.McpClientService;
import com.webchat.platformapi.ai.extension.McpProperties;
import com.webchat.platformapi.ai.extension.McpServerEntity;
import com.webchat.platformapi.ai.extension.McpServerRepository;
import com.webchat.platformapi.ai.security.AiCryptoService;
import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.common.api.ErrorCodes;
import com.webchat.platformapi.config.SysConfigService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class McpAdminControllerTest {

    @Mock
    private McpClientService mcpClientService;
    @Mock
    private AiCryptoService cryptoService;
    @Mock
    private SysConfigService sysConfigService;
    @Mock
    private McpServerRepository repository;

    private MockMvc mockMvc;
    private UUID adminUserId;
    private McpProperties properties;

    @BeforeEach
    void setUp() {
        properties = new McpProperties();
        properties.setEnabled(true);
        properties.setMaxServers(5);
        properties.setRequestTimeoutMs(4000);
        when(mcpClientService.getProperties()).thenReturn(properties);
        when(sysConfigService.get(anyString())).thenReturn(Optional.empty());

        mockMvc = MockMvcBuilders.standaloneSetup(new McpAdminController(mcpClientService, cryptoService, sysConfigService)).build();
        adminUserId = UUID.randomUUID();
    }

    @Test
    void getConfigRejectsUnauthorizedRequests() throws Exception {
        mockMvc.perform(get("/api/v1/admin/mcp/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.UNAUTHORIZED));
    }

    @Test
    void getConfigReturnsCurrentProperties() throws Exception {
        mockMvc.perform(admin(get("/api/v1/admin/mcp/config")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.maxServers").value(5))
                .andExpect(jsonPath("$.data.requestTimeoutMs").value(4000));
    }

    @Test
    void updateConfigPersistsValidatedValues() throws Exception {
        mockMvc.perform(
                        admin(put("/api/v1/admin/mcp/config")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "enabled": false,
                                          "maxServers": 7,
                                          "requestTimeoutMs": 9000
                                        }
                                        """))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.enabled").value(false))
                .andExpect(jsonPath("$.data.maxServers").value(7))
                .andExpect(jsonPath("$.data.requestTimeoutMs").value(9000));

        verify(sysConfigService).set("mcp.enabled", "false");
        verify(sysConfigService).set("mcp.max_servers", "7");
        verify(sysConfigService).set("mcp.request_timeout_ms", "9000");
    }

    @Test
    void createServerBindsBodyAndPersistsEncryptedToken() throws Exception {
        when(mcpClientService.getRepository()).thenReturn(repository);
        when(repository.count()).thenReturn(0L);
        when(cryptoService.isConfigured()).thenReturn(true);
        when(cryptoService.encrypt("secret-token")).thenReturn("enc-token");
        when(repository.save(any(McpServerEntity.class))).thenAnswer(invocation -> {
            McpServerEntity entity = invocation.getArgument(0);
            entity.setId(11L);
            return entity;
        });

        mockMvc.perform(
                        admin(post("/api/v1/admin/mcp/servers")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "name": "Local MCP",
                                          "description": "loopback server",
                                          "endpointUrl": "https://mcp.example.com/sse",
                                          "transportType": "http_sse",
                                          "authToken": "secret-token",
                                          "enabled": true
                                        }
                                        """))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(11))
                .andExpect(jsonPath("$.data.name").value("Local MCP"));

        ArgumentCaptor<McpServerEntity> captor = ArgumentCaptor.forClass(McpServerEntity.class);
        verify(repository).save(captor.capture());
        assertEquals("Local MCP", captor.getValue().getName());
        assertEquals("enc-token", captor.getValue().getAuthToken());
    }

    @Test
    void testConnectionRouteReturnsServicePayload() throws Exception {
        when(mcpClientService.getRepository()).thenReturn(repository);
        McpServerEntity entity = server(11L, true);
        when(repository.findById(11L)).thenReturn(Optional.of(entity));
        when(mcpClientService.testConnection(11L)).thenReturn(Map.of("success", true, "toolCount", 2));

        mockMvc.perform(admin(post("/api/v1/admin/mcp/servers/{id}/test", 11L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.success").value(true))
                .andExpect(jsonPath("$.data.toolCount").value(2));
    }

    @Test
    void refreshToolsRouteReturnsToolArray() throws Exception {
        when(mcpClientService.getRepository()).thenReturn(repository);
        McpServerEntity entity = server(11L, true);
        when(repository.findById(11L)).thenReturn(Optional.of(entity));
        when(mcpClientService.refreshTools(11L)).thenReturn(List.of(
                new McpClientService.McpTool(11L, "Local MCP", "fetch_status", "Fetch status", Map.of())
        ));

        mockMvc.perform(admin(post("/api/v1/admin/mcp/servers/{id}/refresh", 11L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].name").value("fetch_status"))
                .andExpect(jsonPath("$.data[0].description").value("Fetch status"));
    }

    @Test
    void deleteServerUsesPathVariable() throws Exception {
        when(mcpClientService.getRepository()).thenReturn(repository);
        mockMvc.perform(admin(delete("/api/v1/admin/mcp/servers/{id}", 11L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(repository).deleteById(11L);
    }

    private McpServerEntity server(Long id, boolean enabled) {
        McpServerEntity entity = new McpServerEntity();
        entity.setId(id);
        entity.setName("Local MCP");
        entity.setDescription("loopback");
        entity.setEndpointUrl("https://mcp.example.com/sse");
        entity.setTransportType("http_sse");
        entity.setEnabled(enabled);
        entity.setLastRefreshedAt(Instant.parse("2026-03-24T08:00:00Z"));
        return entity;
    }

    private MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder builder) {
        return builder
                .requestAttr(JwtAuthFilter.ATTR_USER_ID, adminUserId)
                .requestAttr(JwtAuthFilter.ATTR_USER_ROLE, "admin");
    }
}

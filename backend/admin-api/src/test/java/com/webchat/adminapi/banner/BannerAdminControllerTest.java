package com.webchat.adminapi.banner;

import com.webchat.platformapi.admin.ops.BannerService;
import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.common.api.ErrorCodes;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class BannerAdminControllerTest {

    @Mock
    private BannerService bannerService;

    private MockMvc mockMvc;
    private UUID adminUserId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new BannerAdminController(bannerService)).build();
        adminUserId = UUID.randomUUID();
    }

    @Test
    void listRejectsAnonymousRequests() throws Exception {
        mockMvc.perform(get("/api/v1/admin/banners"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.UNAUTHORIZED));

        verifyNoInteractions(bannerService);
    }

    @Test
    void listReturnsPagedEnvelope() throws Exception {
        UUID bannerId = UUID.randomUUID();
        when(bannerService.listForAdmin("hero", "info", true, 1, 2)).thenReturn(new PageImpl<>(
                java.util.List.of(Map.of(
                        "id", bannerId.toString(),
                        "title", "Hero banner",
                        "type", "info",
                        "enabled", true,
                        "createdAt", Instant.parse("2026-03-24T12:00:00Z").toString()
                )),
                PageRequest.of(1, 2),
                3
        ));

        mockMvc.perform(admin(get("/api/v1/admin/banners")
                        .param("page", "1")
                        .param("size", "2")
                        .param("search", "hero")
                        .param("type", "info")
                        .param("enabled", "true")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.items[0].title").value("Hero banner"))
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(2));
    }

    @Test
    void createBindsRequestBody() throws Exception {
        UUID bannerId = UUID.randomUUID();
        when(bannerService.create(any())).thenReturn(Map.of(
                "id", bannerId.toString(),
                "title", "Launch",
                "type", "info",
                "enabled", true
        ));

        mockMvc.perform(admin(post("/api/v1/admin/banners")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Launch",
                                  "content": "Live now",
                                  "type": "info",
                                  "enabled": true
                                }
                                """)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(bannerId.toString()))
                .andExpect(jsonPath("$.data.title").value("Launch"));

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(bannerService).create(captor.capture());
        assertEquals("Launch", captor.getValue().get("title"));
    }

    @Test
    void updateUsesPathVariableAndBody() throws Exception {
        UUID bannerId = UUID.randomUUID();
        when(bannerService.update(eq(bannerId), any())).thenReturn(Map.of(
                "id", bannerId.toString(),
                "title", "Updated launch",
                "type", "warning",
                "enabled", false
        ));

        mockMvc.perform(admin(put("/api/v1/admin/banners/{id}", bannerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Updated launch",
                                  "content": "Changed copy",
                                  "type": "warning",
                                  "enabled": false
                                }
                                """)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(bannerId.toString()))
                .andExpect(jsonPath("$.data.title").value("Updated launch"))
                .andExpect(jsonPath("$.data.enabled").value(false));
    }

    @Test
    void deleteUsesPathVariable() throws Exception {
        UUID bannerId = UUID.randomUUID();

        mockMvc.perform(admin(delete("/api/v1/admin/banners/{id}", bannerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(bannerService).delete(bannerId);
    }

    private MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder builder) {
        return builder
                .requestAttr(JwtAuthFilter.ATTR_USER_ID, adminUserId)
                .requestAttr(JwtAuthFilter.ATTR_USER_ROLE, "admin");
    }
}

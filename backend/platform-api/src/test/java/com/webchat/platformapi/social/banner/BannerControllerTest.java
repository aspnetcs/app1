package com.webchat.platformapi.social.banner;

import com.webchat.platformapi.admin.ops.BannerService;
import com.webchat.platformapi.auth.group.UserGroupService;
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

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class BannerControllerTest {

    @Mock
    private BannerService bannerService;

    @Mock
    private UserGroupService userGroupService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        BannerController controller = new BannerController(
                bannerService,
                userGroupService,
                false
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void activeUsesBannersRouteForAuthenticatedUser() throws Exception {
        UUID userId = UUID.randomUUID();
        when(bannerService.listActive()).thenReturn(List.of(
                Map.of(
                        "id", UUID.randomUUID().toString(),
                        "title", "Maintenance",
                        "type", "warning"
                )
        ));

        mockMvc.perform(
                        get("/api/v1/banners/active")
                                .requestAttr(JwtAuthFilter.ATTR_USER_ID, userId)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].title").value("Maintenance"))
                .andExpect(jsonPath("$.data[0].type").value("warning"));

        verify(bannerService).listActive();
    }

    @Test
    void activeRejectsUnauthenticatedRequests() throws Exception {
        mockMvc.perform(get("/api/v1/banners/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.UNAUTHORIZED));
    }

    @Test
    void activeRejectsWhenBannerBlockedByGroupPolicy() throws Exception {
        UUID userId = UUID.randomUUID();
        BannerController controller = new BannerController(
                bannerService,
                userGroupService,
                true
        );
        MockMvc policyMockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        when(userGroupService.isFeatureAllowed(userId, "banner")).thenReturn(false);

        policyMockMvc.perform(
                        get("/api/v1/banners/active")
                                .requestAttr(JwtAuthFilter.ATTR_USER_ID, userId)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.UNAUTHORIZED))
                .andExpect(jsonPath("$.message").value("banner not allowed by group policy"));

        verify(userGroupService).isFeatureAllowed(userId, "banner");
        verify(bannerService, never()).listActive();
    }
}

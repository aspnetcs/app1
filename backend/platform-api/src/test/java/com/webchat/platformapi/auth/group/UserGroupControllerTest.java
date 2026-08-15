package com.webchat.platformapi.auth.group;

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
class UserGroupControllerTest {

    @Mock
    private UserGroupService userGroupService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        UserGroupController controller = new UserGroupController(userGroupService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void meUsesGroupsRouteAndReturnsProfileContract() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        when(userGroupService.resolveProfile(userId)).thenReturn(new UserGroupService.GroupProfile(
                List.of(Map.of(
                        "id", groupId.toString(),
                        "name", "pilot-users"
                )),
                List.of("gpt-4o"),
                List.of("banner", "follow_up"),
                15
        ));

        mockMvc.perform(
                        get("/api/v1/groups/me")
                                .requestAttr(JwtAuthFilter.ATTR_USER_ID, userId)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.groups[0].id").value(groupId.toString()))
                .andExpect(jsonPath("$.data.groups[0].name").value("pilot-users"))
                .andExpect(jsonPath("$.data.allowedModels[0]").value("gpt-4o"))
                .andExpect(jsonPath("$.data.featureFlags[0]").value("banner"))
                .andExpect(jsonPath("$.data.chatRateLimitPerMinute").value(15));

        verify(userGroupService).resolveProfile(userId);
    }

    @Test
    void meReturnsZeroWhenRateLimitIsUnset() throws Exception {
        UUID userId = UUID.randomUUID();
        when(userGroupService.resolveProfile(userId)).thenReturn(new UserGroupService.GroupProfile(
                List.of(),
                List.of(),
                List.of(),
                null
        ));

        mockMvc.perform(
                        get("/api/v1/groups/me")
                                .requestAttr(JwtAuthFilter.ATTR_USER_ID, userId)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.chatRateLimitPerMinute").value(0));

        verify(userGroupService).resolveProfile(userId);
    }

    @Test
    void meRejectsUnauthenticatedRequests() throws Exception {
        mockMvc.perform(get("/api/v1/groups/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.UNAUTHORIZED));
    }

    @Test
    void legacyGroupProfileRouteIsNotMapped() throws Exception {
        mockMvc.perform(get("/api/v1/group/me"))
                .andExpect(status().isNotFound());
    }
}

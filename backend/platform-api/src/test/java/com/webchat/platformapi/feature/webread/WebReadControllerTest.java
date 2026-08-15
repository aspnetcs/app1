package com.webchat.platformapi.feature.webread;

import com.webchat.platformapi.audit.AuditService;
import com.webchat.platformapi.auth.group.UserGroupService;
import com.webchat.platformapi.common.api.ErrorCodes;
import com.webchat.platformapi.webread.WebReadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class WebReadControllerTest {

    @Mock
    private WebReadService webReadService;

    @Mock
    private AuditService auditService;

    @Mock
    private UserGroupService userGroupService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        WebReadController controller = new WebReadController(webReadService, auditService, userGroupService, false);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void fetchUsesExactRouteAndRejectsUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/v1/web-read/fetch").contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.UNAUTHORIZED));
    }

    @Test
    void legacyWebReadRouteIsNotMapped() throws Exception {
        mockMvc.perform(post("/api/v1/web-read/read").contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
    }
}

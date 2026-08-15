package com.webchat.platformapi.ai.texttransform;

import com.webchat.platformapi.audit.AuditService;
import com.webchat.platformapi.auth.group.UserGroupService;
import com.webchat.platformapi.common.api.ErrorCodes;
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
class PromptOptimizeControllerTest {

    @Mock
    private PromptOptimizeService promptOptimizeService;

    @Mock
    private AuditService auditService;

    @Mock
    private UserGroupService userGroupService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        PromptOptimizeController controller = new PromptOptimizeController(
                promptOptimizeService,
                auditService,
                userGroupService,
                false
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void optimizeUsesExactRouteAndRejectsUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/v1/prompt-optimize").contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.UNAUTHORIZED));
    }

    @Test
    void legacyPromptOptimizeRouteIsNotMapped() throws Exception {
        mockMvc.perform(post("/api/v1/prompt-optimize/run").contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
    }
}

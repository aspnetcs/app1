package com.webchat.adminapi.translation;

import com.webchat.platformapi.ai.texttransform.TranslationService;
import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.common.api.ErrorCodes;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TranslationAdminControllerTest {

    @Mock
    private TranslationService translationService;

    private MockMvc mockMvc;
    private UUID adminUserId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TranslationAdminController(translationService)).build();
        adminUserId = UUID.randomUUID();
    }

    @Test
    void configRejectsAnonymousRequests() throws Exception {
        mockMvc.perform(get("/api/v1/admin/translation/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.UNAUTHORIZED));
    }

    @Test
    void configReturnsCurrentServiceValues() throws Exception {
        when(translationService.isEnabled()).thenReturn(true);
        when(translationService.getDefaultModel()).thenReturn("gpt-4o-mini");
        when(translationService.getDefaultTargetLanguage()).thenReturn("en");
        when(translationService.getMaxInputChars()).thenReturn(5000);

        mockMvc.perform(admin(get("/api/v1/admin/translation/config")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.defaultModel").value("gpt-4o-mini"))
                .andExpect(jsonPath("$.data.defaultTargetLanguage").value("en"))
                .andExpect(jsonPath("$.data.maxInputChars").value(5000));
    }

    private MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder builder) {
        return builder
                .requestAttr(JwtAuthFilter.ATTR_USER_ID, adminUserId)
                .requestAttr(JwtAuthFilter.ATTR_USER_ROLE, "admin");
    }
}

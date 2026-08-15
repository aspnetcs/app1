package com.webchat.platformapi.auth.oauth;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

class OAuthConfigSupportTest {

    @Test
    void normalizeKeysTrimsAndLowercasesProviderKeys() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put(" GitHub ", "github");
        values.put("GOOGLE", "google");

        assertThat(OAuthConfigSupport.normalizeKeys(values))
                .containsExactly(
                        entry("github", "github"),
                        entry("google", "google")
                );
    }

    @Test
    void helpersPreferExplicitValuesAndHandleBlankInput() {
        assertThat(OAuthConfigSupport.firstNonBlank("GitHub", "fallback")).isEqualTo("GitHub");
        assertThat(OAuthConfigSupport.firstNonBlank("   ", "fallback")).isEqualTo("fallback");
        assertThat(OAuthConfigSupport.capitalize("github")).isEqualTo("Github");
        assertThat(OAuthConfigSupport.capitalize(" ")).isEmpty();
    }
}

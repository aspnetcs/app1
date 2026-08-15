package com.webchat.platformapi.auth.role;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlatformRolePolicyDefaultsTest {

    @Test
    void guestRoleDoesNotHardcodeSingleModelByDefault() throws IOException {
        MutablePropertySources sources = new MutablePropertySources();
        List<PropertySource<?>> loaded = new YamlPropertySourceLoader()
                .load("platform-api-application", new ClassPathResource("application.yml"));
        for (PropertySource<?> source : loaded) {
            sources.addLast(source);
        }

        // YamlPropertySourceLoader keeps the raw placeholder string in the PropertySource.
        // PropertySourcesPropertyResolver resolves placeholders, which would turn it into an empty string
        // when ROLE_GUEST_ALLOWED_MODELS is not set. We assert the raw config is placeholder-based.
        String guestAllowedModels = null;
        for (PropertySource<?> source : loaded) {
            Object value = source.getProperty("platform.role-policy.policies.guest.allowed-models");
            if (value != null) {
                guestAllowedModels = value.toString();
                break;
            }
        }

        assertEquals("${ROLE_GUEST_ALLOWED_MODELS:}", guestAllowedModels);
    }
}

package com.webchat.platformapi.infra.common;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;

import static org.assertj.core.api.Assertions.assertThat;

class CorsConfigTest {

    @Test
    void platformApiDoesNotRegisterAdminStaticHandler() {
        GenericApplicationContext context = new GenericApplicationContext();
        context.refresh();

        try {
            CorsConfig config = new CorsConfig("http://localhost:*");
            ResourceHandlerRegistry registry = new ResourceHandlerRegistry(context, new MockServletContext());

            config.addResourceHandlers(registry);

            assertThat(registry.hasMappingForPattern("/admin/**")).isFalse();
        } finally {
            context.close();
        }
    }
}

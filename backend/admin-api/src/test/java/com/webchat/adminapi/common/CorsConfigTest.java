package com.webchat.adminapi.common;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.handler.SimpleUrlHandlerMapping;
import org.springframework.web.servlet.mvc.ParameterizableViewController;
import org.springframework.web.servlet.resource.ResourceHttpRequestHandler;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CorsConfigTest {

    @Test
    void adminStaticHandlerIncludesLocalAndPackagedLocations() {
        GenericApplicationContext context = new GenericApplicationContext();
        context.refresh();

        try {
            CorsConfig config = new CorsConfig(
                    "http://localhost:*",
                    "file:../../admin/dist/,file:/app/admin-dist/"
            );
            ResourceHandlerRegistry registry = new ResourceHandlerRegistry(context, new MockServletContext());

            config.addResourceHandlers(registry);

            assertThat(registry.hasMappingForPattern("/admin/**")).isTrue();

            SimpleUrlHandlerMapping mapping = ReflectionTestUtils.invokeMethod(registry, "getHandlerMapping");
            assertThat(mapping).isNotNull();
            assertThat(mapping.getUrlMap()).containsKey("/admin/**");
            assertThat(mapping.getUrlMap().get("/admin/**")).isInstanceOf(ResourceHttpRequestHandler.class);

            @SuppressWarnings("unchecked")
            List<String> locationValues = (List<String>) ReflectionTestUtils.getField(
                    mapping.getUrlMap().get("/admin/**"),
                    "locationValues"
            );
            assertThat(locationValues).containsExactly("file:../../admin/dist/", "file:/app/admin-dist/");
        } finally {
            context.close();
        }
    }

    @Test
    void adminEntryRedirectsRootPathsToIndexHtml() {
        GenericApplicationContext context = new GenericApplicationContext();
        context.refresh();

        try {
            CorsConfig config = new CorsConfig("http://localhost:*", "file:../../admin/dist/");
            ViewControllerRegistry registry = new ViewControllerRegistry(context);

            config.addViewControllers(registry);

            SimpleUrlHandlerMapping mapping = ReflectionTestUtils.invokeMethod(registry, "buildHandlerMapping");
            assertThat(mapping).isNotNull();
            assertThat(mapping.getUrlMap()).containsKeys("/admin", "/admin/");
            assertRedirectToIndexHtml(mapping.getUrlMap().get("/admin"));
            assertRedirectToIndexHtml(mapping.getUrlMap().get("/admin/"));
        } finally {
            context.close();
        }
    }

    private static void assertRedirectToIndexHtml(Object handler) {
        assertThat(handler).isInstanceOf(ParameterizableViewController.class);
        assertThat(((ParameterizableViewController) handler).getViewName())
                .isEqualTo("redirect:/admin/index.html");
    }
}

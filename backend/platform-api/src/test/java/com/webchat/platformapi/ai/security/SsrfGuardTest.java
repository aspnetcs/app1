package com.webchat.platformapi.ai.security;

import com.webchat.platformapi.ai.AiProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SsrfGuardTest {

    @Test
    void allowsHttpsHostInAllowlist() {
        AiProperties p = new AiProperties();
        p.getSsrf().setAllowlist(List.of("api.openai.com"));
        SsrfGuard g = new SsrfGuard(p);

        assertDoesNotThrow(() -> g.assertAllowedBaseUrl("https://api.openai.com"));
    }

    @Test
    void blocksHostNotInAllowlist() {
        AiProperties p = new AiProperties();
        p.getSsrf().setAllowlist(List.of("api.openai.com"));
        SsrfGuard g = new SsrfGuard(p);

        assertThrows(SsrfGuard.SsrfException.class, () -> g.assertAllowedBaseUrl("https://evil.example.com"));
    }

    @Test
    void supportsWildcardSuffixAllowlist() {
        AiProperties p = new AiProperties();
        p.getSsrf().setAllowlist(List.of("*.openai.com"));
        SsrfGuard g = new SsrfGuard(p);

        assertDoesNotThrow(() -> g.assertAllowedBaseUrl("https://api.openai.com"));
    }

    @Test
    void blocksHttpByDefault() {
        AiProperties p = new AiProperties();
        p.getSsrf().setAllowlist(List.of("api.openai.com"));
        p.getSsrf().setAllowHttp(false);
        SsrfGuard g = new SsrfGuard(p);

        assertThrows(SsrfGuard.SsrfException.class, () -> g.assertAllowedBaseUrl("http://api.openai.com"));
    }

    @Test
    void allowsHttpLocalhostWhenEnabled() {
        AiProperties p = new AiProperties();
        p.getSsrf().setAllowlist(List.of()); // localhost bypasses allowlist when allowHttpLocalhost=true
        p.getSsrf().setAllowHttpLocalhost(true);
        SsrfGuard g = new SsrfGuard(p);

        assertDoesNotThrow(() -> g.assertAllowedBaseUrl("http://localhost:11434"));
    }

    @Test
    void allowsHttpHostDockerInternalWhenLocalhostModeEnabled() {
        AiProperties p = new AiProperties();
        p.getSsrf().setAllowlist(List.of());
        p.getSsrf().setAllowHttpLocalhost(true);
        SsrfGuard g = new SsrfGuard(p);

        assertDoesNotThrow(() -> g.assertAllowedBaseUrl("http://host.docker.internal:11434"));
    }

    @Test
    void blocksPrivateIpWhenNotAllowed() {
        AiProperties p = new AiProperties();
        p.getSsrf().setAllowlist(List.of("10.0.0.1"));
        p.getSsrf().setAllowPrivate(false);
        SsrfGuard g = new SsrfGuard(p);

        assertThrows(SsrfGuard.SsrfException.class, () -> g.assertAllowedBaseUrl("https://10.0.0.1"));
    }

    @Test
    void allowsPrivateIpWhenEnabled() {
        AiProperties p = new AiProperties();
        p.getSsrf().setAllowlist(List.of("10.0.0.1"));
        p.getSsrf().setAllowPrivate(true);
        SsrfGuard g = new SsrfGuard(p);

        assertDoesNotThrow(() -> g.assertAllowedBaseUrl("https://10.0.0.1"));
    }
}


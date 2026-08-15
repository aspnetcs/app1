package com.webchat.platformapi.ai.security;

import com.webchat.platformapi.ai.AiProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AiCryptoServiceTest {

    @Test
    void encryptDecryptRoundtrip() {
        AiProperties p = new AiProperties();
        p.setMasterKey("test-master-key");
        AiCryptoService crypto = new AiCryptoService(p);

        assertTrue(crypto.isConfigured());

        String enc = crypto.encrypt("sk-test-123");
        assertNotNull(enc);
        assertTrue(enc.startsWith("v1:"));

        String dec = crypto.decrypt(enc);
        assertEquals("sk-test-123", dec);

        String h = crypto.sha256Hex("sk-test-123");
        assertEquals(64, h.length());
    }

    @Test
    void encryptRequiresMasterKey() {
        AiProperties p = new AiProperties();
        p.setMasterKey(" ");
        AiCryptoService crypto = new AiCryptoService(p);

        assertFalse(crypto.isConfigured());
        assertThrows(IllegalStateException.class, () -> crypto.encrypt("x"));
        assertThrows(IllegalStateException.class, () -> crypto.decrypt("v1:AAA="));
    }
}


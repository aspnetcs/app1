package com.webchat.platformapi.ai.security;

import com.webchat.platformapi.ai.AiProperties;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class AiCryptoService {

    private static final String ENC_VERSION_PREFIX = "v1:";
    private static final int GCM_TAG_BITS = 128;
    private static final int NONCE_BYTES = 12;

    private final SecureRandom secureRandom = new SecureRandom();
    private final SecretKeySpec secretKey;

    public AiCryptoService(AiProperties properties) {
        String master = SecretResolver.valueOrFileContents(properties == null ? null : properties.getMasterKey());
        if (master == null || master.isBlank()) {
            this.secretKey = null;
            return;
        }
        this.secretKey = new SecretKeySpec(sha256(master), "AES");
    }

    public boolean isConfigured() {
        return secretKey != null;
    }

    public String encrypt(String plaintext) {
        if (plaintext == null) return null;
        if (secretKey == null) throw new IllegalStateException("AI master key not configured");
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            secureRandom.nextBytes(nonce);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, nonce));
            byte[] out = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[nonce.length + out.length];
            System.arraycopy(nonce, 0, combined, 0, nonce.length);
            System.arraycopy(out, 0, combined, nonce.length, out.length);

            return ENC_VERSION_PREFIX + Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("encrypt failed", e);
        }
    }

    public String decrypt(String encrypted) {
        if (encrypted == null) return null;
        if (secretKey == null) throw new IllegalStateException("AI master key not configured");
        try {
            String payload = encrypted.startsWith(ENC_VERSION_PREFIX) ? encrypted.substring(ENC_VERSION_PREFIX.length()) : encrypted;
            byte[] combined = Base64.getDecoder().decode(payload);
            if (combined.length <= NONCE_BYTES) throw new IllegalArgumentException("bad ciphertext");

            byte[] nonce = new byte[NONCE_BYTES];
            byte[] cipherText = new byte[combined.length - NONCE_BYTES];
            System.arraycopy(combined, 0, nonce, 0, NONCE_BYTES);
            System.arraycopy(combined, NONCE_BYTES, cipherText, 0, cipherText.length);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, nonce));
            byte[] out = cipher.doFinal(cipherText);
            return new String(out, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("decrypt failed", e);
        }
    }

    public String sha256Hex(String input) {
        if (input == null) return "";
        return java.util.HexFormat.of().formatHex(sha256(input));
    }

    private static byte[] sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest(input.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}


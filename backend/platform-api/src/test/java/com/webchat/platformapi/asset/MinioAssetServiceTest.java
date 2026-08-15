package com.webchat.platformapi.asset;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MinioAssetServiceTest {

    @Test
    void normalizeSha256RejectsMissingDigest() {
        MinioAssetService.AssetException error = assertThrows(
                MinioAssetService.AssetException.class,
                () -> MinioAssetService.normalizeSha256(" ")
        );

        assertEquals("sha256 required", error.getMessage());
    }

    @Test
    void normalizeSha256RejectsNonHexDigest() {
        MinioAssetService.AssetException error = assertThrows(
                MinioAssetService.AssetException.class,
                () -> MinioAssetService.normalizeSha256("g".repeat(64))
        );

        assertEquals("sha256 invalid", error.getMessage());
    }

    @Test
    void normalizeSha256LowercasesValidDigest() {
        String normalized = MinioAssetService.normalizeSha256("A".repeat(64));

        assertEquals("a".repeat(64), normalized);
    }
}

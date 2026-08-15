package com.webchat.platformapi.asset;

import com.webchat.platformapi.common.api.ErrorCodes;
import com.webchat.platformapi.infra.asset.AssetV1Controller;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AssetV1ControllerTest {

    @Test
    void assetRoutesUseExactPathsAndRejectUnauthenticated() throws Exception {
        MinioAssetService assetService = mock(MinioAssetService.class);
        AssetV1Controller controller = new AssetV1Controller(assetService);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        mockMvc.perform(post("/api/v1/asset/presign").contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.UNAUTHORIZED));

        mockMvc.perform(post("/api/v1/asset/confirm").contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.UNAUTHORIZED));
    }

    @Test
    void legacyAssetRoutesAreNotMapped() throws Exception {
        MinioAssetService assetService = mock(MinioAssetService.class);
        AssetV1Controller controller = new AssetV1Controller(assetService);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        mockMvc.perform(post("/api/v1/assets/presign").contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/asset").contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsInvalidSha256BeforeServiceCall() {
        MinioAssetService assetService = mock(MinioAssetService.class);
        AssetV1Controller controller = new AssetV1Controller(assetService);

        var response = controller.confirm(
                UUID.randomUUID(),
                Map.of(
                        "purpose", "voice",
                        "objectKey", "u/test/voice/file",
                        "sha256", "not-a-sha256"
                )
        );

        assertEquals(ErrorCodes.PARAM_MISSING, response.code());
        assertEquals("sha256 format is invalid", response.message());
        assertNull(response.data());
        verify(assetService, never()).confirmAndPresignGet(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void normalizesSha256BeforeDelegating() {
        MinioAssetService assetService = mock(MinioAssetService.class);
        AssetV1Controller controller = new AssetV1Controller(assetService);
        UUID userId = UUID.randomUUID();
        String sha256 = "ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789";
        String objectKey = "u/" + userId + "/voice/file";

        when(assetService.confirmAndPresignGet(eq(objectKey), eq(12L), eq("audio/mpeg"), eq(sha256.toLowerCase())))
                .thenReturn(new MinioAssetService.ConfirmResult("bucket", objectKey, "https://download", 60, 12L, "audio/mpeg", sha256.toLowerCase()));

        var response = controller.confirm(
                userId,
                Map.of(
                        "purpose", "voice",
                        "objectKey", objectKey,
                        "mimeType", "audio/mpeg",
                        "size", 12,
                        "sha256", sha256
                )
        );

        assertEquals(ErrorCodes.SUCCESS, response.code());
        assertEquals(sha256.toLowerCase(), response.data().get("sha256"));
        verify(assetService).confirmAndPresignGet(eq(objectKey), eq(12L), eq("audio/mpeg"), eq(sha256.toLowerCase()));
    }
}

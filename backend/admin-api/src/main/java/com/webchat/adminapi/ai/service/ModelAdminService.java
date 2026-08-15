package com.webchat.adminapi.ai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.webchat.adminapi.ai.dto.ChannelFetchModelsRequest;
import com.webchat.adminapi.ai.helper.ChannelValidationHelper;
import com.webchat.platformapi.ai.channel.AiChannelEntity;
import com.webchat.platformapi.ai.channel.AiChannelRepository;
import com.webchat.platformapi.ai.security.SsrfGuard;
import com.webchat.platformapi.common.api.ApiResponse;
import com.webchat.platformapi.common.api.ErrorCodes;
import com.webchat.platformapi.common.util.RequestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ModelAdminService {

    private static final Logger log = LoggerFactory.getLogger(ModelAdminService.class);

    private final AiChannelRepository channelRepo;
    private final ChannelValidationHelper channelValidationHelper;
    private final ChannelKeyAdminService channelKeyAdminService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ModelAdminService(
            AiChannelRepository channelRepo,
            ChannelValidationHelper channelValidationHelper,
            ChannelKeyAdminService channelKeyAdminService
    ) {
        this.channelRepo = channelRepo;
        this.channelValidationHelper = channelValidationHelper;
        this.channelKeyAdminService = channelKeyAdminService;
    }

    public ApiResponse<Map<String, Object>> fetchModels(ChannelFetchModelsRequest body) {
        String baseUrl = null;
        String apiKey = null;

        if (body != null && body.channelId() != null && !body.channelId().isNull()) {
            Long channelId;
            try {
                channelId = channelValidationHelper.parseChannelId(body.channelId());
            } catch (IllegalArgumentException e) {
                return ApiResponse.error(ErrorCodes.PARAM_MISSING, "channel_id invalid");
            }
            AiChannelEntity channel = channelRepo.findById(channelId).orElse(null);
            if (channel == null) {
                return ApiResponse.error(ErrorCodes.PARAM_MISSING, "channel not found");
            }
            baseUrl = channel.getBaseUrl();
            apiKey = channelKeyAdminService.findFirstDecryptedKey(channelId);
        }

        if (body != null) {
            String providedBaseUrl = RequestUtils.trimOrNull(body.baseUrl());
            if (providedBaseUrl != null) {
                baseUrl = providedBaseUrl;
            }
            String providedApiKey = RequestUtils.trimOrNull(body.apiKey());
            if (providedApiKey != null) {
                apiKey = providedApiKey;
            }
        }

        if (baseUrl == null || baseUrl.isBlank()) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, "base_url required");
        }
        if (apiKey == null || apiKey.isBlank()) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, "api_key required");
        }

        try {
            channelValidationHelper.assertAllowedBaseUrl(baseUrl);
        } catch (SsrfGuard.SsrfException e) {
            return ApiResponse.error(ErrorCodes.SERVER_ERROR, "SSRF: " + e.getMessage());
        }

        String modelsUrl = baseUrl.endsWith("/") ? baseUrl + "models" : baseUrl + "/models";
        try {
            URL url = URI.create(modelsUrl).toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            connection.setRequestProperty("Accept", "application/json");
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(15_000);

            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
            String responseBody = stream == null ? "" : new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            connection.disconnect();

            if (status < 200 || status >= 300) {
                return ApiResponse.error(
                        ErrorCodes.SERVER_ERROR,
                        "upstream " + status + ": " + responseBody.substring(0, Math.min(200, responseBody.length()))
                );
            }

            Map<String, Object> parsed = objectMapper.readValue(responseBody, new TypeReference<>() {
            });
            Object dataObj = parsed.get("data");
            List<String> modelIds = new ArrayList<>();
            if (dataObj instanceof List<?> dataList) {
                for (Object item : dataList) {
                    if (item instanceof Map<?, ?> map) {
                        Object idObj = map.get("id");
                        if (idObj != null) {
                            modelIds.add(String.valueOf(idObj));
                        }
                    }
                }
            }
            modelIds.sort(String::compareTo);
            return ApiResponse.ok(Map.of("models", modelIds, "count", modelIds.size()));
        } catch (Exception e) {
            log.warn("[admin] fetch-models failed: {}", e.getMessage(), e);
            return ApiResponse.error(
                    ErrorCodes.SERVER_ERROR,
                    "fetch failed: " + (e.getMessage() == null ? "unknown" : e.getMessage())
            );
        }
    }
}

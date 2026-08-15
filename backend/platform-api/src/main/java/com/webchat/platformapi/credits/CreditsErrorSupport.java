package com.webchat.platformapi.credits;

import com.webchat.platformapi.common.api.ApiResponse;
import com.webchat.platformapi.common.api.ErrorCodes;
import org.springframework.lang.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

public final class CreditsErrorSupport {

    private CreditsErrorSupport() {
    }

    public record CreditsError(String reason, int apiCode, int httpStatus) {
    }

    public static CreditsError resolve(@Nullable String reason) {
        String normalized = reason == null || reason.isBlank()
                ? "credits_policy_denied"
                : reason.trim();
        return switch (normalized) {
            case "credits_insufficient" ->
                    new CreditsError(normalized, ErrorCodes.CREDITS_INSUFFICIENT, 429);
            case "credits_reserve_failed" ->
                    new CreditsError(normalized, ErrorCodes.CREDITS_RESERVE_FAILED, 503);
            case "credits_policy_unavailable" ->
                    new CreditsError(normalized, ErrorCodes.CREDITS_RESERVE_FAILED, 503);
            case "model_not_allowed" ->
                    new CreditsError(normalized, ErrorCodes.MODEL_NOT_ALLOWED, 403);
            case "credits_account_not_found" ->
                    new CreditsError(normalized, ErrorCodes.CREDITS_ACCOUNT_NOT_FOUND, 503);
            default ->
                    new CreditsError(normalized, ErrorCodes.UNAUTHORIZED, 403);
        };
    }

    public static <T> ApiResponse<T> apiError(@Nullable String reason) {
        CreditsError error = resolve(reason);
        return ApiResponse.error(error.apiCode(), error.reason());
    }

    public static Map<String, Object> gatewayErrorBody(@Nullable String reason) {
        CreditsError error = resolve(reason);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("code", error.reason());
        payload.put("apiCode", error.apiCode());
        payload.put("message", error.reason());
        return Map.of("error", payload);
    }
}

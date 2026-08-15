package com.webchat.platformapi.ai.chat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChatStreamProcessorTest {

    @Test
    void resolveCreditsErrorMessageExplainsInsufficientBalance() {
        assertEquals(
                "Credits 余额不足，请联系管理员充值",
                ChatStreamProcessor.resolveCreditsErrorMessage("credits_insufficient")
        );
    }

    @Test
    void resolveCreditsErrorMessageExplainsReserveFailure() {
        assertEquals(
                "Credits 预扣失败，请稍后再试",
                ChatStreamProcessor.resolveCreditsErrorMessage("credits_reserve_failed")
        );
    }

    @Test
    void resolveCreditsErrorMessageExplainsModelRestriction() {
        assertEquals(
                "当前角色无权使用此模型",
                ChatStreamProcessor.resolveCreditsErrorMessage("model_not_allowed")
        );
    }

    @Test
    void resolveUserFacingErrorMessageExplainsAuthFailure() {
        assertEquals(
                "当前渠道鉴权失败，请联系管理员更新渠道密钥",
                ChatStreamProcessor.resolveUserFacingErrorMessage("http_error", 401, "status=401")
        );
    }

    @Test
    void resolveUserFacingErrorMessageExplainsUpstream5xx() {
        assertEquals(
                "上游服务异常 (HTTP 503)，请稍后再试",
                ChatStreamProcessor.resolveUserFacingErrorMessage("http_error", 503, "status=503")
        );
    }

    @Test
    void resolveUserFacingErrorMessageExplainsDecryptFailure() {
        assertEquals(
                "当前渠道密钥配置无效，请联系管理员检查渠道密钥",
                ChatStreamProcessor.resolveUserFacingErrorMessage("decrypt_failed", null, "decrypt failed")
        );
    }

    @Test
    void resolveUserFacingErrorMessageFallsBackForNoChannel() {
        assertEquals(
                "当前没有可用渠道，请稍后再试",
                ChatStreamProcessor.resolveUserFacingErrorMessage("no_channel", null, "")
        );
    }
    @Test
    void resolveCreditsErrorMessageExplainsPolicyUnavailable() {
        assertEquals(
                "Credits policy temporarily unavailable; please try again later",
                ChatStreamProcessor.resolveCreditsErrorMessage("credits_policy_unavailable")
        );
    }
}

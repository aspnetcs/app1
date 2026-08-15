package com.webchat.platformapi.auth.verification;

import com.webchat.platformapi.common.api.ApiResponse;
import com.webchat.platformapi.common.api.ErrorCodes;
import com.webchat.platformapi.common.util.RequestUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/risk/captcha")
public class CaptchaV1Controller {

    private final CaptchaService captchaService;

    public CaptchaV1Controller(CaptchaService captchaService) {
        this.captchaService = captchaService;
    }

    /**
     * 生成验证码（随机 slider / math / text）
     */
    @GetMapping("/generate")
    public ApiResponse<Map<String, Object>> generate(HttpServletRequest request) {
        if (!captchaService.allowGenerate(RequestUtils.clientIp(request))) {
            return ApiResponse.error(ErrorCodes.RATE_LIMIT, "请求过于频繁，请稍后再试");
        }
        try {
            return ApiResponse.ok(captchaService.generateCaptcha());
        } catch (Exception e) {
            return ApiResponse.error(ErrorCodes.SERVER_ERROR,
                    "captcha generation failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /**
     * 统一验证入口
     * body: { captchaId: "...", data: { ... } }
     * - math:   data = { answer: 12 }
     * - slider: data = { movePercent: 0.45, tracks: [...], duration: 500 }
     * - text:   data = { points: [{ px: 0.5, py: 0.3 }, ...] }
     */
    @PostMapping("/verify")
    @SuppressWarnings("unchecked")
    public ApiResponse<Map<String, Object>> verify(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        if (!captchaService.allowVerify(RequestUtils.clientIp(request))) {
            return ApiResponse.error(ErrorCodes.RATE_LIMIT, "请求过于频繁，请稍后再试");
        }
        Object captchaIdObj = body == null ? null : body.get("captchaId");
        String captchaId = captchaIdObj == null ? null : String.valueOf(captchaIdObj);
        Object dataObj = body == null ? null : body.get("data");

        if (captchaId == null || captchaId.isBlank() || !(dataObj instanceof Map<?, ?> data)) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, "参数缺失");
        }

        String token = captchaService.verifyCaptchaAndIssueChallenge(captchaId, (Map<String, Object>) data, RequestUtils.clientIp(request));
        if (token == null) {
            return ApiResponse.error(ErrorCodes.CAPTCHA_FAILED, "验证失败或已过期");
        }

        return ApiResponse.ok("验证通过", Map.of("token", token));
    }
}

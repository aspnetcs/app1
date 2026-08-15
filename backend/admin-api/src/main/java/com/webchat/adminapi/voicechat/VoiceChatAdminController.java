package com.webchat.adminapi.voicechat;

import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.ai.audio.VoiceChatProperties;
import com.webchat.platformapi.common.api.ApiResponse;
import com.webchat.platformapi.common.api.ErrorCodes;
import com.webchat.platformapi.config.SysConfigService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/voice-chat")
@ConditionalOnProperty(name = {"platform.dev-panel", "platform.voice-chat.enabled"}, havingValue = "true", matchIfMissing = true)
public class VoiceChatAdminController {
    private static final String CFG_VOICE_CHAT_ENABLED = "voicechat.enabled";
    private static final String CFG_VOICE_CHAT_MAX_DURATION = "voicechat.max_duration_seconds";
    private static final String CFG_VOICE_CHAT_STT_MODEL = "voicechat.stt_model";
    private static final String CFG_VOICE_CHAT_TTS_MODEL = "voicechat.tts_model";
    private static final String CFG_VOICE_CHAT_TTS_VOICE = "voicechat.tts_voice";

    private final VoiceChatProperties properties;
    private final SysConfigService sysConfigService;

    public VoiceChatAdminController(VoiceChatProperties properties, SysConfigService sysConfigService) {
        this.properties = properties;
        this.sysConfigService = sysConfigService;
    }

    @GetMapping("/config")
    public ApiResponse<Map<String, Object>> getConfig(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role
    ) {
        if (userId == null || !"admin".equalsIgnoreCase(role)) {
            return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "admin only");
        }
        return ApiResponse.ok(Map.of(
                "enabled", properties.isEnabled(),
                "maxDurationSeconds", properties.getMaxDurationSeconds(),
                "sttModel", properties.getSttModel(),
                "ttsModel", properties.getTtsModel(),
                "ttsVoice", properties.getTtsVoice()
        ));
    }

    @PutMapping("/config")
    public ApiResponse<Map<String, Object>> updateConfig(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role,
            @RequestBody Map<String, Object> body
    ) {
        if (userId == null || !"admin".equalsIgnoreCase(role)) {
            return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "admin only");
        }
        if (body.containsKey("enabled")) {
            boolean enabled = Boolean.parseBoolean(String.valueOf(body.get("enabled")));
            properties.setEnabled(enabled);
            sysConfigService.set(CFG_VOICE_CHAT_ENABLED, String.valueOf(enabled));
        }
        if (body.containsKey("maxDurationSeconds")) {
            Integer maxDuration = parseInt(body.get("maxDurationSeconds"));
            if (maxDuration == null || maxDuration < 1) {
                return ApiResponse.error(ErrorCodes.PARAM_MISSING, "maxDurationSeconds must be >= 1");
            }
            properties.setMaxDurationSeconds(maxDuration);
            sysConfigService.set(CFG_VOICE_CHAT_MAX_DURATION, String.valueOf(maxDuration));
        }
        if (body.containsKey("sttModel")) {
            String sttModel = String.valueOf(body.get("sttModel")).trim();
            properties.setSttModel(sttModel);
            sysConfigService.set(CFG_VOICE_CHAT_STT_MODEL, sttModel);
        }
        if (body.containsKey("ttsModel")) {
            String ttsModel = String.valueOf(body.get("ttsModel")).trim();
            properties.setTtsModel(ttsModel);
            sysConfigService.set(CFG_VOICE_CHAT_TTS_MODEL, ttsModel);
        }
        if (body.containsKey("ttsVoice")) {
            String ttsVoice = String.valueOf(body.get("ttsVoice")).trim();
            properties.setTtsVoice(ttsVoice);
            sysConfigService.set(CFG_VOICE_CHAT_TTS_VOICE, ttsVoice);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("enabled", properties.isEnabled());
        result.put("maxDurationSeconds", properties.getMaxDurationSeconds());
        result.put("sttModel", properties.getSttModel());
        result.put("ttsModel", properties.getTtsModel());
        result.put("ttsVoice", properties.getTtsVoice());
        return ApiResponse.ok(result);
    }

    private static Integer parseInt(Object raw) {
        if (raw == null) return null;
        if (raw instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(String.valueOf(raw).trim());
        } catch (Exception e) {
            return null;
        }
    }
}





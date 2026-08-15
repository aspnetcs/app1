package com.webchat.platformapi.auth.verification;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CaptchaServiceTest {

    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> valueOps;

    private CaptchaService captchaService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(valueOps);
        captchaService = new CaptchaService(redis, objectMapper);
    }

    @Test
    void generateMathCaptcha() {
        Map<String, Object> result = captchaService.generateMathCaptcha();

        assertNotNull(result.get("captchaId"));
        assertEquals("math", result.get("type"));

        String mathImage = (String) result.get("mathImage");
        assertNotNull(mathImage);
        assertTrue(mathImage.startsWith("data:image/"));

        verify(valueOps).set(startsWith("captcha:"), anyString(), any(Duration.class));
    }

    @Test
    void generateCaptchaReturnsOneOfThreeTypes() {
        Map<String, Object> result = captchaService.generateCaptcha();

        assertNotNull(result.get("captchaId"));
        String type = (String) result.get("type");
        assertTrue(type.equals("math") || type.equals("slider") || type.equals("text"),
                "Expected type to be one of: math, slider, text but was: " + type);
    }

    @Test
    void verifyCorrectMathAnswer() throws Exception {
        String captchaId = "test-id";
        String key = "captcha:" + captchaId;
        int correctAnswer = 7;

        String json = objectMapper.writeValueAsString(Map.of("type", "math", "answer", correctAnswer));
        when(valueOps.get(key)).thenReturn(json);
        when(redis.delete(key)).thenReturn(true);

        String token = captchaService.verifyCaptchaAndIssueChallenge(captchaId, Map.of("answer", correctAnswer));

        assertNotNull(token);
        assertTrue(token.startsWith("CT_"));
        verify(redis).delete(key);
        verify(valueOps).set(startsWith("challenge:"), anyString(), any(Duration.class));
    }

    @Test
    void verifyWrongMathAnswer() throws Exception {
        String captchaId = "test-id";
        String key = "captcha:" + captchaId;

        String json = objectMapper.writeValueAsString(Map.of("type", "math", "answer", 7));
        when(valueOps.get(key)).thenReturn(json);
        when(redis.delete(key)).thenReturn(true);

        String result = captchaService.verifyCaptchaAndIssueChallenge(captchaId, Map.of("answer", 99));
        assertNull(result);
    }

    @Test
    void verifyExpiredCaptcha() {
        when(valueOps.get(startsWith("captcha:"))).thenReturn(null);

        String result = captchaService.verifyCaptchaAndIssueChallenge("expired-id", Map.of("answer", 1));
        assertNull(result);
    }

    @Test
    void verifySliderCorrect() throws Exception {
        String captchaId = "slider-test";
        String key = "captcha:" + captchaId;
        double percentX = 0.45;

        String json = objectMapper.writeValueAsString(Map.of("type", "slider", "percentX", percentX));
        when(valueOps.get(key)).thenReturn(json);
        when(redis.delete(key)).thenReturn(true);

        String token = captchaService.verifyCaptchaAndIssueChallenge(captchaId, Map.of(
                "movePercent", 0.46,
                "duration", 520,
                "tracks", List.of(
                        Map.of("x", 10, "y", 100),
                        Map.of("x", 20, "y", 101),
                        Map.of("x", 30, "y", 100),
                        Map.of("x", 40, "y", 102),
                        Map.of("x", 50, "y", 101)
                )
        ));
        assertNotNull(token);
        assertTrue(token.startsWith("CT_"));
    }

    @Test
    void verifySliderTooFar() throws Exception {
        String captchaId = "slider-test";
        String key = "captcha:" + captchaId;

        String json = objectMapper.writeValueAsString(Map.of("type", "slider", "percentX", 0.45));
        when(valueOps.get(key)).thenReturn(json);
        when(redis.delete(key)).thenReturn(true);

        String result = captchaService.verifyCaptchaAndIssueChallenge(captchaId, Map.of(
                "movePercent", 0.9,
                "duration", 520,
                "tracks", List.of(
                        Map.of("x", 10, "y", 100),
                        Map.of("x", 20, "y", 101),
                        Map.of("x", 30, "y", 100),
                        Map.of("x", 40, "y", 102),
                        Map.of("x", 50, "y", 101)
                )
        ));
        assertNull(result);
    }

    @Test
    void verifySliderMissingTracksFails() throws Exception {
        String captchaId = "slider-missing";
        String key = "captcha:" + captchaId;

        String json = objectMapper.writeValueAsString(Map.of("type", "slider", "percentX", 0.45));
        when(valueOps.get(key)).thenReturn(json);
        when(redis.delete(key)).thenReturn(true);

        String result = captchaService.verifyCaptchaAndIssueChallenge(captchaId, Map.of(
                "movePercent", 0.45
        ));
        assertNull(result);
    }

    @Test
    void verifyTextCorrect() throws Exception {
        String captchaId = "text-test";
        String key = "captcha:" + captchaId;

        String json = objectMapper.writeValueAsString(Map.of(
                "type", "text",
                "radius", 30,
                "points", List.of(
                        Map.of("x", 300, "y", 120),
                        Map.of("x", 220, "y", 200),
                        Map.of("x", 420, "y", 260)
                )
        ));
        when(valueOps.get(key)).thenReturn(json);
        when(redis.delete(key)).thenReturn(true);

        String token = captchaService.verifyCaptchaAndIssueChallenge(captchaId, Map.of(
                "points", List.of(
                        Map.of("px", 0.5, "py", 120.0 / 360.0),
                        Map.of("px", 220.0 / 600.0, "py", 200.0 / 360.0),
                        Map.of("px", 420.0 / 600.0, "py", 260.0 / 360.0)
                )
        ));

        assertNotNull(token);
        assertTrue(token.startsWith("CT_"));
    }
}

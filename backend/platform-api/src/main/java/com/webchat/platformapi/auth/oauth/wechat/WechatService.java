package com.webchat.platformapi.auth.oauth.wechat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

@Service
public class WechatService {

    private static final Duration ACCESS_TOKEN_SAFETY_MARGIN = Duration.ofSeconds(60);

    private final WechatProperties properties;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public WechatService(WechatProperties properties, StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.properties = properties;
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    public record WechatSession(String openid, String unionid, String sessionKey) {}

    public WechatSession jscode2session(String brand, String code) throws WechatException {
        String effectiveBrand = resolveBrand(brand);
        WechatProperties.WechatApp app = requireApp(effectiveBrand);

        String url = "https://api.weixin.qq.com/sns/jscode2session" +
                "?appid=" + urlEncode(app.getAppid()) +
                "&secret=" + urlEncode(app.getSecret()) +
                "&js_code=" + urlEncode(code) +
                "&grant_type=authorization_code";

        Map<String, Object> resp = getJson(url);
        int errcode = intVal(resp.get("errcode"));
        if (errcode != 0) {
            throw new WechatException(errcode, str(resp.get("errmsg")));
        }

        String openid = str(resp.get("openid"));
        String unionid = str(resp.get("unionid"));
        String sessionKey = str(resp.get("session_key"));
        if (openid == null || openid.isBlank()) throw new WechatException(-1, "missing openid");
        return new WechatSession(openid, unionid, sessionKey);
    }

    public String getPhoneNumber(String brand, String phoneCode) throws WechatException {
        String effectiveBrand = resolveBrand(brand);
        String accessToken = getAccessToken(effectiveBrand);

        String url = "https://api.weixin.qq.com/wxa/business/getuserphonenumber?access_token=" + urlEncode(accessToken);
        Map<String, Object> resp = postJson(url, Map.of("code", phoneCode));

        int errcode = intVal(resp.get("errcode"));
        if (errcode != 0) {
            throw new WechatException(errcode, str(resp.get("errmsg")));
        }

        Object phoneInfoObj = resp.get("phone_info");
        if (!(phoneInfoObj instanceof Map<?, ?> phoneInfo)) {
            throw new WechatException(-1, "missing phone_info");
        }
        Object phoneNumberObj = phoneInfo.get("phoneNumber");
        String phone = str(phoneNumberObj);
        if (phone == null || phone.isBlank()) throw new WechatException(-1, "missing phoneNumber");
        return phone;
    }

    private String getAccessToken(String brand) throws WechatException {
        String key = "wx_access_token:" + brand;
        String cached;
        try {
            cached = redis.opsForValue().get(key);
        } catch (Exception e) {
            throw new WechatException(-1, "wechat token cache unavailable");
        }
        if (cached != null && !cached.isBlank()) return cached;

        WechatProperties.WechatApp app = requireApp(brand);
        String url = "https://api.weixin.qq.com/cgi-bin/token" +
                "?grant_type=client_credential" +
                "&appid=" + urlEncode(app.getAppid()) +
                "&secret=" + urlEncode(app.getSecret());

        Map<String, Object> resp = getJson(url);
        int errcode = intVal(resp.get("errcode"));
        if (errcode != 0) {
            throw new WechatException(errcode, str(resp.get("errmsg")));
        }

        String token = str(resp.get("access_token"));
        int expiresIn = intVal(resp.get("expires_in"));
        if (token == null || token.isBlank()) throw new WechatException(-1, "missing access_token");

        long ttlSeconds = Math.max(30, expiresIn - (int) ACCESS_TOKEN_SAFETY_MARGIN.toSeconds());
        try {
            redis.opsForValue().set(key, token, Duration.ofSeconds(ttlSeconds));
        } catch (Exception e) {
            throw new WechatException(-1, "wechat token cache unavailable");
        }
        return token;
    }

    private Map<String, Object> getJson(String url) throws WechatException {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                throw new WechatException(resp.statusCode(), "http status=" + resp.statusCode());
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> map = objectMapper.readValue(resp.body(), Map.class);
            return map;
        } catch (WechatException e) {
            throw e;
        } catch (Exception e) {
            throw new WechatException(-1, e.getMessage() == null ? "http get failed" : e.getMessage());
        }
    }

    private Map<String, Object> postJson(String url, Object body) throws WechatException {
        try {
            String json = objectMapper.writeValueAsString(body);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                throw new WechatException(resp.statusCode(), "http status=" + resp.statusCode());
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> map = objectMapper.readValue(resp.body(), Map.class);
            return map;
        } catch (WechatException e) {
            throw e;
        } catch (Exception e) {
            throw new WechatException(-1, e.getMessage() == null ? "http post failed" : e.getMessage());
        }
    }

    private String resolveBrand(String brand) {
        if (brand != null && !brand.isBlank()) return brand.trim();
        if (properties.getDefaultBrand() != null && !properties.getDefaultBrand().isBlank()) return properties.getDefaultBrand().trim();
        if (properties.getApps() != null && properties.getApps().size() == 1) return properties.getApps().keySet().iterator().next();
        return null;
    }

    private WechatProperties.WechatApp requireApp(String brand) throws WechatException {
        if (brand == null || brand.isBlank()) throw new WechatException(-1, "brand is required");
        WechatProperties.WechatApp app = properties.getApps() == null ? null : properties.getApps().get(brand);
        if (app == null) throw new WechatException(-1, "unknown brand: " + brand);
        if (app.getAppid() == null || app.getAppid().isBlank()) throw new WechatException(-1, "missing appid for brand: " + brand);
        if (app.getSecret() == null || app.getSecret().isBlank()) throw new WechatException(-1, "missing secret for brand: " + brand);
        return app;
    }

    private static String urlEncode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String str(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
    }

    private static int intVal(Object v) {
        if (v instanceof Number n) return n.intValue();
        if (v == null) return 0;
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (Exception e) {
            return 0;
        }
    }

    public static class WechatException extends Exception {
        private final int errcode;

        public WechatException(int errcode, String message) {
            super(message == null ? "" : message);
            this.errcode = errcode;
        }

        public int getErrcode() {
            return errcode;
        }
    }
}


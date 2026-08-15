package com.webchat.platformapi.auth.v1;

import com.webchat.platformapi.auth.session.AuthTokenService;
import com.webchat.platformapi.auth.guest.GuestHistoryLoginSupport;
import com.webchat.platformapi.infra.config.BrandResolver;
import com.webchat.platformapi.common.api.ApiResponse;
import com.webchat.platformapi.common.api.ErrorCodes;
import com.webchat.platformapi.common.util.RequestUtils;
import com.webchat.platformapi.common.util.UserInfoUtils;
import com.webchat.platformapi.user.identity.UserIdentityEntity;
import com.webchat.platformapi.user.identity.UserIdentityRepository;
import com.webchat.platformapi.user.UserEntity;
import com.webchat.platformapi.user.UserRepository;
import com.webchat.platformapi.auth.oauth.wechat.WechatService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
public class WechatAuthV1Controller {

    private static final Logger log = LoggerFactory.getLogger(WechatAuthV1Controller.class);

    private final WechatService wechatService;
    private final BrandResolver brandResolver;
    private final UserRepository userRepository;
    private final UserIdentityRepository userIdentityRepository;
    private final AuthTokenService authTokenService;
    private final GuestHistoryLoginSupport guestHistoryLoginSupport;

    public WechatAuthV1Controller(
            WechatService wechatService,
            BrandResolver brandResolver,
            UserRepository userRepository,
            UserIdentityRepository userIdentityRepository,
            AuthTokenService authTokenService,
            GuestHistoryLoginSupport guestHistoryLoginSupport
    ) {
        this.wechatService = wechatService;
        this.brandResolver = brandResolver;
        this.userRepository = userRepository;
        this.userIdentityRepository = userIdentityRepository;
        this.authTokenService = authTokenService;
        this.guestHistoryLoginSupport = guestHistoryLoginSupport;
    }

    @PostMapping("/wx-login")
    public ApiResponse<Map<String, Object>> wxLogin(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        String brand = brandResolver.resolve(str(body, "brand"), request);
        String code = str(body, "code");
        if (code == null) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, "参数缺失");
        }

        WechatService.WechatSession session;
        try {
            session = wechatService.jscode2session(brand, code);
        } catch (WechatService.WechatException e) {
            log.warn("[wechat-auth] wxLogin jscode2session failed for brand {}: {}", brand, e.getMessage());
            return ApiResponse.error(ErrorCodes.WX_API_ERROR, "微信登录暂时不可用");
        }

        UserResolveResult result;
        try {
            result = resolveUserByWechat(brand, session.openid(), session.unionid());
        } catch (ConflictException e) {
            return ApiResponse.error(ErrorCodes.WX_LOGIN_FAILED, "账号冲突，请联系管理员");
        }

        AuthTokenService.DeviceInfo device = deviceInfo(body);
        if (brand != null && !brand.isBlank()) {
            String deviceType = device == null ? null : device.deviceType();
            String deviceName = device == null ? null : device.deviceName();
            device = new AuthTokenService.DeviceInfo(deviceType, deviceName, brand);
        }

        guestHistoryLoginSupport.migrateQuietly(request, result.user().getId(), "wechat-auth");
        AuthTokenService.IssuedTokens tokens = authTokenService.issue(
                result.user(),
                device,
                RequestUtils.clientIp(request),
                RequestUtils.userAgent(request)
        );

        return ApiResponse.ok("登录成功", Map.of(
                "token", tokens.accessToken(),
                "accessToken", tokens.accessToken(),
                "refreshToken", tokens.refreshToken(),
                "expiresIn", tokens.accessExpiresIn(),
                "refreshExpiresIn", tokens.refreshExpiresIn(),
                "sessionId", tokens.sessionId().toString(),
                "userInfo", UserInfoUtils.toUserInfo(result.user(), true),
                "isNewUser", result.isNew()
        ));
    }

    @PostMapping("/wx-phone-login")
    public ApiResponse<Map<String, Object>> wxPhoneLogin(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        String brand = brandResolver.resolve(str(body, "brand"), request);
        String code = RequestUtils.firstNonBlank(str(body, "code"), str(body, "wxLoginCode"));
        String phoneCode = RequestUtils.firstNonBlank(str(body, "phoneCode"), str(body, "phone_code"));
        if (code == null || phoneCode == null) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, "参数缺失");
        }

        WechatService.WechatSession session;
        try {
            session = wechatService.jscode2session(brand, code);
        } catch (WechatService.WechatException e) {
            log.warn("[wechat-auth] wxPhoneLogin jscode2session failed for brand {}: {}", brand, e.getMessage());
            return ApiResponse.error(ErrorCodes.WX_API_ERROR, "微信登录暂时不可用");
        }

        String phoneRaw;
        try {
            phoneRaw = wechatService.getPhoneNumber(brand, phoneCode);
        } catch (WechatService.WechatException e) {
            log.warn("[wechat-auth] getPhoneNumber failed for brand {}: {}", brand, e.getMessage());
            return ApiResponse.error(ErrorCodes.WX_API_ERROR, "微信手机号获取失败");
        }

        String phone = normalizeCnPhone(phoneRaw);
        if (!RequestUtils.isValidPhone(phone)) {
            return ApiResponse.error(ErrorCodes.INVALID_PHONE, "手机号无效");
        }

        UserEntity identityUser;
        try {
            identityUser = findUserByWechatIdentities(brand, session.openid(), session.unionid());
        } catch (ConflictException e) {
            return ApiResponse.error(ErrorCodes.WX_PHONE_FAILED, "账号冲突，请联系管理员");
        }
        UserEntity phoneUser = userRepository.findByPhoneAndDeletedAtIsNull(phone).orElse(null);

        if (identityUser != null && phoneUser != null && !identityUser.getId().equals(phoneUser.getId())) {
            return ApiResponse.error(ErrorCodes.WX_PHONE_FAILED, "账号冲突，请联系管理员");
        }

        boolean isNew = false;
        UserEntity user = phoneUser != null ? phoneUser : identityUser;
        if (user == null) {
            user = new UserEntity();
            user.setPhone(phone);
            user = userRepository.save(user);
            isNew = true;
        } else if (user.getPhone() == null || user.getPhone().isBlank()) {
            user.setPhone(phone);
            try {
                user = userRepository.save(user);
            } catch (Exception e) {
                return ApiResponse.error(ErrorCodes.USER_EXISTS, "手机号已被占用");
            }
        } else if (!user.getPhone().equals(phone)) {
            return ApiResponse.error(ErrorCodes.WX_PHONE_FAILED, "手机号冲突");
        }

        try {
            ensureIdentity("system", "global", "phone", phone, user.getId(), true);
            ensureWechatIdentities(brand, session.openid(), session.unionid(), user.getId());
        } catch (ConflictException e) {
            return ApiResponse.error(ErrorCodes.WX_PHONE_FAILED, "账号冲突，请联系管理员");
        }

        AuthTokenService.DeviceInfo device = deviceInfo(body);
        if (brand != null && !brand.isBlank()) {
            String deviceType = device == null ? null : device.deviceType();
            String deviceName = device == null ? null : device.deviceName();
            device = new AuthTokenService.DeviceInfo(deviceType, deviceName, brand);
        }

        guestHistoryLoginSupport.migrateQuietly(request, user.getId(), "wechat-auth");
        AuthTokenService.IssuedTokens tokens = authTokenService.issue(
                user,
                device,
                RequestUtils.clientIp(request),
                RequestUtils.userAgent(request)
        );

        return ApiResponse.ok("登录成功", Map.of(
                "token", tokens.accessToken(),
                "accessToken", tokens.accessToken(),
                "refreshToken", tokens.refreshToken(),
                "expiresIn", tokens.accessExpiresIn(),
                "refreshExpiresIn", tokens.refreshExpiresIn(),
                "sessionId", tokens.sessionId().toString(),
                "userInfo", UserInfoUtils.toUserInfo(user, true),
                "isNewUser", isNew
        ));
    }

    private record UserResolveResult(UserEntity user, boolean isNew) {}

    private UserResolveResult resolveUserByWechat(String brand, String openid, String unionid) throws ConflictException {
        UserEntity existing = findUserByWechatIdentities(brand, openid, unionid);
        if (existing != null) {
            ensureWechatIdentities(brand, openid, unionid, existing.getId());
            return new UserResolveResult(existing, false);
        }

        UserEntity user = new UserEntity();
        user = userRepository.save(user);
        ensureWechatIdentities(brand, openid, unionid, user.getId());
        return new UserResolveResult(user, true);
    }

    private UserEntity findUserByWechatIdentities(String brand, String openid, String unionid) throws ConflictException {
        UUID byUnion = null;
        if (unionid != null && !unionid.isBlank()) {
            UserIdentityEntity unionIdentity = userIdentityRepository
                    .findFirstByProviderAndProviderScopeAndTypeAndIdentifier("wechat", "global", "unionid", unionid)
                    .orElse(null);
            if (unionIdentity != null) {
                byUnion = unionIdentity.getUserId();
            }
        }

        UUID byOpenid = null;
        if (openid != null && !openid.isBlank() && brand != null && !brand.isBlank()) {
            UserIdentityEntity openIdentity = userIdentityRepository
                    .findFirstByProviderAndProviderScopeAndTypeAndIdentifier("wechat", brand, "openid", openid)
                    .orElse(null);
            if (openIdentity != null) {
                byOpenid = openIdentity.getUserId();
            }
        }

        if (byUnion != null && byOpenid != null && !byUnion.equals(byOpenid)) {
            throw new ConflictException();
        }

        UUID userId = byUnion != null ? byUnion : byOpenid;
        if (userId == null) {
            return null;
        }
        return userRepository.findByIdAndDeletedAtIsNull(userId).orElse(null);
    }

    private void ensureWechatIdentities(String brand, String openid, String unionid, UUID userId) throws ConflictException {
        if (brand != null && !brand.isBlank() && openid != null && !openid.isBlank()) {
            ensureIdentity("wechat", brand, "openid", openid, userId, true);
        }
        if (unionid != null && !unionid.isBlank()) {
            ensureIdentity("wechat", "global", "unionid", unionid, userId, true);
        }
    }

    private void ensureIdentity(String provider, String scope, String type, String identifier, UUID userId, boolean verified) throws ConflictException {
        UserIdentityEntity existing = userIdentityRepository
                .findFirstByProviderAndProviderScopeAndTypeAndIdentifier(provider, scope, type, identifier)
                .orElse(null);
        if (existing != null) {
            if (!existing.getUserId().equals(userId)) {
                throw new ConflictException();
            }
            return;
        }

        UserIdentityEntity entity = new UserIdentityEntity();
        entity.setUserId(userId);
        entity.setProvider(provider);
        entity.setProviderScope(scope);
        entity.setType(type);
        entity.setIdentifier(identifier);
        entity.setVerified(verified);
        try {
            userIdentityRepository.save(entity);
        } catch (Exception ex) {
            throw new ConflictException();
        }
    }

    private static String str(Map<String, Object> body, String key) {
        if (body == null) return null;
        Object value = body.get(key);
        if (value == null) return null;
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private static String normalizeCnPhone(String raw) {
        if (raw == null) return null;
        String value = raw.replace(" ", "");
        if (value.startsWith("+86")) value = value.substring(3);
        if (value.startsWith("86") && value.length() == 13) value = value.substring(2);
        return value;
    }

    private static AuthTokenService.DeviceInfo deviceInfo(Map<String, Object> body) {
        if (body == null) return null;
        Object deviceObj = body.get("device");
        Map<String, Object> device = deviceObj instanceof Map<?, ?> raw ? castMap(raw) : null;

        String deviceType = RequestUtils.firstNonBlank(str(device, "deviceType"), str(body, "deviceType"));
        String deviceName = RequestUtils.firstNonBlank(str(device, "deviceName"), str(body, "deviceName"));
        String brand = RequestUtils.firstNonBlank(str(body, "brand"), str(device, "brand"));
        if ((deviceType == null || deviceType.isBlank())
                && (deviceName == null || deviceName.isBlank())
                && (brand == null || brand.isBlank())) {
            return null;
        }
        return new AuthTokenService.DeviceInfo(deviceType, deviceName, brand);
    }

    private static Map<String, Object> castMap(Map<?, ?> raw) {
        Map<String, Object> output = new HashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (entry.getKey() == null) continue;
            output.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return output;
    }

    private static class ConflictException extends Exception {
    }
}

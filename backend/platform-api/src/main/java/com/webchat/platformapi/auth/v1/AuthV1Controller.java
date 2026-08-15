package com.webchat.platformapi.auth.v1;

import com.webchat.platformapi.audit.AuditService;
import org.springframework.dao.DataIntegrityViolationException;
import com.webchat.platformapi.auth.credential.UserCredentialEntity;
import com.webchat.platformapi.auth.credential.UserCredentialRepository;
import com.webchat.platformapi.auth.guest.GuestHistoryLoginSupport;
import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.auth.session.AuthTokenService;
import com.webchat.platformapi.auth.session.DeviceSessionEntity;
import com.webchat.platformapi.auth.session.DeviceSessionRepository;
import com.webchat.platformapi.auth.v1.dto.*;
import com.webchat.platformapi.auth.verification.RedisVerificationService;
import com.webchat.platformapi.common.api.ApiResponse;
import com.webchat.platformapi.common.api.ErrorCodes;
import com.webchat.platformapi.common.util.RequestUtils;
import com.webchat.platformapi.common.util.UserInfoUtils;
import com.webchat.platformapi.user.identity.UserIdentityEntity;
import com.webchat.platformapi.user.identity.UserIdentityRepository;
import com.webchat.platformapi.user.UserEntity;
import com.webchat.platformapi.user.UserRepository;
import com.webchat.platformapi.user.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Legacy auth workflow facade kept as an internal bean while the public HTTP
 * surface is split across focused controllers.
 */
@Service
public class AuthV1Controller {

    private static final Logger log = LoggerFactory.getLogger(AuthV1Controller.class);

    private static final int PASSWORD_MAX_LEN = 1024;
    private static final Duration IDENTIFIER_CHECK_WINDOW = Duration.ofMinutes(1);
    private static final int IDENTIFIER_CHECK_LIMIT_PER_IP = 20;
    private static final int IDENTIFIER_CHECK_LIMIT_PER_IDENTIFIER = 8;

    private final RedisVerificationService verificationService;
    private final UserService userService;
    private final UserRepository userRepository;
    private final UserCredentialRepository credentialRepository;
    private final UserIdentityRepository userIdentityRepository;
    private final DeviceSessionRepository deviceSessionRepository;
    private final AuthTokenService authTokenService;
    private final AuditService auditService;
    private final GuestHistoryLoginSupport guestHistoryLoginSupport;
    private final PasswordLoginService passwordLoginService;
    private final StringRedisTemplate redis;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthV1Controller(
            RedisVerificationService verificationService,
            UserService userService,
            UserRepository userRepository,
            UserCredentialRepository credentialRepository,
            UserIdentityRepository userIdentityRepository,
            DeviceSessionRepository deviceSessionRepository,
            AuthTokenService authTokenService,
            AuditService auditService,
            GuestHistoryLoginSupport guestHistoryLoginSupport,
            PasswordLoginService passwordLoginService,
            StringRedisTemplate redis
    ) {
        this.verificationService = verificationService;
        this.userService = userService;
        this.userRepository = userRepository;
        this.credentialRepository = credentialRepository;
        this.userIdentityRepository = userIdentityRepository;
        this.deviceSessionRepository = deviceSessionRepository;
        this.authTokenService = authTokenService;
        this.auditService = auditService;
        this.guestHistoryLoginSupport = guestHistoryLoginSupport;
        this.passwordLoginService = passwordLoginService;
        this.redis = redis;
    }

    // ======================= 标识符检查 =======================

    public ApiResponse<Map<String, Object>> checkIdentifier(String identifierRaw, HttpServletRequest request) {
        String identifier = RequestUtils.trimOrNull(identifierRaw);
        if (identifier == null) {
            return ApiResponse.ok(Map.of("type", "unknown", "hasPassword", false));
        }

        boolean isPhone = RequestUtils.isValidPhone(identifier);
        boolean isEmail = RequestUtils.isValidEmail(identifier);
        String type = isPhone ? "phone" : isEmail ? "email" : "unknown";

        if (!isPhone && !isEmail) {
            return ApiResponse.ok(Map.of("type", type, "hasPassword", false));
        }

        if (!allowIdentifierCheck(identifier, RequestUtils.clientIp(request))) {
            return ApiResponse.error(ErrorCodes.RATE_LIMIT, "操作过于频繁，请稍后再试");
        }

        UserEntity user = isPhone
                ? userRepository.findByPhoneAndDeletedAtIsNull(identifier).orElse(null)
                : userRepository.findByEmailAndDeletedAtIsNull(identifier).orElse(null);
        boolean hasPassword = user != null && credentialRepository.findById(user.getId()).isPresent();
        return ApiResponse.ok(Map.of("type", type, "hasPassword", hasPassword));
    }

    // ======================= 验证码 =======================

    @PostMapping("/sms/send-code")
    public ApiResponse<Void> smsSendCode(@RequestBody SmsSendCodeRequest req, HttpServletRequest request) {
        String phone = RequestUtils.trimOrNull(req.phone());
        String purpose = RequestUtils.trimOrNull(req.purpose());
        String challengeToken = RequestUtils.trimOrNull(req.challengeToken());

        if (!RequestUtils.isValidPhone(phone)) return ApiResponse.error(ErrorCodes.INVALID_PHONE, "请输入正确的手机号");
        if (purpose == null) return ApiResponse.error(ErrorCodes.PARAM_MISSING, "参数缺失");
        if (!consumeChallengeToken(challengeToken, request)) {
            return ApiResponse.error(ErrorCodes.CAPTCHA_FAILED, "人机验证失败或已过期，请重试");
        }

        try {
            verificationService.sendSmsCode(phone, purpose);
            return ApiResponse.ok("验证码已发送", null);
        } catch (RedisVerificationService.RateLimitException e) {
            return ApiResponse.error(ErrorCodes.RATE_LIMIT, e.getMessage());
        }
    }

    @PostMapping("/bind-email-send-code")
    public ApiResponse<Void> bindEmailSendCode(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestBody BindEmailSendCodeRequest req
    ) {
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "未登录");

        String phone = RequestUtils.trimOrNull(req.phone());
        String email = RequestUtils.trimOrNull(req.email());
        String purpose = RequestUtils.trimOrNull(req.purpose());
        if (purpose == null) purpose = "bind_email";
        if (!RequestUtils.isValidEmail(email)) return ApiResponse.error(ErrorCodes.INVALID_EMAIL, "请输入正确的邮箱地址");

        UserEntity user = userRepository.findByIdAndDeletedAtIsNull(userId).orElse(null);
        if (user == null) return ApiResponse.error(ErrorCodes.USER_NOT_FOUND, "用户不存在");

        if (user.getPhone() != null && !user.getPhone().isBlank()) {
            if (phone == null || !phone.equals(user.getPhone())) {
                return ApiResponse.error(ErrorCodes.INVALID_PHONE, "手机号不匹配");
            }
        }

        UserEntity existing = userRepository.findByEmailAndDeletedAtIsNull(email).orElse(null);
        if (existing != null && !existing.getId().equals(userId)) {
            return ApiResponse.error(ErrorCodes.USER_EXISTS, "该邮箱已被使用");
        }

        try {
            verificationService.sendEmailCode(email, purpose);
            return ApiResponse.ok("验证码已发送", null);
        } catch (RedisVerificationService.RateLimitException e) {
            return ApiResponse.error(ErrorCodes.RATE_LIMIT, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error(ErrorCodes.SERVER_ERROR, "验证码发送失败");
        }
    }

    @PostMapping("/bind-email")
    public ApiResponse<Void> bindEmail(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestBody BindEmailRequest req
    ) {
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "未登录");
        return bindEmailWithCode(userId, req.phone(), req.email(), req.code(), req.purpose());
    }

    @PostMapping("/email/send-code")
    public ApiResponse<Void> emailSendCode(@RequestBody EmailSendCodeRequest req, HttpServletRequest request) {
        String email = RequestUtils.trimOrNull(req.email());
        String purpose = RequestUtils.trimOrNull(req.purpose());
        String challengeToken = RequestUtils.trimOrNull(req.challengeToken());

        if (!RequestUtils.isValidEmail(email)) return ApiResponse.error(ErrorCodes.INVALID_EMAIL, "请输入正确的邮箱地址");
        if (purpose == null) return ApiResponse.error(ErrorCodes.PARAM_MISSING, "参数缺失");
        if (!consumeChallengeToken(challengeToken, request)) {
            return ApiResponse.error(ErrorCodes.CAPTCHA_FAILED, "人机验证失败或已过期，请重试");
        }

        try {
            verificationService.sendEmailCode(email, purpose);
            return ApiResponse.ok("验证码已发送", null);
        } catch (RedisVerificationService.RateLimitException e) {
            return ApiResponse.error(ErrorCodes.RATE_LIMIT, e.getMessage());
        }
    }

    // ======================= 登录 =======================

    @PostMapping("/sms/login")
    public ApiResponse<Map<String, Object>> smsLogin(@RequestBody SmsLoginRequest req, HttpServletRequest request) {
        String phone = RequestUtils.trimOrNull(req.phone());
        String code = RequestUtils.trimOrNull(req.code());
        String purpose = RequestUtils.trimOrNull(req.purpose());
        if (purpose == null) purpose = "login";

        if (!RequestUtils.isValidPhone(phone)) return ApiResponse.error(ErrorCodes.INVALID_PHONE, "请输入正确的手机号");
        if (code == null) return ApiResponse.error(ErrorCodes.INVALID_CODE, "请输入验证码");

        boolean ok = verificationService.verifySmsCode(phone, purpose, code);
        if (!ok) return ApiResponse.error(ErrorCodes.INVALID_CODE, "验证码错误或已过期");

        UserService.FindOrCreateResult r = userService.findOrCreateByPhone(phone);
        UserEntity user = r.user();
        ensureSystemIdentity(user.getId(), "phone", phone, true);

        auditService.log(user.getId(), r.isNew() ? "auth.register.sms" : "auth.login.sms", Map.of(
                "purpose", purpose,
                "isNewUser", r.isNew()
        ), RequestUtils.clientIp(request), RequestUtils.userAgent(request));

        return issueTokenResponse(user, request, r.isNew() ? "注册并登录成功" : "登录成功", r.isNew());
    }

    /** compat: 兼容旧路径 */
    @PostMapping("/sms-login")
    public ApiResponse<Map<String, Object>> smsLoginCompat(@RequestBody SmsLoginRequest req, HttpServletRequest request) {
        return smsLogin(req, request);
    }

    @PostMapping("/email/login")
    public ApiResponse<Map<String, Object>> emailLogin(@RequestBody EmailLoginRequest req, HttpServletRequest request) {
        String email = RequestUtils.trimOrNull(req.email());
        String code = RequestUtils.trimOrNull(req.code());
        String purpose = RequestUtils.trimOrNull(req.purpose());
        if (purpose == null) purpose = "login";

        if (!RequestUtils.isValidEmail(email)) return ApiResponse.error(ErrorCodes.INVALID_EMAIL, "请输入正确的邮箱地址");
        if (code == null) return ApiResponse.error(ErrorCodes.INVALID_CODE, "请输入验证码");

        boolean ok = verificationService.verifyEmailCode(email, purpose, code);
        if (!ok) return ApiResponse.error(ErrorCodes.INVALID_CODE, "验证码错误或已过期");

        UserService.FindOrCreateResult r = userService.findOrCreateByEmail(email);
        UserEntity user = r.user();
        ensureSystemIdentity(user.getId(), "email", email, true);

        auditService.log(user.getId(), r.isNew() ? "auth.register.email" : "auth.login.email", Map.of(
                "purpose", purpose,
                "isNewUser", r.isNew()
        ), RequestUtils.clientIp(request), RequestUtils.userAgent(request));

        return issueTokenResponse(user, request, r.isNew() ? "注册并登录成功" : "登录成功", r.isNew());
    }

    /** compat: 兼容旧路径 */
    @PostMapping("/email-login")
    public ApiResponse<Map<String, Object>> emailLoginCompat(@RequestBody EmailLoginRequest req, HttpServletRequest request) {
        return emailLogin(req, request);
    }

    @PostMapping("/password/login")
    public ApiResponse<Map<String, Object>> passwordLogin(@RequestBody PasswordLoginRequest req, HttpServletRequest request) {
        PasswordLoginService.PasswordLoginResult result =
                passwordLoginService.authenticate(req, RequestUtils.clientIp(request), false);
        if (result.errorResponse() != null) return result.errorResponse();

        auditService.log(result.user().getId(), "auth.login.password", Map.of(
                "identifierType", result.identifierType()
        ), RequestUtils.clientIp(request), RequestUtils.userAgent(request));
        return issueTokenResponse(result.user(), request, "\u767b\u5f55\u6210\u529f", false);
    }

    /** compat: 兼容旧路径 */
    @PostMapping("/pwd-login")
    public ApiResponse<Map<String, Object>> passwordLoginCompat(@RequestBody PasswordLoginRequest req, HttpServletRequest request) {
        return passwordLogin(req, request);
    }

    // ======================= 管理员登录 =======================

    @PostMapping("/admin/login")
    public ApiResponse<Map<String, Object>> adminLogin(@RequestBody PasswordLoginRequest req, HttpServletRequest request) {
        PasswordLoginService.PasswordLoginResult result =
                passwordLoginService.authenticate(req, RequestUtils.clientIp(request), true);
        if (result.errorResponse() != null) return result.errorResponse();

        auditService.log(result.user().getId(), "auth.login.admin", Map.of(
                "identifierType", result.identifierType()
        ), RequestUtils.clientIp(request), RequestUtils.userAgent(request));
        return issueTokenResponse(result.user(), request, "\u7ba1\u7406\u5458\u767b\u5f55\u6210\u529f", false);
    }

    // ======================= 注册 =======================

    @PostMapping("/register")
    public ApiResponse<Map<String, Object>> register(@RequestBody RegisterRequest req, HttpServletRequest request) {
        String phone = RequestUtils.trimOrNull(req.phone());
        String email = RequestUtils.trimOrNull(req.email());
        String code = RequestUtils.trimOrNull(req.code());
        String password = req.password();
        String purpose = RequestUtils.trimOrNull(req.purpose());
        if (purpose == null) purpose = "register";

        boolean hasPhone = phone != null;
        boolean hasEmail = email != null;
        if (hasPhone == hasEmail) return ApiResponse.error(ErrorCodes.PARAM_MISSING, "参数缺失");

        if (code == null) return ApiResponse.error(ErrorCodes.INVALID_CODE, "请输入验证码");
        if (password == null || password.length() < 6) return ApiResponse.error(ErrorCodes.INVALID_PASSWORD, "密码至少 6 位");
        if (password.length() > PASSWORD_MAX_LEN) return ApiResponse.error(ErrorCodes.INVALID_PASSWORD, "密码长度过长");

        if (hasPhone) {
            if (!RequestUtils.isValidPhone(phone)) return ApiResponse.error(ErrorCodes.INVALID_PHONE, "请输入正确的手机号");
            boolean ok = verificationService.verifySmsCode(phone, purpose, code);
            if (!ok) return ApiResponse.error(ErrorCodes.INVALID_CODE, "验证码错误或已过期");

            UserService.FindOrCreateResult r = userService.findOrCreateByPhone(phone);
            if (!r.isNew()) return ApiResponse.error(ErrorCodes.USER_EXISTS, "用户已存在");

            UserEntity savedUser = r.user();
            ensureSystemIdentity(savedUser.getId(), "phone", phone, true);

            saveCredential(savedUser.getId(), password);
            auditService.log(savedUser.getId(), "auth.register", Map.of(
                    "method", "phone",
                    "purpose", purpose
            ), RequestUtils.clientIp(request), RequestUtils.userAgent(request));
            return issueTokenResponse(savedUser, request, "注册并登录成功", true);
        }

        if (!RequestUtils.isValidEmail(email)) return ApiResponse.error(ErrorCodes.INVALID_EMAIL, "请输入正确的邮箱地址");
        boolean ok = verificationService.verifyEmailCode(email, purpose, code);
        if (!ok) return ApiResponse.error(ErrorCodes.INVALID_CODE, "验证码错误或已过期");

        UserService.FindOrCreateResult r = userService.findOrCreateByEmail(email);
        if (!r.isNew()) return ApiResponse.error(ErrorCodes.USER_EXISTS, "用户已存在");

        UserEntity savedUser = r.user();
        ensureSystemIdentity(savedUser.getId(), "email", email, true);

        saveCredential(savedUser.getId(), password);
        auditService.log(savedUser.getId(), "auth.register", Map.of(
                "method", "email",
                "purpose", purpose
        ), RequestUtils.clientIp(request), RequestUtils.userAgent(request));
        return issueTokenResponse(savedUser, request, "注册并登录成功", true);
    }

    // ======================= 会话管理 =======================

    @PostMapping("/token/refresh")
    public ApiResponse<Map<String, Object>> refresh(@RequestBody TokenRefreshRequest req, HttpServletRequest request) {
        String refreshToken = RequestUtils.trimOrNull(req.refreshToken());
        if (refreshToken == null) return ApiResponse.error(ErrorCodes.PARAM_MISSING, "参数缺失");

        try {
            AuthTokenService.IssuedTokens tokens = authTokenService.refresh(refreshToken, RequestUtils.clientIp(request), RequestUtils.userAgent(request));
            return ApiResponse.ok(Map.of(
                    "token", tokens.accessToken(),
                    "accessToken", tokens.accessToken(),
                    "refreshToken", tokens.refreshToken(),
                    "expiresIn", tokens.accessExpiresIn(),
                    "refreshExpiresIn", tokens.refreshExpiresIn(),
                    "sessionId", tokens.sessionId().toString()
            ));
        } catch (AuthTokenService.InvalidRefreshTokenException e) {
            return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "refresh token 无效或已过期");
        }
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestBody TokenRefreshRequest req) {
        String refreshToken = RequestUtils.trimOrNull(req.refreshToken());
        if (refreshToken == null) return ApiResponse.error(ErrorCodes.PARAM_MISSING, "参数缺失");
        authTokenService.revoke(refreshToken);
        return ApiResponse.ok("已退出", null);
    }

    // ======================= 需要登录的接口 =======================

    @PostMapping("/set-password")
    public ApiResponse<Void> setPassword(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestBody SetPasswordRequest req
    ) {
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "未登录");

        String password = req.password();
        if (password == null || password.length() < 6) return ApiResponse.error(ErrorCodes.INVALID_PASSWORD, "密码至少 6 位");
        if (password.length() > PASSWORD_MAX_LEN) return ApiResponse.error(ErrorCodes.INVALID_PASSWORD, "密码长度过长");

        UserEntity user = userRepository.findByIdAndDeletedAtIsNull(userId).orElse(null);
        if (user == null) return ApiResponse.error(ErrorCodes.USER_NOT_FOUND, "用户不存在");

        UserCredentialEntity cred = credentialRepository.findById(userId).orElse(null);
        if (cred != null) return ApiResponse.error(ErrorCodes.PASSWORD_ALREADY_SET, "已设置密码，请使用修改密码功能");

        saveCredential(userId, password);
        return ApiResponse.ok("密码设置成功", null);
    }

    @PostMapping("/update-email")
    public ApiResponse<Void> updateEmail(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestBody UpdateEmailRequest req
    ) {
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "未登录");

        return bindEmailWithCode(userId, req.phone(), req.email(), req.code(), req.purpose());
    }

    private ApiResponse<Void> bindEmailWithCode(UUID userId, String phoneRaw, String emailRaw, String codeRaw, String purposeRaw) {
        String phone = RequestUtils.trimOrNull(phoneRaw);
        String email = RequestUtils.trimOrNull(emailRaw);
        String code = RequestUtils.trimOrNull(codeRaw);
        String purpose = RequestUtils.trimOrNull(purposeRaw);
        if (purpose == null) purpose = "bind_email";

        if (!RequestUtils.isValidEmail(email)) return ApiResponse.error(ErrorCodes.INVALID_EMAIL, "请输入正确的邮箱地址");
        if (code == null) return ApiResponse.error(ErrorCodes.INVALID_CODE, "请输入验证码");

        UserEntity user = userRepository.findByIdAndDeletedAtIsNull(userId).orElse(null);
        if (user == null) return ApiResponse.error(ErrorCodes.USER_NOT_FOUND, "用户不存在");

        if (user.getPhone() != null && !user.getPhone().isBlank()) {
            if (phone == null || !phone.equals(user.getPhone())) {
                return ApiResponse.error(ErrorCodes.INVALID_PHONE, "手机号不匹配");
            }
        }

        UserEntity existing = userRepository.findByEmailAndDeletedAtIsNull(email).orElse(null);
        if (existing != null && !existing.getId().equals(userId)) {
            return ApiResponse.error(ErrorCodes.USER_EXISTS, "该邮箱已被使用");
        }

        boolean ok = verificationService.verifyEmailCode(email, purpose, code);
        if (!ok) return ApiResponse.error(ErrorCodes.INVALID_CODE, "验证码错误或已过期");

        user.setEmail(email);
        try {
            userRepository.save(user);
        } catch (Exception e) {
            return ApiResponse.error(ErrorCodes.SERVER_ERROR, "邮箱绑定失败");
        }
        ensureSystemIdentity(userId, "email", email, true);
        return ApiResponse.ok("邮箱绑定成功", null);
    }

    @PostMapping("/change-password")
    public ApiResponse<Void> changePassword(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestBody ChangePasswordRequest req
    ) {
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "未登录");

        String code = RequestUtils.trimOrNull(req.code());
        String newPassword = req.newPassword();
        String purpose = RequestUtils.trimOrNull(req.purpose());
        if (purpose == null) purpose = "change_password";
        String verifyType = RequestUtils.trimOrNull(req.verifyType());

        if (code == null) return ApiResponse.error(ErrorCodes.INVALID_CODE, "请输入验证码");
        if (newPassword == null || newPassword.length() < 6) return ApiResponse.error(ErrorCodes.INVALID_PASSWORD, "新密码至少 6 位");
        if (newPassword.length() > PASSWORD_MAX_LEN) return ApiResponse.error(ErrorCodes.INVALID_PASSWORD, "密码长度过长");

        UserEntity user = userRepository.findByIdAndDeletedAtIsNull(userId).orElse(null);
        if (user == null) return ApiResponse.error(ErrorCodes.USER_NOT_FOUND, "用户不存在");

        // Bug #1 fix: use verifyType to pick only one channel, avoiding double-consume
        boolean ok = false;
        if ("sms".equalsIgnoreCase(verifyType)) {
            if (user.getPhone() != null && !user.getPhone().isBlank()) {
                ok = verificationService.verifySmsCode(user.getPhone(), purpose, code);
            }
        } else if ("email".equalsIgnoreCase(verifyType)) {
            if (user.getEmail() != null && !user.getEmail().isBlank()) {
                ok = verificationService.verifyEmailCode(user.getEmail(), purpose, code);
            }
        } else {
            // Legacy: verifyType not provided — try phone first, only try email if phone is absent
            if (user.getPhone() != null && !user.getPhone().isBlank()) {
                ok = verificationService.verifySmsCode(user.getPhone(), purpose, code);
            } else if (user.getEmail() != null && !user.getEmail().isBlank()) {
                ok = verificationService.verifyEmailCode(user.getEmail(), purpose, code);
            }
        }
        if (!ok) return ApiResponse.error(ErrorCodes.INVALID_CODE, "验证码错误或已过期");

        UserCredentialEntity cred = credentialRepository.findById(userId).orElse(null);
        if (cred == null) {
            cred = new UserCredentialEntity();
            cred.setUserId(userId);
        }
        cred.setPasswordHash(passwordEncoder.encode(newPassword));
        credentialRepository.save(cred);

        passwordLoginService.clearFailuresForUser(user);

        return ApiResponse.ok("密码修改成功", null);
    }

    @PostMapping("/delete-account")
    public ApiResponse<Void> deleteAccount(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId
    ) {
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "未登录");

        UserEntity user = userRepository.findByIdAndDeletedAtIsNull(userId).orElse(null);
        if (user == null) return ApiResponse.error(ErrorCodes.USER_NOT_FOUND, "用户不存在");

        // 软删除
        user.setDeletedAt(Instant.now());
        userRepository.save(user);

        // 吊销该用户所有活跃会话
        try {
            List<DeviceSessionEntity> sessions = deviceSessionRepository.findAllByUserIdAndRevokedAtIsNull(userId);
            Instant now = Instant.now();
            for (DeviceSessionEntity session : sessions) {
                session.setRevokedAt(now);
            }
            deviceSessionRepository.saveAll(sessions);
        } catch (Exception e) {
            log.warn("[auth] revoke sessions failed: userId={}, error={}", userId, e.toString());
        }

        // 释放 user_identity（允许用户重新注册/重新绑定；避免软删除导致唯一约束长期占用）
        try {
            userIdentityRepository.deleteAllByUserId(userId);
        } catch (Exception e) {
            log.warn("[auth] release identities failed: userId={}, error={}", userId, e.toString());
        }

        return ApiResponse.ok("账户已删除", null);
    }

    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> me(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId
    ) {
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "未登录");

        UserEntity user = userRepository.findByIdAndDeletedAtIsNull(userId).orElse(null);
        if (user == null) return ApiResponse.error(ErrorCodes.USER_NOT_FOUND, "用户不存在");

        return ApiResponse.ok(UserInfoUtils.toUserInfo(user, wxBound(userId)));
    }

    // ======================= Token 响应构建 =======================

    private ApiResponse<Map<String, Object>> issueTokenResponse(UserEntity user, HttpServletRequest request, String message, boolean isNew) {
        guestHistoryLoginSupport.migrateQuietly(request, user.getId(), "auth");
        AuthTokenService.IssuedTokens tokens = authTokenService.issue(user, null, RequestUtils.clientIp(request), RequestUtils.userAgent(request));
        return ApiResponse.ok(message, Map.of(
                "token", tokens.accessToken(),
                "accessToken", tokens.accessToken(),
                "refreshToken", tokens.refreshToken(),
                "expiresIn", tokens.accessExpiresIn(),
                "refreshExpiresIn", tokens.refreshExpiresIn(),
                "sessionId", tokens.sessionId().toString(),
                "userInfo", UserInfoUtils.toUserInfo(user, wxBound(user.getId())),
                "isNewUser", isNew
        ));
    }

    private void saveCredential(UUID userId, String password) {
        UserCredentialEntity cred = new UserCredentialEntity();
        cred.setUserId(userId);
        cred.setPasswordHash(passwordEncoder.encode(password));
        credentialRepository.save(cred);
    }

    private boolean allowIdentifierCheck(String identifier, String requesterIp) {
        String normalizedIp = RequestUtils.trimOrNull(requesterIp);
        if (normalizedIp == null) {
            normalizedIp = "unknown";
        }
        String normalizedIdentifier = identifier.trim().toLowerCase();
        return incrementRateLimit("auth:check_identifier:ip:", RequestUtils.sha256Hex(normalizedIp), IDENTIFIER_CHECK_LIMIT_PER_IP)
                && incrementRateLimit(
                "auth:check_identifier:identifier:",
                RequestUtils.sha256Hex(normalizedIdentifier),
                IDENTIFIER_CHECK_LIMIT_PER_IDENTIFIER
        );
    }

    private boolean incrementRateLimit(String prefix, String token, int limit) {
        String key = prefix + token;
        try {
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1) {
                redis.expire(key, IDENTIFIER_CHECK_WINDOW);
            }
            return count == null || count <= limit;
        } catch (Exception e) {
            log.warn("[auth] identifier check rate limit failed closed: key={}, error={}", key, e.toString());
            return false;
        }
    }

    // ======================= 工具方法 =======================

    private boolean consumeChallengeToken(String challengeToken, HttpServletRequest request) {
        return verificationService.consumeChallengeToken(challengeToken, RequestUtils.clientIp(request));
    }

    private boolean wxBound(UUID userId) {
        try {
            return userId != null && userIdentityRepository.existsByUserIdAndProvider(userId, "wechat");
        } catch (Exception e) {
            return false;
        }
    }

    private void ensureSystemIdentity(UUID userId, String type, String identifier, boolean verified) {
        if (userId == null || type == null || identifier == null) return;
        String provider = "system";
        String scope = "global";
        try {
            boolean exists = userIdentityRepository.existsByProviderAndProviderScopeAndTypeAndIdentifier(provider, scope, type, identifier);
            if (exists) return;
            UserIdentityEntity e = new UserIdentityEntity();
            e.setUserId(userId);
            e.setProvider(provider);
            e.setProviderScope(scope);
            e.setType(type);
            e.setIdentifier(identifier);
            e.setVerified(verified);
            userIdentityRepository.save(e);
        } catch (DataIntegrityViolationException e) {
            // Bug #6 fix: concurrent insert — identity already exists, safe to ignore
            log.debug("[auth] ensureSystemIdentity duplicate: userId={}, type={}", userId, type);
        } catch (Exception e) {
            log.warn("[auth] ensureSystemIdentity failed: userId={}, type={}, error={}", userId, type, e.toString());
        }
    }
}

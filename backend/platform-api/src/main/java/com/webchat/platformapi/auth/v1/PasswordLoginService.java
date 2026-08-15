package com.webchat.platformapi.auth.v1;

import com.webchat.platformapi.auth.credential.UserCredentialEntity;
import com.webchat.platformapi.auth.credential.UserCredentialRepository;
import com.webchat.platformapi.auth.v1.dto.PasswordLoginRequest;
import com.webchat.platformapi.auth.verification.RedisVerificationService;
import com.webchat.platformapi.common.api.ApiResponse;
import com.webchat.platformapi.common.api.ErrorCodes;
import com.webchat.platformapi.common.util.RequestUtils;
import com.webchat.platformapi.user.UserEntity;
import com.webchat.platformapi.user.UserRepository;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class PasswordLoginService {

    private static final Logger log = LoggerFactory.getLogger(PasswordLoginService.class);

    private static final int PASSWORD_MAX_FAILURES = 5;
    private static final Duration PASSWORD_FAIL_WINDOW = Duration.ofMinutes(15);
    private static final Duration PASSWORD_LOCK_TTL = Duration.ofMinutes(15);
    private static final int PASSWORD_MAX_LEN = 1024;

    private final RedisVerificationService verificationService;
    private final UserRepository userRepository;
    private final UserCredentialRepository credentialRepository;
    private final StringRedisTemplate redis;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public PasswordLoginService(
            RedisVerificationService verificationService,
            UserRepository userRepository,
            UserCredentialRepository credentialRepository,
            StringRedisTemplate redis
    ) {
        this.verificationService = verificationService;
        this.userRepository = userRepository;
        this.credentialRepository = credentialRepository;
        this.redis = redis;
    }

    public PasswordLoginResult authenticate(PasswordLoginRequest req, String requesterIp, boolean adminOnly) {
        String identifier = RequestUtils.firstNonBlank(req.phone(), req.email());
        if (identifier == null) {
            identifier = RequestUtils.trimOrNull(req.identifier());
        }
        String password = RequestUtils.trimOrNull(req.password());
        String challengeToken = RequestUtils.trimOrNull(req.challengeToken());

        if (identifier == null) {
            return PasswordLoginResult.error(ApiResponse.error(
                    ErrorCodes.PARAM_MISSING,
                    adminOnly ? "请输入账号" : "参数缺失"
            ));
        }
        if (password == null) {
            return PasswordLoginResult.error(ApiResponse.error(ErrorCodes.INVALID_PASSWORD, "请输入密码"));
        }
        if (password.length() > PASSWORD_MAX_LEN) {
            return PasswordLoginResult.error(ApiResponse.error(ErrorCodes.INVALID_PASSWORD, "密码长度过长"));
        }

        // For admin login, skip captcha if not provided (dev mode tolerance)
        if (!adminOnly || challengeToken != null) {
            if (!verificationService.consumeChallengeToken(challengeToken, requesterIp)) {
                return PasswordLoginResult.error(ApiResponse.error(
                        ErrorCodes.CAPTCHA_FAILED,
                        "人机验证失败或已过期，请重试"
                ));
            }
        }

        String lockId = buildLockId(identifier);
        if (isPasswordLocked(lockId)) {
            return PasswordLoginResult.error(ApiResponse.error(
                    ErrorCodes.ACCOUNT_LOCKED,
                    "密码错误次数过多，请稍后再试"
            ));
        }

        UserEntity user = resolveUser(identifier);
        if (user == null) {
            return PasswordLoginResult.error(ApiResponse.error(ErrorCodes.WRONG_PASSWORD, "账号或密码错误"));
        }
        if (adminOnly && !"admin".equalsIgnoreCase(user.getRole())) {
            return PasswordLoginResult.error(ApiResponse.error(ErrorCodes.WRONG_PASSWORD, "账号或密码错误"));
        }

        UserCredentialEntity cred = credentialRepository.findById(user.getId()).orElse(null);
        if (cred == null) {
            return PasswordLoginResult.error(ApiResponse.error(ErrorCodes.WRONG_PASSWORD, "账号或密码错误"));
        }

        if (!passwordEncoder.matches(password, cred.getPasswordHash())) {
            recordPasswordFailure(lockId);
            if (isPasswordLocked(lockId)) {
                return PasswordLoginResult.error(ApiResponse.error(
                        ErrorCodes.ACCOUNT_LOCKED,
                        "密码错误次数过多，请稍后再试"
                ));
            }
            return PasswordLoginResult.error(ApiResponse.error(ErrorCodes.WRONG_PASSWORD, "账号或密码错误"));
        }

        clearPasswordFailures(lockId);
        String identifierType = RequestUtils.isValidPhone(identifier)
                ? "phone"
                : RequestUtils.isValidEmail(identifier) ? "email" : "identifier";
        return PasswordLoginResult.success(user, identifierType);
    }

    public void clearFailuresForUser(UserEntity user) {
        if (user == null) {
            return;
        }
        clearFailuresForIdentifier(user.getPhone());
        clearFailuresForIdentifier(user.getEmail());
    }

    private UserEntity resolveUser(String identifier) {
        if (RequestUtils.isValidPhone(identifier)) {
            return userRepository.findByPhoneAndDeletedAtIsNull(identifier).orElse(null);
        }
        if (RequestUtils.isValidEmail(identifier)) {
            return userRepository.findByEmailAndDeletedAtIsNull(identifier).orElse(null);
        }
        String normalized = RequestUtils.trimOrNull(identifier);
        if (normalized == null) {
            return null;
        }
        return userRepository.findByEmailAndDeletedAtIsNull(normalized).orElse(null);
    }

    private String buildLockId(String identifier) {
        String normalized = RequestUtils.trimOrNull(identifier);
        if (normalized == null) {
            return null;
        }
        return RequestUtils.sha256Hex("pwd:" + normalized.toLowerCase());
    }

    private void clearFailuresForIdentifier(String identifier) {
        String normalized = RequestUtils.trimOrNull(identifier);
        if (normalized == null) {
            return;
        }
        clearPasswordFailures(buildLockId(normalized));
    }

    private boolean isPasswordLocked(String lockId) {
        if (lockId == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(redis.hasKey("pwd_lock:" + lockId));
        } catch (Exception e) {
            return false;
        }
    }

    private void recordPasswordFailure(String lockId) {
        if (lockId == null) {
            return;
        }
        try {
            String failKey = "pwd_fail:" + lockId;
            Long count = redis.opsForValue().increment(failKey);
            if (count != null && count == 1) {
                redis.expire(failKey, PASSWORD_FAIL_WINDOW);
            }
            if (count != null && count >= PASSWORD_MAX_FAILURES) {
                redis.opsForValue().set("pwd_lock:" + lockId, "1", PASSWORD_LOCK_TTL);
                redis.delete(failKey);
            }
        } catch (Exception e) {
            log.warn("[auth] recordPasswordFailure Redis error, fail-closed: lockId={}, error={}", lockId, e.toString());
            throw new RuntimeException("安全服务暂不可用，请稍后再试");
        }
    }

    private void clearPasswordFailures(String lockId) {
        if (lockId == null) {
            return;
        }
        try {
            redis.delete("pwd_fail:" + lockId);
            redis.delete("pwd_lock:" + lockId);
        } catch (Exception e) {
            log.debug("[auth] clearPasswordFailures failed: lockId={}, error={}", lockId, e.toString());
        }
    }

    public record PasswordLoginResult(
            UserEntity user,
            String identifierType,
            ApiResponse<Map<String, Object>> errorResponse
    ) {
        static PasswordLoginResult success(UserEntity user, String identifierType) {
            return new PasswordLoginResult(user, identifierType, null);
        }

        static PasswordLoginResult error(ApiResponse<Map<String, Object>> errorResponse) {
            return new PasswordLoginResult(null, null, errorResponse);
        }
    }
}

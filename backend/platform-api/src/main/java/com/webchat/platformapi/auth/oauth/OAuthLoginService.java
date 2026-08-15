package com.webchat.platformapi.auth.oauth;

import com.webchat.platformapi.auth.session.AuthTokenService;
import com.webchat.platformapi.common.util.UserInfoUtils;
import com.webchat.platformapi.user.identity.UserIdentityEntity;
import com.webchat.platformapi.user.identity.UserIdentityRepository;
import com.webchat.platformapi.user.UserEntity;
import com.webchat.platformapi.user.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class OAuthLoginService {

    private final OAuthRuntimeConfigService runtimeConfigService;
    private final UserRepository userRepository;
    private final UserIdentityRepository userIdentityRepository;
    private final AuthTokenService authTokenService;

    public OAuthLoginService(
            OAuthRuntimeConfigService runtimeConfigService,
            UserRepository userRepository,
            UserIdentityRepository userIdentityRepository,
            AuthTokenService authTokenService
    ) {
        this.runtimeConfigService = runtimeConfigService;
        this.userRepository = userRepository;
        this.userIdentityRepository = userIdentityRepository;
        this.authTokenService = authTokenService;
    }

    public record LoginOutcome(UserEntity user, Map<String, Object> loginResponse, boolean isNewUser) {
    }

    @Transactional(rollbackOn = Exception.class)
    public LoginOutcome login(
            String provider,
            OAuthUserProfile profile,
            AuthTokenService.DeviceInfo device,
            String ip,
            String userAgent
    ) throws OAuthLoginException {
        OAuthRuntimeConfigService.RuntimeConfig runtimeConfig = runtimeConfigService.currentConfig();
        if (provider == null || provider.isBlank()) throw new OAuthLoginException("oauth provider missing");
        if (profile == null || profile.providerUserId() == null || profile.providerUserId().isBlank()) {
            throw new OAuthLoginException("oauth profile missing");
        }

        UserEntity identityUser = findByIdentity(provider, profile.identifierType(), profile.providerUserId());
        String verifiedEmail = normalizeVerifiedEmail(profile);
        UserEntity emailUser = verifiedEmail == null || !runtimeConfig.autoLinkByEmail()
                ? null
                : userRepository.findByEmailAndDeletedAtIsNull(verifiedEmail).orElse(null);

        if (identityUser != null && emailUser != null && !identityUser.getId().equals(emailUser.getId())) {
            throw new OAuthLoginException("oauth account conflict");
        }

        boolean isNewUser = false;
        UserEntity user = identityUser != null ? identityUser : emailUser;
        if (user == null) {
            user = new UserEntity();
            isNewUser = true;
        }

        if (blank(user.getEmail()) && verifiedEmail != null) {
            user.setEmail(verifiedEmail);
        }
        if (blank(user.getAvatar()) && !blank(profile.avatarUrl())) {
            user.setAvatar(profile.avatarUrl());
        }
        if (user.getCreatedAt() == null) {
            user.setCreatedAt(Instant.now());
        }
        user = userRepository.save(user);

        ensureIdentity(provider, "global", blank(profile.identifierType()) ? "id" : profile.identifierType(),
                profile.providerUserId(), user.getId(), true);
        if (verifiedEmail != null && (blank(user.getEmail()) || verifiedEmail.equalsIgnoreCase(user.getEmail()))) {
            if (blank(user.getEmail())) {
                user.setEmail(verifiedEmail);
                user = userRepository.save(user);
            }
            ensureIdentity("system", "global", "email", verifiedEmail, user.getId(), true);
        }

        AuthTokenService.IssuedTokens tokens = authTokenService.issue(user, device, ip, userAgent);
        Map<String, Object> loginResponse = toLoginResponse(user, tokens);
        loginResponse.put("isNewUser", isNewUser);
        return new LoginOutcome(user, loginResponse, isNewUser);
    }

    private UserEntity findByIdentity(String provider, String type, String identifier) {
        return userIdentityRepository
                .findFirstByProviderAndProviderScopeAndTypeAndIdentifier(provider, "global", type, identifier)
                .flatMap(identity -> userRepository.findByIdAndDeletedAtIsNull(identity.getUserId()))
                .orElse(null);
    }

    private void ensureIdentity(String provider, String scope, String type, String identifier, UUID userId, boolean verified)
            throws OAuthLoginException {
        UserIdentityEntity existing = userIdentityRepository
                .findFirstByProviderAndProviderScopeAndTypeAndIdentifier(provider, scope, type, identifier)
                .orElse(null);
        if (existing != null) {
            if (!existing.getUserId().equals(userId)) {
                throw new OAuthLoginException("oauth identity conflict");
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
        } catch (DataIntegrityViolationException e) {
            throw new OAuthLoginException("oauth identity conflict", e);
        }
    }

    private String normalizeVerifiedEmail(OAuthUserProfile profile) {
        if (profile == null || !profile.emailVerified() || blank(profile.email())) return null;
        return profile.email().trim().toLowerCase();
    }

    private Map<String, Object> toLoginResponse(UserEntity user, AuthTokenService.IssuedTokens tokens) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("token", tokens.accessToken());
        response.put("accessToken", tokens.accessToken());
        response.put("refreshToken", tokens.refreshToken());
        response.put("expiresIn", tokens.accessExpiresIn());
        response.put("refreshExpiresIn", tokens.refreshExpiresIn());
        response.put("sessionId", tokens.sessionId().toString());
        response.put("userInfo", UserInfoUtils.toUserInfo(user, userIdentityRepository.existsByUserIdAndProvider(user.getId(), "wechat")));
        return response;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    public static class OAuthLoginException extends Exception {
        public OAuthLoginException(String message) {
            super(message);
        }

        public OAuthLoginException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

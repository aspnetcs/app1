package com.webchat.platformapi.auth.guest;


import com.webchat.platformapi.ai.conversation.AiConversationRepository;
import com.webchat.platformapi.agent.AgentEntity;
import com.webchat.platformapi.agent.AgentRepository;
import com.webchat.platformapi.ai.usage.AiUsageLogRepository;
import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.auth.session.DeviceSessionEntity;
import com.webchat.platformapi.auth.session.DeviceSessionRepository;
import com.webchat.platformapi.user.UserEntity;
import com.webchat.platformapi.user.UserRepository;
import com.webchat.platformapi.user.identity.UserIdentityRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

@Service
public class GuestHistoryMigrationService {

    public static final String GUEST_RECOVERY_TOKEN_HEADER = "X-Guest-Recovery-Token";

    private final GuestAuthProperties properties;
    private final GuestAuthService guestAuthService;
    private final UserRepository userRepository;
    private final AiConversationRepository aiConversationRepository;
    private final AgentRepository agentRepository;
    private final AiUsageLogRepository aiUsageLogRepository;

    private final DeviceSessionRepository deviceSessionRepository;
    private final UserIdentityRepository userIdentityRepository;

    public GuestHistoryMigrationService(
            GuestAuthProperties properties,
            GuestAuthService guestAuthService,
            UserRepository userRepository,
            AiConversationRepository aiConversationRepository,
            AgentRepository agentRepository,
            AiUsageLogRepository aiUsageLogRepository,
            DeviceSessionRepository deviceSessionRepository,
            UserIdentityRepository userIdentityRepository
    ) {
        this.properties = properties;
        this.guestAuthService = guestAuthService;
        this.userRepository = userRepository;
        this.aiConversationRepository = aiConversationRepository;
        this.agentRepository = agentRepository;
        this.aiUsageLogRepository = aiUsageLogRepository;
        this.deviceSessionRepository = deviceSessionRepository;
        this.userIdentityRepository = userIdentityRepository;
    }

    @Transactional(rollbackOn = Exception.class)
    public void migrateGuestHistory(HttpServletRequest request, UUID targetUserId) {
        if (!properties.isHistoryMigrationEnabled() || request == null || targetUserId == null) {
            return;
        }
        UserEntity sourceGuest = resolveSourceGuest(request);
        if (sourceGuest == null || targetUserId.equals(sourceGuest.getId())) {
            return;
        }

        UUID sourceUserId = sourceGuest.getId();
        aiConversationRepository.reassignUserId(sourceUserId, targetUserId);
        migrateAgents(sourceUserId, targetUserId);
        aiUsageLogRepository.reassignUserId(sourceUserId, targetUserId);

        revokeGuestSessions(sourceUserId);
        userIdentityRepository.deleteAllByUserIdAndProvider(sourceUserId, GuestAuthService.GUEST_DEVICE_PROVIDER);
        sourceGuest.setDeletedAt(Instant.now());
        userRepository.save(sourceGuest);
    }

    private UserEntity resolveSourceGuest(HttpServletRequest request) {
        UserEntity bearerGuest = resolveBearerGuest(request);
        if (bearerGuest != null) {
            return bearerGuest;
        }
        String recoveryToken = GuestRecoveryCookieSupport.resolveRecoveryToken(request, GUEST_RECOVERY_TOKEN_HEADER);
        if (recoveryToken == null) {
            return null;
        }
        return guestAuthService.findGuestUserByRecoveryToken(recoveryToken);
    }

    private UserEntity resolveBearerGuest(HttpServletRequest request) {
        Object roleAttr = request.getAttribute(JwtAuthFilter.ATTR_USER_ROLE);
        if (roleAttr == null || !GuestAuthService.GUEST_ROLE.equalsIgnoreCase(String.valueOf(roleAttr).trim())) {
            return null;
        }
        Object userIdAttr = request.getAttribute(JwtAuthFilter.ATTR_USER_ID);
        if (userIdAttr == null) {
            return null;
        }
        UUID userId;
        try {
            userId = userIdAttr instanceof UUID ? (UUID) userIdAttr : UUID.fromString(String.valueOf(userIdAttr));
        } catch (Exception e) {
            return null;
        }
        UserEntity guestUser = userRepository.findByIdAndDeletedAtIsNull(userId).orElse(null);
        if (guestUser == null || !GuestAuthService.GUEST_ROLE.equalsIgnoreCase(String.valueOf(guestUser.getRole()).trim())) {
            return null;
        }
        return guestUser;
    }



    private void migrateAgents(UUID sourceUserId, UUID targetUserId) {
        for (AgentEntity agent : agentRepository.findByUserIdAndDeletedAtIsNullOrderBySortOrderAscCreatedAtDesc(sourceUserId)) {
            agent.setUserId(targetUserId);
            agentRepository.save(agent);
        }
    }

    private void revokeGuestSessions(UUID guestUserId) {
        Instant now = Instant.now();
        List<DeviceSessionEntity> sessions = deviceSessionRepository.findAllByUserIdAndRevokedAtIsNull(guestUserId);
        if (sessions.isEmpty()) {
            return;
        }
        for (DeviceSessionEntity session : sessions) {
            session.setRevokedAt(now);
            session.setLastActiveAt(now);
        }
        deviceSessionRepository.saveAll(sessions);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String mergeCsv(String preferred, String fallback) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        appendCsv(values, preferred);
        appendCsv(values, fallback);
        return String.join(",", values);
    }

    private static void appendCsv(LinkedHashSet<String> values, String raw) {
        String normalized = trimToNull(raw);
        if (normalized == null) {
            return;
        }
        for (String item : normalized.split(",")) {
            String value = trimToNull(item);
            if (value != null) {
                values.add(value);
            }
        }
    }
}

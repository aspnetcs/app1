package com.webchat.platformapi.auth.guest;

import com.webchat.platformapi.common.util.RequestUtils;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class GuestHistoryLoginSupport {

    private static final Logger log = LoggerFactory.getLogger(GuestHistoryLoginSupport.class);

    private final GuestHistoryMigrationService guestHistoryMigrationService;

    public GuestHistoryLoginSupport(GuestHistoryMigrationService guestHistoryMigrationService) {
        this.guestHistoryMigrationService = guestHistoryMigrationService;
    }

    public void migrateQuietly(HttpServletRequest request, UUID userId, String source) {
        if (request == null || userId == null) {
            return;
        }
        try {
            guestHistoryMigrationService.migrateGuestHistory(request, userId);
        } catch (Exception e) {
            log.warn("[{}] guest history migration skipped: targetUserId={}, error={}",
                    normalizeSource(source), userId, e.toString());
        }
    }

    public void migrateQuietly(HttpServletRequest request, Map<String, Object> loginResponse, String source) {
        migrateQuietly(request, parseUserId(loginResponse), source);
    }

    static UUID parseUserId(Map<String, Object> loginResponse) {
        if (loginResponse == null) {
            return null;
        }
        Object userInfoObj = loginResponse.get("userInfo");
        if (!(userInfoObj instanceof Map<?, ?> rawUserInfo)) {
            return null;
        }
        Object userIdObj = rawUserInfo.get("userId");
        if (userIdObj == null) {
            return null;
        }
        try {
            return UUID.fromString(String.valueOf(userIdObj));
        } catch (Exception e) {
            return null;
        }
    }

    private static String normalizeSource(String source) {
        String normalized = RequestUtils.trimOrNull(source);
        return normalized == null ? "auth" : normalized;
    }
}

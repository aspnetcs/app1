package com.webchat.platformapi.memory;

import com.webchat.platformapi.config.SysConfigService;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class MemoryRuntimeService {

    static final String KEY_ENABLED = "platform.memory.enabled";
    static final String KEY_REQUIRE_CONSENT = "platform.memory.require-consent";
    static final String KEY_MAX_ENTRIES = "platform.memory.max-entries-per-user";
    static final String KEY_MAX_CHARS = "platform.memory.max-chars-per-entry";
    static final String KEY_RETENTION_DAYS = "platform.memory.retention-days";
    static final String KEY_SUMMARY_MODEL = "platform.memory.summary-model";

    private final Environment environment;
    private final SysConfigService sysConfigService;
    private final MemoryConsentRepository consentRepository;
    private final MemoryEntryRepository entryRepository;
    private final MemoryAuditRepository auditRepository;

    public MemoryRuntimeService(
            Environment environment,
            SysConfigService sysConfigService,
            MemoryConsentRepository consentRepository,
            MemoryEntryRepository entryRepository,
            MemoryAuditRepository auditRepository
    ) {
        this.environment = environment;
        this.sysConfigService = sysConfigService;
        this.consentRepository = consentRepository;
        this.entryRepository = entryRepository;
        this.auditRepository = auditRepository;
    }

    public Map<String, Object> getRuntimeConfig(UUID userId) {
        boolean enabled = getBoolean(KEY_ENABLED, false);
        boolean requireConsent = getBoolean(KEY_REQUIRE_CONSENT, true);
        boolean consentGranted = !requireConsent || consentRepository.findById(userId)
                .map(MemoryConsentEntity::isEnabled)
                .orElse(false);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("enabled", enabled);
        data.put("consentRequired", requireConsent);
        data.put("consentGranted", consentGranted);
        data.put("summary", buildSummary(userId));
        return data;
    }

    public Map<String, Object> updateConsent(UUID userId, boolean enabled) {
        MemoryConsentEntity entity = consentRepository.findById(userId).orElseGet(() -> {
            MemoryConsentEntity created = new MemoryConsentEntity();
            created.setUserId(userId);
            return created;
        });
        entity.setEnabled(enabled);
        consentRepository.save(entity);

        auditRepository.save(newAudit(
                userId,
                "consent_update",
                enabled ? "User enabled memory consent" : "User disabled memory consent",
                Map.of("enabled", enabled)
        ));
        return getRuntimeConfig(userId);
    }

    public MemoryAdminConfig getAdminConfig() {
        return new MemoryAdminConfig(
                getBoolean(KEY_ENABLED, false),
                getBoolean(KEY_REQUIRE_CONSENT, true),
                getInt(KEY_MAX_ENTRIES, 50),
                getInt(KEY_MAX_CHARS, 1000),
                getInt(KEY_RETENTION_DAYS, 30),
                getString(KEY_SUMMARY_MODEL, "")
        );
    }

    public MemoryAdminConfig updateAdminConfig(MemoryAdminConfig config) {
        sysConfigService.set(KEY_ENABLED, String.valueOf(config.enabled()));
        sysConfigService.set(KEY_REQUIRE_CONSENT, String.valueOf(config.requireConsent()));
        sysConfigService.set(KEY_MAX_ENTRIES, String.valueOf(Math.max(1, config.maxEntriesPerUser())));
        sysConfigService.set(KEY_MAX_CHARS, String.valueOf(Math.max(32, config.maxCharsPerEntry())));
        sysConfigService.set(KEY_RETENTION_DAYS, String.valueOf(Math.max(1, config.retentionDays())));
        sysConfigService.set(KEY_SUMMARY_MODEL, normalizeText(config.summaryModel()));
        return getAdminConfig();
    }

    public MemoryAdminStats getAdminStats() {
        long totalEntries = entryRepository.count();
        long totalUsers = consentRepository.count();
        long pendingReviews = auditRepository.countByStatus("pending");
        double averageEntriesPerUser = totalUsers == 0 ? 0D : (double) totalEntries / (double) totalUsers;
        return new MemoryAdminStats(
                getBoolean(KEY_ENABLED, false),
                totalUsers,
                totalEntries,
                pendingReviews,
                Math.round(averageEntriesPerUser * 100.0D) / 100.0D
        );
    }

    public Page<MemoryAuditEntity> latestAudits(int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(200, size));
        return auditRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(safePage, safeSize));
    }

    private MemoryAuditEntity newAudit(UUID userId, String action, String summary, Map<String, Object> detail) {
        MemoryAuditEntity entity = new MemoryAuditEntity();
        entity.setUserId(userId);
        entity.setAction(action);
        entity.setSummary(summary);
        entity.setStatus("success");
        entity.setDetailJson(detail);
        return entity;
    }

    private String buildSummary(UUID userId) {
        List<MemoryEntryEntity> entries = entryRepository.findByUserIdAndStatusOrderByUpdatedAtDesc(
                userId,
                "active",
                PageRequest.of(0, 3)
        );
        if (entries.isEmpty()) {
            return "";
        }
        String joined = entries.stream()
                .map(entry -> normalizeText(entry.getSummary()) != null ? normalizeText(entry.getSummary()) : normalizeText(entry.getContent()))
                .filter(value -> value != null && !value.isBlank())
                .reduce((left, right) -> left + "；" + right)
                .orElse("");
        return joined.length() > 240 ? joined.substring(0, 240) : joined;
    }

    private boolean getBoolean(String key, boolean defaultValue) {
        String raw = sysConfigService.get(key).orElseGet(() -> environment.getProperty(key, String.valueOf(defaultValue)));
        return "true".equalsIgnoreCase(raw) || "1".equals(raw);
    }

    private int getInt(String key, int defaultValue) {
        String raw = sysConfigService.get(key).orElseGet(() -> environment.getProperty(key, String.valueOf(defaultValue)));
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private String getString(String key, String defaultValue) {
        return normalizeText(sysConfigService.get(key).orElseGet(() -> environment.getProperty(key, defaultValue)));
    }

    private static String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? "" : trimmed;
    }

    public record MemoryAdminConfig(
            boolean enabled,
            boolean requireConsent,
            int maxEntriesPerUser,
            int maxCharsPerEntry,
            int retentionDays,
            String summaryModel
    ) {
    }

    public record MemoryAdminStats(
            boolean enabled,
            long totalUsers,
            long totalEntries,
            long pendingReviews,
            double averageEntriesPerUser
    ) {
    }
}

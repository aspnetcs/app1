package com.webchat.platformapi.admin.ops;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class BannerService {

    private final SysBannerRepository bannerRepository;

    public BannerService(SysBannerRepository bannerRepository) {
        this.bannerRepository = bannerRepository;
    }

    public List<Map<String, Object>> listActive() {
        Instant now = Instant.now();
        return bannerRepository.findAll(
                Specification.where(notDeleted())
                        .and(matchEnabled(true))
                        .and(activeAt(now)),
                Sort.by(Sort.Order.asc("sortOrder"), Sort.Order.desc("createdAt"))
        ).stream().map(BannerService::toDto).toList();
    }

    public Page<Map<String, Object>> listForAdmin(String search, String type, Boolean enabled, int page, int size) {
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.max(1, Math.min(size, 100)),
                Sort.by(Sort.Order.asc("sortOrder"), Sort.Order.desc("createdAt"))
        );
        Specification<SysBannerEntity> spec = Specification.where(notDeleted())
                .and(matchSearch(search))
                .and(matchType(type))
                .and(matchEnabled(enabled));
        Page<SysBannerEntity> result = bannerRepository.findAll(spec, pageable);
        List<Map<String, Object>> items = result.getContent().stream().map(BannerService::toDto).toList();
        return new PageImpl<>(items, pageable, result.getTotalElements());
    }

    public Map<String, Object> create(Map<String, Object> body) {
        SysBannerEntity entity = new SysBannerEntity();
        applyBody(entity, body);
        return toDto(bannerRepository.save(entity));
    }

    public Map<String, Object> update(UUID id, Map<String, Object> body) {
        SysBannerEntity entity = requireBanner(id);
        applyBody(entity, body);
        return toDto(bannerRepository.save(entity));
    }

    public void delete(UUID id) {
        SysBannerEntity entity = requireBanner(id);
        entity.setDeletedAt(Instant.now());
        bannerRepository.save(entity);
    }

    public static Map<String, Object> toDto(SysBannerEntity entity) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", entity.getId());
        out.put("type", entity.getType());
        out.put("title", entity.getTitle());
        out.put("content", entity.getContent());
        out.put("dismissible", entity.isDismissible());
        out.put("enabled", entity.isEnabled());
        out.put("sortOrder", entity.getSortOrder());
        out.put("sort_order", entity.getSortOrder());
        out.put("startTime", entity.getStartTime() == null ? "" : entity.getStartTime().toString());
        out.put("start_time", entity.getStartTime() == null ? "" : entity.getStartTime().toString());
        out.put("endTime", entity.getEndTime() == null ? "" : entity.getEndTime().toString());
        out.put("end_time", entity.getEndTime() == null ? "" : entity.getEndTime().toString());
        out.put("createdAt", entity.getCreatedAt() == null ? "" : entity.getCreatedAt().toString());
        out.put("created_at", entity.getCreatedAt() == null ? "" : entity.getCreatedAt().toString());
        out.put("updatedAt", entity.getUpdatedAt() == null ? "" : entity.getUpdatedAt().toString());
        out.put("updated_at", entity.getUpdatedAt() == null ? "" : entity.getUpdatedAt().toString());
        return out;
    }

    private static Specification<SysBannerEntity> notDeleted() {
        return (root, query, cb) -> cb.isNull(root.get("deletedAt"));
    }

    private void applyBody(SysBannerEntity entity, Map<String, Object> body) {
        if (body == null) {
            throw new IllegalArgumentException("request body is empty");
        }
        String title = string(body.get("title"));
        String content = string(body.get("content"));
        if (title == null) {
            throw new IllegalArgumentException("missing title");
        }
        if (content == null) {
            throw new IllegalArgumentException("missing content");
        }
        entity.setTitle(title);
        entity.setContent(content);
        entity.setType(parseType(string(body.get("type"))));
        entity.setDismissible(boolValue(body.get("dismissible"), true, "dismissible"));
        entity.setEnabled(boolValue(body.get("enabled"), true, "enabled"));
        entity.setSortOrder(intValue(body.get("sortOrder"), body.get("sort_order"), 0, "sortOrder"));
        entity.setStartTime(parseInstant(body.get("startTime"), body.get("start_time"), "startTime"));
        entity.setEndTime(parseInstant(body.get("endTime"), body.get("end_time"), "endTime"));
    }

    private SysBannerEntity requireBanner(UUID id) {
        SysBannerEntity entity = bannerRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("banner not found"));
        if (entity.getDeletedAt() != null) {
            throw new IllegalArgumentException("banner not found");
        }
        return entity;
    }

    private static Specification<SysBannerEntity> matchSearch(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        String like = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("title")), like),
                cb.like(cb.lower(root.get("content")), like)
        );
    }

    private static Specification<SysBannerEntity> matchType(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        String normalized = normalizeType(type);
        return (root, query, cb) -> cb.equal(root.get("type"), normalized);
    }

    private static Specification<SysBannerEntity> matchEnabled(Boolean enabled) {
        if (enabled == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("enabled"), enabled);
    }

    private static Specification<SysBannerEntity> activeAt(Instant now) {
        return (root, query, cb) -> cb.and(
                cb.or(cb.isNull(root.get("startTime")), cb.lessThanOrEqualTo(root.get("startTime"), now)),
                cb.or(cb.isNull(root.get("endTime")), cb.greaterThanOrEqualTo(root.get("endTime"), now))
        );
    }

    private static String string(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private static String normalizeType(String value) {
        String text = value == null ? "info" : value.trim().toLowerCase(Locale.ROOT);
        return switch (text) {
            case "warning", "error" -> text;
            default -> "info";
        };
    }

    private static String parseType(String value) {
        if (value == null) {
            return "info";
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return "info";
        }
        return switch (normalized) {
            case "info", "warning", "error" -> normalized;
            default -> throw new IllegalArgumentException("invalid type: " + value);
        };
    }

    private static boolean boolValue(Object value, boolean fallback, String fieldName) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        String text = String.valueOf(value).trim();
        if ("true".equalsIgnoreCase(text)) {
            return true;
        }
        if ("false".equalsIgnoreCase(text)) {
            return false;
        }
        throw new IllegalArgumentException("invalid " + fieldName);
    }

    private static int intValue(Object primary, Object fallbackValue, int fallback, String fieldName) {
        Object value = primary != null ? primary : fallbackValue;
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("invalid " + fieldName);
        }
        try {
            return Integer.parseInt(text);
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid " + fieldName, e);
        }
    }

    private static Instant parseInstant(Object primary, Object fallback, String fieldName) {
        Object value = primary != null ? primary : fallback;
        if (value == null) {
            return null;
        }
        if (value instanceof Instant instant) {
            return instant;
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Instant.parse(text);
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid " + fieldName, e);
        }
    }
}

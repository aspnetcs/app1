package com.webchat.adminapi.group;

import com.webchat.platformapi.auth.group.SysUserGroupEntity;
import com.webchat.platformapi.auth.group.SysUserGroupMemberEntity;
import com.webchat.platformapi.auth.group.SysUserGroupMemberRepository;
import com.webchat.platformapi.auth.group.SysUserGroupRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class UserGroupAdminService {

    private final SysUserGroupRepository groupRepository;
    private final SysUserGroupMemberRepository memberRepository;

    public UserGroupAdminService(SysUserGroupRepository groupRepository, SysUserGroupMemberRepository memberRepository) {
        this.groupRepository = groupRepository;
        this.memberRepository = memberRepository;
    }

    public Page<Map<String, Object>> listForAdmin(String search, Boolean enabled, int page, int size) {
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.max(1, Math.min(size, 100)),
                Sort.by(Sort.Order.asc("sortOrder"), Sort.Order.desc("createdAt"))
        );
        Specification<SysUserGroupEntity> spec = Specification.where(notDeleted())
                .and(matchSearch(search))
                .and(matchEnabled(enabled));
        Page<SysUserGroupEntity> result = groupRepository.findAll(spec, pageable);

        Map<UUID, List<UUID>> membersByGroup = Map.of();
        if (!result.getContent().isEmpty()) {
            List<UUID> groupIds = result.getContent().stream().map(SysUserGroupEntity::getId).toList();
            membersByGroup = memberRepository.findByGroupIdIn(groupIds).stream()
                    .collect(Collectors.groupingBy(
                            SysUserGroupMemberEntity::getGroupId,
                            Collectors.mapping(SysUserGroupMemberEntity::getUserId, Collectors.toList())
                    ));
        }
        final Map<UUID, List<UUID>> finalMembersByGroup = membersByGroup;

        List<Map<String, Object>> items = result.getContent().stream()
                .map(group -> {
                    List<UUID> memberIds = finalMembersByGroup.getOrDefault(group.getId(), List.of());
                    return toDto(group, memberIds, memberIds.size());
                })
                .toList();
        return new PageImpl<>(items, pageable, result.getTotalElements());
    }

    public Map<String, Object> create(Map<String, Object> body) {
        SysUserGroupEntity entity = new SysUserGroupEntity();
        applyBody(entity, body);
        SysUserGroupEntity saved = groupRepository.save(entity);
        List<UUID> memberIds = parseUserIds(body.get("memberUserIds"), body.get("member_user_ids"));
        syncMembers(saved.getId(), memberIds);
        return toDto(saved, memberIds, memberIds.size());
    }

    public Map<String, Object> update(UUID id, Map<String, Object> body) {
        SysUserGroupEntity entity = requireGroup(id);
        applyBody(entity, body);
        SysUserGroupEntity saved = groupRepository.save(entity);

        boolean membersProvided = body.containsKey("memberUserIds") || body.containsKey("member_user_ids");
        List<UUID> memberIds = parseUserIds(body.get("memberUserIds"), body.get("member_user_ids"));
        List<UUID> responseMemberIds;
        if (membersProvided) {
            syncMembers(saved.getId(), memberIds);
            responseMemberIds = memberIds;
        } else {
            responseMemberIds = loadMemberIds(saved.getId());
        }
        return toDto(saved, responseMemberIds, responseMemberIds.size());
    }

    public void delete(UUID id) {
        SysUserGroupEntity entity = requireGroup(id);
        entity.setDeletedAt(Instant.now());
        groupRepository.save(entity);
        memberRepository.deleteByGroupId(id);
    }

    private void applyBody(SysUserGroupEntity entity, Map<String, Object> body) {
        if (body == null) throw new IllegalArgumentException("request body is empty");
        String name = string(body.get("name"));
        if (name == null) throw new IllegalArgumentException("missing name");
        entity.setName(name);
        entity.setDescription(defaultString(string(body.get("description"))));
        entity.setAllowedModels(String.join(",", parseCsv(body.get("allowedModels"), body.get("allowed_models"))));
        entity.setFeatureFlags(String.join(",", parseCsv(body.get("featureFlags"), body.get("feature_flags"))));
        entity.setChatRateLimitPerMinute(intValue(body.get("chatRateLimitPerMinute"), body.get("chat_rate_limit_per_minute")));
        entity.setEnabled(boolValue(body.get("enabled"), true));
        Integer sortOrder = intValue(body.get("sortOrder"), body.get("sort_order"));
        entity.setSortOrder(sortOrder == null ? 0 : Math.max(0, sortOrder));
    }

    private void syncMembers(UUID groupId, List<UUID> memberIds) {
        memberRepository.deleteByGroupId(groupId);
        for (UUID memberId : memberIds) {
            SysUserGroupMemberEntity membership = new SysUserGroupMemberEntity();
            membership.setGroupId(groupId);
            membership.setUserId(memberId);
            memberRepository.save(membership);
        }
    }

    private List<UUID> loadMemberIds(UUID groupId) {
        return memberRepository.findByGroupId(groupId).stream()
                .map(SysUserGroupMemberEntity::getUserId)
                .toList();
    }

    private SysUserGroupEntity requireGroup(UUID id) {
        SysUserGroupEntity entity = groupRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("group not found"));
        if (entity.getDeletedAt() != null) throw new IllegalArgumentException("group not found");
        return entity;
    }

    private static Map<String, Object> toDto(SysUserGroupEntity entity, List<UUID> memberUserIds, int memberCount) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", entity.getId());
        out.put("name", entity.getName());
        out.put("description", entity.getDescription() == null ? "" : entity.getDescription());
        out.put("allowedModels", parseCsv(entity.getAllowedModels()));
        out.put("allowed_models", parseCsv(entity.getAllowedModels()));
        out.put("featureFlags", parseCsv(entity.getFeatureFlags()));
        out.put("feature_flags", parseCsv(entity.getFeatureFlags()));
        out.put("chatRateLimitPerMinute", entity.getChatRateLimitPerMinute());
        out.put("chat_rate_limit_per_minute", entity.getChatRateLimitPerMinute());
        out.put("enabled", entity.isEnabled());
        out.put("sortOrder", entity.getSortOrder());
        out.put("sort_order", entity.getSortOrder());
        out.put("memberCount", memberCount);
        out.put("member_count", memberCount);
        out.put("memberUserIds", memberUserIds.stream().map(UUID::toString).toList());
        out.put("member_user_ids", memberUserIds.stream().map(UUID::toString).toList());
        out.put("createdAt", entity.getCreatedAt() == null ? "" : entity.getCreatedAt().toString());
        out.put("updatedAt", entity.getUpdatedAt() == null ? "" : entity.getUpdatedAt().toString());
        return out;
    }

    private static Specification<SysUserGroupEntity> notDeleted() {
        return (root, query, cb) -> cb.isNull(root.get("deletedAt"));
    }

    private static Specification<SysUserGroupEntity> matchSearch(String search) {
        if (search == null || search.isBlank()) return null;
        String like = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("name")), like),
                cb.like(cb.lower(cb.coalesce(root.get("description"), "")), like)
        );
    }

    private static Specification<SysUserGroupEntity> matchEnabled(Boolean enabled) {
        if (enabled == null) return null;
        return (root, query, cb) -> cb.equal(root.get("enabled"), enabled);
    }

    private static String string(Object value) {
        if (value == null) return null;
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private static String defaultString(String value) {
        return value == null ? "" : value;
    }

    private static List<String> parseCsv(Object primary, Object fallback) {
        Object value = primary != null ? primary : fallback;
        return parseCsv(value);
    }

    private static List<String> parseCsv(Object value) {
        if (value == null) return List.of();
        if (value instanceof Collection<?> collection) {
            return collection.stream()
                    .map(item -> item == null ? "" : String.valueOf(item).trim())
                    .filter(item -> !item.isEmpty())
                    .distinct()
                    .toList();
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) return List.of();
        return Arrays.stream(text.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .distinct()
                .toList();
    }

    private static List<UUID> parseUserIds(Object primary, Object fallback) {
        return parseCsv(primary, fallback).stream()
                .map(id -> {
                    try {
                        return UUID.fromString(id);
                    } catch (Exception e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toList();
    }

    private static Boolean boolValue(Object value, boolean fallback) {
        if (value instanceof Boolean bool) return bool;
        if (value == null) return fallback;
        String text = String.valueOf(value).trim();
        if ("true".equalsIgnoreCase(text)) return true;
        if ("false".equalsIgnoreCase(text)) return false;
        return fallback;
    }

    private static Integer intValue(Object primary, Object fallback) {
        Object value = primary != null ? primary : fallback;
        if (value instanceof Number number) return number.intValue();
        if (value == null) return null;
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (Exception e) {
            return null;
        }
    }
}

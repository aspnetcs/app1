package com.webchat.platformapi.auth.group;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class UserGroupService {

    private final SysUserGroupRepository groupRepository;
    private final SysUserGroupMemberRepository memberRepository;

    public UserGroupService(SysUserGroupRepository groupRepository, SysUserGroupMemberRepository memberRepository) {
        this.groupRepository = groupRepository;
        this.memberRepository = memberRepository;
    }

    public GroupProfile resolveProfile(UUID userId) {
        List<SysUserGroupMemberEntity> memberships = memberRepository.findByUserId(userId);
        if (memberships.isEmpty()) {
            return new GroupProfile(List.of(), List.of(), List.of(), null);
        }

        Map<UUID, SysUserGroupEntity> groupsById = groupRepository.findAllById(
                memberships.stream().map(SysUserGroupMemberEntity::getGroupId).toList()
        ).stream()
                .filter(group -> group.getDeletedAt() == null && group.isEnabled())
                .collect(Collectors.toMap(SysUserGroupEntity::getId, group -> group));

        List<Map<String, Object>> groups = new ArrayList<>();
        Set<String> allowedModels = new LinkedHashSet<>();
        Set<String> featureFlags = new LinkedHashSet<>();
        Integer chatRateLimit = null;

        for (SysUserGroupMemberEntity membership : memberships) {
            SysUserGroupEntity group = groupsById.get(membership.getGroupId());
            if (group == null) {
                continue;
            }
            groups.add(toDto(group, List.of(userId), 1));

            List<String> models = parseCsv(group.getAllowedModels());
            if (!models.isEmpty()) {
                allowedModels.addAll(models);
            }
            featureFlags.addAll(parseCsv(group.getFeatureFlags()));

            Integer limit = group.getChatRateLimitPerMinute();
            if (limit != null && limit > 0) {
                chatRateLimit = chatRateLimit == null ? limit : Math.min(chatRateLimit, limit);
            }
        }

        return new GroupProfile(groups, List.copyOf(allowedModels), List.copyOf(featureFlags), chatRateLimit);
    }

    public boolean isModelAllowed(UUID userId, String modelId) {
        if (modelId == null || modelId.isBlank()) {
            return true;
        }
        GroupProfile profile = resolveProfile(userId);
        if (profile.allowedModels().isEmpty()) {
            return true;
        }
        return profile.allowedModels().contains(modelId.trim());
    }

    public boolean isFeatureAllowed(UUID userId, String featureKey) {
        if (featureKey == null || featureKey.isBlank()) {
            return true;
        }
        GroupProfile profile = resolveProfile(userId);
        if (profile.featureFlags().isEmpty()) {
            return true;
        }
        String normalized = normalizeFeatureKey(featureKey);
        return profile.featureFlags().stream()
                .map(UserGroupService::normalizeFeatureKey)
                .anyMatch(normalized::equals);
    }

    private static String normalizeFeatureKey(String featureKey) {
        if (featureKey == null) {
            return "";
        }
        String normalized = featureKey.trim().toLowerCase(Locale.ROOT);
        if ("roleplay".equals(normalized)) {
            return "multi_agent_discussion";
        }
        return normalized;
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

    private static List<String> parseCsv(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof Iterable<?> iterable) {
            List<String> items = new ArrayList<>();
            for (Object item : iterable) {
                String valueText = item == null ? "" : String.valueOf(item).trim();
                if (!valueText.isEmpty()) {
                    items.add(valueText);
                }
            }
            return items.stream().distinct().toList();
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return List.of();
        }
        return Arrays.stream(text.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .distinct()
                .toList();
    }

    public record GroupProfile(
            List<Map<String, Object>> groups,
            List<String> allowedModels,
            List<String> featureFlags,
            Integer chatRateLimitPerMinute
    ) {
    }
}

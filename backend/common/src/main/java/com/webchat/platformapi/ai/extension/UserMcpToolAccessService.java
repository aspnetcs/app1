package com.webchat.platformapi.ai.extension;

import com.webchat.platformapi.market.MarketAssetType;
import com.webchat.platformapi.market.UserSavedAssetEntity;
import com.webchat.platformapi.market.UserSavedAssetRepository;
import com.webchat.platformapi.preferences.UserPreferenceEntity;
import com.webchat.platformapi.preferences.UserPreferenceRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class UserMcpToolAccessService {

    private final UserPreferenceRepository userPreferenceRepository;
    private final UserSavedAssetRepository userSavedAssetRepository;
    private final McpServerRepository mcpServerRepository;
    private final ObjectProvider<McpClientService> mcpClientServiceProvider;

    public UserMcpToolAccessService(
            UserPreferenceRepository userPreferenceRepository,
            UserSavedAssetRepository userSavedAssetRepository,
            McpServerRepository mcpServerRepository,
            ObjectProvider<McpClientService> mcpClientServiceProvider
    ) {
        this.userPreferenceRepository = userPreferenceRepository;
        this.userSavedAssetRepository = userSavedAssetRepository;
        this.mcpServerRepository = mcpServerRepository;
        this.mcpClientServiceProvider = mcpClientServiceProvider;
    }

    public List<McpClientService.McpTool> listSelectedTools(UUID userId) {
        if (userId == null) {
            return List.of();
        }
        McpClientService clientService = mcpClientServiceProvider.getIfAvailable();
        if (clientService == null || !clientService.getProperties().isEnabled()) {
            return List.of();
        }

        Selection selection = resolveSelection(userId);
        if (selection.serverIds().isEmpty()) {
            return List.of();
        }

        Map<Long, Integer> orderLookup = new LinkedHashMap<>();
        for (int index = 0; index < selection.serverIds().size(); index++) {
            orderLookup.put(selection.serverIds().get(index), index);
        }

        return clientService.listAllTools().stream()
                .filter(tool -> selection.serverIds().contains(tool.serverId()))
                .sorted((left, right) -> {
                    int leftOrder = orderLookup.getOrDefault(left.serverId(), Integer.MAX_VALUE);
                    int rightOrder = orderLookup.getOrDefault(right.serverId(), Integer.MAX_VALUE);
                    if (leftOrder != rightOrder) {
                        return Integer.compare(leftOrder, rightOrder);
                    }
                    return left.name().compareToIgnoreCase(right.name());
                })
                .toList();
    }

    public List<String> listSelectedToolNames(UUID userId) {
        return listSelectedTools(userId).stream()
                .map(McpClientService::toolDefinitionName)
                .distinct()
                .toList();
    }

    public List<Map<String, Object>> describeSelectedCatalog(UUID userId) {
        return listSelectedTools(userId).stream().map(tool -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", McpClientService.toolDefinitionName(tool));
            item.put("displayName", tool.name());
            item.put("description", tool.description());
            item.put("serverName", tool.serverName());
            item.put("source", "mcp");
            return item;
        }).toList();
    }

    private Selection resolveSelection(UUID userId) {
        UserPreferenceEntity preference = userPreferenceRepository.findById(userId).orElse(null);
        String mode = normalizeMode(preference == null ? null : preference.getMcpMode());
        if ("disabled".equals(mode)) {
            return new Selection(List.of());
        }

        String preferredSourceId = normalize(preference == null ? null : preference.getPreferredMcpServerId());
        List<UserSavedAssetEntity> savedAssets = userSavedAssetRepository
                .findByUserIdAndAssetTypeOrderBySortOrderAscCreatedAtDesc(userId, MarketAssetType.MCP)
                .stream()
                .filter(UserSavedAssetEntity::isEnabled)
                .toList();
        if (savedAssets.isEmpty()) {
            return new Selection(List.of());
        }

        LinkedHashSet<Long> orderedIds = new LinkedHashSet<>();
        if ("manual".equals(mode)) {
            Long preferredId = parseServerId(preferredSourceId);
            if (preferredId != null) {
                orderedIds.add(preferredId);
            }
        } else {
            Long preferredId = parseServerId(preferredSourceId);
            if (preferredId != null) {
                orderedIds.add(preferredId);
            }
            for (UserSavedAssetEntity asset : savedAssets) {
                Long serverId = parseServerId(asset.getSourceId());
                if (serverId != null) {
                    orderedIds.add(serverId);
                }
            }
        }

        if (orderedIds.isEmpty()) {
            return new Selection(List.of());
        }

        Map<Long, Boolean> availability = new LinkedHashMap<>();
        for (McpServerEntity server : mcpServerRepository.findAllById(new ArrayList<>(orderedIds))) {
            availability.put(server.getId(), server.isEnabled());
        }

        List<Long> serverIds = new ArrayList<>();
        for (Long serverId : orderedIds) {
            if (Boolean.TRUE.equals(availability.get(serverId))) {
                serverIds.add(serverId);
            }
        }
        return new Selection(serverIds);
    }

    private static String normalizeMode(String raw) {
        String value = normalize(raw);
        if (value == null) {
            return "auto";
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "disabled", "manual" -> normalized;
            default -> "auto";
        };
    }

    private static Long parseServerId(String raw) {
        String value = normalize(raw);
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        return value.isEmpty() ? null : value;
    }

    private record Selection(List<Long> serverIds) {
    }
}

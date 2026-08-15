package com.webchat.platformapi.ai.gateway;

import com.webchat.platformapi.ai.channel.AiChannelEntity;
import com.webchat.platformapi.ai.channel.ChannelRouter;
import com.webchat.platformapi.auth.role.RolePolicyService;
import com.webchat.platformapi.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

final class GatewayModelsFacade {

    private static final Logger log = LoggerFactory.getLogger(GatewayModelsFacade.class);

    private final ChannelRouter channelRouter;
    private final UserRepository userRepo;
    private final RolePolicyService rolePolicyService;

    GatewayModelsFacade(
            ChannelRouter channelRouter,
            UserRepository userRepo,
            RolePolicyService rolePolicyService
    ) {
        this.channelRouter = channelRouter;
        this.userRepo = userRepo;
        this.rolePolicyService = rolePolicyService;
    }

    ResponseEntity<Map<String, Object>> models(UUID userId, String role) {
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", Map.of("message", "unauthorized")));
        }
        try {
            if (userRepo.findByIdAndDeletedAtIsNull(userId).isEmpty()) {
                return ResponseEntity.status(401).body(Map.of("error", Map.of("message", "unauthorized")));
            }
        } catch (Exception e) {
            log.warn("[v1] models auth lookup failed (fail-closed): userId={}", userId);
            return ResponseEntity.status(503).body(Map.of("error", Map.of("message", "user lookup unavailable")));
        }

        Set<String> modelSet = new TreeSet<>();
        try {
            List<AiChannelEntity> channels = channelRouter.listRoutableChannels();
            for (AiChannelEntity channel : channels) {
                if (channel == null) {
                    continue;
                }
                addDeclaredModels(modelSet, channel.getModels());
                if (channel.getModelMapping() != null) {
                    modelSet.addAll(channel.getModelMapping().keySet());
                }
            }
        } catch (Exception e) {
            log.debug("[v1] models query failed: {}", e.getMessage());
        }

        if (rolePolicyService != null) {
            try {
                Set<String> allowed = rolePolicyService.resolveAllowedModels(userId, role);
                if (!allowed.isEmpty()) {
                    modelSet.retainAll(allowed);
                }
            } catch (Exception e) {
                log.warn("[v1] models role policy failed (fail-closed): userId={}", userId);
                return ResponseEntity.status(503).body(Map.of("error", Map.of("message", "policy unavailable")));
            }
        }

        List<Map<String, Object>> data = new ArrayList<>();
        for (String model : modelSet) {
            data.add(Map.of("id", model, "object", "model", "owned_by", "system"));
        }
        return ResponseEntity.ok(Map.of("object", "list", "data", data));
    }

    private static void addDeclaredModels(Set<String> modelSet, String declaredModels) {
        if (declaredModels == null) {
            return;
        }
        for (String model : declaredModels.split(",")) {
            String trimmed = model.trim();
            if (!trimmed.isEmpty()) {
                modelSet.add(trimmed);
            }
        }
    }
}

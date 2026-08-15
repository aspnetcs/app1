package com.webchat.platformapi.ai.channel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class ChannelRouter {

    private static final Logger log = LoggerFactory.getLogger(ChannelRouter.class);

    private final AiChannelRepository channelRepo;
    private final AiChannelKeyRepository keyRepo;
    private final StringRedisTemplate redis;

    public ChannelRouter(AiChannelRepository channelRepo, AiChannelKeyRepository keyRepo, StringRedisTemplate redis) {
        this.channelRepo = channelRepo;
        this.keyRepo = keyRepo;
        this.redis = redis;
    }

    public ChannelSelection select(String model) {
        return select(model, Set.of(), Set.of());
    }

    public ChannelSelection selectSpecificChannel(Long channelId, String model, Set<Long> excludeKeyIds) {
        if (channelId == null) throw new NoChannelException("channelId is null");
        AiChannelEntity channel = channelRepo.findById(channelId).orElseThrow(() -> new NoChannelException("channel not found: " + channelId));
        if (!channel.isEnabled() || channel.getStatus() == AiChannelStatus.DISABLED_MANUAL) {
            throw new NoChannelException("channel unavailable: " + channelId);
        }

        String requestedModel = model == null ? "" : model.trim();
        if (!supportsModel(channel, requestedModel)) {
            throw NoChannelException.forModel(requestedModel);
        }

        AiChannelKeyEntity key = pickKey(channelId, excludeKeyIds == null ? Set.of() : excludeKeyIds);
        String actualModel = channel.getMappedModel(requestedModel);
        return new ChannelSelection(channel, key, actualModel);
    }

    public ChannelSelection select(String model, Set<Long> excludeChannelIds, Set<Long> excludeKeyIds) {
        String m = model == null ? "" : model.trim();

        List<AiChannelEntity> filtered = findCandidateChannels(m, excludeChannelIds);
        if (filtered.isEmpty()) {
            log.warn("[ai-router] No channel for model='{}', excludeChannels={}", m, excludeChannelIds);
            throw NoChannelException.forModel(m);
        }

        int maxPriority = Integer.MIN_VALUE;
        for (AiChannelEntity ch : filtered) maxPriority = Math.max(maxPriority, ch.getPriority());

        List<AiChannelEntity> top = new ArrayList<>();
        for (AiChannelEntity ch : filtered) {
            if (ch.getPriority() == maxPriority) top.add(ch);
        }
        if (top.isEmpty()) throw NoChannelException.forModel(m);

        // If a channel has no available key, try the next channel in the same priority group.
        List<AiChannelEntity> pool = new ArrayList<>(top);
        while (!pool.isEmpty()) {
            AiChannelEntity selected = weightedRandom(pool);
            try {
                AiChannelKeyEntity key = pickKey(selected.getId(), excludeKeyIds == null ? Set.of() : excludeKeyIds);
                String actualModel = selected.getMappedModel(m);
                return new ChannelSelection(selected, key, actualModel);
            } catch (NoChannelException e) {
                pool.remove(selected);
            }
        }

        throw NoChannelException.forModel(m);
    }

    public List<AiChannelEntity> listRoutableChannels() {
        List<AiChannelEntity> channels = channelRepo.findByEnabledTrue();
        if (channels == null || channels.isEmpty()) {
            return List.of();
        }

        List<AiChannelEntity> normal = new ArrayList<>();
        List<AiChannelEntity> autoDisabled = new ArrayList<>();
        for (AiChannelEntity channel : channels) {
            if (channel == null || channel.getId() == null) continue;
            if (channel.getStatus() == AiChannelStatus.DISABLED_MANUAL) continue;
            if (!hasAvailableKey(channel.getId())) continue;
            if (channel.getStatus() == AiChannelStatus.NORMAL) {
                normal.add(channel);
            } else if (channel.getStatus() == AiChannelStatus.DISABLED_AUTO) {
                autoDisabled.add(channel);
            }
        }
        return !normal.isEmpty() ? normal : autoDisabled;
    }

    private List<AiChannelEntity> findCandidateChannels(String model, Set<Long> excludeChannelIds) {
        List<AiChannelEntity> channels = listRoutableChannels();
        if (channels.isEmpty()) {
            return List.of();
        }

        List<AiChannelEntity> filtered = new ArrayList<>();
        for (AiChannelEntity channel : channels) {
            if (channel == null || channel.getId() == null) {
                continue;
            }
            if (excludeChannelIds != null && excludeChannelIds.contains(channel.getId())) {
                continue;
            }
            if (!supportsModel(channel, model)) {
                continue;
            }
            filtered.add(channel);
        }
        return filtered;
    }

    private AiChannelKeyEntity pickKey(Long channelId, Set<Long> excludeKeyIds) {
        if (channelId == null) throw new NoChannelException("channelId is null");
        List<AiChannelKeyEntity> keys = keyRepo.findByChannel_IdOrderByIdAsc(channelId);
        if (keys == null) keys = List.of();

        List<AiChannelKeyEntity> available = filterKeys(keys, excludeKeyIds, AiChannelStatus.NORMAL);
        boolean fallbackToAutoDisabled = false;
        if (available.isEmpty()) {
            available = filterKeys(keys, excludeKeyIds, AiChannelStatus.DISABLED_AUTO);
            fallbackToAutoDisabled = !available.isEmpty();
        }
        if (available.isEmpty()) throw new NoChannelException("no key for channel: " + channelId);
        if (fallbackToAutoDisabled) {
            log.warn("[ai-router] fallback to auto-disabled key pool for channel {}", channelId);
        }

        int index = 0;
        try {
            String rrKey = "ai:ch:key_rr:" + channelId;
            Long n = redis.opsForValue().increment(rrKey);
            if (n != null && n > 0) {
                index = (int) ((n - 1) % available.size());
                if (n == 1) redis.expire(rrKey, java.time.Duration.ofHours(24));
            } else {
                index = ThreadLocalRandom.current().nextInt(available.size());
            }
        } catch (Exception e) {
            log.warn("[ai-router] Redis key RR failed, fallback to random", e);
            index = ThreadLocalRandom.current().nextInt(available.size());
        }
        return available.get(index);
    }

    private static AiChannelEntity weightedRandom(List<AiChannelEntity> channels) {
        if (channels == null || channels.isEmpty()) throw new NoChannelException("empty candidates");
        long total = 0;
        for (AiChannelEntity c : channels) total += Math.max(1, c.getWeight());
        if (total <= 0) return channels.get(0);

        long r = ThreadLocalRandom.current().nextLong(total);
        for (AiChannelEntity c : channels) {
            r -= Math.max(1, c.getWeight());
            if (r < 0) return c;
        }
        return channels.get(0);
    }

    /**
     * Check if a channel supports the requested model.
     * <p>
     * Both the {@code models} field (comma-separated) and {@code modelMapping} keys
     * support wildcard entries ending with {@code *} for prefix matching.
     * Examples: "gpt-5*" matches "gpt-5", "gpt-5.4", "gpt-5-codex-mini".
     * <p>
     * If neither {@code models} nor {@code modelMapping} is configured on the channel,
     * it is treated as supporting all models (catch-all channel).
     */
    private static boolean supportsModel(AiChannelEntity channel, String model) {
        if (channel == null) return false;
        if (model == null || model.isBlank()) return true;

        String models = channel.getModels();
        boolean hasModels = models != null && !models.isBlank();
        if (hasModels) {
            for (String item : models.split(",")) {
                String trimmed = item.trim();
                if (trimmed.isEmpty()) continue;
                if (model.equals(trimmed)) return true;
                // Wildcard: "gpt-5*" matches "gpt-5", "gpt-5.4", "gpt-5-codex"
                if (trimmed.endsWith("*")) {
                    String prefix = trimmed.substring(0, trimmed.length() - 1);
                    if (!prefix.isEmpty() && model.startsWith(prefix)) return true;
                }
            }
        }

        Map<String, String> mapping = channel.getModelMapping();
        boolean hasMapping = mapping != null && !mapping.isEmpty();
        if (hasMapping) {
            if (mapping.containsKey(model)) return true;
            for (String pattern : mapping.keySet()) {
                if (pattern == null || !pattern.endsWith("*")) continue;
                String prefix = pattern.substring(0, pattern.length() - 1);
                if (!prefix.isEmpty() && model.startsWith(prefix)) return true;
            }
        }

        // No models and no mapping configured -> support all models
        if (!hasModels && !hasMapping) return true;
        return false;
    }

    private boolean hasAvailableKey(Long channelId) {
        if (channelId == null) {
            return false;
        }
        List<AiChannelKeyEntity> keys = keyRepo.findByChannel_IdOrderByIdAsc(channelId);
        if (keys == null || keys.isEmpty()) {
            return false;
        }
        List<AiChannelKeyEntity> normalKeys = filterKeys(keys, Set.of(), AiChannelStatus.NORMAL);
        List<AiChannelKeyEntity> autoKeys = filterKeys(keys, Set.of(), AiChannelStatus.DISABLED_AUTO);
        return !normalKeys.isEmpty() || !autoKeys.isEmpty();
    }

    private static List<AiChannelKeyEntity> filterKeys(List<AiChannelKeyEntity> keys, Set<Long> excludeKeyIds, int status) {
        List<AiChannelKeyEntity> filtered = new ArrayList<>();
        for (AiChannelKeyEntity key : keys) {
            if (key == null || key.getId() == null) continue;
            if (!key.isEnabled()) continue;
            if (key.getStatus() != status) continue;
            if (excludeKeyIds != null && excludeKeyIds.contains(key.getId())) continue;
            filtered.add(key);
        }
        return filtered;
    }
}

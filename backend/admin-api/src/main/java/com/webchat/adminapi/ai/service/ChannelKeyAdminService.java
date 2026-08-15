package com.webchat.adminapi.ai.service;

import com.webchat.adminapi.ai.dto.ChannelKeyStatusUpdateRequest;
import com.webchat.platformapi.ai.channel.AiChannelEntity;
import com.webchat.platformapi.ai.channel.AiChannelKeyEntity;
import com.webchat.platformapi.ai.channel.AiChannelKeyRepository;
import com.webchat.platformapi.ai.channel.AiChannelRepository;
import com.webchat.platformapi.ai.channel.AiChannelStatus;
import com.webchat.platformapi.ai.controller.dto.ChannelKeysRequest;
import com.webchat.platformapi.ai.security.AiCryptoService;
import com.webchat.platformapi.common.api.ApiResponse;
import com.webchat.platformapi.common.api.ErrorCodes;
import com.webchat.platformapi.common.util.RequestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ChannelKeyAdminService {

    private static final Logger log = LoggerFactory.getLogger(ChannelKeyAdminService.class);

    private final AiChannelRepository channelRepo;
    private final AiChannelKeyRepository keyRepo;
    private final AiCryptoService crypto;

    public ChannelKeyAdminService(
            AiChannelRepository channelRepo,
            AiChannelKeyRepository keyRepo,
            AiCryptoService crypto
    ) {
        this.channelRepo = channelRepo;
        this.keyRepo = keyRepo;
        this.crypto = crypto;
    }

    public boolean isConfigured() {
        return crypto.isConfigured();
    }

    public int countKeys(Long channelId) {
        try {
            return keyRepo.findByChannel_IdOrderByIdAsc(channelId).size();
        } catch (Exception e) {
            log.warn("[admin] count keys failed for channel {}", channelId, e);
            return 0;
        }
    }

    public String findFirstDecryptedKey(Long channelId) {
        List<AiChannelKeyEntity> keys = keyRepo.findByChannel_IdOrderByIdAsc(channelId);
        if (keys == null || keys.isEmpty()) {
            return null;
        }
        try {
            return crypto.decrypt(keys.get(0).getApiKeyEncrypted());
        } catch (Exception e) {
            log.warn("[admin] decrypt first key failed for channel {}", channelId, e);
            return null;
        }
    }

    public ApiResponse<List<Map<String, Object>>> listKeys(Long id) {
        List<AiChannelKeyEntity> keys = keyRepo.findByChannel_IdOrderByIdAsc(id);
        List<Map<String, Object>> out = new ArrayList<>();
        for (AiChannelKeyEntity key : keys) {
            if (key == null) {
                continue;
            }
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", key.getId());
            map.put("keyHash", key.getKeyHash());
            map.put("enabled", key.isEnabled());
            map.put("status", key.getStatus());
            map.put("weight", key.getWeight());
            map.put("consecutiveFailures", key.getConsecutiveFailures());
            map.put("successCount", key.getSuccessCount());
            map.put("failCount", key.getFailCount());
            map.put("lastSuccessAt", key.getLastSuccessAt() == null ? "" : key.getLastSuccessAt().toString());
            map.put("lastFailAt", key.getLastFailAt() == null ? "" : key.getLastFailAt().toString());
            map.put("createdAt", key.getCreatedAt() == null ? "" : key.getCreatedAt().toString());
            map.put("updatedAt", key.getUpdatedAt() == null ? "" : key.getUpdatedAt().toString());
            out.add(map);
        }
        return ApiResponse.ok(out);
    }

    public ApiResponse<Map<String, Object>> addKeys(Long id, ChannelKeysRequest req) {
        if (!crypto.isConfigured()) {
            return ApiResponse.error(ErrorCodes.SERVER_ERROR, "AI_MASTER_KEY 未配置");
        }
        AiChannelEntity channel = channelRepo.findById(id).orElse(null);
        if (channel == null) {
            return ApiResponse.error(ErrorCodes.SERVER_ERROR, "channel 不存在");
        }

        int created = addKeys(channel, req == null ? null : req.apiKeys(), req == null ? null : req.apiKey());
        return ApiResponse.ok(Map.of("createdKeys", created));
    }

    public int addKeys(AiChannelEntity channel, List<String> apiKeys, String apiKeyCompat) {
        if (channel == null || channel.getId() == null) {
            return 0;
        }
        List<String> keys = normalizeKeys(apiKeys, apiKeyCompat);

        int created = 0;
        for (String plain : keys) {
            String hash = crypto.sha256Hex(plain);
            String enc = crypto.encrypt(plain);
            AiChannelKeyEntity entity = new AiChannelKeyEntity();
            entity.setChannel(channel);
            entity.setKeyHash(hash);
            entity.setApiKeyEncrypted(enc);
            entity.setEnabled(true);
            entity.setStatus(AiChannelStatus.NORMAL);
            entity.setWeight(1);
            try {
                keyRepo.save(entity);
                created += 1;
            } catch (DataIntegrityViolationException ignored) {
                // duplicate key_hash for the same channel, ignore
            } catch (Exception ex) {
                log.warn("[admin] save key failed for channel {}", channel.getId(), ex);
            }
        }
        return created;
    }

    public void deleteAllForChannel(Long channelId) {
        List<AiChannelKeyEntity> keys = keyRepo.findByChannel_IdOrderByIdAsc(channelId);
        if (keys != null && !keys.isEmpty()) {
            keyRepo.deleteAll(keys);
        }
    }

    public ApiResponse<Void> updateKeyStatus(Long id, Long keyId, ChannelKeyStatusUpdateRequest body) {
        AiChannelKeyEntity key = keyRepo.findById(keyId).orElse(null);
        if (key == null || key.getChannel() == null || !Objects.equals(key.getChannel().getId(), id)) {
            return ApiResponse.error(ErrorCodes.SERVER_ERROR, "key 不存在");
        }
        if (body != null) {
            if (body.enabled() != null) {
                key.setEnabled(body.enabled());
            }
            if (body.status() != null) {
                key.setStatus(body.status());
            }
            if (body.weight() != null) {
                key.setWeight(Math.max(1, body.weight()));
            }
        }
        keyRepo.save(key);
        return ApiResponse.ok("ok", null);
    }

    public ApiResponse<Void> deleteKey(Long id, Long keyId) {
        AiChannelKeyEntity key = keyRepo.findById(keyId).orElse(null);
        if (key != null && key.getChannel() != null && Objects.equals(key.getChannel().getId(), id)) {
            try {
                keyRepo.deleteById(keyId);
            } catch (Exception e) {
                log.warn("[admin] delete key {} failed", keyId, e);
            }
        }
        return ApiResponse.ok("已删除", null);
    }

    private static List<String> normalizeKeys(List<String> apiKeys, String apiKeyCompat) {
        List<String> keys = new ArrayList<>();
        if (apiKeys != null && !apiKeys.isEmpty()) {
            for (String key : apiKeys) {
                String value = RequestUtils.trimOrNull(key);
                if (value != null) {
                    keys.add(value);
                }
            }
            return keys;
        }

        String compat = RequestUtils.trimOrNull(apiKeyCompat);
        if (compat == null) {
            return keys;
        }
        for (String part : compat.split("\\|")) {
            String value = RequestUtils.trimOrNull(part);
            if (value != null) {
                keys.add(value);
            }
        }
        return keys;
    }
}

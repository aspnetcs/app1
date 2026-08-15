package com.webchat.adminapi.ai.service;

import com.webchat.adminapi.ai.dto.ChannelFetchModelsRequest;
import com.webchat.adminapi.ai.dto.ChannelKeyStatusUpdateRequest;
import com.webchat.adminapi.ai.dto.ChannelStatusUpdateRequest;
import com.webchat.adminapi.ai.dto.ChannelTestRequest;
import com.webchat.adminapi.ai.helper.ChannelValidationHelper;
import com.webchat.platformapi.ai.channel.AiChannelEntity;
import com.webchat.platformapi.ai.channel.AiChannelKeyRepository;
import com.webchat.platformapi.ai.channel.AiChannelRepository;
import com.webchat.platformapi.ai.channel.ChannelMonitor;
import com.webchat.platformapi.ai.controller.dto.ChannelKeysRequest;
import com.webchat.platformapi.ai.controller.dto.ChannelUpsertRequest;
import com.webchat.platformapi.ai.security.AiCryptoService;
import com.webchat.platformapi.ai.security.SsrfGuard;
import com.webchat.platformapi.common.api.ApiResponse;
import com.webchat.platformapi.common.api.ErrorCodes;
import com.webchat.platformapi.common.util.RequestUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AiChannelAdminService {

    private final AiChannelRepository channelRepo;
    private final ChannelValidationHelper channelValidationHelper;
    private final ChannelKeyAdminService channelKeyAdminService;
    private final ModelAdminService modelAdminService;
    private final ChannelTestService channelTestService;
    private final ChannelUsageAdminService channelUsageAdminService;

    public AiChannelAdminService(
            AiChannelRepository channelRepo,
            AiChannelKeyRepository keyRepo,
            AiCryptoService crypto,
            ChannelValidationHelper channelValidationHelper,
            ChannelMonitor channelMonitor,
            JdbcTemplate jdbc
    ) {
        this.channelRepo = channelRepo;
        this.channelValidationHelper = channelValidationHelper;
        this.channelKeyAdminService = new ChannelKeyAdminService(channelRepo, keyRepo, crypto);
        this.modelAdminService = new ModelAdminService(channelRepo, channelValidationHelper, channelKeyAdminService);
        this.channelTestService = new ChannelTestService(channelRepo, channelMonitor);
        this.channelUsageAdminService = new ChannelUsageAdminService(channelRepo, keyRepo, jdbc);
    }

    public ApiResponse<Map<String, Object>> list(int page, int size, String keyword) {
        List<AiChannelEntity> channels = channelRepo.findAll();
        List<Map<String, Object>> out = new ArrayList<>();
        String normalizedKeyword = RequestUtils.trimOrNull(keyword);
        for (AiChannelEntity channel : channels) {
            if (channel == null) {
                continue;
            }
            int keyCount = channelKeyAdminService.countKeys(channel.getId());
            Map<String, Object> item = ChannelAdminMapper.toChannelDto(channel, keyCount);
            if (ChannelAdminMapper.matchesKeyword(item, normalizedKeyword)) {
                out.add(item);
            }
        }
        int safePage = Math.max(0, page);
        int safeSize = size <= 0 ? Math.max(1, out.size()) : Math.max(1, Math.min(100, size));
        int total = out.size();
        int maxPage = total == 0 ? 0 : (total - 1) / safeSize;
        int resolvedPage = Math.min(safePage, maxPage);
        int from = Math.min(resolvedPage * safeSize, total);
        int to = Math.min(from + safeSize, total);
        return ApiResponse.ok(Map.of(
                "items", out.subList(from, to),
                "total", total,
                "page", resolvedPage,
                "size", safeSize
        ));
    }

    public ApiResponse<Map<String, Object>> create(ChannelUpsertRequest req) {
        if (!channelKeyAdminService.isConfigured()) {
            return ApiResponse.error(ErrorCodes.SERVER_ERROR, "AI_MASTER_KEY 未配置");
        }

        String name = RequestUtils.trimOrNull(req == null ? null : req.name());
        String type = RequestUtils.trimOrNull(req == null ? null : req.type());
        String baseUrl = RequestUtils.trimOrNull(req == null ? null : req.baseUrl());
        if (name == null) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, "参数缺失: name");
        }
        if (type == null) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, "参数缺失: type");
        }
        if (baseUrl == null) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, "参数缺失: base_url");
        }

        try {
            channelValidationHelper.assertAllowedBaseUrl(baseUrl);
        } catch (SsrfGuard.SsrfException e) {
            return ApiResponse.error(ErrorCodes.SERVER_ERROR, "SSRF 拦截: " + e.getMessage());
        }

        AiChannelEntity channel = new AiChannelEntity();
        channel.setName(name);
        channel.setType(type.toLowerCase());
        channel.setBaseUrl(baseUrl);
        channel.setModels(RequestUtils.trimOrNull(req.models()));
        if (req.testModel() != null) {
            channel.setTestModel(RequestUtils.trimOrNull(req.testModel()));
        }
        if (req.fallbackChannelId() != null) {
            if (!channelRepo.existsById(req.fallbackChannelId())) {
                return ApiResponse.error(ErrorCodes.PARAM_MISSING, "fallbackChannelId not found");
            }
            channel.setFallbackChannelId(req.fallbackChannelId());
        } else {
            channel.setFallbackChannelId(null);
        }
        if (req.modelMapping() != null) {
            channel.setModelMapping(new HashMap<>(req.modelMapping()));
        }
        if (req.extraConfig() != null) {
            channel.setExtraConfig(new HashMap<>(req.extraConfig()));
        }
        if (req.priority() != null) {
            channel.setPriority(req.priority());
        }
        if (req.weight() != null) {
            channel.setWeight(req.weight());
        }
        if (req.maxConcurrent() != null) {
            channel.setMaxConcurrent(req.maxConcurrent());
        }
        if (req.enabled() != null) {
            channel.setEnabled(req.enabled());
        }
        if (req.status() != null) {
            channel.setStatus(req.status());
        }

        AiChannelEntity saved = channelRepo.save(channel);
        int createdKeys = channelKeyAdminService.addKeys(saved, req.apiKeys(), req.apiKey());
        return ApiResponse.ok(Map.of(
                "channel", ChannelAdminMapper.toChannelDto(saved, createdKeys),
                "createdKeys", createdKeys
        ));
    }

    public ApiResponse<Map<String, Object>> update(Long id, ChannelUpsertRequest req) {
        AiChannelEntity channel = channelRepo.findById(id).orElse(null);
        if (channel == null) {
            return ApiResponse.error(ErrorCodes.SERVER_ERROR, "channel 不存在");
        }

        String name = RequestUtils.trimOrNull(req == null ? null : req.name());
        String type = RequestUtils.trimOrNull(req == null ? null : req.type());
        String baseUrl = RequestUtils.trimOrNull(req == null ? null : req.baseUrl());
        if (name != null) {
            channel.setName(name);
        }
        if (type != null) {
            channel.setType(type.toLowerCase());
        }
        if (baseUrl != null) {
            try {
                channelValidationHelper.assertAllowedBaseUrl(baseUrl);
                channel.setBaseUrl(baseUrl);
            } catch (SsrfGuard.SsrfException e) {
                return ApiResponse.error(ErrorCodes.SERVER_ERROR, "SSRF 拦截: " + e.getMessage());
            }
        }
        if (req.models() != null) {
            channel.setModels(RequestUtils.trimOrNull(req.models()));
        }
        if (req.testModel() != null) {
            channel.setTestModel(RequestUtils.trimOrNull(req.testModel()));
        }
        if (req.fallbackChannelId() != null) {
            if (req.fallbackChannelId().equals(id)) {
                return ApiResponse.error(ErrorCodes.PARAM_MISSING, "fallbackChannelId cannot point to itself");
            }
            if (!channelRepo.existsById(req.fallbackChannelId())) {
                return ApiResponse.error(ErrorCodes.PARAM_MISSING, "fallbackChannelId not found");
            }
            channel.setFallbackChannelId(req.fallbackChannelId());
        }
        if (req.modelMapping() != null) {
            channel.setModelMapping(new HashMap<>(req.modelMapping()));
        }
        if (req.extraConfig() != null) {
            channel.setExtraConfig(new HashMap<>(req.extraConfig()));
        }
        if (req.priority() != null) {
            channel.setPriority(req.priority());
        }
        if (req.weight() != null) {
            channel.setWeight(req.weight());
        }
        if (req.maxConcurrent() != null) {
            channel.setMaxConcurrent(req.maxConcurrent());
        }
        if (req.enabled() != null) {
            channel.setEnabled(req.enabled());
        }
        if (req.status() != null) {
            channel.setStatus(req.status());
        }

        AiChannelEntity saved = channelRepo.save(channel);

        // Save new API keys if provided (F3 fix: update() must persist keys like create() does)
        int addedKeys = channelKeyAdminService.addKeys(saved, req.apiKeys(), req.apiKey());

        return ApiResponse.ok(Map.of(
                "channel", ChannelAdminMapper.toChannelDto(saved, channelKeyAdminService.countKeys(saved.getId())),
                "addedKeys", addedKeys
        ));
    }

    public ApiResponse<Void> delete(Long id) {
        try {
            channelKeyAdminService.deleteAllForChannel(id);
            channelRepo.deleteById(id);
        } catch (Exception e) {
            return ApiResponse.error(ErrorCodes.SERVER_ERROR, "删除失败: " + (e.getMessage() == null ? "unknown" : e.getMessage()));
        }
        return ApiResponse.ok("已删除", null);
    }

    public ApiResponse<Map<String, Object>> fetchModels(ChannelFetchModelsRequest body) {
        return modelAdminService.fetchModels(body);
    }

    public ApiResponse<Map<String, Object>> updateStatus(Long id, ChannelStatusUpdateRequest body) {
        AiChannelEntity channel = channelRepo.findById(id).orElse(null);
        if (channel == null) {
            return ApiResponse.error(ErrorCodes.SERVER_ERROR, "channel 不存在");
        }
        if (body != null) {
            if (body.enabled() != null) {
                channel.setEnabled(body.enabled());
            }
            if (body.status() != null) {
                channel.setStatus(body.status());
            }
        }
        AiChannelEntity saved = channelRepo.save(channel);
        return ApiResponse.ok(Map.of(
                "channel", ChannelAdminMapper.toChannelDto(saved, channelKeyAdminService.countKeys(saved.getId()))
        ));
    }

    public ApiResponse<ChannelMonitor.ProbeResult> test(Long id, ChannelTestRequest body) {
        return channelTestService.test(id, body);
    }

    public ApiResponse<Map<String, Object>> testAll(Integer limit, ChannelTestRequest body) {
        return channelTestService.testAll(limit, body);
    }

    public ApiResponse<Map<String, Object>> usage(Integer hours) {
        return channelUsageAdminService.usage(hours);
    }

    public ApiResponse<List<Map<String, Object>>> listKeys(Long id) {
        return channelKeyAdminService.listKeys(id);
    }

    public ApiResponse<Map<String, Object>> addKeys(Long id, ChannelKeysRequest req) {
        return channelKeyAdminService.addKeys(id, req);
    }

    public ApiResponse<Void> updateKeyStatus(Long id, Long keyId, ChannelKeyStatusUpdateRequest body) {
        return channelKeyAdminService.updateKeyStatus(id, keyId, body);
    }

    public ApiResponse<Void> deleteKey(Long id, Long keyId) {
        return channelKeyAdminService.deleteKey(id, keyId);
    }
}

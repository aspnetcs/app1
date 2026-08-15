package com.webchat.adminapi.ai.service;

import com.webchat.adminapi.ai.dto.ChannelTestRequest;
import com.webchat.platformapi.ai.channel.AiChannelEntity;
import com.webchat.platformapi.ai.channel.AiChannelRepository;
import com.webchat.platformapi.ai.channel.ChannelMonitor;
import com.webchat.platformapi.common.api.ApiResponse;
import com.webchat.platformapi.common.api.ErrorCodes;
import com.webchat.platformapi.common.util.RequestUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class ChannelTestService {

    private final AiChannelRepository channelRepo;
    private final ChannelMonitor channelMonitor;

    public ChannelTestService(AiChannelRepository channelRepo, ChannelMonitor channelMonitor) {
        this.channelRepo = channelRepo;
        this.channelMonitor = channelMonitor;
    }

    public ApiResponse<ChannelMonitor.ProbeResult> test(Long id, ChannelTestRequest body) {
        if (channelMonitor == null) {
            return ApiResponse.error(ErrorCodes.SERVER_ERROR, "monitor not available");
        }
        String model = body == null ? null : RequestUtils.trimOrNull(body.model());
        return ApiResponse.ok(channelMonitor.probeChannel(id, model, true));
    }

    public ApiResponse<Map<String, Object>> testAll(Integer limit, ChannelTestRequest body) {
        if (channelMonitor == null) {
            return ApiResponse.error(ErrorCodes.SERVER_ERROR, "monitor not available");
        }
        int safeLimit = limit == null ? 50 : Math.max(1, Math.min(200, limit));
        String model = body == null ? null : RequestUtils.trimOrNull(body.model());

        List<AiChannelEntity> channels = channelRepo.findAll();
        channels.sort(Comparator.comparing(AiChannelEntity::getId, Comparator.nullsLast(Long::compareTo)));
        if (channels.size() > safeLimit) {
            channels = channels.subList(0, safeLimit);
        }

        List<ChannelMonitor.ProbeResult> results = new ArrayList<>();
        int okCount = 0;
        for (AiChannelEntity channel : channels) {
            if (channel == null || channel.getId() == null) {
                continue;
            }
            ChannelMonitor.ProbeResult result = channelMonitor.probeChannel(channel.getId(), model, true);
            results.add(result);
            if (result != null && result.ok()) {
                okCount += 1;
            }
        }

        return ApiResponse.ok(Map.of(
                "total", results.size(),
                "ok", okCount,
                "results", results
        ));
    }
}

package com.webchat.platformapi.ai.usage;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AiUsageService {

    private final AiUsageLogRepository repo;

    public AiUsageService(AiUsageLogRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public void logUsage(UUID userId, Long channelId, String channelType, String model,
                         int promptTokens, int completionTokens, long latencyMs,
                         boolean success, String requestId) {
        persistUsage(userId, channelId, channelType, model,
                promptTokens, completionTokens, latencyMs, success, requestId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void logUsageStrict(UUID userId, Long channelId, String channelType, String model,
                               int promptTokens, int completionTokens, long latencyMs,
                               boolean success, String requestId) {
        persistUsage(userId, channelId, channelType, model,
                promptTokens, completionTokens, latencyMs, success, requestId);
    }

    private void persistUsage(UUID userId, Long channelId, String channelType, String model,
                              int promptTokens, int completionTokens, long latencyMs,
                              boolean success, String requestId) {
        AiUsageLogEntity entity = new AiUsageLogEntity();
        entity.setUserId(userId);
        entity.setChannelId(channelId);
        entity.setChannelType(channelType);
        entity.setModel(model);
        entity.setPromptTokens(promptTokens);
        entity.setCompletionTokens(completionTokens);
        entity.setTotalTokens(promptTokens + completionTokens);
        entity.setLatencyMs(latencyMs);
        entity.setSuccess(success);
        entity.setRequestId(requestId);
        repo.save(entity);
    }

    public Map<String, Object> getUserSummary(UUID userId, Instant from, Instant to) {
        return repo.summarizeByUser(userId, from, to);
    }

    public List<Map<String, Object>> getAdminSummary(Instant from, Instant to) {
        return repo.summarizeByChannelAndModel(from, to);
    }
}

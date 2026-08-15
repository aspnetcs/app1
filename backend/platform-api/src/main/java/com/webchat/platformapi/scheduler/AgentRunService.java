package com.webchat.platformapi.scheduler;

import com.webchat.platformapi.agent.AgentEntity;
import com.webchat.platformapi.agent.AgentRepository;
import com.webchat.platformapi.ai.channel.AiChannelRepository;
import com.webchat.platformapi.scheduler.entity.AgentRunApprovalEntity;
import com.webchat.platformapi.scheduler.entity.AgentRunEntity;
import com.webchat.platformapi.scheduler.repository.AgentRunApprovalRepository;
import com.webchat.platformapi.scheduler.repository.AgentRunRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class AgentRunService {

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_APPROVED = "approved";
    public static final String STATUS_REJECTED = "rejected";
    public static final String STATUS_RUNNING = "running";
    public static final String STATUS_COMPLETED = "completed";
    public static final String STATUS_FAILED = "failed";

    public static final String APPROVAL_PENDING = "pending";
    public static final String APPROVAL_APPROVED = "approved";
    public static final String APPROVAL_REJECTED = "rejected";

    private final AgentRunRepository runRepo;
    private final AgentRunApprovalRepository approvalRepo;
    private final AgentRepository agentRepo;
    private final AiChannelRepository channelRepo;

    public AgentRunService(
            AgentRunRepository runRepo,
            AgentRunApprovalRepository approvalRepo,
            AgentRepository agentRepo,
            AiChannelRepository channelRepo
    ) {
        this.runRepo = runRepo;
        this.approvalRepo = approvalRepo;
        this.agentRepo = agentRepo;
        this.channelRepo = channelRepo;
    }

    @Transactional
    public Map<String, Object> createRun(UUID userId, UUID agentId, Long requestedChannelId) {
        if (userId == null) throw new IllegalArgumentException("userId required");
        if (agentId == null) throw new IllegalArgumentException("agentId required");

        AgentEntity agent = agentRepo.findByIdAndDeletedAtIsNull(agentId)
                .orElseThrow(() -> new IllegalArgumentException("agent not found"));
        if (!agent.isEnabled()) {
            throw new IllegalArgumentException("agent is disabled");
        }

        if (requestedChannelId != null && !channelRepo.existsById(requestedChannelId)) {
            throw new IllegalArgumentException("requestedChannelId not found");
        }

        AgentRunEntity run = new AgentRunEntity();
        run.setId(UUID.randomUUID());
        run.setUserId(userId);
        run.setAgentId(agentId);
        run.setRequestedChannelId(requestedChannelId);
        run.setStatus(STATUS_PENDING);
        runRepo.save(run);

        AgentRunApprovalEntity approval = new AgentRunApprovalEntity();
        approval.setId(UUID.randomUUID());
        approval.setRunId(run.getId());
        approval.setStatus(APPROVAL_PENDING);
        approvalRepo.save(approval);

        return toMap(run, approval);
    }

    public Map<String, Object> getRun(UUID userId, UUID runId) {
        AgentRunEntity run = runRepo.findById(runId).orElseThrow(() -> new IllegalArgumentException("run not found"));
        if (!run.getUserId().equals(userId)) throw new IllegalArgumentException("run not found");
        AgentRunApprovalEntity approval = approvalRepo.findByRunId(runId).orElse(null);
        return toMap(run, approval);
    }

    public Map<String, Object> listRuns(UUID userId, int page, int size) {
        int resolvedSize = Math.min(Math.max(size, 1), 100);
        int resolvedPage = Math.max(page, 0);
        Page<AgentRunEntity> result = runRepo.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(resolvedPage, resolvedSize));
        return Map.of(
                "items", result.getContent().stream().map(run -> toMap(run, approvalRepo.findByRunId(run.getId()).orElse(null))).toList(),
                "total", result.getTotalElements(),
                "page", result.getNumber(),
                "size", result.getSize()
        );
    }

    @Transactional
    public Map<String, Object> startRun(UUID userId, UUID runId) {
        AgentRunEntity run = runRepo.findById(runId).orElseThrow(() -> new IllegalArgumentException("run not found"));
        if (!run.getUserId().equals(userId)) throw new IllegalArgumentException("run not found");
        if (!STATUS_APPROVED.equals(run.getStatus())) {
            throw new IllegalStateException("run is not approved");
        }
        run.setStatus(STATUS_RUNNING);
        run.setStartedAt(Instant.now());
        runRepo.save(run);
        return toMap(run, approvalRepo.findByRunId(runId).orElse(null));
    }

    @Transactional
    public Map<String, Object> completeRun(UUID userId, UUID runId) {
        AgentRunEntity run = runRepo.findById(runId).orElseThrow(() -> new IllegalArgumentException("run not found"));
        if (!run.getUserId().equals(userId)) throw new IllegalArgumentException("run not found");
        if (!STATUS_RUNNING.equals(run.getStatus())) {
            throw new IllegalStateException("run is not running");
        }
        run.setStatus(STATUS_COMPLETED);
        run.setCompletedAt(Instant.now());
        runRepo.save(run);
        return toMap(run, approvalRepo.findByRunId(runId).orElse(null));
    }

    @Transactional
    public Map<String, Object> failRun(UUID userId, UUID runId, String errorMessage) {
        AgentRunEntity run = runRepo.findById(runId).orElseThrow(() -> new IllegalArgumentException("run not found"));
        if (!run.getUserId().equals(userId)) throw new IllegalArgumentException("run not found");
        if (!STATUS_RUNNING.equals(run.getStatus())) {
            throw new IllegalStateException("run is not running");
        }
        run.setStatus(STATUS_FAILED);
        run.setCompletedAt(Instant.now());
        run.setErrorMessage(errorMessage != null ? errorMessage.trim() : null);
        runRepo.save(run);
        return toMap(run, approvalRepo.findByRunId(runId).orElse(null));
    }

    private static Map<String, Object> toMap(AgentRunEntity run, AgentRunApprovalEntity approval) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", run.getId() != null ? run.getId().toString() : null);
        map.put("userId", run.getUserId() != null ? run.getUserId().toString() : null);
        map.put("agentId", run.getAgentId() != null ? run.getAgentId().toString() : null);
        map.put("requestedChannelId", run.getRequestedChannelId());
        map.put("boundChannelId", run.getBoundChannelId());
        map.put("status", run.getStatus());
        map.put("errorMessage", run.getErrorMessage());
        map.put("createdAt", run.getCreatedAt() != null ? run.getCreatedAt().toString() : null);
        map.put("updatedAt", run.getUpdatedAt() != null ? run.getUpdatedAt().toString() : null);
        map.put("startedAt", run.getStartedAt() != null ? run.getStartedAt().toString() : null);
        map.put("completedAt", run.getCompletedAt() != null ? run.getCompletedAt().toString() : null);
        if (approval != null) {
            Map<String, Object> approvalMap = new LinkedHashMap<>();
            approvalMap.put("status", approval.getStatus());
            approvalMap.put("decidedBy", approval.getDecidedBy() != null ? approval.getDecidedBy().toString() : null);
            approvalMap.put("decidedAt", approval.getDecidedAt() != null ? approval.getDecidedAt().toString() : null);
            approvalMap.put("note", approval.getNote());
            map.put("approval", approvalMap);
        } else {
            map.put("approval", null);
        }
        return map;
    }
}

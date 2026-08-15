package com.webchat.platformapi.ai.gateway;

import com.webchat.platformapi.ai.usage.AiUsageService;
import com.webchat.platformapi.credits.CreditsRuntimeService;
import com.webchat.platformapi.user.UserEntity;
import com.webchat.platformapi.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;
import java.util.UUID;

final class GatewayQuotaService {

    private static final Logger log = LoggerFactory.getLogger(GatewayQuotaService.class);

    private final TransactionTemplate transactionTemplate;
    private final AiUsageService usageService;
    private final UserRepository userRepo;
    private final CreditsRuntimeService creditsRuntimeService;

    GatewayQuotaService(PlatformTransactionManager transactionManager, AiUsageService usageService,
                        UserRepository userRepo, CreditsRuntimeService creditsRuntimeService) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.usageService = usageService;
        this.userRepo = userRepo;
        this.creditsRuntimeService = creditsRuntimeService;
    }

    void persistUsageAndDeductQuota(UUID userId, Long channelId, String model,
                                    int promptTokens, int completionTokens, int totalTokens,
                                    String requestId) {
        int finalTotal = totalTokens > 0 ? totalTokens : (promptTokens + completionTokens);
        if (finalTotal <= 0) {
            throw new IllegalStateException("usage missing");
        }

        transactionTemplate.executeWithoutResult(status -> {
            usageService.logUsageStrict(userId, channelId, "gateway", model,
                    promptTokens, completionTokens, 0L, true, requestId);

            int updated = userRepo.incrementTokenUsedIfWithinQuota(userId, finalTotal);
            if (updated <= 0) {
                throw new IllegalStateException("quota deduct skipped");
            }
        });
    }

    long reserveStreamingQuota(UUID userId, Map<String, Object> requestBody) {
        try {
            UserEntity user = userRepo.findByIdAndDeletedAtIsNull(userId).orElse(null);
            if (user == null) {
                throw new IllegalStateException("unauthorized");
            }
            long quota = user.getTokenQuota();
            if (quota <= 0) {
                return 0L;
            }
            long remaining = quota - user.getTokenUsed();
            if (remaining <= 0) {
                throw new IllegalStateException("Token quota exceeded");
            }
            Integer updated = transactionTemplate.execute(status -> userRepo.incrementTokenUsedIfWithinQuota(userId, remaining));
            if (updated == null || updated <= 0) {
                throw new IllegalStateException("Token quota exceeded");
            }
            applyStreamingQuotaLimit(requestBody, remaining);
            return remaining;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[v1] quota reserve failed (fail-closed): userId={}, error={}", userId, e.toString());
            throw new IllegalStateException("Quota service unavailable", e);
        }
    }

    void releaseReservedQuota(UUID userId, long reservedTokens) {
        if (reservedTokens <= 0) {
            return;
        }
        Integer updated = transactionTemplate.execute(status -> userRepo.decrementTokenUsed(userId, reservedTokens));
        if (updated == null || updated <= 0) {
            throw new IllegalStateException("quota refund skipped");
        }
    }

    void logUsageAndFinalizeReservedQuota(UUID userId, long reservedTokens, Long channelId, String model,
                                          int promptTokens, int completionTokens, int totalTokens,
                                          String requestId, boolean usageKnown) {
        transactionTemplate.executeWithoutResult(status -> {
            usageService.logUsageStrict(userId, channelId, "gateway", model,
                    promptTokens, completionTokens, 0L, true, requestId);

            if (!usageKnown || reservedTokens <= 0) {
                return;
            }

            long finalTotal = Math.max(0L, totalTokens);
            long refund = reservedTokens - Math.min(reservedTokens, finalTotal);
            if (refund <= 0) {
                return;
            }

            int updated = userRepo.decrementTokenUsed(userId, refund);
            if (updated <= 0) {
                throw new IllegalStateException("quota refund skipped");
            }
        });
    }

    void finalizeReservedQuotaAfterStream(UUID userId, long reservedTokens, Long channelId, String model,
                                          int promptTokens, int completionTokens, int totalTokens,
                                          String requestId, boolean usageKnown, boolean sentAnyDelta) {
        try {
            logUsageAndFinalizeReservedQuota(userId, reservedTokens, channelId, model,
                    promptTokens, completionTokens, totalTokens, requestId, usageKnown);
        } catch (RuntimeException e) {
            if (sentAnyDelta && usageKnown) {
                try {
                    protectPartialStreamReservation(userId, reservedTokens, totalTokens);
                } catch (RuntimeException correctionFailure) {
                    log.warn("[v1] protect partial stream reservation failed: userId={}, requestId={}, error={}",
                            userId, requestId, correctionFailure.toString());
                    e.addSuppressed(correctionFailure);
                }
            }
            throw e;
        }
    }

    void protectReservedQuotaOnStreamFailure(UUID userId, long reservedTokens,
                                             int promptTokens, int completionTokens, int totalTokens,
                                             boolean sentAnyDelta) {
        if (!sentAnyDelta || reservedTokens <= 0) {
            return;
        }
        // Safety-first: once partial body was emitted, an interrupted stream keeps the reservation.
    }

    void protectPartialStreamReservation(UUID userId, long reservedTokens, int totalTokens) {
        if (reservedTokens <= 0) {
            return;
        }

        long settledTotal = Math.max(0L, totalTokens);
        long refund = reservedTokens - Math.min(reservedTokens, settledTotal);
        if (refund <= 0) {
            return;
        }

        releaseReservedQuota(userId, refund);
    }

    private static void applyStreamingQuotaLimit(Map<String, Object> requestBody, long reservedTokens) {
        if (requestBody == null || reservedTokens <= 0) {
            return;
        }
        int maxAllowed = reservedTokens > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) reservedTokens;
        Integer maxCompletionTokens = readPositiveInt(requestBody.get("max_completion_tokens"));
        if (maxCompletionTokens != null) {
            requestBody.put("max_completion_tokens", Math.min(maxCompletionTokens, maxAllowed));
            return;
        }
        Integer maxTokens = readPositiveInt(requestBody.get("max_tokens"));
        if (maxTokens != null) {
            requestBody.put("max_tokens", Math.min(maxTokens, maxAllowed));
            return;
        }
        requestBody.put("max_tokens", maxAllowed);
    }

    private static Integer readPositiveInt(Object value) {
        if (value instanceof Number number) {
            int parsed = number.intValue();
            return parsed > 0 ? parsed : null;
        }
        if (value instanceof String text) {
            try {
                int parsed = Integer.parseInt(text.trim());
                return parsed > 0 ? parsed : null;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}

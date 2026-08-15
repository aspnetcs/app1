package com.webchat.platformapi.ai.gateway;

import com.webchat.platformapi.ai.usage.AiUsageService;
import com.webchat.platformapi.user.UserEntity;
import com.webchat.platformapi.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GatewayQuotaServiceTest {

    @Test
    void reserveStreamingQuotaClampsMaxTokensToRemainingQuota() {
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();
        UserRepository userRepository = mock(UserRepository.class);
        AiUsageService usageService = mock(AiUsageService.class);
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setTokenQuota(50);
        user.setTokenUsed(5);

        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(user));
        when(userRepository.incrementTokenUsedIfWithinQuota(userId, 45)).thenReturn(1);

        GatewayQuotaService quotaService = new GatewayQuotaService(
                transactionManager,
                usageService,
                userRepository,
                null
        );
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("max_tokens", 500);

        long reserved = quotaService.reserveStreamingQuota(userId, requestBody);

        assertEquals(45L, reserved);
        assertEquals(45, requestBody.get("max_tokens"));
        assertEquals(1, transactionManager.beginCount);
        assertEquals(1, transactionManager.commitCount);
        assertEquals(0, transactionManager.rollbackCount);
    }

    static final class RecordingTransactionManager implements PlatformTransactionManager {
        int beginCount;
        int commitCount;
        int rollbackCount;

        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) throws TransactionException {
            beginCount++;
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) throws TransactionException {
            commitCount++;
        }

        @Override
        public void rollback(TransactionStatus status) throws TransactionException {
            rollbackCount++;
        }
    }
}

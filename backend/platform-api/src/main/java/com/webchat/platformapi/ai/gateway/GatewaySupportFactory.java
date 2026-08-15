package com.webchat.platformapi.ai.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.webchat.platformapi.ai.adapter.AdapterFactory;
import com.webchat.platformapi.ai.channel.AiChannelRepository;
import com.webchat.platformapi.ai.channel.ChannelMonitor;
import com.webchat.platformapi.ai.channel.ChannelRouter;
import com.webchat.platformapi.ai.security.AiCryptoService;
import com.webchat.platformapi.ai.security.SsrfGuard;
import com.webchat.platformapi.ai.usage.AiUsageService;
import com.webchat.platformapi.auth.role.RolePolicyService;
import com.webchat.platformapi.credits.CreditsRuntimeService;
import com.webchat.platformapi.user.UserRepository;
import org.springframework.transaction.PlatformTransactionManager;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;

final class GatewaySupportFactory {

    private GatewaySupportFactory() {
    }

    static Assembly assemble(
            ObjectMapper objectMapper,
            AdapterFactory adapterFactory,
            ChannelRouter channelRouter,
            AiCryptoService cryptoService,
            SsrfGuard ssrfGuard,
            ChannelMonitor channelMonitor,
            AiChannelRepository channelRepo,
            UserRepository userRepo,
            AiUsageService usageService,
            RolePolicyService rolePolicyService,
            PlatformTransactionManager transactionManager,
            HttpClient httpClient,
            ExecutorService executor,
            CreditsRuntimeService creditsRuntimeService
    ) {
        GatewayModelsFacade gatewayModelsFacade = new GatewayModelsFacade(channelRouter, userRepo, rolePolicyService);
        GatewayQuotaService gatewayQuotaService = new GatewayQuotaService(
                transactionManager,
                usageService,
                userRepo,
                creditsRuntimeService
        );
        GatewayProxyService gatewayProxyService = new GatewayProxyService(
                objectMapper,
                adapterFactory,
                channelRouter,
                cryptoService,
                ssrfGuard,
                channelMonitor,
                httpClient,
                gatewayQuotaService,
                creditsRuntimeService
        );
        return new Assembly(gatewayModelsFacade, gatewayQuotaService, gatewayProxyService, executor);
    }

    static HttpClient createHttpClient() {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    static ExecutorService createExecutor() {
        int poolSize = Math.max(4, Runtime.getRuntime().availableProcessors() * 4);
        return new ThreadPoolExecutor(
                poolSize,
                poolSize,
                60L,
                java.util.concurrent.TimeUnit.SECONDS,
                new java.util.concurrent.LinkedBlockingQueue<>(256),
                runnable -> {
                    Thread thread = new Thread(runnable, "gateway-sse-worker");
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    record Assembly(
            GatewayModelsFacade gatewayModelsFacade,
            GatewayQuotaService gatewayQuotaService,
            GatewayProxyService gatewayProxyService,
            ExecutorService executor
    ) {
    }
}

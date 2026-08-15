package com.webchat.platformapi.ws;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WsConfig implements WebSocketConfigurer {

    private final WsHandler wsHandler;
    private final String[] allowedOriginPatterns;

    public WsConfig(
            WsHandler wsHandler,
            @Value("${cors.ws-allowed-origin-patterns:http://localhost:*,http://127.0.0.1:*,null}") String patterns
    ) {
        this.wsHandler = wsHandler;
        this.allowedOriginPatterns = patterns == null ? new String[0] : patterns.split("\\s*,\\s*");
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(wsHandler, "/ws").setAllowedOriginPatterns(allowedOriginPatterns);
    }
}


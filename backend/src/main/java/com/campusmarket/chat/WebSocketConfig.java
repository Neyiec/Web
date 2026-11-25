package com.campusmarket.chat;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    private final MarketSocket socket;

    public WebSocketConfig(MarketSocket socket) {
        this.socket = socket;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(socket, "/ws");
    }
}

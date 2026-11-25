package com.campusmarket.chat;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.campusmarket.security.LoginSessions;
import com.campusmarket.security.MarketPrincipal;

@Component
public class MarketSocket extends TextWebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger(MarketSocket.class);
    private final LoginSessions sessions;
    private final ConcurrentHashMap<Long,Set<Connection>> clients = new ConcurrentHashMap<>();

    public MarketSocket(LoginSessions sessions) {
        this.sessions = sessions;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession socket) throws IOException {
        var account = identity(socket);
        if (account == null || !sessions.mayReceive(account)) {
            socket.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        var connection = new Connection(account, new ConcurrentWebSocketSessionDecorator(socket, 5000, 64 * 1024));
        clients.compute(account.id(), (id, existing) -> {
            var set = existing == null ? ConcurrentHashMap.<Connection>newKeySet() : existing;
            set.add(connection);
            return set;
        });
    }

    @Override
    public void afterConnectionClosed(WebSocketSession socket, CloseStatus status) {
        var account = identity(socket);
        if (account != null) {
            clients.computeIfPresent(account.id(), (id, set) -> {
                set.removeIf(client -> client.socket().getId().equals(socket.getId()));
                return set.isEmpty() ? null : set;
            });
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession socket, TextMessage message) throws IOException {
        socket.close(CloseStatus.POLICY_VIOLATION);
    }

    @TransactionalEventListener
    public void chatChanged(ChatController.ChatChanged event) {
        String body = "{\"type\":\"chat\",\"conversationId\":" + event.conversationId() + "}";
        notifyUser(event.buyerId(), body);
        notifyUser(event.sellerId(), body);
    }

    public void auctionSettled(long seller, Long buyer, long auctionId, Long reservationId, String outcome) {
        String body = "{\"type\":\"auction-settled\",\"auctionId\":" + auctionId
                + ",\"reservationId\":" + reservationId + ",\"outcome\":\"" + outcome + "\"}";
        notifyUser(seller, body);
        if (buyer != null) {
            notifyUser(buyer, body);
        }
    }

    private void notifyUser(long accountId, String message) {
        for (Connection connection : clients.getOrDefault(accountId, Set.of())) {
            var socket = connection.socket();
            try {
                if (!sessions.mayReceive(connection.account())) {
                    socket.close(CloseStatus.POLICY_VIOLATION);
                } else if (socket.isOpen()) {
                    socket.sendMessage(new TextMessage(message));
                }
            } catch (IOException | DataAccessException | IllegalStateException failure) {
                log.debug("WebSocket notification failed for {}", socket.getId(), failure);
            }
        }
    }

    private MarketPrincipal identity(WebSocketSession socket) {
        return socket.getPrincipal() instanceof Authentication auth && auth.getPrincipal() instanceof MarketPrincipal principal
                ? principal : null;
    }

    private record Connection(MarketPrincipal account, WebSocketSession socket) {}
}

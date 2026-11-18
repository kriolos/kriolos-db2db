package com.zettais.db2db.web.ws;

import io.quarkus.websockets.next.OnClose;
import io.quarkus.websockets.next.OnOpen;
import io.quarkus.websockets.next.OnTextMessage;
import io.quarkus.websockets.next.WebSocket;
import io.quarkus.websockets.next.WebSocketConnection;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

@ApplicationScoped
@WebSocket(path = "/ws/migration")
public class MigrationWebSocket {

    private static final Logger log = LoggerFactory.getLogger(MigrationWebSocket.class);

    private static final Set<WebSocketConnection> ACTIVE_CONNECTIONS = new CopyOnWriteArraySet<>();

    @Inject
    WebSocketConnection connection;

    @OnOpen
    public void onOpen() {
        ACTIVE_CONNECTIONS.add(connection);
        log.info("WebSocket client connected: {}. Total clients: {}", connection.id(), ACTIVE_CONNECTIONS.size());
    }

    @OnClose
    public void onClose() {
        ACTIVE_CONNECTIONS.remove(connection);
        log.info("WebSocket client disconnected: {}. Remaining clients: {}", connection.id(), ACTIVE_CONNECTIONS.size());
    }

    @OnTextMessage
    public String onMessage(String message) {
        log.debug("Received WebSocket message: {}", message);
        return "{\"status\": \"ACK\", \"received\": \"" + message + "\"}";
    }

    public static void broadcast(String message) {
        for (WebSocketConnection conn : ACTIVE_CONNECTIONS) {
            try {
                conn.sendText(message).subscribe().with(
                        item -> {},
                        failure -> log.warn("Failed sending WebSocket message to {}: {}", conn.id(), failure.getMessage())
                );
            } catch (Exception ex) {
                log.warn("Error broadcasting to connection {}: {}", conn.id(), ex.getMessage());
            }
        }
    }
}

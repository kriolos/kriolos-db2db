package org.db2db.web.ws;

import io.quarkus.websockets.next.OnClose;
import io.quarkus.websockets.next.OnOpen;
import io.quarkus.websockets.next.OnTextMessage;
import io.quarkus.websockets.next.WebSocket;
import io.quarkus.websockets.next.WebSocketConnection;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@WebSocket(path = "/ws/migration")
public class MigrationWebSocket {

    private static final Logger log = LoggerFactory.getLogger(MigrationWebSocket.class);

    @Inject
    WebSocketConnection connection;

    @OnOpen
    public void onOpen() {
        log.info("WebSocket client connected: {}", connection.id());
    }

    @OnClose
    public void onClose() {
        log.info("WebSocket client disconnected: {}", connection.id());
    }

    @OnTextMessage
    public String onMessage(String message) {
        log.info("Received message: {}", message);
        return "{\"status\": \"ACK\", \"received\": \"" + message + "\"}";
    }
}

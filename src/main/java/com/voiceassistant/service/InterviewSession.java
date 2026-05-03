package com.voiceassistant.service;
import com.voiceassistant.dto.InterviewSettings;
import lombok.Getter;
import lombok.Setter;
import org.java_websocket.client.WebSocketClient;
import java.util.concurrent.atomic.AtomicBoolean;
/**
 * Holds all mutable state for a single interview session.
 * One instance per active interview — no shared state between users.
 */
@Getter
@Setter
public class InterviewSession {
    private final String sessionId;
    private WebSocketClient realtimeClient;
    private InterviewSettings settings;
    private String currentAssistantItemId;
    private long assistantStartedAt;
    private final AtomicBoolean isAssistantResponding = new AtomicBoolean(false);
    private final AtomicBoolean assistantSentViaBuffer = new AtomicBoolean(false);
    private final AtomicBoolean userSentViaTranscription = new AtomicBoolean(false);
    private final StringBuffer audioTranscriptBuffer = new StringBuffer();
    private final StringBuffer textDeltaBuffer = new StringBuffer();
    public InterviewSession(String sessionId) {
        this.sessionId = sessionId;
    }
    /**
     * Reset flags when user starts speaking (new turn).
     */
    public void resetForNewTurn() {
        isAssistantResponding.set(false);
        assistantSentViaBuffer.set(false);
        userSentViaTranscription.set(false);
        audioTranscriptBuffer.setLength(0);
        textDeltaBuffer.setLength(0);
        currentAssistantItemId = null;
    }
    /**
     * Clear all buffers after response is done.
     */
    public void clearBuffers() {
        textDeltaBuffer.setLength(0);
        audioTranscriptBuffer.setLength(0);
    }
    /**
     * Close the WebSocket client if open.
     */
    public void close() {
        if (realtimeClient != null && realtimeClient.isOpen()) {
            try {
                realtimeClient.close();
            } catch (Exception e) {
                // ignore
            }
        }
    }
    public boolean isConnected() {
        return realtimeClient != null && realtimeClient.isOpen();
    }
}
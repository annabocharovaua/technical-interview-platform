package com.voiceassistant.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.voiceassistant.dto.InterviewSettings;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class OpenAIRealtimeService {

    private static final String REALTIME_API_URL =
            "wss://api.openai.com/v1/realtime?model=gpt-4o-realtime-preview";

    private static final String TOPIC_STATUS       = "/topic/realtime-status";
    private static final String TOPIC_SPEECH       = "/topic/speech-status";
    private static final String TOPIC_CONVERSATION = "/topic/conversation";
    private static final String TOPIC_AUDIO        = "/topic/audio-stream";
    private static final String TOPIC_TRANSCRIPT   = "/topic/transcript-stream";
    private static final String TOPIC_ERROR        = "/topic/error";

    private String topic(String base, String sessionId) {
        return base + "/" + sessionId;
    }

    @Value("${openai.api-key}")
    private String apiKey;

    private final SimpMessagingTemplate messagingTemplate;
    private final Gson gson = new Gson();

    private final ConcurrentHashMap<String, InterviewSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, Runnable> sessionCloseCallbacks = new ConcurrentHashMap<>();

    public OpenAIRealtimeService(SimpMessagingTemplate messagingTemplate,
                                 WeakQuestionService weakQuestionService) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Creates a new interview session and connects to OpenAI Realtime API.
     * Returns the sessionId for subsequent calls.
     */
    public String connect(InterviewSettings settings, Runnable onClose) {
        String sessionId = UUID.randomUUID().toString();
        if (onClose != null) {
            sessionCloseCallbacks.put(sessionId, onClose);
        }
        InterviewSession session = new InterviewSession(sessionId);
        session.setSettings(settings);

        try {
            Map<String, String> headers = new HashMap<>();
            headers.put("Authorization", "Bearer " + apiKey);
            headers.put("OpenAI-Beta", "realtime=v1");

            WebSocketClient client = new WebSocketClient(new URI(REALTIME_API_URL), headers) {

                @Override
                public void onOpen(ServerHandshake handshake) {
                    log.info("[{}] OpenAI Realtime API connected", sessionId);
                    sendSessionConfig(session);
                    messagingTemplate.convertAndSend(topic(TOPIC_STATUS, session.getSessionId()), "connected");
                }

                @Override
                public void onMessage(String message) {
                    handleIncomingMessage(session, message);
                }

                @Override
                public void onMessage(ByteBuffer bytes) {
                    log.debug("[{}] Received binary: {} bytes", sessionId, bytes.remaining());
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    log.warn("[{}] Disconnected: {} - {}", sessionId, code, reason);
                    messagingTemplate.convertAndSend(topic(TOPIC_STATUS, sessionId), "disconnected");
                    sessions.remove(sessionId);
                    Runnable callback = sessionCloseCallbacks.remove(sessionId);
                    if (callback != null) {
                        callback.run();
                    }
                }

                @Override
                public void onError(Exception ex) {
                    log.error("[{}] Error: {}", sessionId, ex.getMessage(), ex);
                    messagingTemplate.convertAndSend(TOPIC_ERROR,
                            "Realtime API error: " + ex.getMessage());
                }
            };

            session.setRealtimeClient(client);
            sessions.put(sessionId, session);
            client.connect();

            log.info("[{}] Session created for {} / {}", sessionId,
                    settings != null ? settings.getProgrammingLanguage() : "default",
                    settings != null ? settings.getPosition() : "default");

            return sessionId;

        } catch (Exception e) {
            log.error("[{}] Failed to connect: {}", sessionId, e.getMessage(), e);
            sessions.remove(sessionId);
            throw new RuntimeException("Failed to connect to OpenAI Realtime API", e);
        }
    }

    private void sendSessionConfig(InterviewSession session) {
        JsonObject sessionUpdate = new JsonObject();
        sessionUpdate.addProperty("type", "session.update");

        JsonObject sessionObj = new JsonObject();

        JsonArray modalities = new JsonArray();
        modalities.add("text");
        modalities.add("audio");
        sessionObj.add("modalities", modalities);

        sessionObj.addProperty("instructions", buildInstructions(session));
        sessionObj.addProperty("voice", "alloy");
        sessionObj.addProperty("input_audio_format", "pcm16");
        sessionObj.addProperty("output_audio_format", "pcm16");

        JsonObject transcription = new JsonObject();
        transcription.addProperty("model", "gpt-4o-transcribe");
        transcription.addProperty("language", getWhisperLanguageCode(session.getSettings()));
        sessionObj.add("input_audio_transcription", transcription);

        JsonObject turnDetection = new JsonObject();
        turnDetection.addProperty("type", "server_vad");
        turnDetection.addProperty("threshold", 0.3);        // was 0.4
        turnDetection.addProperty("prefix_padding_ms", 400); // was 300 — captures slightly more start of speech
        turnDetection.addProperty("silence_duration_ms", 600); // was 700 — faster response
        sessionObj.add("turn_detection", turnDetection);

        sessionObj.add("tools", new JsonArray());
        sessionObj.addProperty("tool_choice", "auto");
        sessionObj.addProperty("temperature", 0.8);
        sessionObj.addProperty("max_response_output_tokens", "inf");

        //String instructions = buildInstructions(session);
        //log.info("═══════════════════════════════════════════════════════════");
        //log.info("[{}] FINAL PROMPT being sent to OpenAI Realtime API:", session.getSessionId());
        //log.info("═══════════════════════════════════════════════════════════");
        //log.info("\n{}", instructions);
        //log.info("═══════════════════════════════════════════════════════════");
        //log.info("[{}] Prompt length: {} characters", session.getSessionId(), instructions.length());
        // ═══════════════════════════

        sessionUpdate.add("session", sessionObj);
        session.getRealtimeClient().send(gson.toJson(sessionUpdate));
    }

    private String getWhisperLanguageCode(InterviewSettings settings) {
        if (settings == null) return "en";
        String lang = settings.getInterviewLanguage();
        if (lang == null) return "en";
        return switch (lang) {
            case "Ukrainian" -> "uk";
            case "German"    -> "de";
            case "Polish"    -> "pl";
            case "English"   -> "en";
            default          -> "en";
        };
    }

    private String buildInstructions(InterviewSession session) {
        InterviewSettings settings = session.getSettings();
        if (settings == null) {
            return "You are a friendly voice assistant. Answer briefly and naturally.";
        }
        return settings.generatePrompt();
    }

    private void handleIncomingMessage(InterviewSession session, String message) {
        try {
            JsonObject event = gson.fromJson(message, JsonObject.class);
            String type = event.get("type").getAsString();

            switch (type) {
                case "session.created", "session.updated" -> {}

                case "conversation.item.created" -> handleConversationItem(session, event);

                case "response.audio.delta" -> {
                    session.getIsAssistantResponding().set(true);


                    if (session.getCurrentAssistantItemId() == null && event.has("item_id")) {
                        session.setCurrentAssistantItemId(event.get("item_id").getAsString());
                        session.setAssistantStartedAt(System.currentTimeMillis());
                        session.getAssistantSentViaBuffer().set(false);
                    }

                    handleAudioDelta(session, event);
                }

                case "response.audio.done" -> {
                    session.getIsAssistantResponding().set(false);
                    session.setCurrentAssistantItemId(null);
                    messagingTemplate.convertAndSend(topic(TOPIC_SPEECH, session.getSessionId()), "assistant_done");
                }

                case "response.audio_transcript.delta" -> handleAudioTranscriptDelta(session, event);
                case "response.audio_transcript.done" -> handleAudioTranscriptDone(session, event);

                case "response.text.delta" -> handleTextDelta(session, event);
                case "response.text.done" -> {}

                case "response.done" -> handleResponseDone(session, event);

                case "input_audio_buffer.speech_started" -> {
                    session.resetForNewTurn();
                    cancelResponse(session);
                    messagingTemplate.convertAndSend(topic(TOPIC_SPEECH, session.getSessionId()), "user_speaking");
                }
                case "input_audio_buffer.speech_stopped" ->
                        messagingTemplate.convertAndSend(topic(TOPIC_SPEECH, session.getSessionId()), "user_stopped");
                case "input_audio_buffer.committed" -> {}

                case "conversation.item.input_audio_transcription.completed" ->
                        handleTranscriptionCompleted(session, event);

                case "error" -> handleError(session, event);

                default -> log.debug("[{}] Unhandled: {}", session.getSessionId(), type);
            }
        } catch (Exception e) {
            log.error("[{}] Error processing message: {}", session.getSessionId(), e.getMessage(), e);
        }
    }

    private void handleAudioTranscriptDelta(InterviewSession session, JsonObject event) {
        if (!event.has("delta") || event.get("delta").isJsonNull()) return;
        String delta = event.get("delta").getAsString();
        if (delta != null && !delta.isEmpty()) {
            session.getAudioTranscriptBuffer().append(delta);
        }
    }

    private void handleAudioTranscriptDone(InterviewSession session, JsonObject event) {
        String transcript = null;

        if (event.has("transcript") && !event.get("transcript").isJsonNull()) {
            transcript = event.get("transcript").getAsString();
        }

        if ((transcript == null || transcript.isBlank()) && session.getAudioTranscriptBuffer().length() > 0) {
            transcript = session.getAudioTranscriptBuffer().toString().trim();
        }

        session.getAudioTranscriptBuffer().setLength(0);

        if (transcript == null || transcript.isBlank()) return;
        if (session.getAssistantSentViaBuffer().get()) return;

        sendConversationMessage(session,"assistant", transcript);
        session.getAssistantSentViaBuffer().set(true);
    }

    private void handleTextDelta(InterviewSession session, JsonObject event) {
        if (!event.has("delta") || event.get("delta").isJsonNull()) return;
        String delta = event.get("delta").getAsString();
        if (delta != null && !delta.isEmpty()) {
            session.getTextDeltaBuffer().append(delta);
            messagingTemplate.convertAndSend(topic(TOPIC_TRANSCRIPT, session.getSessionId()), delta);
        }
    }

    private void handleResponseDone(InterviewSession session, JsonObject event) {
        if (!session.getAssistantSentViaBuffer().get() && session.getTextDeltaBuffer().length() > 0) {
            String text = session.getTextDeltaBuffer().toString().trim();
            if (!text.isEmpty()) {
                sendConversationMessage(session,"assistant", text);
                session.getAssistantSentViaBuffer().set(true);
            }
        }

        session.clearBuffers();
        messagingTemplate.convertAndSend(topic(TOPIC_SPEECH, session.getSessionId()), "assistant_done");
    }

    private void handleConversationItem(InterviewSession session, JsonObject event) {
        try {
            JsonObject item = event.getAsJsonObject("item");
            if (!item.has("role") || item.get("role").isJsonNull()) return;
            String role = item.get("role").getAsString();

            if (!item.has("content") || item.get("content").isJsonNull()) return;
            JsonArray contentArray = item.getAsJsonArray("content");
            if (contentArray.isEmpty()) return;

            for (int i = 0; i < contentArray.size(); i++) {
                JsonObject content = contentArray.get(i).getAsJsonObject();
                if (!content.has("type") || content.get("type").isJsonNull()) continue;
                String contentType = content.get("type").getAsString();

                if ("assistant".equalsIgnoreCase(role)) {
                    if (session.getAssistantSentViaBuffer().get()) continue;

                    String text = null;
                    if ("text".equals(contentType) && content.has("text") && !content.get("text").isJsonNull()) {
                        text = content.get("text").getAsString();
                    } else if ("audio".equals(contentType) && content.has("transcript") && !content.get("transcript").isJsonNull()) {
                        text = content.get("transcript").getAsString();
                    }

                    if (text != null && !text.isBlank()) {
                        sendConversationMessage(session,"assistant", text);
                        session.getAssistantSentViaBuffer().set(true);
                    }
                } else if ("user".equalsIgnoreCase(role)) {
                    if ("input_text".equals(contentType) && content.has("text") && !content.get("text").isJsonNull()) {
                        sendConversationMessage(session,"user", content.get("text").getAsString());
                    } else if ("input_audio".equals(contentType) && !session.getUserSentViaTranscription().get()) {
                        if (content.has("transcript") && !content.get("transcript").isJsonNull()) {
                            String t = content.get("transcript").getAsString();
                            if (!t.isBlank()) {
                                sendConversationMessage(session,"user", t);
                                session.getUserSentViaTranscription().set(true);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("[{}] Error handling conversation item: {}", session.getSessionId(), e.getMessage(), e);
        }
    }

    private void handleTranscriptionCompleted(InterviewSession session, JsonObject event) {
        if (!event.has("transcript") || event.get("transcript").isJsonNull()) return;
        String transcript = event.get("transcript").getAsString();
        if (transcript.isBlank()) return;
        sendConversationMessage(session,"user", transcript);
        session.getUserSentViaTranscription().set(true);
    }

    private void handleAudioDelta(InterviewSession session, JsonObject event) {
        if (!event.has("delta") || event.get("delta").isJsonNull()) return;
        String audioDelta = event.get("delta").getAsString();
        if (audioDelta != null && !audioDelta.isEmpty()) {
            messagingTemplate.convertAndSend(topic(TOPIC_AUDIO, session.getSessionId()), audioDelta);
        }
    }

    private void handleError(InterviewSession session, JsonObject event) {
        if (!event.has("error") || event.get("error").isJsonNull()) return;
        JsonObject error = event.getAsJsonObject("error");
        String errorMessage = error.has("message") ? error.get("message").getAsString() : "Unknown";

        if (errorMessage.contains("no active response") || errorMessage.contains("Cancellation failed")) {
            return;
        }

        String errorType = error.has("type") ? error.get("type").getAsString() : "unknown";
        log.error("OpenAI error [{}]: {}", errorType, errorMessage);
        messagingTemplate.convertAndSend(topic(TOPIC_ERROR, session.getSessionId()), errorType + ": " + errorMessage);
    }

    private void sendConversationMessage(InterviewSession session, String role, String content) {
        if (content == null || content.isBlank()) return;
        Map<String, String> data = new HashMap<>();
        data.put("role", role);
        data.put("content", content);
        messagingTemplate.convertAndSend(topic(TOPIC_CONVERSATION, session.getSessionId()), gson.toJson(data));
    }

    private InterviewSession requireSession(String sessionId) {
        InterviewSession session = sessions.get(sessionId);
        if (session == null) {
            throw new IllegalStateException("No active session: " + sessionId);
        }
        return session;
    }

    public void cancelResponse(String sessionId) {
        InterviewSession session = sessions.get(sessionId);
        if (session != null) cancelResponse(session);
    }

    private void cancelResponse(InterviewSession session) {
        if (!session.isConnected()) return;
        messagingTemplate.convertAndSend(topic(TOPIC_SPEECH, session.getSessionId()), "interrupt");
        if (!session.getIsAssistantResponding().get()) return;

        try {

            JsonObject cancel = new JsonObject();
            cancel.addProperty("type", "response.cancel");
            session.getRealtimeClient().send(gson.toJson(cancel));

            if (session.getCurrentAssistantItemId() != null) {
                JsonObject truncate = new JsonObject();
                truncate.addProperty("type", "conversation.item.truncate");
                truncate.addProperty("item_id", session.getCurrentAssistantItemId());
                truncate.addProperty("content_index", 0);

                long playedMs = System.currentTimeMillis() - session.getAssistantStartedAt();
                truncate.addProperty("audio_end_ms", Math.max(0, playedMs));

                session.getRealtimeClient().send(gson.toJson(truncate));
                log.info("[{}] Truncated at {}ms", session.getSessionId(), playedMs);
            }

            session.getIsAssistantResponding().set(false);
        } catch (Exception e) {
            log.error("[{}] Error cancelling: {}", session.getSessionId(), e.getMessage());
        }
    }

    public void sendAudioBase64(String sessionId, String base64Audio) {
        InterviewSession session = requireSession(sessionId);
        if (!session.isConnected()) return;

        JsonObject msg = new JsonObject();
        msg.addProperty("type", "input_audio_buffer.append");
        msg.addProperty("audio", base64Audio);
        session.getRealtimeClient().send(gson.toJson(msg));
    }

    public void commitAudioBuffer(String sessionId) {
        InterviewSession session = requireSession(sessionId);
        if (!session.isConnected()) return;


        JsonObject commit = new JsonObject();
        commit.addProperty("type", "input_audio_buffer.commit");
        session.getRealtimeClient().send(gson.toJson(commit));

        JsonObject responseCreate = new JsonObject();
        responseCreate.addProperty("type", "response.create");
        session.getRealtimeClient().send(gson.toJson(responseCreate));

        messagingTemplate.convertAndSend(topic(TOPIC_SPEECH, session.getSessionId()), "user_stopped");
        log.info("[{}] Buffer committed + response.create sent", sessionId);
    }

    public void requestFinalFeedback(String sessionId) {
        InterviewSession session = requireSession(sessionId);
        if (!session.isConnected()) return;

        cancelResponse(session);

        JsonObject conversationItem = new JsonObject();
        conversationItem.addProperty("type", "conversation.item.create");

        JsonObject item = new JsonObject();
        item.addProperty("type", "message");
        item.addProperty("role", "user");

        JsonArray content = new JsonArray();
        JsonObject textContent = new JsonObject();
        textContent.addProperty("type", "input_text");
        textContent.addProperty("text",
                "Interview time is ending. Please summarize our interview. "
                        + "Give a brief assessment of my answers, highlight strengths "
                        + "and areas for improvement. "
                        + "Tell me if I am ready for a real interview for this position.");
        content.add(textContent);
        item.add("content", content);
        conversationItem.add("item", item);
        session.getRealtimeClient().send(gson.toJson(conversationItem));

        JsonObject responseCreate = new JsonObject();
        responseCreate.addProperty("type", "response.create");
        session.getRealtimeClient().send(gson.toJson(responseCreate));
    }

    public void disconnect(String sessionId) {
        InterviewSession session = sessions.remove(sessionId);
        if (session != null) {
            session.close();
            log.info("[{}] Session disconnected", sessionId);
        }
    }

    @PreDestroy
    public void disconnectAll() {
        sessions.values().forEach(InterviewSession::close);
        sessions.clear();
        log.info("All sessions disconnected");
    }

    public boolean isSessionConnected(String sessionId) {
        InterviewSession session = sessions.get(sessionId);
        return session != null && session.isConnected();
    }
}
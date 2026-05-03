package com.voiceassistant.controller;

import com.voiceassistant.dto.InterviewSettings;
import com.voiceassistant.dto.WeakQuestionDto;
import com.voiceassistant.entity.User;
import com.voiceassistant.service.InterviewSession;
import com.voiceassistant.service.OpenAIRealtimeService;
import com.voiceassistant.service.WeakQuestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * REST controller for real-time voice interview management.
 * Handles WebSocket connections, interview sessions, and AI conversation streaming.
 * Manages live coding interviews with voice interaction capabilities.
 *
 * @see OpenAIRealtimeService real-time voice streaming service
 */
@Slf4j
@RestController
@RequestMapping("/api/realtime")
@RequiredArgsConstructor
public class RealtimeController {

  private final OpenAIRealtimeService realtimeService;
  private final WeakQuestionService weakQuestionService;

  /**
   * Maintains mapping of userId to active interview sessionId.
   * Ensures each user has isolated session without cross-contamination.
   */
  private final ConcurrentHashMap<Long, String> userSessions = new ConcurrentHashMap<>();

  /**
   * Retrieves weak questions identified from previous interviews for this user.
   * These are questions where user performed poorly or needs improvement.
   *
   * @param user authenticated user requesting their weak questions
   * @return ResponseEntity with list of weak questions and count,
   *         or HTTP 401 if not authenticated, HTTP 500 on error
   */
  @GetMapping("/weak-questions")
  public ResponseEntity<Map<String, Object>> getWeakQuestionsForInterview(
          @RequestParam(required = false) String programmingLanguage,
          @AuthenticationPrincipal User user) {
    try {
      if (user == null) {
        return ResponseEntity.status(401).body(Map.of(
                "error", true,
                "message", "User not authenticated",
                "questions", List.of()
        ));
      }

      log.info("Getting weak questions for interview: userId={}, programmingLanguage='{}'",
              user.getId(), programmingLanguage);

      List<WeakQuestionDto> weakQuestionsDto = weakQuestionService.getWeakQuestions(
              user.getId(), programmingLanguage);

      List<Map<String, Object>> weakQuestions = weakQuestionsDto.stream()
              .map(dto -> {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("id", dto.getId());
                map.put("question", dto.getQuestion());
                map.put("topic", dto.getTopic());
                map.put("accuracy", dto.getAccuracy());
                map.put("position", dto.getPosition());
                map.put("attemptCount", dto.getAttemptCount());
                map.put("createdAt", dto.getCreatedAt());
                return map;
              })
              .collect(Collectors.toList());

      return ResponseEntity.ok(Map.of(
              "error", false,
              "questions", weakQuestions,
              "count", weakQuestions.size()
      ));
    } catch (Exception e) {
      log.error("Error loading weak questions: {}", e.getMessage(), e);
      return ResponseEntity.status(500).body(Map.of(
              "error", true,
              "message", "Failed to load weak questions",
              "questions", List.of()
      ));
    }
  }

  /**
   * Initiates real-time interview session with OpenAI API.
   * Establishes WebSocket connection for voice streaming and conversation.
   *
   * @param settings interview configuration (language, difficulty, weak question tracking)
   * @param user authenticated user starting interview
   * @return ResponseEntity with session ID and WebSocket URL,
   *         or HTTP 401 if not authenticated, HTTP 500 on error
   */
  @PostMapping("/connect")
  public ResponseEntity<Map<String, String>> connect(
          @RequestBody(required = false) InterviewSettings settings,
          @AuthenticationPrincipal User user) {
    try {
      if (user == null) {
        return ResponseEntity.status(401).body(Map.of(
                "status", "error",
                "message", "Not authenticated"
        ));
      }

      if (settings != null && Boolean.TRUE.equals(settings.getTrackWeakQuestions())) {
        String progLang = settings.getProgrammingLanguage();
        log.info("Loading weak questions for connect: userId={}, programmingLanguage='{}'",
                user.getId(), progLang);

        List<WeakQuestionDto> weakQuestionsDto = weakQuestionService
                .getWeakQuestions(user.getId(), progLang);

        if (weakQuestionsDto != null && !weakQuestionsDto.isEmpty()) {
          settings.setWeakQuestions(weakQuestionsDto.stream()
                  .map(WeakQuestionDto::getQuestion).toList());
          log.info("Loaded {} weak questions for language '{}'",
                  weakQuestionsDto.size(), progLang);
        }
      }

      String oldSessionId = userSessions.get(user.getId());
      if (oldSessionId != null) {
        log.info("Disconnecting previous session {} for user {}", oldSessionId, user.getId());
        try {
          realtimeService.disconnect(oldSessionId);
        } catch (Exception e) {
          log.warn("Error disconnecting old session: {}", e.getMessage());
        }
        userSessions.remove(user.getId());
      }

      final Long userId = user.getId();
      String sessionId = realtimeService.connect(settings, () -> {
        userSessions.remove(userId);
        log.info("Auto-cleaned userSessions for user {} after WS close", userId);
      });

      userSessions.put(user.getId(), sessionId);
      log.info("Session {} mapped to user {}", sessionId, user.getId());

      return ResponseEntity.ok(Map.of(
              "status", "connected",
              "sessionId", sessionId
      ));
    } catch (Exception e) {
      log.error("Failed to connect: {}", e.getMessage(), e);
      return ResponseEntity.status(500).body(Map.of(
              "status", "error",
              "message", e.getMessage()
      ));
    }
  }

  @PostMapping("/audio")
  public ResponseEntity<Void> sendAudio(
          @RequestBody Map<String, String> request,
          @AuthenticationPrincipal User user) {
    try {
      String base64Audio = request.get("audio");
      String sessionId = request.get("sessionId");

      if (base64Audio == null || base64Audio.isEmpty()) return ResponseEntity.badRequest().build();
      if (sessionId == null || sessionId.isEmpty()) return ResponseEntity.badRequest().build();

      if (user != null) {
        String userSession = userSessions.get(user.getId());
        if (userSession == null || !userSession.equals(sessionId)) {
          log.warn("User {} tried to send audio to session {} (owns: {})",
                  user.getId(), sessionId, userSession);
          return ResponseEntity.status(403).build();
        }
      }

      realtimeService.sendAudioBase64(sessionId, base64Audio);
      return ResponseEntity.ok().build();
    } catch (Exception e) {
      log.error("Error sending audio: {}", e.getMessage());
      return ResponseEntity.status(500).build();
    }
  }

  @PostMapping("/cancel")
  public ResponseEntity<Void> cancelResponse(
          @RequestBody(required = false) Map<String, String> request,
          @AuthenticationPrincipal User user) {
    String sessionId = resolveSessionId(request, user);
    if (sessionId != null) {
      realtimeService.cancelResponse(sessionId);
    }
    return ResponseEntity.ok().build();
  }

  @PostMapping("/audio-buffer-commit")
  public ResponseEntity<Map<String, String>> commitAudioBuffer(
          @RequestBody(required = false) Map<String, String> request,
          @AuthenticationPrincipal User user) {
    try {
      String sessionId = resolveSessionId(request, user);
      if (sessionId == null) {
        return ResponseEntity.badRequest().body(Map.of(
                "status", "error",
                "message", "No active session"
        ));
      }
      realtimeService.commitAudioBuffer(sessionId);
      return ResponseEntity.ok(Map.of("status", "success"));
    } catch (Exception e) {
      return ResponseEntity.status(500).body(Map.of(
              "status", "error",
              "message", e.getMessage()
      ));
    }
  }

  @PostMapping("/end-interview")
  public ResponseEntity<Void> endInterview(
          @RequestBody(required = false) Map<String, String> request,
          @AuthenticationPrincipal User user) {
    String sessionId = resolveSessionId(request, user);
    if (sessionId != null) {
      realtimeService.requestFinalFeedback(sessionId);
    }
    return ResponseEntity.ok().build();
  }

  @PostMapping("/disconnect")
  public ResponseEntity<Void> disconnect(
          @RequestBody(required = false) Map<String, String> request,
          @AuthenticationPrincipal User user) {
    String sessionId = resolveSessionId(request, user);
    if (sessionId != null) {
      realtimeService.disconnect(sessionId);
      if (user != null) {
        userSessions.remove(user.getId());
      }
    }
    return ResponseEntity.ok().build();
  }

  /**
   * Resolves sessionId from request body or from user's stored session.
   * Priority: request body sessionId > user's mapped session.
   */
  private String resolveSessionId(Map<String, String> request, User user) {
    if (request != null) {
      String sessionId = request.get("sessionId");
      if (sessionId != null && !sessionId.isEmpty()) {
        return sessionId;
      }
    }
    if (user != null) {
      return userSessions.get(user.getId());
    }
    return null;
  }

  @GetMapping("/session-status")
  public ResponseEntity<Map<String, Object>> sessionStatus(@AuthenticationPrincipal User user) {
    String sessionId = userSessions.get(user.getId());
    boolean connected = sessionId != null && realtimeService.isSessionConnected(sessionId);
    return ResponseEntity.ok(Map.of(
            "sessionId", sessionId != null ? sessionId : "",
            "connected", connected
    ));
  }

  @MessageMapping("/audio")
  public void receiveAudio(@Payload Map<String, String> payload, Principal principal) {
    String sessionId = payload.get("sessionId");
    String base64Audio = payload.get("audio");
    if (sessionId == null || base64Audio == null || base64Audio.isEmpty()) return;
    realtimeService.sendAudioBase64(sessionId, base64Audio);
  }
}
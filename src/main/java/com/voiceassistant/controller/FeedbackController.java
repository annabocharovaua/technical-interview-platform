package com.voiceassistant.controller;
import com.voiceassistant.entity.User;
import com.voiceassistant.dto.FeedbackRequest;
import com.voiceassistant.dto.WeakQuestionDto;
import com.voiceassistant.service.FeedbackService;
import com.voiceassistant.service.WeakQuestionService;
import com.voiceassistant.service.LearningResourceService;
import com.voiceassistant.service.PdfService;
import com.voiceassistant.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
/**
 * REST controller for interview feedback generation and reporting.
 * Generates AI-powered interview feedback, creates PDF reports, and manages learning resources.
 * Provides comprehensive analysis of interview performance and personalized recommendations.
 *
 * @see FeedbackService feedback generation logic
 * @see PdfService PDF report generation
 */
@Slf4j
@RestController
@RequestMapping("/api/feedback")
@RequiredArgsConstructor
public class FeedbackController {
    private final FeedbackService feedbackService;
    private final WeakQuestionService weakQuestionService;
    private final LearningResourceService learningResourceService;
    private final PdfService pdfService;
    private final UserService userService;

    /**
     * Generates comprehensive interview feedback using AI analysis.
     * Analyzes interview transcript, identifies strengths and areas for improvement,
     * and recommends learning resources.
     *
     * @param request containing interview transcript, programming language, position, job description
     * @param user authenticated user requesting feedback
     * @return ResponseEntity with detailed feedback including evaluation, resources, weak questions,
     *         or error response if transcript is empty or processing fails
     */
    @PostMapping("/generate")
    public ResponseEntity<Map<String, Object>> generateFeedback(
            @RequestBody Map<String, Object> request,
            @AuthenticationPrincipal User user) {
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, String>> transcript = (List<Map<String, String>>) request.get("transcript");
            String programmingLanguage = (String) request.get("programmingLanguage");
            String position = (String) request.get("position");
            String interviewLanguage = (String) request.get("interviewLanguage");
            String jobDescription = (String) request.get("jobDescription");
            @SuppressWarnings("unchecked")
            List<String> jobRequirements = (List<String>) request.get("jobRequirements");
            Number priceNum = (Number) request.get("price");
            int price = priceNum != null ? priceNum.intValue() : 0;
            boolean isTestInterview = price == 0;
            if (transcript == null || transcript.isEmpty()) {
                log.warn("Empty transcript received");
                return ResponseEntity.ok(Map.of(
                        "error", true,
                        "message", "No interview transcript provided"
                ));
            }
            FeedbackRequest feedbackRequest = new FeedbackRequest(
                    transcript,
                    programmingLanguage,
                    position,
                    interviewLanguage,
                    isTestInterview,
                    jobDescription,
                    jobRequirements == null ? Collections.emptyList() : jobRequirements
            );
            var feedbackResult = feedbackService.generateFeedback(feedbackRequest);
            if (feedbackResult.isPresent()) {
                Map<String, Object> result = feedbackResult.get();
                if (user != null && result.containsKey("resources")) {
                    try {
                        learningResourceService.saveRecommendedResources(result, position, user);
                    } catch (Exception e) {
                        log.warn("Failed to save resources: {}", e.getMessage());
                    }
                }
                return ResponseEntity.ok(result);
            } else {
                log.error("OpenAI service failed to generate feedback");
                return ResponseEntity.status(500).body(Map.of(
                        "error", true,
                        "message", "Failed to generate feedback: OpenAI service unavailable"
                ));
            }
        } catch (Exception e) {
            log.error("Error generating feedback: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                    "error", true,
                    "message", "Failed to generate feedback: " + e.getMessage()
            ));
        }
    }
    /**
     * Generates a PDF report from the provided feedback.
     *
     * @param request request body containing feedback and settings maps
     * @return PDF file as byte array
     */
    @PostMapping("/pdf")
    public ResponseEntity<byte[]> generatePdfReport(@RequestBody Map<String, Object> request) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> feedback = (Map<String, Object>) request.get("feedback");
            @SuppressWarnings("unchecked")
            Map<String, Object> settings = (Map<String, Object>) request.get("settings");
            @SuppressWarnings("unchecked")
            Map<String, Object> antiCheat = (Map<String, Object>) request.get("antiCheatReport");
            if (feedback == null) {
                return ResponseEntity.badRequest().build();
            }
            if (settings == null) {
                settings = Map.of("programmingLanguage", "Unknown", "position", "Unknown");
            }
            if (antiCheat != null) {
                feedback.put("antiCheatReport", antiCheat);
            }
            byte[] pdfBytes = pdfService.generateInterviewReport(feedback, settings);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment",
                    "interview-report-" + System.currentTimeMillis() + ".pdf");
            return ResponseEntity.ok().headers(headers).body(pdfBytes);
        } catch (Exception e) {
            log.error("Error generating PDF: {}", e.getMessage(), e);
            return ResponseEntity.status(500).build();
        }
    }
    /**
     * Processes interview feedback and saves weak questions (accuracy below 70%).
     * Deletes questions that were answered correctly.
     */
    @PostMapping("/weak-questions")
    public ResponseEntity<Map<String, Object>> saveWeakQuestions(
            @RequestBody Map<String, Object> request,
            @AuthenticationPrincipal User user) {
        try {
            if (user == null) {
                return ResponseEntity.status(401).body(Map.of(
                        "error", true,
                        "message", "User not authenticated"
                ));
            }

            Boolean trackWeakQuestions = (Boolean) request.get("trackWeakQuestions");
            if (trackWeakQuestions == null || !trackWeakQuestions) {
                return ResponseEntity.ok(Map.of("saved", 0, "message", "Tracking disabled"));
            }

            Long userId = user.getId();
            @SuppressWarnings("unchecked")
            Map<String, Object> feedback = (Map<String, Object>) request.get("feedback");

            String programmingLanguage = (String) request.get("programmingLanguage");
            if (programmingLanguage == null || programmingLanguage.isBlank()) {
                programmingLanguage = (String) request.get("language");
            }
            String position = (String) request.get("position");

            log.info("saveWeakQuestions: userId={}, programmingLanguage='{}', position='{}'",
                    userId, programmingLanguage, position);

            if (feedback == null || feedback.isEmpty()) {
                return ResponseEntity.ok(Map.of("saved", 0, "message", "No feedback to process"));
            }

            weakQuestionService.processFeedback(userId, feedback, programmingLanguage, position);

            return ResponseEntity.ok(Map.of(
                    "saved", 1,
                    "message", "Weak questions processed successfully"
            ));
        } catch (Exception e) {
            log.error("Error saving weak questions: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                    "error", true,
                    "message", "Failed to save weak questions: " + e.getMessage()
            ));
        }
    }
    /**
     * Rates a learning resource by the authenticated user.
     *
     * @param request request body containing resourceId, rating, title, url and topic
     * @param user    authenticated user
     * @return rating result or error response
     */
    @PostMapping("/rate-resource")
    public ResponseEntity<Map<String, Object>> rateResource(
            @RequestBody Map<String, Object> request,
            @AuthenticationPrincipal User user) {
        try {
            if (user == null) {
                return ResponseEntity.status(401).body(Map.of(
                        "error", true,
                        "message", "User not authenticated"
                ));
            }
            Number resourceIdNum = (Number) request.get("resourceId");
            Number ratingNum = (Number) request.get("rating");
            if (ratingNum == null) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", true,
                        "message", "Missing rating"
                ));
            }
            Long resourceId = resourceIdNum != null ? resourceIdNum.longValue() : null;
            int rating = ratingNum.intValue();
            String title = (String) request.get("title");
            String url = (String) request.get("url");
            String topic = (String) request.get("topic");
            log.info("User {} rating resource {}: {} stars", user.getId(), resourceId, rating);
            Map<String, Object> result = learningResourceService.rateResource(
                    resourceId, rating, title, url, topic, user
            );
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            log.error("Validation error: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of(
                    "error", true,
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("Error rating resource: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                    "error", true,
                    "message", "Failed to rate resource: " + e.getMessage()
            ));
        }
    }
    /**
     * Returns weak questions for the authenticated user or by userId parameter.
     *
     * @param user        authenticated user
     * @param userIdParam optional user ID query parameter fallback
     * @return list of weak questions with count or error response
     */
    @GetMapping("/weak-questions")
    public ResponseEntity<Map<String, Object>> getWeakQuestions(
            @AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of(
                    "error", true,
                    "message", "User not authenticated"
            ));
        }
        try {
            Long userId = user.getId();
            if (userId == null) {
                return ResponseEntity.status(401).body(Map.of(
                        "error", true,
                        "message", "User not authenticated. Please provide userId parameter."
                ));
            }
            List<WeakQuestionDto> weakQuestions = weakQuestionService.getWeakQuestions(userId);
            List<Map<String, Object>> result = weakQuestions.stream()
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
                    "questions", result,
                    "count", result.size()
            ));
        } catch (Exception e) {
            log.error("Error getting weak questions: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                    "error", true,
                    "message", "Failed to get weak questions: " + e.getMessage()
            ));
        }
    }
    /**
     * Returns recommended learning resources for a given topic filtered by rating above 3.5.
     *
     * @param topic topic name
     * @return list of resources with count or error response
     */
    @GetMapping("/resources/{topic}")
    public ResponseEntity<Map<String, Object>> getResourcesByTopic(@PathVariable String topic) {
        try {
            log.info("Getting resources for topic: {}", topic);
            List<Map<String, Object>> filteredResources = learningResourceService.getResourcesByTopic(topic);
            return ResponseEntity.ok(Map.of(
                    "topic", topic,
                    "resources", filteredResources,
                    "count", filteredResources.size()
            ));
        } catch (IllegalArgumentException e) {
            log.error("Validation error: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of(
                    "error", true,
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("Error getting resources for topic {}: {}", topic, e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                    "error", true,
                    "message", "Failed to get resources: " + e.getMessage()
            ));
        }
    }
    /**
     * Sends interview report via email to the authenticated user
     */
    @PostMapping("/send-report")
    public ResponseEntity<Map<String, Object>> sendReportViaEmail(
            @RequestBody Map<String, Object> request,
            @AuthenticationPrincipal User user) {
        try {
            if (user == null) {
                return ResponseEntity.status(401).body(Map.of(
                        "error", true,
                        "message", "User not authenticated"
                ));
            }
            Long userId = user.getId();
            @SuppressWarnings("unchecked")
            Map<String, Object> feedback = (Map<String, Object>) request.get("feedback");
            @SuppressWarnings("unchecked")
            Map<String, Object> settings = (Map<String, Object>) request.get("settings");
            if (feedback == null) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", true,
                        "message", "Feedback data is required"
                ));
            }
            if (settings == null) {
                settings = Map.of("programmingLanguage", "Unknown", "position", "Unknown");
            }
            byte[] pdfContent = pdfService.generateInterviewReport(feedback, settings);
            boolean emailSent = userService.sendInterviewReportEmail(userId, pdfContent);
            if (emailSent) {
                log.info("Interview report sent via email to user: {}", userId);
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "message", "Report sent successfully to user"
                ));
            } else {
                log.error("Failed to send interview report via email for user: {}", userId);
                return ResponseEntity.status(500).body(Map.of(
                        "error", true,
                        "message", "Failed to send email. Please try again later."
                ));
            }
        } catch (Exception e) {
            log.error("Error sending report via email: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                    "error", true,
                    "message", "Failed to send report: " + e.getMessage()
            ));
        }
    }
}
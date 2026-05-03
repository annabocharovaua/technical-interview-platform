package com.voiceassistant.dto;
import java.util.List;
import java.util.Map;
/**
 * Data Transfer Object for interview feedback request.
 * Contains interview transcript and context for AI-powered analysis.
 */
public record FeedbackRequest(
        List<Map<String, String>> transcript,
        String programmingLanguage,
        String position,
        String interviewLanguage,
        boolean isTestInterview,
        String jobDescription,
        List<String> jobRequirements
) {
}
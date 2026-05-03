package com.voiceassistant.service;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.voiceassistant.dto.FeedbackRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
@Slf4j
@Service
public class FeedbackService {
    private static final String OPENAI_API_URL = "https://api.openai.com/v1/chat/completions";
    private static final String GPT_MODEL      = "gpt-4o";
    private static final double TEMPERATURE    = 0.7;
    @Value("${openai.api-key}")
    private String apiKey;
    private final HttpClient httpClient;
    private final Gson       gson = new Gson();
    public FeedbackService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }
    public Optional<Map<String, Object>> generateFeedback(FeedbackRequest feedbackRequest) {
        try {
            List<Map<String, String>> filteredTranscript = filterSystemMessages(
                    feedbackRequest.transcript());
            String transcriptText = buildTranscriptText(filteredTranscript);
            String prompt = feedbackRequest.isTestInterview()
                    ? buildTestInterviewPrompt(feedbackRequest, transcriptText)
                    : buildFullInterviewPrompt(feedbackRequest, transcriptText);
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("model", GPT_MODEL);
            requestBody.addProperty("temperature", TEMPERATURE);
            JsonArray messages = new JsonArray();
            JsonObject message = new JsonObject();
            message.addProperty("role", "user");
            message.addProperty("content", prompt);
            messages.add(message);
            requestBody.add("messages", messages);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(OPENAI_API_URL))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(requestBody)))
                    .timeout(Duration.ofSeconds(60))
                    .build();
            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString());
            log.info("OpenAI API response status: {}", response.statusCode());
            if (response.statusCode() == 200) {
                return parseOpenAiResponse(response.body());
            }
            log.error("OpenAI API error: {} - {}", response.statusCode(), response.body());
            return Optional.empty();
        } catch (Exception e) {
            log.error("Error generating feedback: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }
    private Optional<Map<String, Object>> parseOpenAiResponse(String responseBody) {
        try {
            JsonObject responseJson = gson.fromJson(responseBody, JsonObject.class);
            String content = responseJson
                    .getAsJsonArray("choices")
                    .get(0).getAsJsonObject()
                    .getAsJsonObject("message")
                    .get("content").getAsString();
            content = content.replaceAll("```json\\s*", "")
                    .replaceAll("```\\s*", "")
                    .trim();
            @SuppressWarnings("unchecked")
            Map<String, Object> result = gson.fromJson(content, Map.class);
            log.info("Feedback generated successfully, keys: {}", result.keySet());
            return Optional.of(result);
        } catch (Exception e) {
            log.error("Error parsing OpenAI response: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }
    private String buildTranscriptText(List<Map<String, String>> transcript) {
        if (transcript == null || transcript.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Map<String, String> msg : transcript) {
            String role    = msg.get("role");
            String content = msg.get("content");
            sb.append(role.equals("user") ? "Candidate: " : "Interviewer: ")
                    .append(content)
                    .append("\n\n");
        }
        return sb.toString();
    }
    private String extractLevel(String position) {
        if (position == null) return "Middle";
        String lower = position.toLowerCase();
        if (lower.contains("junior"))                                               return "Junior";
        if (lower.contains("senior") || lower.contains("lead")
                || lower.contains("principal") || lower.contains("architect"))      return "Senior";
        return "Middle";
    }
    private String buildLevelSpecificGuidance(String position) {
        return switch (extractLevel(position)) {
            case "Junior" -> """
                    - JUNIOR level expectations:
                      * Basic understanding of fundamental concepts (data types, loops, conditionals, OOP basics)
                      * Knowledge of standard library functions
                      * Ability to write simple programs
                      * May lack depth on advanced topics
                      * A complete, relevant answer at this level = 70-80% accuracy
                      * Bonus points for explaining WHY, not just WHAT
                    """;
            case "Senior" -> """
                    - SENIOR level expectations:
                      * Deep technical knowledge of language features, design patterns, architecture
                      * Understanding of performance implications and trade-offs
                      * Experience with complex systems and architectural decisions
                      * Ability to explain reasoning and discuss alternatives
                      * A complete answer with good depth = 80-95% accuracy
                      * Deduct points only for missing important considerations or poor trade-off analysis
                    """;
            default -> """
                    - MIDDLE level expectations:
                      * Solid understanding of core concepts and common patterns
                      * Ability to design and implement moderate complexity solutions
                      * Knowledge of best practices and performance considerations
                      * A good, complete answer at this level = 75-85% accuracy
                      * Should show understanding of when and why to use different approaches
                    """;
        };
    }
    private String buildJobContext(FeedbackRequest req) {
        StringBuilder sb = new StringBuilder();
        if (req.jobDescription() != null && !req.jobDescription().isBlank()) {
            sb.append("\nJOB DESCRIPTION:\n").append(req.jobDescription()).append("\n");
        }
        if (req.jobRequirements() != null && !req.jobRequirements().isEmpty()) {
            sb.append("\nJOB REQUIREMENTS:\n");
            for (String requirement : req.jobRequirements()) {
                sb.append("- ").append(requirement).append("\n");
            }
        }
        return sb.toString();
    }
    private String buildTestInterviewPrompt(FeedbackRequest req, String transcriptText) {
        String levelGuidance = buildLevelSpecificGuidance(req.position());
        String level = extractLevel(req.position());
        StringBuilder sb = new StringBuilder();
        sb.append("You are an expert technical interviewer. Analyze the transcript of a TEST (trial) interview.\n\n");
        sb.append("CRITICAL LANGUAGE REQUIREMENT:\n");
        sb.append("- You MUST respond ENTIRELY in ENGLISH.\n");
        sb.append("- Every single field in the JSON must be written in English.\n");
        sb.append("- Do NOT use Ukrainian, Russian, or any other language.\n");
        sb.append("- This rule has NO exceptions — even if the interview was conducted in another language.\n\n");
        sb.append("Programming language: ").append(req.programmingLanguage()).append("\n");
        sb.append("Position: ").append(req.position()).append(" (Level: ").append(level).append(")\n\n");
        sb.append("INTERVIEW TRANSCRIPT:\n");
        sb.append(transcriptText).append("\n\n");
        sb.append("ASSESSMENT RULES FOR TEST INTERVIEW:\n");
        sb.append("1. Evaluate ONLY the topics actually discussed in the interview\n");
        sb.append("2. Do NOT add hardcoded categories — only real topics from the conversation\n");
        sb.append("3. If 1 topic was discussed — return 1 category; if 2 topics — 2 categories, etc.\n");
        sb.append("4. Provide clear English recommendations for areas needing improvement\n");
        sb.append("5. strengths   — brief strengths on discussed topics (in English)\n");
        sb.append("6. improvements — what needs to be learned or improved (in English)\n");
        sb.append("7. resources   — English learning resources for improvement areas\n");
        sb.append("8. verdict must indicate this is a \"Test Interview\" and give a brief English conclusion\n\n");
        sb.append("LEVEL-SPECIFIC EVALUATION:\n");
        sb.append(levelGuidance).append("\n\n");
        sb.append("Provide assessment in JSON format (no markdown, only pure JSON):\n");
        sb.append("{\n");
        sb.append("    \"overallScore\": <number 0-100, average score for discussed topics>,\n");
        sb.append("    \"verdict\": \"<Test Interview: brief conclusion about demonstrated knowledge>\",\n");
        sb.append("    \"isTestInterview\": true,\n");
        sb.append("    \"categories\": [\n");
        sb.append("        {\"name\": \"<topic discussed>\", \"score\": <0-100>}\n");
        sb.append("    ],\n");
        sb.append("    \"strengths\": [\n");
        sb.append("        \"<strength on discussed topic>\"\n");
        sb.append("    ],\n");
        sb.append("    \"improvements\": [\n");
        sb.append("        \"<specific area needing improvement based on answers>\",\n");
        sb.append("        \"<another improvement area>\"\n");
        sb.append("    ],\n");
        sb.append("    \"topicsNotCovered\": [\n");
        sb.append("        \"<important topic for this position that was not discussed>\"\n");
        sb.append("    ],\n");
        sb.append("    \"resources\": [\n");
        sb.append("        {\n");
        sb.append("            \"topic\": \"<topic name>\",\n");
        sb.append("            \"links\": [\n");
        sb.append("                {\"title\": \"<resource title>\", \"url\": \"<URL>\"},\n");
        sb.append("                {\"title\": \"<another resource>\", \"url\": \"<URL>\"}\n");
        sb.append("            ]\n");
        sb.append("        }\n");
        sb.append("    ],\n");
        sb.append("    \"detailedAnswers\": [\n");
        sb.append("        {\n");
        sb.append("            \"questionNumber\": 1,\n");
        sb.append("            \"question\": \"<exact question from interview in English>\",\n");
        sb.append("            \"candidateAnswer\": \"<exact answer from transcript>\",\n");
        sb.append("            \"accuracy\": <0-100>,\n");
        sb.append("            \"feedback\": \"<specific feedback on this answer in English>\",\n");
        sb.append("            \"topic\": \"<topic this question covers>\"\n");
        sb.append("        }\n");
        sb.append("    ]\n");
        sb.append("}\n\n");
        sb.append("CRITICAL INSTRUCTIONS:\n");
        sb.append("- Extract questions from INTERVIEWER messages\n");
        sb.append("- Extract answers from CANDIDATE messages\n");
        sb.append("- If candidate says \"I don't know\" without trying — set accuracy = 0\n");
        sb.append("- If candidate shows some understanding but lacks details — evaluate based on LEVEL\n");
        sb.append("- Include ALL questions from the transcript in detailedAnswers\n");
        sb.append("- For topicsNotCovered, list 2-3 important topics for ").append(req.position()).append(" position not discussed\n");
        sb.append("- For resources, provide official documentation, tutorials, or courses in English\n");
        sb.append("- Score fairly: a complete answer at the candidate's level = 70-100%, not just 50%\n");
        return sb.toString();
    }
    private String buildFullInterviewPrompt(FeedbackRequest req, String transcriptText) {
        String jobContext = buildJobContext(req);
        String levelGuidance = buildLevelSpecificGuidance(req.position());
        String level = extractLevel(req.position());
        String jobInstructions = jobContext.isBlank() ? "" :
                "\n4. JOB-SPECIFIC ANALYSIS:\n" +
                "   - Compare candidate answers with the job requirements provided\n" +
                "   - In topicsNotCovered, prioritize topics from the job description not discussed\n" +
                "   - Provide resources specifically for technologies mentioned in the job posting\n";
        StringBuilder sb = new StringBuilder();
        sb.append("You are an expert technical interviewer. Analyze the interview transcript and provide a detailed candidate assessment.\n\n");
        sb.append("CRITICAL LANGUAGE REQUIREMENT:\n");
        sb.append("- You MUST respond ENTIRELY in ENGLISH.\n");
        sb.append("- Every single field in the JSON must be written in English.\n");
        sb.append("- Do NOT use Ukrainian, Russian, or any other language.\n");
        sb.append("- This rule has NO exceptions — even if the interview was conducted in another language.\n\n");
        sb.append("Programming language: ").append(req.programmingLanguage()).append("\n");
        sb.append("Position: ").append(req.position()).append(" (Level: ").append(level).append(")\n");
        sb.append(jobContext).append("\n");
        sb.append("INTERVIEW TRANSCRIPT:\n");
        sb.append(transcriptText).append("\n\n");
        sb.append("LEVEL-SPECIFIC EVALUATION:\n");
        sb.append(levelGuidance).append("\n\n");
        sb.append("ASSESSMENT RULES:\n");
        sb.append("1. DYNAMIC CATEGORIES: Extract topics ONLY from the actual conversation\n");
        sb.append("   - Do NOT use hardcoded categories\n");
        sb.append("   - Each category = specific topic discussed (e.g. \"REST API\", \"SQL Joins\", \"Multithreading\")\n");
        sb.append("   - Minimum 3, maximum 8 categories\n\n");
        sb.append("2. UNCOVERED TOPICS (topicsNotCovered):\n");
        sb.append("   - Important topics for ").append(req.position()).append(" position NOT discussed in the interview\n");
        sb.append("   - List 3-5 topics worth studying\n");
        sb.append("   - If job description is provided, prioritize topics from there\n\n");
        sb.append("3. RESOURCES (resources):\n");
        sb.append("   - For each recommendation provide 1-2 quality English resources\n");
        sb.append("   - Format: title + URL\n");
        sb.append("   - Priority: official documentation, quality tutorials, books\n");
        sb.append(jobInstructions).append("\n");
        sb.append("Provide assessment in JSON format (no markdown, only pure JSON):\n");
        sb.append("{\n");
        sb.append("    \"overallScore\": <number 0-100>,\n");
        sb.append("    \"verdict\": \"<is the candidate ready for the position>\",\n");
        sb.append("    \"isTestInterview\": false,\n");
        sb.append("    \"categories\": [\n");
        sb.append("        {\"name\": \"<specific topic from interview>\", \"score\": <0-100>},\n");
        sb.append("        {\"name\": \"<another topic>\", \"score\": <0-100>}\n");
        sb.append("    ],\n");
        sb.append("    \"strengths\": [\n");
        sb.append("        \"<strength 1>\",\n");
        sb.append("        \"<strength 2>\",\n");
        sb.append("        \"<strength 3>\"\n");
        sb.append("    ],\n");
        sb.append("    \"improvements\": [\n");
        sb.append("        \"<improvement area 1>\",\n");
        sb.append("        \"<improvement area 2>\",\n");
        sb.append("        \"<improvement area 3>\"\n");
        sb.append("    ],\n");
        sb.append("    \"topicsNotCovered\": [\n");
        sb.append("        \"<important topic not discussed>\",\n");
        sb.append("        \"<another topic>\"\n");
        sb.append("    ],\n");
        sb.append("    \"resources\": [\n");
        sb.append("        {\n");
        sb.append("            \"topic\": \"<topic name>\",\n");
        sb.append("            \"links\": [\n");
        sb.append("                {\"title\": \"<resource name>\", \"url\": \"<link>\"},\n");
        sb.append("                {\"title\": \"<resource name 2>\", \"url\": \"<link>\"}\n");
        sb.append("            ]\n");
        sb.append("        }\n");
        sb.append("    ],\n");
        sb.append("    \"detailedAnswers\": [\n");
        sb.append("        {\n");
        sb.append("            \"questionNumber\": 1,\n");
        sb.append("            \"question\": \"<exact question from interview in English>\",\n");
        sb.append("            \"candidateAnswer\": \"<exact answer from transcript>\",\n");
        sb.append("            \"accuracy\": <0-100>,\n");
        sb.append("            \"feedback\": \"<specific feedback in English>\",\n");
        sb.append("            \"topic\": \"<topic this question covers>\"\n");
        sb.append("        }\n");
        sb.append("    ]\n");
        sb.append("}\n\n");
        sb.append("CRITICAL INSTRUCTIONS:\n");
        sb.append("- Extract ALL questions from INTERVIEWER messages in chronological order\n");
        sb.append("- Match each question with the corresponding CANDIDATE response\n");
        sb.append("- NEVER invent questions not present in the transcript\n");
        sb.append("- If no interviewer question found for an answer, set question = \"<Missing interviewer question>\" and accuracy = 0\n");
        sb.append("- Score based on candidate LEVEL:\n");
        sb.append("  * JUNIOR:  complete basic answer = 70-80%\n");
        sb.append("  * MIDDLE:  solid answer with depth = 75-85%\n");
        sb.append("  * SENIOR:  deep technical answer = 80-95%\n");
        sb.append("- If candidate says \"I don't know\" without trying — set accuracy = 0\n");
        sb.append("- Include ALL questions in detailedAnswers without exception\n");
        sb.append("- For resources, prioritize topics where accuracy < 60%\n");
        return sb.toString();
    }
    private List<Map<String, String>> filterSystemMessages(List<Map<String, String>> transcript) {
        if (transcript == null || transcript.isEmpty()) {
            return transcript;
        }
        return transcript.stream()
                .filter(msg -> {
                    String content = msg.getOrDefault("content", "").toLowerCase();
                    boolean isTimeReminder = content.equals("⏰ 1 minute remaining")
                            || content.equals("⏰ 5 minutes remaining")
                            || content.equals("⏰ 30 seconds remaining")
                            || (content.contains("minute remaining") && content.length() < 50);
                    boolean isCompletionMessage = content.contains("interview time is ending")
                            || content.contains("interview completed")
                            || content.contains("thank you for participating");
                    return !isTimeReminder && !isCompletionMessage;
                })
                .collect(Collectors.toList());
    }
}
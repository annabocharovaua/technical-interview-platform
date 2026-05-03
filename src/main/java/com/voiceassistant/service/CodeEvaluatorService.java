package com.voiceassistant.service;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.voiceassistant.dto.CodeSubmission;
import com.voiceassistant.dto.EvaluationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
/**
 * Service for evaluating submitted code using OpenAI GPT models.
 * Analyzes code correctness, quality, optimization, and provides detailed feedback.
 * Scores solutions on correctness and code quality criteria.
 */
@Slf4j
@Service
public class CodeEvaluatorService {
    @Value("${openai.api-key}")
    private String apiKey;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final Gson gson = new Gson();

    /**
     * Evaluates submitted code solution using AI.
     * Analyzes correctness, code quality, and provides score (0-100) with feedback.
     *
     * @param submission code submission containing code, language, difficulty, and task info
     * @return EvaluationResult with score and detailed feedback from AI
     */
    public EvaluationResult evaluate(CodeSubmission submission) {
        try {
            String prompt = buildPrompt(submission);
            String response = callOpenAI(prompt);
            log.info("AI response: {}", response);
            response = response.trim();
            if (response.startsWith("```json")) {
                response = response.substring(7);
            } else if (response.startsWith("```")) {
                response = response.substring(3);
            }
            if (response.endsWith("```")) {
                response = response.substring(0, response.length() - 3);
            }
            response = response.trim();
            JsonObject jsonResponse = gson.fromJson(response, JsonObject.class);
            int score = jsonResponse.has("score") ? jsonResponse.get("score").getAsInt() : 50;
            String summary = jsonResponse.has("summary") ? jsonResponse.get("summary").getAsString() : "";
            String feedback = response;
            return new EvaluationResult(score, feedback, "", true);
        } catch (Exception e) {
            log.error("Error evaluating code", e);
            String fallbackJson = "{\"score\":50,\"summary\":\"Failed to evaluate code\",\"pros\":[],\"cons\":[],\"suggestions\":[]}";
            return new EvaluationResult(50, fallbackJson, "", true);
        }
    }
    private String buildPrompt(CodeSubmission submission) {
        String taskInfo = "";
        if (submission.getTaskTitle() != null && !submission.getTaskTitle().isEmpty()) {
            taskInfo = "Task: " + submission.getTaskTitle() + "\n";
        }
        if (submission.getTaskDescription() != null && !submission.getTaskDescription().isEmpty()) {
            taskInfo += "Task description:\n" + submission.getTaskDescription() + "\n\n";
        }
        return "You are a fair technical interviewer evaluating a coding solution.\n\n" +
               taskInfo +
               "Programming language: " + submission.getLanguage() + "\n" +
               "Difficulty level: " + submission.getDifficulty() + "\n\n" +
               "Candidate's solution:\n```\n" + submission.getCode() + "\n```\n\n" +
               "EVALUATION CRITERIA:\n" +
               "• 0 points — code does NOT solve the task at all (template, Hello World, wrong logic)\n" +
               "• 1-30 points — minimal attempt, completely incorrect\n" +
               "• 31-50 points — partially solves the task, significant errors\n" +
               "• 51-70 points — solves the task but suboptimally or has minor errors\n" +
               "• 71-90 points — correctly solves the task with good optimization\n" +
               "• 91-100 points — correct solution with good code quality\n\n" +
               "IMPORTANT RULES:\n" +
               "• If code outputs 'Hello World' or is just a template without logic — give 0 points!\n" +
               "• Do NOT penalize for comments like '// Your code here' or '// Test examples' — these are from our template\n" +
               "• Do NOT penalize for simple/basic approach if it correctly solves the problem\n" +
               "• Do NOT consider time spent — evaluate ONLY the code quality and correctness\n" +
               "• If the solution is CORRECT and works — give 100 points\n" +
               "• Only reduce points if there are actual bugs, wrong logic, or code doesn't compile\n\n" +
               "Return response ONLY in JSON format (no markdown):\n" +
               "{\n" +
               "  \"score\": <number 0-100>,\n" +
               "  \"summary\": \"<short conclusion in Ukrainian, 1-2 sentences>\",\n" +
               "  \"pros\": [\"<pro 1 in Ukrainian>\", \"<pro 2>\"],\n" +
               "  \"cons\": [\"<con 1 in Ukrainian>\", \"<con 2>\"],\n" +
               "  \"suggestions\": [\"<suggestion 1 in Ukrainian>\", \"<suggestion 2>\"]\n" +
               "}\n\n" +
               "All text must be in English. If no pros/cons/suggestions — return empty array [].";
    }
    private String callOpenAI(String prompt) {
        try {
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("model", "gpt-4o");
            requestBody.addProperty("temperature", 0.5);
            JsonArray messages = new JsonArray();
            JsonObject message = new JsonObject();
            message.addProperty("role", "user");
            message.addProperty("content", prompt);
            messages.add(message);
            requestBody.add("messages", messages);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.openai.com/v1/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(requestBody)))
                    .timeout(Duration.ofSeconds(30))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonObject responseJson = gson.fromJson(response.body(), JsonObject.class);
                return responseJson
                        .getAsJsonArray("choices")
                        .get(0).getAsJsonObject()
                        .getAsJsonObject("message")
                        .get("content").getAsString();
            } else {
                log.error("OpenAI API error: {} - {}", response.statusCode(), response.body());
                return "Score: 50\nFeedback: Average performance.";
            }
        } catch (Exception e) {
            log.error("Error calling OpenAI", e);
            return "Score: 50\nFeedback: Average performance.";
        }
    }
}
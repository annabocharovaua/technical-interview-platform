package com.voiceassistant.service;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.voiceassistant.dto.CodingSettings;
import com.voiceassistant.dto.CodingTask;
import com.voiceassistant.dto.HintRequest;
import com.voiceassistant.dto.SolutionRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
/**
 * Service for generating and managing coding interview tasks using OpenAI API.
 * Creates programming challenges, hints, and solutions based on user preferences.
 * Handles task generation, time limit calculation, and code evaluation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CodingTaskService {
    @Value("${openai.api-key}")
    private String apiKey;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final Gson gson = new Gson();

    /**
     * Generates a unique coding task based on specified settings.
     * Creates task with title, description, starter code, and optional time limit.
     *
     * @param settings containing language, difficulty level, topic, and other preferences
     * @return CodingTask object with all task details
     */
    public CodingTask generateTask(CodingSettings settings) {
        String prompt = buildPrompt(settings);
        String response = callOpenAI(prompt);
        CodingTask task = parseTask(response);
        task.setId(UUID.randomUUID().toString());
        task.setLanguage(settings.getLanguage());
        task.setDifficulty(settings.getDifficulty());
        task.setTopic(settings.getTopic());
        if (settings.isTimedMode()) {
            Integer timeLimit = generateTimeLimit(settings.getDifficulty(), task.getDescription());
            task.setTimeLimit(timeLimit);
        }
        return task;
    }
    /**
     * Generates an estimated time limit for task completion based on difficulty and task details.
     * Accounts for problem complexity and adjusts time accordingly.
     *
     * @param difficulty task difficulty level (EASY, MEDIUM, HARD)
     * @param description task description for AI analysis
     * @return time limit in seconds
     */
    private Integer generateTimeLimit(String difficulty, String description) {
        int baseMinutes;
        switch (difficulty != null ? difficulty.toLowerCase() : "medium") {
            case "easy":
                baseMinutes = 10;
                break;
            case "hard":
                baseMinutes = 30;
                break;
            case "medium":
            default:
                baseMinutes = 20;
                break;
        }
        String prompt = "You are evaluating a coding task. Based on the difficulty level and task description, " +
                "estimate how many minutes a competent developer should need to solve this task.\n\n" +
                "Difficulty: " + difficulty + "\n" +
                "Task description: " + description + "\n\n" +
                "Rules:\n" +
                "- Easy tasks: 5-15 minutes\n" +
                "- Medium tasks: 15-25 minutes\n" +
                "- Hard tasks: 25-40 minutes\n\n" +
                "Return ONLY a number (minutes), nothing else. Example: 15";
        try {
            String response = callOpenAI(prompt).trim();
            int minutes = Integer.parseInt(response.replaceAll("[^0-9]", ""));
            minutes = Math.max(5, Math.min(45, minutes));
            return minutes * 60;
        } catch (Exception e) {
            log.warn("Failed to parse AI time estimate, using default", e);
            return baseMinutes * 60;
        }
    }
    /**
     * Generates a helpful hint for solving a coding task.
     * Returns a 2-3 sentence hint without revealing the complete solution.
     *
     * @param request containing task title, description, and language
     * @return hint text that guides toward solution
     */
    public String generateHint(HintRequest request) {
        String prompt = "Give a short hint (2-3 sentences) for the following programming task. " +
                "Do NOT give the full solution, only guide the thinking direction.\n\n" +
                "Task: " + request.getTaskTitle() + "\n" +
                "Description: " + request.getTaskDescription() + "\n" +
                "Language: " + request.getLanguage() + "\n\n" +
                "Hint:";
        return callOpenAI(prompt);
    }
    /**
     * Generates an optimal solution code for a given coding task.
     * Returns complete, runnable code without explanations.
     *
     * @param request containing task title, description, and target language
     * @return complete solution code ready to run
     */
    public String generateSolution(SolutionRequest request) {
        String prompt = "Write an optimal solution for the following programming task.\n\n" +
                "Task: " + request.getTaskTitle() + "\n" +
                "Description: " + request.getTaskDescription() + "\n" +
                "Programming language: " + request.getLanguage() + "\n\n" +
                "Return ONLY the code without explanations. The code must be complete and ready to run.";
        String response = callOpenAI(prompt);
        response = response.trim();
        if (response.startsWith("```")) {
            int firstNewline = response.indexOf("\n");
            if (firstNewline != -1) {
                response = response.substring(firstNewline + 1);
            }
        }
        if (response.endsWith("```")) {
            response = response.substring(0, response.length() - 3).trim();
        }
        return response;
    }
    private String buildPrompt(CodingSettings settings) {
        String topic = settings.getTopic() != null && !settings.getTopic().isEmpty() ? settings.getTopic() : "general interview questions";
        String language = settings.getLanguage();
        String difficulty = settings.getDifficulty() != null ? settings.getDifficulty().toLowerCase() : "medium";
        String categoryInstructions = "";
        if ("STRING_MANIPULATION".equals(topic)) {
            categoryInstructions = "The task MUST involve string manipulation, parsing, pattern matching, or text processing. Examples: palindrome, anagrams, string compression, substring search.";
        } else if ("DATA_STRUCTURES".equals(topic)) {
            categoryInstructions = "The task MUST involve data structures like arrays, linked lists, trees, graphs, hash maps, or stacks. Examples: binary search tree, graph traversal, hashtable collision handling.";
        } else if ("ALGORITHMS".equals(topic)) {
            categoryInstructions = "The task MUST involve algorithmic problem-solving: sorting, searching, dynamic programming, or optimization. Examples: merge sort, binary search, fibonacci, knapsack problem.";
        } else if ("OOP_PATTERNS".equals(topic)) {
            categoryInstructions = "The task MUST involve object-oriented design, design patterns, or OOP principles. Examples: singleton, factory pattern, inheritance, polymorphism, encapsulation.";
        } else if ("SYSTEM_DESIGN".equals(topic)) {
            categoryInstructions = "The task MUST involve system design concepts: scalability, caching, databases, microservices. Examples: design URL shortener, cache system, rate limiter.";
        } else if ("WEB_DEVELOPMENT".equals(topic)) {
            categoryInstructions = "The task MUST involve web development: HTTP, REST APIs, frontend-backend communication, or web protocols.";
        }
        return "Generate a programming task for " + language + " at " + difficulty + " difficulty level.\n\n" +
               categoryInstructions + "\n\n" +
               "The task should:\n" +
               "1. Be realistic and similar to real interview questions\n" +
               "2. Be DIRECTLY RELATED to the category mentioned above\n" +
               "3. Have clear examples and constraints\n" +
               "4. Require problem-solving skills relevant to the category\n" +
               "CRITICAL - STARTER CODE RULES:\n" +
               "- The starter code MUST contain ONLY the function/method SIGNATURE\n" +
               "- The method body MUST be EMPTY or contain only '// Your code here'\n" +
               "- DO NOT include any working code, implementation, or solution\n" +
               "- DO NOT include any logic, algorithms, or calculations\n" +
               "- Only the method signature and comments allowed\n" +
               "- Example: public static boolean isPalindrome(String str) { // Your code here }\n" +
               "- The main method MUST call the function with test examples\n\n" +
               "IMPORTANT: You MUST provide starter code with BOTH:\n" +
               "1. A descriptive function/method signature WITH EMPTY BODY (only comments allowed)\n" +
               "2. A main method/entry point that calls the function with test examples\n" +
               "3. NO TODO comments - use only '// Your code here' or similar\n\n" +
               "Response structure (use these exact labels in Ukrainian):\n" +
               "Title: <short task name>\n" +
               "Description: <detailed task description with examples and constraints>\n" +
               "Code: <starter code with EMPTY method body>\n\n" +
               "Example for Java String Manipulation task:\n" +
               "Code:\n" +
               "```java\n" +
               "public class Solution {\n" +
               "    public static String reverseString(String str) {\n" +
               "        // Your code here\n" +
               "        return null;\n" +
               "    }\n" +
               "\n" +
               "    public static void main(String[] args) {\n" +
               "        System.out.println(reverseString(\"hello\")); // Expected: olleh\n" +
               "        System.out.println(reverseString(\"world\")); // Expected: dlrow\n" +
               "    }\n" +
               "}\n" +
               "```\n\n" +
               "REMEMBER: The reverseString method should be empty in the starter code - do NOT implement it!\n" +
               "DO NOT generate tasks about " + getExcludedCategories(topic) + " unless it's the required category above!";
    }
    private String getExcludedCategories(String currentCategory) {
        return "factorial, fibonacci, general math, or anything not related to " + currentCategory;
    }
    private String callOpenAI(String prompt) {
        try {
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("model", "gpt-4o");
            requestBody.addProperty("temperature", 0.7);
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
                        .get("message").getAsJsonObject()
                        .get("content").getAsString();
            } else {
                log.error("OpenAI API error: {} - {}", response.statusCode(), response.body());
                return "Title: Default Task\nDescription: Write a function to reverse a string.";
            }
        } catch (Exception e) {
            log.error("Error calling OpenAI", e);
            return "Title: Default Task\nDescription: Write a function to reverse a string.";
        }
    }
    private CodingTask parseTask(String response) {
        String title = "";
        String description = "";
        String starterCode = "";
        int titleStart = response.indexOf("Title:");
        if (titleStart != -1) {
            int titleEnd = response.indexOf("\n", titleStart);
            if (titleEnd != -1) {
                title = response.substring(titleStart + 6, titleEnd).trim();
            }
        }
        int descStart = response.indexOf("Description:");
        int codeStart = response.indexOf("Code:");
        if (descStart != -1) {
            int descEnd = codeStart != -1 ? codeStart : response.length();
            description = response.substring(descStart + 5, descEnd).trim();
        }
        if (codeStart != -1) {
            String codeSection = response.substring(codeStart + 4).trim();
            if (codeSection.contains("```")) {
                int blockStart = codeSection.indexOf("```");
                int firstNewline = codeSection.indexOf("\n", blockStart);
                int blockEnd = codeSection.indexOf("```", firstNewline);
                if (firstNewline != -1 && blockEnd != -1) {
                    starterCode = codeSection.substring(firstNewline + 1, blockEnd).trim();
                } else if (firstNewline != -1) {
                    starterCode = codeSection.substring(firstNewline + 1).trim();
                    if (starterCode.endsWith("```")) {
                        starterCode = starterCode.substring(0, starterCode.length() - 3).trim();
                    }
                }
            } else {
                starterCode = codeSection.trim();
            }
        }
        if (title.isEmpty()) {
            title = "Task";
        }
        if (description.isEmpty()) {
            description = "Implement the function according to the task.";
        }
        return new CodingTask(null, title, description, null, null, null, starterCode, null);
    }
}
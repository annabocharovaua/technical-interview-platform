package com.voiceassistant.service;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.voiceassistant.dto.*;
import com.voiceassistant.entity.*;
import com.voiceassistant.repository.CodingProgressRepository;
import com.voiceassistant.repository.CodingTopicRepository;
import com.voiceassistant.repository.UserSkillProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
/**
 * Service for managing user coding skill profiles and learning progress.
 * Tracks topics, calculates proficiency levels, identifies weak areas, and provides task recommendations.
 * Integrates with OpenAI for topic extraction and AI-driven personalization.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SkillProfileService {
    @Value("${openai.api-key}")
    private String apiKey;
    private final CodingTopicRepository codingTopicRepository;
    private final CodingProgressRepository codingProgressRepository;
    private final UserSkillProfileRepository userSkillProfileRepository;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final Gson gson = new Gson();
    public static final List<String> TASK_CATEGORIES = List.of(
        "STRING_MANIPULATION",
        "DATA_STRUCTURES",
        "ALGORITHMS",
        "OOP_PATTERNS",
        "SYSTEM_DESIGN",
        "WEB_DEVELOPMENT"
    );
    public static final Map<String, String> CATEGORY_DESCRIPTIONS = Map.ofEntries(
        Map.entry("STRING_MANIPULATION", "🔤 String Manipulation - Text processing, parsing, pattern matching"),
        Map.entry("DATA_STRUCTURES", "📊 Data Structures - Arrays, Lists, Maps, Trees, Graphs"),
        Map.entry("ALGORITHMS", "⚙️ Algorithms - Sorting, searching, dynamic programming, optimization"),
        Map.entry("OOP_PATTERNS", "🏗️ OOP & Design Patterns - Object-oriented design, design patterns"),
        Map.entry("SYSTEM_DESIGN", "🏢 System Design - Scalability, caching, databases, microservices"),
        Map.entry("WEB_DEVELOPMENT", "🌐 Web Development - HTTP, REST APIs, web protocols")
    );

    /**
     * Extracts coding topics and concepts from task description using AI analysis.
     * Identifies main topics, category, and key learning concepts.
     *
     * @param taskTitle title of the coding task
     * @param taskDescription detailed description of the task
     * @return ExtractedTopicsDTO with topics, category, difficulty, and key concepts
     */
    @Transactional
    public ExtractedTopicsDTO extractTopicsFromTask(String taskTitle, String taskDescription) {
        try {
            String prompt = "Analyze this coding task and extract the main topics/concepts it covers.\n\n" +
                    "Task: " + taskTitle + "\n" +
                    "Description: " + taskDescription + "\n\n" +
                    "Return a JSON with:\n" +
                    "- topics: array of specific topics (e.g., [\"Hash Maps\", \"Two Pointers\", \"String Manipulation\"])\n" +
                    "- category: one of [STRING_MANIPULATION, DATA_STRUCTURES, ALGORITHMS, OOP_PATTERNS, SYSTEM_DESIGN, WEB_DEVELOPMENT]\n" +
                    "- difficulty_level: EASY, MEDIUM, or HARD\n" +
                    "- key_concepts: array of key concepts to learn\n\n" +
                    "Return ONLY valid JSON, no markdown.";
            String response = callOpenAI(prompt);
            ExtractedTopicsDTO result = gson.fromJson(response, ExtractedTopicsDTO.class);
            return result;
        } catch (Exception e) {
            log.error("Failed to extract topics from task", e);
            return ExtractedTopicsDTO.builder()
                    .topics(List.of("General Programming"))
                    .category("ALGORITHMS")
                    .difficultyLevel("MEDIUM")
                    .build();
        }
    }
    /**
     * Extracts main topic from user code using pattern matching.
     */
    private String extractMainTopicFromUserCode(String userCode, List<String> suggestedTopics) {
        if (userCode == null || userCode.isEmpty()) {
            return !suggestedTopics.isEmpty() ? suggestedTopics.get(0) : "General Programming";
        }
        Map<String, String> prioritizedPatterns = new LinkedHashMap<>();
        prioritizedPatterns.put("String\\.charAt|substring|split|toString|reverse", "String Manipulation");
        prioritizedPatterns.put("Thread|synchronized|Concurrent|Lock|Runnable", "Multithreading");
        prioritizedPatterns.put("TreeMap|TreeSet|Tree|BST|Binary", "Trees");
        prioritizedPatterns.put("ArrayList|LinkedList|Vector|List<", "Lists");
        prioritizedPatterns.put("HashMap|Hashtable|Dictionary|Map<", "Hash Maps");
        prioritizedPatterns.put("Stack|Deque|Queue|offer|poll", "Stacks and Queues");
        prioritizedPatterns.put("sort|Collections\\.sort|Comparable", "Sorting Algorithms");
        prioritizedPatterns.put("binarySearch|Binary.*Search", "Binary Search");
        prioritizedPatterns.put("dp\\[|memo|Dynamic", "Dynamic Programming");
        prioritizedPatterns.put("recursion|recursive", "Recursion");
        prioritizedPatterns.put("interface|abstract|implements|extends", "OOP and Design Patterns");
        prioritizedPatterns.put("stream|lambda|Functional", "Functional Programming");
        prioritizedPatterns.put("try|catch|finally|exception", "Exception Handling");
        for (Map.Entry<String, String> entry : prioritizedPatterns.entrySet()) {
            Pattern pattern = Pattern.compile("(?i)(" + entry.getKey() + ")");
            if (pattern.matcher(userCode).find()) {
                return entry.getValue();
            }
        }
        return !suggestedTopics.isEmpty() ? suggestedTopics.get(0) : "General Programming";
    }
    /**
     * Records task completion and updates user progress.
     */
    @Transactional
    public CodingProgress recordTaskCompletion(Long userId, CodingTask task, CodeSubmission submission,
                                               EvaluationResult evaluation, Integer timeSpentSeconds) {
        try {
            ExtractedTopicsDTO taskTopics = extractTopicsFromTask(task.getTitle(), task.getDescription());
            String mainTopic = extractMainTopicFromUserCode(submission.getCode(), taskTopics.getTopics());
            List<String> finalTopics = List.of(mainTopic);
            CodingProgress progress = CodingProgress.builder()
                    .user(User.builder().id(userId).build())
                    .taskId(task.getId())
                    .taskTitle(task.getTitle())
                    .taskDescription(task.getDescription())
                    .language(submission.getLanguage())
                    .difficulty(task.getDifficulty())
                    .score(evaluation.getScore())
                    .userCode(submission.getCode())
                    .feedback(evaluation.getFeedback())
                    .topicsIdentified(gson.toJson(finalTopics))
                    .taskCategory(taskTopics.getCategory())
                    .hintsUsed(submission.getHintsUsed() != null ? submission.getHintsUsed() : 0)
                    .timeSpentSeconds(timeSpentSeconds)
                    .build();
            CodingProgress saved = codingProgressRepository.save(progress);
            updateTopicProgress(userId, mainTopic, taskTopics.getCategory(), evaluation.getScore());
            updateSkillProfile(userId, evaluation.getScore());
            return saved;
        } catch (Exception e) {
            log.error("Error recording task completion", e);
            throw new RuntimeException("Failed to record task completion", e);
        }
    }
    /**
     * Updates progress for a specific topic.
     */
    @Transactional
    public void updateTopicProgress(Long userId, String topicName, String categoryType, Integer score) {
        Optional<CodingTopic> existingTopic = codingTopicRepository.findByUserIdAndTopicName(userId, topicName);
        CodingTopic topic;
        if (existingTopic.isPresent()) {
            topic = existingTopic.get();
            int totalScore = (int) (topic.getAverageScore() * topic.getTasksCompleted() + score);
            topic.setTasksCompleted(topic.getTasksCompleted() + 1);
            topic.setAverageScore((double) totalScore / topic.getTasksCompleted());
        } else {
            topic = CodingTopic.builder()
                    .user(User.builder().id(userId).build())
                    .topicName(topicName)
                    .categoryType(categoryType)
                    .tasksCompleted(1)
                    .averageScore((double) score)
                    .build();
        }
        codingTopicRepository.save(topic);
    }
    /**
     * Updates user skill profile with latest progress data.
     */
    @Transactional
    public void updateSkillProfile(Long userId, Integer latestScore) {
        UserSkillProfile profile = userSkillProfileRepository.findByUserId(userId)
                .orElseGet(() -> {
                    UserSkillProfile newProfile = UserSkillProfile.builder()
                            .user(User.builder().id(userId).build())
                            .totalTasksAttempted(0)
                            .totalTasksCompleted(0)
                            .overallScore(0.0)
                            .currentLevel("BEGINNER")
                            .build();
                    return userSkillProfileRepository.save(newProfile);
                });
        int tasksAttempted = profile.getTotalTasksAttempted() != null ? profile.getTotalTasksAttempted() : 0;
        int tasksCompleted = profile.getTotalTasksCompleted() != null ? profile.getTotalTasksCompleted() : 0;
        profile.setTotalTasksAttempted(tasksAttempted + 1);
        if (latestScore >= 60) {
            profile.setTotalTasksCompleted(tasksCompleted + 1);
        }
        List<CodingProgress> allProgress = codingProgressRepository.findByUserId(userId);
        if (!allProgress.isEmpty()) {
            double avgScore = allProgress.stream()
                    .mapToInt(CodingProgress::getScore)
                    .average()
                    .orElse(0.0);
            profile.setOverallScore(avgScore);
        }
        List<CodingTopic> topics = codingTopicRepository.findByUserId(userId);
        if (!topics.isEmpty()) {
            Optional<CodingTopic> weakest = topics.stream()
                    .min(Comparator.comparingDouble(CodingTopic::getAverageScore));
            Optional<CodingTopic> strongest = topics.stream()
                    .max(Comparator.comparingDouble(CodingTopic::getAverageScore));
            weakest.ifPresent(t -> profile.setWeakestTopic(t.getTopicName()));
            strongest.ifPresent(t -> profile.setStrongestTopic(t.getTopicName()));
        }
        profile.setCurrentLevel(determineLevelByCompletedTasks(profile.getTotalTasksCompleted(), profile.getOverallScore()));
        profile.setRecommendedTopic(profile.getWeakestTopic());
        profile.setLastAssessed(LocalDateTime.now());
        userSkillProfileRepository.save(profile);
    }
    /**
     * Retrieves user skill profile with all metrics.
     */
    public UserSkillProfileDTO getUserSkillProfile(Long userId) {
        UserSkillProfile profile = userSkillProfileRepository.findByUserId(userId)
                .orElseGet(() -> {
                    UserSkillProfile newProfile = UserSkillProfile.builder()
                            .user(User.builder().id(userId).build())
                            .overallScore(0.0)
                            .totalTasksCompleted(0)
                            .totalTasksAttempted(0)
                            .currentLevel("BEGINNER")
                            .build();
                    return userSkillProfileRepository.save(newProfile);
                });
        List<CodingTopic> topics = codingTopicRepository.findByUserId(userId);
        List<TopicProgressDTO> topicProgress = topics.stream()
                .map(t -> TopicProgressDTO.builder()
                        .topicName(t.getTopicName())
                        .averageScore(t.getAverageScore())
                        .tasksCompleted(t.getTasksCompleted())
                        .tasksFailed(t.getTasksFailed())
                        .categoryType(t.getCategoryType())
                        .build())
                .collect(Collectors.toList());
        return UserSkillProfileDTO.builder()
                .id(profile.getId())
                .overallScore(profile.getOverallScore())
                .totalTasksCompleted(profile.getTotalTasksCompleted())
                .totalTasksAttempted(profile.getTotalTasksAttempted() != null ? profile.getTotalTasksAttempted() : 0)
                .preferredLanguage(profile.getPreferredLanguage())
                .weakestTopic(profile.getWeakestTopic())
                .strongestTopic(profile.getStrongestTopic())
                .currentLevel(profile.getCurrentLevel())
                .recommendedTopic(profile.getRecommendedTopic())
                .topicProgress(topicProgress)
                .build();
    }
    /**
     * Gets recommended next task based on user progress.
     */
    @Transactional
    public CodingSettingsWithFiltersDTO getRecommendedNextTask(Long userId) {
        try {
            UserSkillProfile profile = userSkillProfileRepository.findByUserId(userId)
                    .orElseThrow(() -> new RuntimeException("Skill profile not found for user " + userId));
            List<CodingTopic> topics = codingTopicRepository.findWeakestTopicsByUser(userId);
            String recommendedCategory = "ALGORITHMS";
            String recommendedDifficulty = "EASY";
            if (!topics.isEmpty()) {
                CodingTopic weakestTopic = topics.get(0);
                if (weakestTopic.getCategoryType() != null && !weakestTopic.getCategoryType().isEmpty()) {
                    recommendedCategory = weakestTopic.getCategoryType();
                }
                if (weakestTopic.getAverageScore() < 50) {
                    recommendedDifficulty = "EASY";
                } else if (weakestTopic.getAverageScore() < 70) {
                    recommendedDifficulty = "MEDIUM";
                } else {
                    recommendedDifficulty = "HARD";
                }
            }
            String language = profile.getPreferredLanguage() != null ? profile.getPreferredLanguage() : "Java";
            return CodingSettingsWithFiltersDTO.builder()
                    .category(recommendedCategory)
                    .difficulty(recommendedDifficulty)
                    .language(language)
                    .adaptiveMode(true)
                    .build();
        } catch (Exception e) {
            log.error("Error getting recommendation for user " + userId, e);
            return CodingSettingsWithFiltersDTO.builder()
                    .category("ALGORITHMS")
                    .difficulty("EASY")
                    .language("Java")
                    .adaptiveMode(true)
                    .build();
        }
    }
    /**
     * Returns available task categories.
     */
    public List<TaskCategoryDTO> getAvailableCategories() {
        return TASK_CATEGORIES.stream()
                .map(category -> TaskCategoryDTO.builder()
                        .id(category)
                        .type(category)
                        .name(CATEGORY_DESCRIPTIONS.getOrDefault(category, category))
                        .description("Tasks on: " + CATEGORY_DESCRIPTIONS.get(category))
                        .build())
                .collect(Collectors.toList());
    }
    /**
     * Determines proficiency level based on completed tasks and average score.
     * BEGINNER: 0-5 tasks, INTERMEDIATE: 6-15 tasks + 50% score,
     * ADVANCED: 16-40 tasks + 70% score, EXPERT: 40+ tasks + 80% score.
     */
    private String determineLevelByCompletedTasks(Integer tasksCompleted, Double averageScore) {
        if (tasksCompleted == null) tasksCompleted = 0;
        if (averageScore == null) averageScore = 0.0;
        if (tasksCompleted <= 5) {
            return "BEGINNER";
        } else if (tasksCompleted <= 15) {
            return averageScore >= 50 ? "INTERMEDIATE" : "BEGINNER";
        } else if (tasksCompleted <= 40) {
            if (averageScore < 50) return "BEGINNER";
            return averageScore >= 70 ? "ADVANCED" : "INTERMEDIATE";
        } else {
            if (averageScore < 70) return "INTERMEDIATE";
            return averageScore >= 80 ? "EXPERT" : "ADVANCED";
        }
    }
    /**
     * Calls OpenAI API to process prompt.
     */
    private String callOpenAI(String prompt) throws Exception {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", "gpt-3.5-turbo");
        requestBody.addProperty("temperature", 0.7);
        requestBody.addProperty("max_tokens", 1000);
        JsonArray messages = new JsonArray();
        JsonObject message = new JsonObject();
        message.addProperty("role", "user");
        message.addProperty("content", prompt);
        messages.add(message);
        requestBody.add("messages", messages);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.openai.com/v1/chat/completions"))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            log.error("OpenAI API error: {}", response.body());
            throw new RuntimeException("OpenAI API returned status " + response.statusCode());
        }
        JsonObject jsonResponse = gson.fromJson(response.body(), JsonObject.class);
        return jsonResponse.getAsJsonArray("choices")
                .get(0).getAsJsonObject()
                .getAsJsonObject("message")
                .get("content").getAsString();
    }
}
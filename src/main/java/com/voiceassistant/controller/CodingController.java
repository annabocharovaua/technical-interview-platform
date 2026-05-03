package com.voiceassistant.controller;
import com.voiceassistant.dto.*;
import com.voiceassistant.entity.User;
import com.voiceassistant.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
/**
 * REST controller for coding interview tasks and code submission management.
 * Provides endpoints for task generation, code compilation, evaluation, and hint retrieval.
 * Handles all coding challenge interactions and code evaluation workflow.
 *
 * @see CodingTaskService task generation service
 * @see CodeCompilerService code compilation and execution
 * @see CodeEvaluatorService AI-powered code evaluation
 */
@Slf4j
@RestController
@RequestMapping("/api/coding")
@RequiredArgsConstructor
public class CodingController {
    private final CodingTaskService codingTaskService;
    private final CodeCompilerService codeCompilerService;
    private final CodeEvaluatorService codeEvaluatorService;
    private final SkillProfileService skillProfileService;

    /**
     * Generates a new coding task based on specified settings.
     * Creates unique task with title, description, starter code, and optional time limit.
     *
     * @param settings coding task settings (language, difficulty, topic, timed mode)
     * @return ResponseEntity with generated CodingTask
     */
    @PostMapping("/generate-task")
    public ResponseEntity<CodingTask> generateTask(@RequestBody CodingSettings settings) {
        log.info("Generating task for language: {}, difficulty: {}", settings.getLanguage(), settings.getDifficulty());
        CodingTask task = codingTaskService.generateTask(settings);
        return ResponseEntity.ok(task);
    }
    /**
     * Compiles and executes submitted code.
     * Returns execution output or compilation error messages.
     *
     * @param submission code submission with source code and language
     * @return ResponseEntity with EvaluationResult containing execution output
     */
    @PostMapping("/compile")
    public ResponseEntity<EvaluationResult> compileCode(@RequestBody CodeSubmission submission) {
        log.info("Compiling code for language: {}", submission.getLanguage());
        EvaluationResult result = codeCompilerService.compileAndRun(submission);
        return ResponseEntity.ok(result);
    }
    /**
     * Submits code for evaluation by AI.
     * Compiles code, runs it, and provides AI-generated feedback with score.
     *
     * @param submission code submission with code, language, difficulty, and task info
     * @return ResponseEntity with EvaluationResult containing score and AI feedback
     */
    @PostMapping("/submit")
    public ResponseEntity<EvaluationResult> submitCode(@RequestBody CodeSubmission submission) {
        log.info("Submitting code for evaluation, language: {}", submission.getLanguage());
        EvaluationResult compileResult = codeCompilerService.compileAndRun(submission);
        if (!compileResult.isSuccess()) {
            log.info("Compilation failed");
            return ResponseEntity.ok(compileResult);
        }
        String programOutput = compileResult.getStackTrace();
        if (programOutput != null && programOutput.contains("Output:")) {
            programOutput = programOutput.replace("Output:", "").trim();
        }
        EvaluationResult eval = codeEvaluatorService.evaluate(submission);
        EvaluationResult result = new EvaluationResult(
                eval.getScore(),
                eval.getFeedback(),
                "",
                true,
                programOutput
        );
        return ResponseEntity.ok(result);
    }
    /**
     * Generates a helpful hint for solving a coding task.
     * Returns guidance without revealing complete solution.
     *
     * @param request hint request with task title and description
     * @return ResponseEntity with HintResponse containing hint text
     */
    @PostMapping("/hint")
    public ResponseEntity<HintResponse> getHint(@RequestBody HintRequest request) {
        log.info("Getting hint for task: {}", request.getTaskTitle());
        String hint = codingTaskService.generateHint(request);
        return ResponseEntity.ok(new HintResponse(hint));
    }
    /**
     * Generates a complete solution for a coding task.
     * Returns optimized working code ready to run.
     *
     * @param request solution request with task title and description
     * @return ResponseEntity with SolutionResponse containing solution code
     */
    @PostMapping("/solution")
    public ResponseEntity<SolutionResponse> getSolution(@RequestBody SolutionRequest request) {
        log.info("Getting solution for task: {}", request.getTaskTitle());
        String solution = codingTaskService.generateSolution(request);
        return ResponseEntity.ok(new SolutionResponse(solution));
    }
    /**
     * Retrieves user's current skill profile and progress.
     * Only authenticated users can access their own profile.
     *
     * @param userId user ID to retrieve profile for
     * @param user authenticated user from security context
     * @return ResponseEntity with UserSkillProfileDTO containing user stats and progress
     */
    @GetMapping("/profile/{userId}")
    public ResponseEntity<UserSkillProfileDTO> getUserSkillProfile(
            @PathVariable Long userId,
            @AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(401).build();
        }
        if (!user.getId().equals(userId)) {
            log.warn("User {} tried to access profile of user {}", user.getId(), userId);
            return ResponseEntity.status(403).build();
        }
        log.info("Getting skill profile for user: {}", userId);
        UserSkillProfileDTO profile = skillProfileService.getUserSkillProfile(userId);
        return ResponseEntity.ok(profile);
    }
    /**
     * Recommends next task based on user's skill profile and weak areas.
     * Returns task settings optimized for user's learning goals.
     *
     * @param userId user ID to generate recommendation for
     * @param user authenticated user from security context
     * @return ResponseEntity with CodingSettingsWithFiltersDTO containing recommended task settings
     */
    @GetMapping("/recommend/{userId}")
    public ResponseEntity<CodingSettingsWithFiltersDTO> getRecommendedTask(
            @PathVariable Long userId,
            @AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(401).build();
        }
        if (!user.getId().equals(userId)) {
            return ResponseEntity.status(403).build();
        }
        log.info("Getting recommended task for user: {}", userId);
        CodingSettingsWithFiltersDTO recommendation = skillProfileService.getRecommendedNextTask(userId);
        return ResponseEntity.ok(recommendation);
    }
    /**
     * Retrieves list of all available task categories and difficulty levels.
     * Can be used by frontend for filter options.
     *
     * @return ResponseEntity with list of TaskCategoryDTOs
     */
    @GetMapping("/categories")
    public ResponseEntity<List<TaskCategoryDTO>> getAvailableCategories() {
        List<TaskCategoryDTO> categories = skillProfileService.getAvailableCategories();
        return ResponseEntity.ok(categories);
    }
    /**
     * Generates a coding task with category-based filtering.
     * Uses provided filters (category, difficulty, language) to tailor task generation.
     *
     * @param settings coding settings with filter criteria
     * @return ResponseEntity with generated CodingTask matching filters
     */
    @PostMapping("/generate-task-filtered")
    public ResponseEntity<CodingTask> generateTaskWithFilters(@RequestBody CodingSettingsWithFiltersDTO settings) {
        log.info("Generating task with filters - category: {}, difficulty: {}", settings.getCategory(), settings.getDifficulty());
        CodingSettings codingSettings = new CodingSettings();
        codingSettings.setLanguage(settings.getLanguage() != null ? settings.getLanguage() : "Java");
        codingSettings.setDifficulty(settings.getDifficulty() != null ? settings.getDifficulty() : "MEDIUM");
        codingSettings.setTimedMode(Boolean.TRUE.equals(settings.getTimedMode()));
        if (settings.getCategory() != null) {
            codingSettings.setTopic(settings.getCategory());
        }
        CodingTask task = codingTaskService.generateTask(codingSettings);
        return ResponseEntity.ok(task);
    }
    /**
     * Records task completion progress for authenticated user.
     * Saves score, time spent, code, and identified weak topics.
     * Updates user's skill profile based on performance.
     *
     * @param submission code submission with task details, score, and time spent
     * @param user authenticated user from security context
     * @return ResponseEntity with success message or error details
     */
    @PostMapping("/progress")
    public ResponseEntity<String> recordProgress(
            @RequestBody CodeSubmission submission,
            @AuthenticationPrincipal User user) {
        try {
            if (user == null) {
                return ResponseEntity.status(401).body("User not authenticated");
            }
            submission.setUserId(user.getId());
            log.info("Recording progress for user: {}, task: {}", submission.getUserId(), submission.getTaskTitle());
            CodingTask task = new CodingTask();
            task.setId(submission.getTaskId());
            task.setTitle(submission.getTaskTitle());
            task.setDescription(submission.getTaskDescription());
            task.setLanguage(submission.getLanguage());
            task.setDifficulty(submission.getDifficulty());
            EvaluationResult evaluation = new EvaluationResult();
            evaluation.setScore(submission.getScore() != null ? submission.getScore() : 0);
            evaluation.setFeedback(submission.getFeedback() != null ? submission.getFeedback() : "");
            evaluation.setSuccess(true);
            skillProfileService.recordTaskCompletion(
                    submission.getUserId(),
                    task,
                    submission,
                    evaluation,
                    Math.toIntExact(submission.getTimeSpent())
            );
            return ResponseEntity.ok("Progress recorded successfully");
        } catch (Exception e) {
            log.error("Error recording progress: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body("Failed to record progress: " + e.getMessage());
        }
    }
}
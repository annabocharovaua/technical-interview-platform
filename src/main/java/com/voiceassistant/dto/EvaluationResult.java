package com.voiceassistant.dto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
/**
 * Data Transfer Object representing code evaluation results.
 * Contains score, feedback, and execution output/errors.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationResult {
    private int score;
    private String feedback;
    private String stackTrace;
    private boolean success;
    private String output;
    public EvaluationResult(int score, String feedback, String stackTrace, boolean success) {
        this.score = score;
        this.feedback = feedback;
        this.stackTrace = stackTrace;
        this.success = success;
        this.output = "";
    }
}
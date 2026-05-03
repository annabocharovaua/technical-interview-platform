package com.voiceassistant.dto;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
/**
 * Data Transfer Object for code submission to be evaluated.
 * Contains user code and context about the coding task.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CodeSubmission {
    private String code;
    private String language;
    private String taskId;
    private String taskTitle;
    private String taskDescription;
    private long timeSpent;
    private String difficulty;
    /**
     * User ID is set server-side from JWT token only.
     * JsonIgnore prevents client from injecting arbitrary userId.
     */
    @JsonIgnore
    private Long userId;
    private Integer score;
    private String feedback;
    private Integer hintsUsed;
}
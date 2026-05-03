package com.voiceassistant.dto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
/**
 * Data Transfer Object for solution request.
 * Contains task details for generating solution code.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SolutionRequest {
    private String taskTitle;
    private String taskDescription;
    private String language;
}
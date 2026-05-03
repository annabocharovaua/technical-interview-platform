package com.voiceassistant.dto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
/**
 * Data Transfer Object for hint request for coding tasks.
 * Contains task information for generating relevant hints.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HintRequest {
    private String taskTitle;
    private String taskDescription;
    private String language;
}
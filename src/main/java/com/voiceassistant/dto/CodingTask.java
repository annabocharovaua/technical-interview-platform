package com.voiceassistant.dto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
/**
 * Data Transfer Object representing a coding interview task.
 * Contains task details, starter code, and time constraints.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CodingTask {
    private String id;
    private String title;
    private String description;
    private String language;
    private String difficulty;
    private String topic;
    private String starterCode;
    private Integer timeLimit;
}
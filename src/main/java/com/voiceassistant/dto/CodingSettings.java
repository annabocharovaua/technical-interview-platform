package com.voiceassistant.dto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
/**
 * Data Transfer Object for coding task generation preferences.
 * Specifies language, difficulty level, topic, and timing options.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CodingSettings {
    private String language;
    private String difficulty;
    private String topic;
    private boolean timedMode;
}
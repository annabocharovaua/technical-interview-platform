package com.voiceassistant.dto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
/**
 * Data Transfer Object for coding settings with filter options.
 * Includes language, difficulty, topic, and additional filter parameters.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CodingSettingsWithFiltersDTO {
    private String language;
    private String difficulty;
    private String category;
    private List<String> topics;
    private Boolean timedMode;
    private Boolean adaptiveMode;
}
package com.voiceassistant.dto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
/**
 * Data Transfer Object representing a coding task category.
 * Provides information about task types and difficulty levels.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaskCategoryDTO {
    private String id;
    private String name;
    private String description;
    private String icon;
    private String type;
}
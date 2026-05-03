package com.voiceassistant.dto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
/**
 * Data Transfer Object for learning resources in API responses.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LearningResourceDTO {
    private Long id;
    private String topicName;
    private String category;
    private String difficultyLevel;
    private String resourceType;
    private String title;
    private String url;
    private String description;
    private String language;
    private Double rating;
    private Integer ratingCount;
    private Boolean isVerified;
}
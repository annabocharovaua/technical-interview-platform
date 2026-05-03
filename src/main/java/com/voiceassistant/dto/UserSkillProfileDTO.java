package com.voiceassistant.dto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
/**
 * Data Transfer Object representing user's overall skill profile.
 * Contains aggregate scores and topic-specific progress information.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSkillProfileDTO {
    private Long id;
    private Double overallScore;
    private Integer totalTasksCompleted;
    private Integer totalTasksAttempted;
    private String preferredLanguage;
    private String preferredDifficulty;
    private String weakestTopic;
    private String strongestTopic;
    private String currentLevel;
    private String recommendedTopic;
    private List<TopicProgressDTO> topicProgress;
}
package com.voiceassistant.dto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
/**
 * Data Transfer Object representing user's progress in a specific coding topic.
 * Contains scores and task completion statistics for a topic.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TopicProgressDTO {
    private String topicName;
    private Double averageScore;
    private Integer tasksCompleted;
    private Integer tasksFailed;
    private String categoryType;
}
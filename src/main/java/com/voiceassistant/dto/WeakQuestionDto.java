package com.voiceassistant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WeakQuestionDto {
    private Long id;
    private String question;
    private String topic;
    private Integer accuracy;
    private String position;
    private String language;
    private Integer attemptCount;
    private String createdAt;

    public static WeakQuestionDto fromEntity(com.voiceassistant.entity.InterviewWeakQuestion entity) {
        return WeakQuestionDto.builder()
                .id(entity.getId())
                .question(entity.getQuestion())
                .topic(entity.getTopic() != null ? entity.getTopic() : "General")
                .accuracy(entity.getInterviewScore() != null ? entity.getInterviewScore() : 0)
                .position(entity.getPosition() != null ? entity.getPosition() : "N/A")
                .language(entity.getLanguage() != null ? entity.getLanguage() : "Unknown")
                .attemptCount(entity.getIncorrectAttempts())
                .createdAt(entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : "")
                .build();
    }
}
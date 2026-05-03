package com.voiceassistant.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Represents a weak interview question (score < 70%).
 */
@Entity
@Table(name = "interview_weak_questions", indexes = {
        @Index(name = "idx_user_id", columnList = "user_id"),
        @Index(name = "idx_correct", columnList = "correct_answer_given"),
        @Index(name = "idx_interview_score", columnList = "interview_score")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewWeakQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 1000)
    private String question;

    @Column(length = 255)
    private String topic;

    @Column(length = 50)
    private String language;

    @Column(length = 255)
    private String position;

    @Column
    private Integer interviewScore;

    @Column(nullable = false)
    private Integer incorrectAttempts = 1;

    @Column
    private LocalDateTime lastAsked;

    @Column(nullable = false)
    private Boolean correctAnswerGiven = false;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}


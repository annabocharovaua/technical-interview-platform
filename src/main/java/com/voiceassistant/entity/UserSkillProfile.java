package com.voiceassistant.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entity representing a user's skill profile and learning progress.
 * Aggregates overall performance metrics, tracks weak/strong topics, and manages recommendations.
 * Maintains one-to-one relationship with User entity.
 *
 * @see User one-to-one relationship with user
 */
@Entity
@Table(name = "user_skill_profiles")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSkillProfile {

    /** Unique identifier for the skill profile */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Associated user (one-to-one relationship) */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    /** Overall performance score across all tasks (0-100) */
    @Column(nullable = false)
    @Builder.Default
    private Double overallScore = 0.0;

    /** Total number of coding tasks completed by user */
    @Column(nullable = false)
    @Builder.Default
    private Integer totalTasksCompleted = 0;

    /** Total number of coding tasks attempted by user */
    @Column(nullable = false)
    @Builder.Default
    private Integer totalTasksAttempted = 0;

    /** User's preferred programming language for recommendations */
    @Column(name = "preferred_language")
    private String preferredLanguage;

    /** User's preferred task difficulty level (EASY, MEDIUM, HARD) */
    @Column(name = "preferred_difficulty")
    private String preferredDifficulty;

    /** Topic with the lowest average score (area needing improvement) */
    @Column(name = "weakest_topic")
    private String weakestTopic;

    /** Topic with the highest average score (user's strength) */
    @Column(name = "strongest_topic")
    private String strongestTopic;

    /** Current proficiency level (BEGINNER, INTERMEDIATE, ADVANCED, EXPERT) */
    @Column(name = "current_level")
    private String currentLevel;

    /** Topic recommended for user's next learning focus */
    @Column(name = "recommended_topic")
    private String recommendedTopic;

    /** Last assessment or evaluation timestamp */
    @Column(name = "last_assessed")
    private LocalDateTime lastAssessed;

    /** Profile creation timestamp */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Last modification timestamp */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * JPA lifecycle callback: sets timestamps when profile is created.
     */
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    /**
     * JPA lifecycle callback: updates modification timestamp before entity is updated.
     */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}


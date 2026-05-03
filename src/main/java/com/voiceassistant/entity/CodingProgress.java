package com.voiceassistant.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entity tracking user's progress on individual coding tasks.
 * Records task completion, scores, code submissions, and AI evaluation feedback.
 * Stores metrics like time spent, hints used, and identified weak topics.
 *
 * @see User many-to-one relationship with user
 * @see CodingTopic related topic information
 */
@Entity
@Table(name = "coding_progress")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CodingProgress {

    /** Unique identifier for the progress record */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** User who solved the task */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Unique identifier of the coding task */
    @Column(nullable = false)
    private String taskId;

    /** Title of the coding task */
    @Column(nullable = false)
    private String taskTitle;

    /** Task description and requirements */
    @Column(columnDefinition = "TEXT")
    private String taskDescription;

    /** Programming language used (e.g., Java, Python, JavaScript) */
    @Column(nullable = false)
    private String language;

    /** Task difficulty level (EASY, MEDIUM, HARD) */
    @Column(nullable = false)
    private String difficulty;

    /** Score achieved on this task (0-100) */
    @Column(nullable = false)
    private Integer score;

    /** User's submitted code solution */
    @Column(columnDefinition = "TEXT")
    private String userCode;

    /** AI-generated evaluation feedback on the submission */
    @Column(columnDefinition = "TEXT")
    private String feedback;

    /** Identified weak topics in JSON format */
    @Column(name = "topics_identified")
    private String topicsIdentified;

    /** Category of the task for analytics */
    @Column(name = "task_category")
    private String taskCategory;

    /** Number of hints used during task solving */
    @Column(name = "hints_used", nullable = false)
    @Builder.Default
    private Integer hintsUsed = 0;

    /** Total time spent on task in seconds */
    @Column(name = "time_spent_seconds")
    private Integer timeSpentSeconds;

    /** Task completion timestamp */
    @Column(name = "completed_at", nullable = false)
    private LocalDateTime completedAt;

    /** Record creation timestamp */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * JPA lifecycle callback: sets timestamps when progress record is created.
     */
    @PrePersist
    protected void onCreate() {
        completedAt = LocalDateTime.now();
        createdAt = LocalDateTime.now();
    }
}


package com.voiceassistant.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entity representing a coding topic and user's proficiency in it.
 * Tracks skill level, task attempts, completion rate, and category for each topic per user.
 * Unique constraint ensures one record per user-topic combination.
 *
 * @see User many-to-one relationship with user
 */
@Entity
@Table(name = "coding_topics", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "topic_name"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CodingTopic {

    /** Unique identifier for the topic record */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** User associated with this topic */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Name of the coding topic (e.g., "Arrays", "Recursion", "Dynamic Programming") */
    @Column(nullable = false)
    private String topicName;

    /** Average score achieved on tasks of this topic (0-100) */
    @Column(nullable = false)
    @Builder.Default
    private Double averageScore = 0.0;

    /** Number of tasks completed successfully for this topic */
    @Column(nullable = false)
    @Builder.Default
    private Integer tasksCompleted = 0;

    /** Number of tasks attempted but not completed for this topic */
    @Column(nullable = false)
    @Builder.Default
    private Integer tasksFailed = 0;

    /** Category type for topic classification */
    @Column(name = "category_type")
    private String categoryType;

    /** Record creation timestamp */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Last modification timestamp */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * JPA lifecycle callback: sets timestamps when topic record is created.
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


package com.voiceassistant.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Entity representing a learning resource (article, video, book, etc.)
 * Used for building a knowledge base of recommended materials.
 */
@Entity
@Table(name = "learning_resources", indexes = {
    @Index(name = "idx_topic_name", columnList = "topic_name"),
    @Index(name = "idx_resource_type", columnList = "resource_type")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LearningResource {

    public enum ResourceType {
        ARTICLE, VIDEO, PDF_BOOK, OFFICIAL_DOCS, COURSE, INTERACTIVE
    }

    public enum DifficultyLevel {
        BEGINNER, INTERMEDIATE, ADVANCED, EXPERT
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String topicName;

    @Column(nullable = false, length = 100)
    private String category;

    @Enumerated(EnumType.STRING)
    private DifficultyLevel difficultyLevel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ResourceType resourceType;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(nullable = false, length = 2000)
    private String url;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 20)
    private String language;

    private Double rating;

    @Column(name = "rating_count")
    private Integer ratingCount = 0;

    @Column(name = "is_verified")
    private Boolean isVerified = false;

    @ManyToOne
    @JoinColumn(name = "created_by")
    private User createdBy;

    @CreationTimestamp
    @Column(nullable = false, updatable = false, columnDefinition = "DATETIME DEFAULT CURRENT_TIMESTAMP")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(columnDefinition = "DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP")
    private LocalDateTime updatedAt;
}


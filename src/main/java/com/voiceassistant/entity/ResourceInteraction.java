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
 * Consolidated entity for all resource interactions.
 *
 * Replaces:
 * - resource_feedback (user ratings and comments)
 * - resource_analytics (user interaction events)
 * - user_recommended_resources (resource recommendations)
 *
 * This consolidation simplifies queries and eliminates redundancy.
 */
@Entity
@Table(name = "resource_interactions", indexes = {
    @Index(name = "idx_resource_id", columnList = "resource_id"),
    @Index(name = "idx_user_id", columnList = "user_id"),
    @Index(name = "idx_interaction_type", columnList = "interaction_type"),
    @Index(name = "idx_created_at", columnList = "created_at"),
    @Index(name = "idx_resource_user", columnList = "resource_id, user_id"),
    @Index(name = "idx_user_resource_type", columnList = "user_id, interaction_type")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResourceInteraction {
    /**
     * Type of interaction with the resource
     */
    public enum InteractionType {
        VIEWED,
        CLICKED,
        RATED,
        SHARED,
        RECOMMENDED
    }
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resource_id", nullable = false)
    private LearningResource resource;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InteractionType interactionType;
    /**
     * User's rating for the resource (1-5 stars)
     * Only populated if interactionType = RATED
     */
    @Column(name = "rating")
    private Integer rating;
    /**
     * User's feedback comment about the resource
     * Only populated if interactionType = RATED
     */
    @Column(columnDefinition = "LONGTEXT")
    private String comment;
    /**
     * Whether user found this resource helpful
     * Only populated if interactionType = RATED
     */
    @Column(name = "is_helpful")
    private Boolean isHelpful;
    /**
     * Additional metadata about the interaction
     * Examples: session_id, source, user_agent, etc.
     * For RECOMMENDED events: contains the recommendation reason
     */
    @Column(columnDefinition = "LONGTEXT")
    private String metadata;
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
    /**
     * Check if this is a feedback/rating interaction
     */
    public boolean isFeedback() {
        return interactionType == InteractionType.RATED;
    }
}
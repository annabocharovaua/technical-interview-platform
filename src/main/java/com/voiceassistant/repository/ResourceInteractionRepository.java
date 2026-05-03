package com.voiceassistant.repository;
import com.voiceassistant.entity.ResourceInteraction;
import com.voiceassistant.entity.LearningResource;
import com.voiceassistant.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
/**
 * Repository for ResourceInteraction entity.
 *
 * Consolidates queries that previously required joins to:
 * - resource_feedback
 * - resource_analytics
 * - user_recommended_resources
 */
@Repository
public interface ResourceInteractionRepository extends JpaRepository<ResourceInteraction, Long> {
    /**
     * Get user's feedback for a specific resource
     */
    Optional<ResourceInteraction> findByResourceAndUserAndInteractionType(
        LearningResource resource,
        User user,
        ResourceInteraction.InteractionType interactionType
    );
    /**
     * Get all feedback for a resource
     */
    List<ResourceInteraction> findByResourceAndInteractionType(
        LearningResource resource,
        ResourceInteraction.InteractionType interactionType
    );
    /**
     * Get all interactions for a resource
     */
    List<ResourceInteraction> findByResource(LearningResource resource);
    /**
     * Get all interactions by a user
     */
    List<ResourceInteraction> findByUser(User user);
    /**
     * Get view count for a resource
     */
    @Query("SELECT COUNT(ri) FROM ResourceInteraction ri " +
           "WHERE ri.resource = :resource AND ri.interactionType = 'VIEWED'")
    long countViewsByResource(@Param("resource") LearningResource resource);
    /**
     * Get average rating for a resource
     */
    @Query("SELECT AVG(ri.rating) FROM ResourceInteraction ri " +
           "WHERE ri.resource = :resource AND ri.interactionType = 'RATED' AND ri.rating IS NOT NULL")
    Optional<Double> getAverageRatingByResource(@Param("resource") LearningResource resource);
    /**
     * Get helpfulness count for a resource
     */
    @Query("SELECT COUNT(ri) FROM ResourceInteraction ri " +
           "WHERE ri.resource = :resource AND ri.interactionType = 'RATED' AND ri.isHelpful = true")
    long countHelpfulRatings(@Param("resource") LearningResource resource);
    /**
     * Get resources recommended to a user
     */
    @Query("SELECT ri.resource FROM ResourceInteraction ri " +
           "WHERE ri.user = :user AND ri.interactionType = 'RECOMMENDED' " +
           "ORDER BY ri.createdAt DESC")
    List<LearningResource> findRecommendedResourcesForUser(@Param("user") User user);
    /**
     * Check if resource was recommended to user
     */
    boolean existsByResourceAndUserAndInteractionType(
        LearningResource resource,
        User user,
        ResourceInteraction.InteractionType interactionType
    );
    /**
     * Get recommendation history for a resource-user pair
     */
    List<ResourceInteraction> findByResourceAndUserAndInteractionTypeOrderByCreatedAtDesc(
        LearningResource resource,
        User user,
        ResourceInteraction.InteractionType interactionType
    );
    /**
     * Get all interactions for a resource in a time range
     */
    @Query("SELECT ri FROM ResourceInteraction ri " +
           "WHERE ri.resource = :resource " +
           "AND ri.createdAt BETWEEN :startDate AND :endDate " +
           "ORDER BY ri.createdAt DESC")
    List<ResourceInteraction> findByResourceAndDateRange(
        @Param("resource") LearningResource resource,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );
    /**
     * Get most viewed resources by topic
     */
    @Query("SELECT ri.resource FROM ResourceInteraction ri " +
           "WHERE ri.interactionType = 'VIEWED' " +
           "AND ri.resource.topicName = :topic " +
           "GROUP BY ri.resource " +
           "ORDER BY COUNT(ri) DESC")
    List<LearningResource> findMostViewedByTopic(@Param("topic") String topic);
    /**
     * Get recently viewed resources by user
     */
    @Query("SELECT ri.resource FROM ResourceInteraction ri " +
           "WHERE ri.user = :user " +
           "AND ri.interactionType = 'VIEWED' " +
           "ORDER BY ri.createdAt DESC")
    List<LearningResource> findRecentlyViewedByUser(
        @Param("user") User user,
        org.springframework.data.domain.Pageable pageable
    );
    /**
     * Get resources with highest engagement
     */
    @Query(value = "SELECT ri.resource_id, COUNT(ri.id) as engagement_count " +
           "FROM resource_interactions ri " +
           "WHERE ri.created_at >= DATE_SUB(NOW(), INTERVAL :days DAY) " +
           "GROUP BY ri.resource_id " +
           "ORDER BY engagement_count DESC",
           nativeQuery = true)
    List<Object[]> findMostEngagedResources(@Param("days") int days);
    /**
     * Get aggregated engagement statistics for a resource in a single query.
     * Replaces 5 individual calls with one optimized query.
     * Returns: [totalViews, averageRating, helpfulCount, totalFeedbackCount, totalInteractions]
     */
    @Query("SELECT " +
           "CAST(COUNT(CASE WHEN ri.interactionType = 'VIEWED' THEN 1 END) AS long) as totalViews, " +
           "COALESCE(AVG(CASE WHEN ri.interactionType = 'RATED' AND ri.rating IS NOT NULL THEN ri.rating ELSE NULL END), 0.0) as avgRating, " +
           "CAST(COUNT(CASE WHEN ri.interactionType = 'RATED' AND ri.isHelpful = true THEN 1 END) AS long) as helpfulCount, " +
           "CAST(COUNT(CASE WHEN ri.interactionType = 'RATED' THEN 1 END) AS long) as totalFeedback, " +
           "CAST(COUNT(ri) AS long) as totalInteractions " +
           "FROM ResourceInteraction ri " +
           "WHERE ri.resource = :resource")
    ResourceStatsProjection getAggregatedStats(@Param("resource") LearningResource resource);
    /**
     * Projection interface for aggregated statistics query result
     */
    interface ResourceStatsProjection {
        long getTotalViews();
        double getAvgRating();
        long getHelpfulCount();
        long getTotalFeedback();
        long getTotalInteractions();
    }
    /**
     * Delete old interactions (for maintenance)
     */
    @Modifying
    @Query("DELETE FROM ResourceInteraction ri WHERE ri.createdAt < :cutoffDate")
    void deleteOlderThan(@Param("cutoffDate") LocalDateTime cutoffDate);
    /**
     * Delete all interactions for a user
     */
    void deleteByUser(User user);
    /**
     * Delete all interactions for a resource
     */
    void deleteByResource(LearningResource resource);
}
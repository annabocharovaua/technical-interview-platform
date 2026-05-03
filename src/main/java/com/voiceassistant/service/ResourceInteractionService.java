package com.voiceassistant.service;
import com.voiceassistant.entity.LearningResource;
import com.voiceassistant.entity.ResourceInteraction;
import com.voiceassistant.entity.User;
import com.voiceassistant.repository.ResourceInteractionRepository;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;
/**
 * Service for managing all lifecycle events of user interactions with learning resources.
 * Consolidates feedback recording, analytics event tracking, recommendation management,
 * engagement statistics, and data maintenance into a single cohesive service.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ResourceInteractionService {
    private final ResourceInteractionRepository interactionRepository;
    /**
     * Records or updates a user's rated feedback for a learning resource.
     * If the user has previously rated the same resource, the existing interaction
     * is updated rather than creating a duplicate entry.
     *
     * @param user       user submitting feedback
     * @param resource   resource being rated
     * @param rating     numeric rating value
     * @param comment    optional text comment
     * @param isHelpful  whether the user found the resource helpful
     * @return persisted or updated interaction entity
     */
    public ResourceInteraction recordFeedback(User user, LearningResource resource,
                                              Integer rating, String comment, Boolean isHelpful) {
        log.info("Recording feedback from user {} for resource {}", user.getId(), resource.getId());
        ResourceInteraction interaction = interactionRepository
                .findByResourceAndUserAndInteractionType(
                        resource, user, ResourceInteraction.InteractionType.RATED)
                .orElseGet(() -> ResourceInteraction.builder()
                        .resource(resource)
                        .user(user)
                        .interactionType(ResourceInteraction.InteractionType.RATED)
                        .build());
        interaction.setRating(rating);
        interaction.setComment(comment);
        interaction.setIsHelpful(isHelpful);
        return interactionRepository.save(interaction);
    }
    /**
     * Retrieves all rated feedback interactions for a resource.
     *
     * @param resource target resource
     * @return list of RATED interactions for the resource
     */
    @Transactional(readOnly = true)
    public List<ResourceInteraction> getResourceFeedback(LearningResource resource) {
        return interactionRepository.findByResourceAndInteractionType(
                resource, ResourceInteraction.InteractionType.RATED);
    }
    /**
     * Calculates the average rating across all rated interactions for a resource.
     *
     * @param resource target resource
     * @return optional average rating, or empty if no ratings exist
     */
    @Transactional(readOnly = true)
    public Optional<Double> getAverageRating(LearningResource resource) {
        return interactionRepository.getAverageRatingByResource(resource);
    }
    /**
     * Counts how many users marked the resource as helpful.
     *
     * @param resource target resource
     * @return count of helpful ratings
     */
    @Transactional(readOnly = true)
    public long getHelpfulnessCount(LearningResource resource) {
        return interactionRepository.countHelpfulRatings(resource);
    }
    /**
     * Records a VIEWED interaction for the given user and resource.
     *
     * @param user     user viewing the resource
     * @param resource resource being viewed
     * @return persisted interaction entity
     */
    public ResourceInteraction recordView(User user, LearningResource resource) {
        return recordEvent(user, resource, ResourceInteraction.InteractionType.VIEWED, null);
    }
    /**
     * Records an arbitrary interaction event with optional metadata.
     *
     * @param user      user performing the action
     * @param resource  resource being interacted with
     * @param eventType type of interaction event
     * @param metadata  optional JSON or string metadata describing the event context
     * @return persisted interaction entity
     */
    public ResourceInteraction recordEvent(User user, LearningResource resource,
                                           ResourceInteraction.InteractionType eventType,
                                           String metadata) {
        log.debug("Recording {} event for user {} on resource {}",
                eventType, user.getId(), resource.getId());
        ResourceInteraction interaction = ResourceInteraction.builder()
                .resource(resource)
                .user(user)
                .interactionType(eventType)
                .metadata(metadata)
                .build();
        return interactionRepository.save(interaction);
    }
    /**
     * Returns the total number of VIEWED interactions for a resource.
     *
     * @param resource target resource
     * @return view count
     */
    @Transactional(readOnly = true)
    public long getViewCount(LearningResource resource) {
        return interactionRepository.countViewsByResource(resource);
    }
    /**
     * Returns all interactions associated with a specific resource.
     *
     * @param resource target resource
     * @return list of all interactions for the resource
     */
    @Transactional(readOnly = true)
    public List<ResourceInteraction> getResourceInteractions(LearningResource resource) {
        return interactionRepository.findByResource(resource);
    }
    /**
     * Returns the most viewed resources within a given topic.
     *
     * @param topic topic name to filter by
     * @return list of resources ordered by view count descending
     */
    @Transactional(readOnly = true)
    public List<LearningResource> getMostViewedByTopic(String topic) {
        return interactionRepository.findMostViewedByTopic(topic);
    }
    /**
     * Returns aggregated engagement data for the most active resources
     * over the last N days.
     *
     * @param days number of days to look back
     * @return list of object arrays containing resource and interaction count columns
     */
    @Transactional(readOnly = true)
    public List<Object[]> getMostEngagedResources(int days) {
        return interactionRepository.findMostEngagedResources(days);
    }
    /**
     * Computes a composite engagement statistics snapshot for a resource
     * by aggregating view counts, ratings, helpfulness, and interaction totals.
     * Uses a single optimized query instead of 5 separate database calls.
     *
     * @param resource target resource
     * @return populated {@link ResourceEngagementStats} for the resource
     */
    @Transactional(readOnly = true)
    public ResourceEngagementStats getResourceStats(LearningResource resource) {
        ResourceInteractionRepository.ResourceStatsProjection stats =
            interactionRepository.getAggregatedStats(resource);
        return ResourceEngagementStats.builder()
                .resourceId(resource.getId())
                .resourceTitle(resource.getTitle())
                .totalViews(stats.getTotalViews())
                .averageRating(stats.getAvgRating())
                .helpfulCount(stats.getHelpfulCount())
                .totalFeedback((int) stats.getTotalFeedback())
                .totalInteractions((int) stats.getTotalInteractions())
                .build();
    }
    /**
     * Immutable snapshot of engagement metrics for a single learning resource.
     * The {@link #getEngagementScore()} method derives a composite score from
     * views, average rating, and the proportion of helpful ratings.
     */
    @Data
    @Builder
    public static class ResourceEngagementStats {
        private Long   resourceId;
        private String resourceTitle;
        private long   totalViews;
        private double averageRating;
        private long   helpfulCount;
        private int    totalFeedback;
        private int    totalInteractions;
        /**
         * Calculates a composite engagement score using the formula:
         * {@code views × (rating / 5) × helpfulRatio}.
         * Falls back to {@code views × 0.5} when no feedback has been submitted.
         *
         * @return non-negative engagement score
         */
        public double getEngagementScore() {
            if (totalFeedback == 0) {
                return totalViews * 0.5;
            }
            double helpfulRatio = (double) helpfulCount / totalFeedback;
            return totalViews * (averageRating / 5.0) * helpfulRatio;
        }
    }
}
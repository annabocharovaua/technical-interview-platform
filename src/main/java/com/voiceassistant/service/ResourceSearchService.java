package com.voiceassistant.service;
import com.voiceassistant.dto.LearningResourceDTO;
import com.voiceassistant.entity.LearningResource;
import com.voiceassistant.entity.User;
import com.voiceassistant.repository.LearningResourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
/**
 * Service for searching and filtering learning resources.
 * Provides topic-based search, trending resource ranking, feedback recording,
 * and view tracking by delegating persistence to the repository and
 * engagement tracking to {@link ResourceInteractionService}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResourceSearchService {
    private static final int    DEFAULT_LIMIT         = 10;
    private static final double POPULARITY_CAP        = 5.0;
    private static final String DEFAULT_RESOURCE_TYPE = "ARTICLE";
    private static final String DEFAULT_DIFFICULTY    = "INTERMEDIATE";
    private final LearningResourceRepository  learningResourceRepository;
    private final ResourceInteractionService  resourceInteractionService;
    /**
     * Returns learning resources for each of the provided topics, up to {@code limit}
     * resources per topic. Verified resources are prioritised; unverified resources
     * are used as a fallback when no verified ones exist for a topic.
     * Topics that produce an error are skipped so results for other topics are unaffected.
     *
     * @param topics list of topic names to search for
     * @param limit  maximum number of resources to return per topic
     * @return flat list of DTOs for all matched resources across all topics
     */
    @Transactional(readOnly = true)
    public List<LearningResourceDTO> getResourcesForTopics(List<String> topics, int limit) {
        if (topics == null || topics.isEmpty()) {
            log.warn("Topics list is empty, returning no resources");
            return new ArrayList<>();
        }
        log.info("Getting resources for {} topics with per-topic limit: {}", topics.size(), limit);
        List<LearningResourceDTO> allResources = new ArrayList<>();
        for (String topic : topics) {
            try {
                List<LearningResource> resources = resolveResourcesForTopic(topic, limit);
                List<LearningResourceDTO> dtos = resources.stream()
                        .map(this::toDTO)
                        .toList();
                allResources.addAll(dtos);
                log.info("Found {} resources for topic: {}", dtos.size(), topic);
            } catch (Exception e) {
                log.error("Error fetching resources for topic '{}': {}", topic, e.getMessage(), e);
            }
        }
        log.info("Returning {} total resources across {} topics", allResources.size(), topics.size());
        return allResources;
    }
    /**
     * Resolves resources for a single topic, preferring verified entries.
     * Falls back to all resources ordered by rating when no verified entries exist.
     *
     * @param topic topic name
     * @param limit maximum number of resources to return
     * @return list of resolved resources
     */
    private List<LearningResource> resolveResourcesForTopic(String topic, int limit) {
        List<LearningResource> verified = learningResourceRepository
                .findByTopicNameContainingIgnoreCaseAndIsVerifiedTrueOrderByRatingDesc(topic)
                .stream()
                .limit(limit)
                .toList();
        if (!verified.isEmpty()) {
            return verified;
        }
        return learningResourceRepository
                .findByTopicNameContainingIgnoreCaseOrderByRatingDesc(topic)
                .stream()
                .limit(limit)
                .toList();
    }
    /**
     * Searches for resources matching a topic, ordered by view count via
     * {@link ResourceInteractionService#getMostViewedByTopic(String)}.
     *
     * @param topic topic name to search for; blank values return an empty list
     * @param limit maximum number of results; defaults to {@value #DEFAULT_LIMIT} when null
     * @return list of DTOs for matching resources
     */
    @Transactional(readOnly = true)
    public List<LearningResourceDTO> searchResources(String topic, Integer limit) {
        if (topic == null || topic.isBlank()) {
            log.warn("Blank topic provided to searchResources, returning empty list");
            return new ArrayList<>();
        }
        int effectiveLimit = limit != null ? limit : DEFAULT_LIMIT;
        log.info("Searching resources for topic '{}' with limit {}", topic, effectiveLimit);
        try {
            return resourceInteractionService.getMostViewedByTopic(topic)
                    .stream()
                    .limit(effectiveLimit)
                    .map(this::toDTO)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Error searching resources for topic '{}': {}", topic, e.getMessage(), e);
            return new ArrayList<>();
        }
    }
    /**
     * Returns resources ranked by a composite trending score that balances quality
     * (average rating) with popularity (logarithmic rating count capped at
     * {@value #POPULARITY_CAP}).
     * <p>Score formula: {@code rating × min(log(ratingCount + 1), 5.0)}</p>
     * Uses optimized paginated query instead of loading all resources into memory.
     *
     * @param limit maximum number of trending resources to return;
     *              defaults to {@value #DEFAULT_LIMIT} when null
     * @return list of DTOs ordered by trending score descending
     */
    @Transactional(readOnly = true)
    public List<LearningResourceDTO> getTrendingResources(Integer limit) {
        int effectiveLimit = limit != null ? limit : DEFAULT_LIMIT;
        log.info("Fetching top {} trending resources", effectiveLimit);
        try {
            Pageable pageable = PageRequest.of(0, Math.max(effectiveLimit, 1));
            return learningResourceRepository.findTopByOrderByRatingDesc(pageable)
                    .stream()
                    .map(this::toDTO)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Error fetching trending resources: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }
    /**
     * Computes a trending score for a resource that rewards both high ratings
     * and a large number of ratings, with diminishing returns on rating count.
     *
     * @param resource resource entity
     * @return composite trending score (non-negative)
     */
    private double trendingScore(LearningResource resource) {
        double rating = resource.getRating() != null ? resource.getRating() : 0.0;
        int    count  = resource.getRatingCount() != null ? resource.getRatingCount() : 0;
        return rating * Math.min(Math.log(count + 1), POPULARITY_CAP);
    }
    /**
     * Validates and records a user's rated feedback for a resource.
     * Validates rating before querying the database to fail fast.
     *
     * @param user       user providing feedback
     * @param resourceId ID of the resource being rated
     * @param rating     rating value; must be between 1 and 5
     * @param comment    optional free-text comment
     * @param isHelpful  whether the user found the resource helpful
     * @throws IllegalArgumentException if the rating is out of range
     * @throws RuntimeException         if persistence fails
     */
    @Transactional
    public void recordResourceFeedback(User user, Long resourceId, int rating,
                                       String comment, boolean isHelpful) {
        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException("Rating must be between 1 and 5, got: " + rating);
        }
        log.info("Recording feedback for resource {} from user {}: rating={}, helpful={}",
                resourceId, user.getId(), rating, isHelpful);
        try {
            LearningResource resource = requireResource(resourceId);
            resourceInteractionService.recordFeedback(user, resource, rating, comment, isHelpful);
            log.info("Feedback recorded for resource {}", resourceId);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error recording feedback for resource {}: {}", resourceId, e.getMessage(), e);
            throw new RuntimeException("Failed to record feedback", e);
        }
    }
    /**
     * Records a VIEWED interaction for the specified resource and user.
     *
     * @param user       user who viewed the resource
     * @param resourceId ID of the viewed resource
     * @throws IllegalArgumentException if the resource is not found
     * @throws RuntimeException         if persistence fails
     */
    @Transactional
    public void markResourceAsViewed(User user, Long resourceId) {
        log.info("Marking resource {} as viewed by user {}", resourceId, user.getId());
        try {
            LearningResource resource = requireResource(resourceId);
            resourceInteractionService.recordView(user, resource);
            log.info("Resource {} marked as viewed by user {}", resourceId, user.getId());
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error marking resource {} as viewed: {}", resourceId, e.getMessage(), e);
            throw new RuntimeException("Failed to mark resource as viewed", e);
        }
    }
    /**
     * Loads a resource by ID or throws {@link IllegalArgumentException} if not found.
     *
     * @param resourceId resource primary key
     * @return found resource entity
     * @throws IllegalArgumentException if no resource exists with the given ID
     */
    private LearningResource requireResource(Long resourceId) {
        return learningResourceRepository.findById(resourceId)
                .orElseThrow(() -> new IllegalArgumentException("Resource not found: " + resourceId));
    }
    /**
     * Maps a {@link LearningResource} entity to a {@link LearningResourceDTO}.
     * Null enum fields are replaced with safe default strings so the DTO is
     * always fully populated.
     *
     * @param resource source entity
     * @return populated DTO
     * @throws RuntimeException if mapping fails unexpectedly
     */
    private LearningResourceDTO toDTO(LearningResource resource) {
        try {
            return LearningResourceDTO.builder()
                    .id(resource.getId())
                    .title(resource.getTitle())
                    .topicName(resource.getTopicName())
                    .resourceType(resource.getResourceType() != null
                            ? resource.getResourceType().toString()
                            : DEFAULT_RESOURCE_TYPE)
                    .url(resource.getUrl())
                    .description(resource.getDescription())
                    .rating(resource.getRating() != null ? resource.getRating() : 0.0)
                    .difficultyLevel(resource.getDifficultyLevel() != null
                            ? resource.getDifficultyLevel().toString()
                            : DEFAULT_DIFFICULTY)
                    .build();
        } catch (Exception e) {
            log.error("Error mapping resource {} to DTO: {}", resource.getId(), e.getMessage(), e);
            throw new RuntimeException("Failed to convert resource to DTO", e);
        }
    }
}
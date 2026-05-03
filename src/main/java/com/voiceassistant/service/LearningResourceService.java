package com.voiceassistant.service;
import com.voiceassistant.entity.LearningResource;
import com.voiceassistant.entity.User;
import com.voiceassistant.repository.LearningResourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
/**
 * Service for managing learning resources and user interactions.
 * Handles resource creation, rating, searching, filtering, and recommendation.
 * Tracks user interactions and maintains resource quality metrics.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LearningResourceService {
    private static final double DEFAULT_INITIAL_RATING = 4.5;
    private static final double MINIMUM_DISPLAY_RATING = 3.5;
    private final LearningResourceRepository learningResourceRepository;

    /**
     * Rates a learning resource submitted by user.
     * Creates new resource if resourceId is null, or rates existing resource.
     * Updates running average rating across all user ratings.
     *
     * @param resourceId resource ID (null/0 to create new), or existing resource ID to rate
     * @param rating     rating value from 1-5
     * @param title      resource title (required for new resources)
     * @param url        resource URL (required for new resources and deduplication)
     * @param topic      topic name (required for new resources)
     * @param user       authenticated user submitting the rating
     * @return map with updated rating statistics and resource info
     * @throws IllegalArgumentException if rating out of range or required fields missing
     */
    @Transactional
    public Map<String, Object> rateResource(Long resourceId, int rating, String title,
                                            String url, String topic, User user) {
        if (user == null) {
            throw new IllegalArgumentException("User is required to rate resources");
        }
        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException("Rating must be between 1 and 5");
        }
        log.info("User {} rating resource {} with {} stars", user.getId(), resourceId, rating);
        if (resourceId == null || resourceId == 0) {
            return handleNewResourceRating(rating, title, url, topic, user);
        }
        return handleExistingResourceRating(resourceId, rating, user);
    }
    /**
     * Handles rating submission for a new or URL-deduplicated resource.
     *
     * @param rating rating value
     * @param title  resource title
     * @param url    resource URL
     * @param topic  topic name
     * @param user   authenticated user
     * @return map with rating statistics for the created or existing resource
     * @throws IllegalArgumentException if title, url, or topic are missing
     */
    private Map<String, Object> handleNewResourceRating(int rating, String title,
                                                        String url, String topic, User user) {
        if (title == null || url == null || topic == null) {
            throw new IllegalArgumentException("For new resources, title, url, and topic are required");
        }
        var existing = learningResourceRepository.findByUrl(url);
        if (existing.isPresent()) {
            log.info("Resource with URL already exists: {}", existing.get().getId());
            return handleExistingResourceRating(existing.get().getId(), rating, user);
        }
        LearningResource resource = LearningResource.builder()
                .topicName(topic)
                .category("OpenAI")
                .title(title)
                .url(url)
                .resourceType(LearningResource.ResourceType.OFFICIAL_DOCS)
                .language("English")
                .difficultyLevel(LearningResource.DifficultyLevel.INTERMEDIATE)
                .createdBy(user)
                .isVerified(false)
                .rating((double) rating)
                .ratingCount(1)
                .build();
        LearningResource saved = learningResourceRepository.save(resource);
        log.info("Created new resource: {} (ID: {}) by user {}", title, saved.getId(), user.getId());
        return Map.of(
                "success", true,
                "message", "Resource created and rated",
                "resourceId", saved.getId(),
                "rating", rating,
                "averageRating", (double) rating,
                "ratingCount", 1
        );
    }
    /**
     * Handles rating submission for an existing resource by recalculating its average.
     *
     * @param resourceId resource ID
     * @param rating     rating value
     * @param user       authenticated user
     * @return map with updated rating statistics
     * @throws IllegalArgumentException if resource is not found
     */
    private Map<String, Object> handleExistingResourceRating(Long resourceId, int rating, User user) {
        LearningResource resource = learningResourceRepository.findById(resourceId)
                .orElseThrow(() -> new IllegalArgumentException("Resource not found: " + resourceId));
        double currentRating = resource.getRating() != null ? resource.getRating() : 0;
        int currentCount = resource.getRatingCount() != null ? resource.getRatingCount() : 0;
        double newRating = (currentRating * currentCount + rating) / (currentCount + 1);
        resource.setRating(newRating);
        resource.setRatingCount(currentCount + 1);
        learningResourceRepository.save(resource);
        log.info("Resource {} rated by user {}. New average: {} ({} ratings)",
                resourceId, user.getId(), String.format("%.2f", newRating), currentCount + 1);
        return Map.of(
                "success", true,
                "message", "Resource rated successfully",
                "resourceId", resourceId,
                "rating", rating,
                "averageRating", newRating,
                "ratingCount", currentCount + 1
        );
    }
    /**
     * Persists learning resources extracted from AI-generated interview feedback.
     * Skips resources that already exist by URL and enriches each link map
     * with the saved or existing resource ID.
     *
     * @param feedback map containing a "resources" key with resource groups from feedback
     * @param position job position used as the resource category
     * @param user     authenticated user who triggered feedback generation
     */
    @Transactional
    public void saveRecommendedResources(Map<String, Object> feedback, String position, User user) {
        if (feedback == null) {
            log.warn("Feedback is null, skipping resource saving");
            return;
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> resources = (List<Map<String, Object>>) feedback.get("resources");
        if (resources == null || resources.isEmpty()) {
            log.info("No resources in feedback to save");
            return;
        }
        log.info("Saving {} resource groups for position: {}", resources.size(), position);
        for (Map<String, Object> resourceGroup : resources) {
            String topic = (String) resourceGroup.get("topic");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> links = (List<Map<String, Object>>) resourceGroup.get("links");
            if (links == null || links.isEmpty()) {
                continue;
            }
            for (Map<String, Object> link : links) {
                saveResourceLink(link, topic, position, user);
            }
        }
    }
    /**
     * Saves a single resource link from feedback if it does not already exist.
     * Attaches the persisted resource ID back to the link map.
     *
     * @param link     map containing "title" and "url" keys
     * @param topic    topic name for the resource
     * @param position job position used as category
     * @param user     resource creator
     */
    private void saveResourceLink(Map<String, Object> link, String topic, String position, User user) {
        try {
            String title = (String) link.get("title");
            String url = (String) link.get("url");
            if (title == null || url == null) {
                return;
            }
            var existing = learningResourceRepository.findByUrl(url);
            if (existing.isPresent()) {
                link.put("resourceId", existing.get().getId());
                return;
            }
            LearningResource resource = LearningResource.builder()
                    .topicName(topic)
                    .category(position != null ? position : "General")
                    .title(title)
                    .url(url)
                    .resourceType(LearningResource.ResourceType.OFFICIAL_DOCS)
                    .language("English")
                    .difficultyLevel(LearningResource.DifficultyLevel.INTERMEDIATE)
                    .createdBy(user)
                    .isVerified(false)
                    .rating(DEFAULT_INITIAL_RATING)
                    .ratingCount(0)
                    .build();
            LearningResource saved = learningResourceRepository.save(resource);
            link.put("resourceId", saved.getId());
            log.info("Saved resource: {} (ID: {}) for topic: {}", title, saved.getId(), topic);
        } catch (Exception e) {
            log.error("Error saving resource link: {}", e.getMessage(), e);
        }
    }
    /**
     * Returns learning resources matching the given topic with a rating above the minimum threshold.
     *
     * @param topic topic name to filter by, case-insensitive substring match
     * @return list of resource maps with rating and metadata fields
     */
    public List<Map<String, Object>> getResourcesByTopic(String topic) {
        if (topic == null || topic.isBlank()) {
            return new ArrayList<>();
        }
        log.info("Getting high-rated resources for topic: {}", topic);
        List<LearningResource> allResources = learningResourceRepository.findAll();
        List<Map<String, Object>> result = new ArrayList<>();
        for (LearningResource resource : allResources) {
            if (resource.getTopicName() == null
                    || !resource.getTopicName().toLowerCase().contains(topic.toLowerCase())) {
                continue;
            }
            double rating = resource.getRating() != null ? resource.getRating() : DEFAULT_INITIAL_RATING;
            if (rating < MINIMUM_DISPLAY_RATING) {
                continue;
            }
            Map<String, Object> resourceMap = new LinkedHashMap<>();
            resourceMap.put("id", resource.getId());
            resourceMap.put("title", resource.getTitle());
            resourceMap.put("url", resource.getUrl());
            resourceMap.put("topic", resource.getTopicName());
            resourceMap.put("resourceType", resource.getResourceType());
            resourceMap.put("difficultyLevel", resource.getDifficultyLevel());
            resourceMap.put("language", resource.getLanguage());
            resourceMap.put("rating", String.format("%.2f", rating));
            resourceMap.put("ratingCount", resource.getRatingCount() != null ? resource.getRatingCount() : 0);
            result.add(resourceMap);
        }
        log.info("Found {} high-rated resources for topic: {}", result.size(), topic);
        return result;
    }
}
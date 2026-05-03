package com.voiceassistant.service;
import com.voiceassistant.entity.LearningResource;
import com.voiceassistant.repository.LearningResourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.*;
/**
 * Service for analytics, metrics, and statistical calculations.
 * Provides analytics on resource popularity, engagement, user interactions, and topic metrics.
 * Calculates top resources, topic analytics, and detailed engagement statistics.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsService {
    private final LearningResourceRepository learningResourceRepository;
    private final ResourceInteractionService resourceInteractionService;

    /**
     * Retrieves top viewed resources sorted by view count in descending order.
     *
     * @param limit maximum number of resources to return (default 10)
     * @return list of resource metrics containing views, rating, title, etc.
     */
    public List<Map<String, Object>> getTopViewedResources(Integer limit) {
        try {
            List<LearningResource> allResources = learningResourceRepository.findAll();
            return allResources.stream()
                    .sorted((a, b) -> {
                        long viewsA = resourceInteractionService.getViewCount(a);
                        long viewsB = resourceInteractionService.getViewCount(b);
                        return Long.compare(viewsB, viewsA);
                    })
                    .limit(limit != null ? limit : 10)
                    .map(resource -> {
                        Map<String, Object> metrics = new LinkedHashMap<>();
                        metrics.put("id", resource.getId());
                        metrics.put("title", resource.getTitle());
                        metrics.put("topic", resource.getTopicName());
                        metrics.put("views", resourceInteractionService.getViewCount(resource));
                        metrics.put("rating", resourceInteractionService.getAverageRating(resource).orElse(0.0));
                        return metrics;
                    })
                    .toList();
        } catch (Exception e) {
            log.error("Error getting top viewed resources: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to get top viewed resources", e);
        }
    }
    /**
     * Retrieves comprehensive metrics for a specific resource.
     * Includes views, ratings, engagement score, and interaction statistics.
     *
     * @param resourceId unique resource identifier
     * @return map containing detailed resource metrics
     * @throws IllegalArgumentException if resource not found
     */
    public Map<String, Object> getResourceMetrics(Long resourceId) {
        try {
            var resource = learningResourceRepository.findById(resourceId)
                    .orElseThrow(() -> new IllegalArgumentException("Resource not found: " + resourceId));
            var stats = resourceInteractionService.getResourceStats(resource);
            Map<String, Object> metrics = new LinkedHashMap<>();
            metrics.put("resourceId", resourceId);
            metrics.put("title", stats.getResourceTitle());
            metrics.put("totalViews", stats.getTotalViews());
            metrics.put("averageRating", stats.getAverageRating());
            metrics.put("helpfulCount", stats.getHelpfulCount());
            metrics.put("totalFeedback", stats.getTotalFeedback());
            metrics.put("totalInteractions", stats.getTotalInteractions());
            metrics.put("engagementScore", stats.getEngagementScore());
            return metrics;
        } catch (IllegalArgumentException e) {
            log.error("Validation error: {}", e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            log.error("Error getting resource metrics: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to get resource metrics", e);
        }
    }
    /**
     * Returns analytics for a specific topic including view count and average rating.
     *
     * @param topic topic name
     * @return map with topic analytics
     * @throws IllegalArgumentException if topic is blank
     */
    public Map<String, Object> getTopicAnalytics(String topic) {
        try {
            if (topic == null || topic.isBlank()) {
                throw new IllegalArgumentException("Topic cannot be blank");
            }
            var resources = learningResourceRepository
                    .findByTopicNameContainingIgnoreCaseOrderByRatingDesc(topic);
            if (resources.isEmpty()) {
                return Map.of(
                        "topic", topic,
                        "resourceCount", 0,
                        "totalViews", 0,
                        "averageRating", 0.0
                );
            }
            int totalViews = 0;
            double avgRating = 0;
            int resourceCount = 0;
            for (var resource : resources) {
                totalViews += resourceInteractionService.getViewCount(resource);
                var rating = resourceInteractionService.getAverageRating(resource);
                if (rating.isPresent()) {
                    avgRating += rating.get();
                    resourceCount++;
                }
            }
            if (resourceCount > 0) {
                avgRating /= resourceCount;
            }
            Map<String, Object> topicMetrics = new LinkedHashMap<>();
            topicMetrics.put("topic", topic);
            topicMetrics.put("resourceCount", resources.size());
            topicMetrics.put("totalViews", totalViews);
            topicMetrics.put("averageRating", String.format("%.2f", avgRating));
            return topicMetrics;
        } catch (IllegalArgumentException e) {
            log.error("Validation error: {}", e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            log.error("Error getting topic analytics: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to get topic analytics", e);
        }
    }
    /**
     * Returns most discussed resources sorted by engagement count descending.
     *
     * @param limit maximum number of resources to return
     * @return list of engaged resource maps
     */
    public List<Map<String, Object>> getMostDiscussedResources(Integer limit) {
        try {
            List<Object[]> mostEngaged = resourceInteractionService.getMostEngagedResources(30);
            if (mostEngaged.isEmpty()) {
                return new ArrayList<>();
            }
            return mostEngaged.stream()
                    .limit(limit != null ? limit : 10)
                    .map(objArray -> {
                        Map<String, Object> item = new LinkedHashMap<>();
                        try {
                            Number resourceId = (Number) objArray[0];
                            Number engagement = objArray.length > 1 ? (Number) objArray[1] : 0;
                            var resourceOpt = learningResourceRepository.findById(resourceId.longValue());
                            if (resourceOpt.isPresent()) {
                                LearningResource resource = resourceOpt.get();
                                item.put("id", resource.getId());
                                item.put("title", resource.getTitle());
                                item.put("topic", resource.getTopicName());
                                item.put("engagement", engagement);
                            } else {
                                log.warn("Resource not found for id: {}", resourceId);
                                item.put("id", resourceId.longValue());
                                item.put("title", "Unknown Resource");
                                item.put("topic", "Unknown");
                                item.put("engagement", engagement);
                            }
                        } catch (Exception e) {
                            log.error("Error processing engaged resource: {}", e.getMessage(), e);
                        }
                        return item;
                    })
                    .filter(item -> !item.isEmpty())
                    .toList();
        } catch (Exception e) {
            log.error("Error getting most discussed resources: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to get discussed resources", e);
        }
    }
    /**
     * Returns trending topics sorted by engagement score descending.
     * Score is calculated as views multiplied by log of rating plus one.
     *
     * @param limit maximum number of topics to return
     * @return list of trending topic names
     */
    public List<String> getTrendingTopics(Integer limit) {
        try {
            var allResources = learningResourceRepository.findAll();
            if (allResources.isEmpty()) {
                return new ArrayList<>();
            }
            Map<String, Double> topicScores = new HashMap<>();
            for (LearningResource resource : allResources) {
                String topic = resource.getTopicName();
                if (topic != null && !topic.isBlank()) {
                    long views = resourceInteractionService.getViewCount(resource);
                    double rating = resourceInteractionService.getAverageRating(resource).orElse(0.0);
                    double score = views * Math.log(rating + 1);
                    topicScores.put(topic, topicScores.getOrDefault(topic, 0.0) + score);
                }
            }
            return topicScores.entrySet().stream()
                    .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                    .limit(limit != null ? limit : 5)
                    .map(Map.Entry::getKey)
                    .toList();
        } catch (Exception e) {
            log.error("Error getting trending topics: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to get trending topics", e);
        }
    }
}
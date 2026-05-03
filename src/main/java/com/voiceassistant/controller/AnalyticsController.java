package com.voiceassistant.controller;
import com.voiceassistant.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
/**
 * REST controller for resource analytics and engagement metrics.
 * Provides endpoints for tracking resource popularity, engagement, and user interactions.
 * Enables analytics dashboards and performance monitoring.
 *
 * @see AnalyticsService analytics calculation and aggregation
 */
@Slf4j
@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {
    private final AnalyticsService analyticsService;

    /**
     * Retrieves top viewed learning resources sorted by view count.
     *
     * @param limit maximum number of resources to return (default 10)
     * @return ResponseEntity with list of top resources, their views, ratings, and count
     */
    @GetMapping("/top-viewed")
    public ResponseEntity<Map<String, Object>> getTopViewedResources(
            @RequestParam(defaultValue = "10") Integer limit) {
        try {
            log.info("Getting top {} viewed resources", limit);
            List<Map<String, Object>> topResources = analyticsService.getTopViewedResources(limit);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "resources", topResources,
                    "count", topResources.size()
            ));
        } catch (Exception e) {
            log.error("Error getting top viewed resources: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                    "error", true,
                    "message", "Failed to get top viewed resources: " + e.getMessage()
            ));
        }
    }
    /**
     * Retrieves comprehensive analytics metrics for a specific resource.
     * Includes views, ratings, engagement score, and interaction statistics.
     *
     * @param resourceId unique resource identifier
     * @return ResponseEntity with detailed resource metrics,
     *         or HTTP 400 if resource not found, HTTP 500 on error
     */
    @GetMapping("/resource/{resourceId}/metrics")
    public ResponseEntity<Map<String, Object>> getResourceMetrics(
            @PathVariable Long resourceId) {
        try {
            log.info("Getting metrics for resource: {}", resourceId);
            Map<String, Object> metrics = analyticsService.getResourceMetrics(resourceId);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "metrics", metrics
            ));
        } catch (IllegalArgumentException e) {
            log.error("Validation error: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of(
                    "error", true,
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("Error getting resource metrics: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                    "error", true,
                    "message", "Failed to get resource metrics: " + e.getMessage()
            ));
        }
    }
    /**
     * Retrieves analytics for a specific topic including resource count and engagement.
     * Aggregates metrics across all resources for the given topic.
     *
     * @param name topic name to get analytics for
     * @return ResponseEntity with topic analytics (resources count, views, rating),
     *         or HTTP 400 if topic is empty, HTTP 500 on error
     */
    @GetMapping("/topic/{name}")
    public ResponseEntity<Map<String, Object>> getTopicAnalytics(
            @PathVariable String name) {
        try {
            log.info("Getting analytics for topic: {}", name);
            Map<String, Object> topicMetrics = analyticsService.getTopicAnalytics(name);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "analytics", topicMetrics
            ));
        } catch (IllegalArgumentException e) {
            log.error("Validation error: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of(
                    "error", true,
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("Error getting topic analytics: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                    "error", true,
                    "message", "Failed to get topic analytics: " + e.getMessage()
            ));
        }
    }
    /**
     * Retrieves most discussed resources sorted by user engagement level.
     * Based on views, ratings, and discussion activity.
     *
     * @param limit maximum number of resources to return (default 10)
     * @return ResponseEntity with list of most discussed resources and count
     */
    @GetMapping("/most-discussed")
    public ResponseEntity<Map<String, Object>> getMostDiscussedResources(
            @RequestParam(defaultValue = "10") Integer limit) {
        try {
            log.info("Getting {} most discussed resources", limit);
            List<Map<String, Object>> discussed = analyticsService.getMostDiscussedResources(limit);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "resources", discussed,
                    "count", discussed.size()
            ));
        } catch (Exception e) {
            log.error("Error getting most discussed resources: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                    "error", true,
                    "message", "Failed to get discussed resources: " + e.getMessage()
            ));
        }
    }
    /**
     * Returns trending topics.
     *
     * @param limit maximum number of topics to return, defaults to 5
     * @return list of trending topic names with count
     */
    @GetMapping("/trending-topics")
    public ResponseEntity<Map<String, Object>> getTrendingTopics(
            @RequestParam(defaultValue = "5") Integer limit) {
        try {
            log.info("Getting {} trending topics", limit);
            List<String> trendingTopics = analyticsService.getTrendingTopics(limit);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "topics", trendingTopics,
                    "count", trendingTopics.size()
            ));
        } catch (Exception e) {
            log.error("Error getting trending topics: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                    "error", true,
                    "message", "Failed to get trending topics: " + e.getMessage()
            ));
        }
    }
}
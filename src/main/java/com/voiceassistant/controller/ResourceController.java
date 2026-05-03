package com.voiceassistant.controller;
import com.voiceassistant.dto.LearningResourceDTO;
import com.voiceassistant.dto.ResourceRatingRequest;
import com.voiceassistant.entity.User;
import com.voiceassistant.service.ResourceSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
/**
 * REST controller for managing learning resources.
 * Provides endpoints for searching, rating, and retrieving learning materials.
 * Supports resource discovery for specific topics and user contributions.
 *
 * @see ResourceSearchService resource search and management logic
 */
@Slf4j
@RestController
@RequestMapping("/api/resources")
@RequiredArgsConstructor
public class ResourceController {
    private final ResourceSearchService resourceSearchService;

    /**
     * Retrieves learning resources for specified interview topics.
     * Prioritizes highly-rated and verified resources.
     *
     * @param topics list of topic names to search for
     * @param limit maximum number of resources to return (default 5)
     * @return ResponseEntity with list of LearningResourceDTOs and count,
     *         or HTTP 400 if topics list is empty
     */
    @GetMapping("/for-topics")
    public ResponseEntity<Map<String, Object>> getResourcesForTopics(
            @RequestParam List<String> topics,
            @RequestParam(defaultValue = "5") Integer limit) {
        try {
            if (topics == null || topics.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", true,
                        "message", "At least one topic is required"
                ));
            }
            log.info("Getting resources for {} topics", topics.size());
            List<LearningResourceDTO> resources =
                    resourceSearchService.getResourcesForTopics(topics, limit);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "topics", topics,
                    "resources", resources,
                    "count", resources.size()
            ));
        } catch (Exception e) {
            log.error("Error getting resources for topics: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                    "error", true,
                    "message", "Failed to get resources: " + e.getMessage()
            ));
        }
    }
    /**
     * Searches learning resources by topic name using full-text search.
     * Returns top-rated resources matching the search query.
     *
     * @param topic topic name or search query
     * @param limit maximum number of resources to return (default 10)
     * @return ResponseEntity with list of matching resources and count,
     *         or HTTP 400 if topic is empty
     */
    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> searchResources(
            @RequestParam String topic,
            @RequestParam(defaultValue = "10") Integer limit) {
        try {
            if (topic == null || topic.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", true,
                        "message", "Topic is required"
                ));
            }
            log.info("Searching resources for topic: {}", topic);
            List<LearningResourceDTO> resources =
                    resourceSearchService.searchResources(topic, limit);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "topic", topic,
                    "resources", resources,
                    "count", resources.size()
            ));
        } catch (Exception e) {
            log.error("Error searching resources: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                    "error", true,
                    "message", "Failed to search resources: " + e.getMessage()
            ));
        }
    }
    /**
     * Returns trending resources based on engagement metrics.
     *
     * @param limit maximum number of resources to return, defaults to 10
     * @return list of trending resources with count or error response
     */
    @GetMapping("/trending")
    public ResponseEntity<Map<String, Object>> getTrendingResources(
            @RequestParam(defaultValue = "10") Integer limit) {
        try {
            List<LearningResourceDTO> resources =
                    resourceSearchService.getTrendingResources(limit);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "resources", resources,
                    "count", resources.size()
            ));
        } catch (Exception e) {
            log.error("Error getting trending resources: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                    "error", true,
                    "message", "Failed to get trending resources: " + e.getMessage()
            ));
        }
    }
    /**
     * Rates a learning resource by the authenticated user.
     *
     * @param id      resource ID
     * @param request rating request containing rating value and optional comment
     * @param user    authenticated user
     * @return rating result or error response
     */
    @PostMapping("/{id}/rate")
    public ResponseEntity<Map<String, Object>> rateResource(
            @PathVariable Long id,
            @RequestBody ResourceRatingRequest request,
            @AuthenticationPrincipal User user) {
        try {
            if (user == null) {
                return ResponseEntity.status(401).body(Map.of(
                        "error", true,
                        "message", "User not authenticated"
                ));
            }
            if (request.getRating() < 1 || request.getRating() > 5) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", true,
                        "message", "Rating must be between 1 and 5"
                ));
            }
            log.info("User {} rating resource {} with rating: {}",
                    user.getId(), id, request.getRating());
            resourceSearchService.recordResourceFeedback(
                    user, id, request.getRating(),
                    request.getComment(),
                    request.getRating() >= 4
            );
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Resource rated successfully",
                    "resourceId", id,
                    "rating", request.getRating()
            ));
        } catch (IllegalArgumentException e) {
            log.error("Validation error: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of(
                    "error", true,
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("Error rating resource: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                    "error", true,
                    "message", "Failed to rate resource: " + e.getMessage()
            ));
        }
    }
    /**
     * Marks a learning resource as viewed by the authenticated user.
     *
     * @param id   resource ID
     * @param user authenticated user
     * @return success message or error response
     */
    @PostMapping("/{id}/view")
    public ResponseEntity<Map<String, Object>> markAsViewed(
            @PathVariable Long id,
            @AuthenticationPrincipal User user) {
        try {
            if (user == null) {
                return ResponseEntity.status(401).body(Map.of(
                        "error", true,
                        "message", "User not authenticated"
                ));
            }
            log.info("Marking resource {} as viewed by user {}", id, user.getId());
            resourceSearchService.markResourceAsViewed(user, id);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Resource marked as viewed",
                    "resourceId", id
            ));
        } catch (IllegalArgumentException e) {
            log.error("Validation error: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of(
                    "error", true,
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("Error marking resource as viewed: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                    "error", true,
                    "message", "Failed to mark as viewed: " + e.getMessage()
            ));
        }
    }
}
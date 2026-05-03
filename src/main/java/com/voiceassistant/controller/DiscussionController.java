package com.voiceassistant.controller;
import com.voiceassistant.dto.DiscussionMessageDTO;
import com.voiceassistant.dto.DiscussionRoomDTO;
import com.voiceassistant.entity.User;
import com.voiceassistant.service.DiscussionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
/**
 * REST controller for discussion rooms and user conversations.
 * Enables topic-based discussions, messaging, and community engagement.
 * Supports room creation, messaging, and interaction tracking.
 *
 * @see DiscussionService discussion room and message management
 */
@Slf4j
@RestController
@RequestMapping("/api/discussion")
@RequiredArgsConstructor
public class DiscussionController {
    private final DiscussionService discussionService;

    /**
     * Gets existing discussion room for topic or creates new one.
     * Initializes room if first user accessing this topic.
     *
     * @param name topic name for discussion room
     * @param user authenticated user joining/creating room
     * @return ResponseEntity with DiscussionRoomDTO,
     *         or HTTP 401 if not authenticated
     */
    @GetMapping("/rooms/topic")
    public ResponseEntity<DiscussionRoomDTO> getOrCreateRoom(
            @RequestParam String name,
            @AuthenticationPrincipal User user) {
        if (user == null) return ResponseEntity.status(401).build();
        log.info("Getting or creating room for topic: {}", name);
        DiscussionRoomDTO room = discussionService.getOrCreateRoom(name, user);
        return ResponseEntity.ok(room);
    }
    /**
     * Retrieves all available discussion rooms in the system.
     *
     * @return ResponseEntity with list of all DiscussionRoomDTOs
     */
    @GetMapping("/rooms")
    public ResponseEntity<List<DiscussionRoomDTO>> getAllRooms() {
        log.info("Getting all discussion rooms");
        List<DiscussionRoomDTO> rooms = discussionService.getAllRooms();
        return ResponseEntity.ok(rooms);
    }
    /**
     * Retrieves a specific discussion room by ID.
     *
     * @param roomId discussion room identifier
     * @return ResponseEntity with DiscussionRoomDTO containing room details
     */
    @GetMapping("/rooms/{roomId}")
    public ResponseEntity<DiscussionRoomDTO> getRoomById(@PathVariable Long roomId) {
        log.info("Getting room: {}", roomId);
        DiscussionRoomDTO room = discussionService.getRoomById(roomId);
        return ResponseEntity.ok(room);
    }
    /**
     * Retrieves messages from a discussion room with optional limit.
     * Returns most recent messages up to specified limit.
     *
     * @param roomId discussion room identifier
     * @param limit maximum number of messages to return (default 50)
     * @return ResponseEntity with list of DiscussionMessageDTOs
     */
    @GetMapping("/rooms/{roomId}/messages")
    public ResponseEntity<List<DiscussionMessageDTO>> getRoomMessages(
            @PathVariable Long roomId,
            @RequestParam(defaultValue = "50") Integer limit) {
        log.info("Getting {} messages from room: {}", limit, roomId);
        List<DiscussionMessageDTO> messages =
            discussionService.getRoomMessages(roomId, limit);
        return ResponseEntity.ok(messages);
    }
    /**
     * Posts a new message to a discussion room.
     * User must be authenticated and message content required.
     *
     * @param roomId discussion room ID where message is posted
     * @param request map containing "content" field with message text
     * @param user authenticated user posting the message
     * @return ResponseEntity with DiscussionMessageDTO containing posted message,
     *         or HTTP 401 if not authenticated
     */
    @PostMapping("/rooms/{roomId}/messages")
    public ResponseEntity<DiscussionMessageDTO> postMessage(
            @PathVariable Long roomId,
            @RequestBody Map<String, String> request,
            @AuthenticationPrincipal User user) {
        if (user == null) return ResponseEntity.status(401).build();
        String content = request.get("content");
        log.info("User {} posting message to room: {}", user.getId(), roomId);
        DiscussionMessageDTO message =
            discussionService.postMessage(roomId, user, content);
        return ResponseEntity.ok(message);
    }
    /**
     * Marks a message as liked (upvotes).
     * Increments the like count for the message.
     *
     * @param messageId ID of message to like
     * @return ResponseEntity with 200 OK status
     */
    @PostMapping("/messages/{messageId}/like")
    public ResponseEntity<Void> likeMessage(@PathVariable Long messageId) {
        log.info("Liking message: {}", messageId);
        discussionService.likeMessage(messageId);
        return ResponseEntity.ok().build();
    }
    /**
     * Deletes a message from discussion room.
     * Only message owner can delete their message.
     *
     * @param messageId ID of message to delete
     * @param user authenticated user requesting deletion
     * @return ResponseEntity with 200 OK status,
     *         or HTTP 401 if not authenticated
     */
    @DeleteMapping("/messages/{messageId}")
    public ResponseEntity<Void> deleteMessage(
            @PathVariable Long messageId,
            @AuthenticationPrincipal User user) {
        if (user == null) return ResponseEntity.status(401).build();
        log.info("User {} deleting message: {}", user.getId(), messageId);
        discussionService.deleteMessage(messageId, user);
        return ResponseEntity.ok().build();
    }
}
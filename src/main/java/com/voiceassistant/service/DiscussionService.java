package com.voiceassistant.service;
import com.voiceassistant.dto.DiscussionMessageDTO;
import com.voiceassistant.dto.DiscussionRoomDTO;
import com.voiceassistant.entity.DiscussionMessage;
import com.voiceassistant.entity.DiscussionRoom;
import com.voiceassistant.entity.User;
import com.voiceassistant.repository.DiscussionMessageRepository;
import com.voiceassistant.repository.DiscussionRoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.stream.Collectors;
/**
 * Service for managing discussion rooms and user conversations.
 * Enables users to create discussion rooms on topics, post messages, and interact.
 * Supports room creation, messaging, and member management.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DiscussionService {
    private final DiscussionRoomRepository roomRepository;
    private final DiscussionMessageRepository messageRepository;

    /**
     * Creates a new discussion room for a topic if not exists, or retrieves existing.
     * Initializes room with creator information and default settings.
     *
     * @param topicName name of the discussion topic
     * @param user user who creates or joins the room
     * @return DiscussionRoomDTO with room information
     */
    @Transactional
    public DiscussionRoomDTO getOrCreateRoom(String topicName, User user) {
        DiscussionRoom room = roomRepository.findByTopicName(topicName)
            .orElseGet(() -> {
                DiscussionRoom newRoom = DiscussionRoom.builder()
                    .topicName(topicName)
                    .title("Discussion: " + topicName)
                    .description("Discuss about " + topicName)
                    .createdBy(user)
                    .memberCount(1)
                    .messageCount(0)
                    .build();
                return roomRepository.save(newRoom);
            });
        return toDTO(room);
    }
    /**
     * Retrieves all available discussion rooms in the system.
     *
     * @return list of all DiscussionRoomDTOs
     */
    public List<DiscussionRoomDTO> getAllRooms() {
        return roomRepository.findAll()
            .stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
    }
    /**
     * Retrieves a specific discussion room by its ID.
     *
     * @param roomId discussion room ID
     * @return DiscussionRoomDTO with room details
     * @throws IllegalArgumentException if room not found
     */
    public DiscussionRoomDTO getRoomById(Long roomId) {
        return roomRepository.findById(roomId)
            .map(this::toDTO)
            .orElseThrow(() -> new IllegalArgumentException("Room not found"));
    }
    /**
     * Post message to discussion room
     */
    @Transactional
    public DiscussionMessageDTO postMessage(Long roomId, User user, String content) {
        DiscussionRoom room = roomRepository.findById(roomId)
            .orElseThrow(() -> new IllegalArgumentException("Room not found"));
        DiscussionMessage message = DiscussionMessage.builder()
            .room(room)
            .user(user)
            .content(content)
            .likesCount(0)
            .build();
        DiscussionMessage saved = messageRepository.save(message);
        room.setMessageCount((room.getMessageCount() != null ? room.getMessageCount() : 0) + 1);
        roomRepository.save(room);
        log.info("User {} posted message in room {}", user.getId(), roomId);
        return messageToDTO(saved);
    }
    /**
     * Get messages for room (paginated)
     */
    public List<DiscussionMessageDTO> getRoomMessages(Long roomId, Integer limit) {
        return messageRepository.findByRoomIdOrderByCreatedAtDesc(roomId)
            .stream()
            .limit(limit)
            .map(this::messageToDTO)
            .collect(Collectors.toList());
    }
    /**
     * Like a message
     */
    @Transactional
    public void likeMessage(Long messageId) {
        DiscussionMessage message = messageRepository.findById(messageId)
            .orElseThrow(() -> new IllegalArgumentException("Message not found"));
        message.setLikesCount((message.getLikesCount() != null ? message.getLikesCount() : 0) + 1);
        messageRepository.save(message);
        log.info("Message {} liked", messageId);
    }
    /**
     * Delete message
     */
    @Transactional
    public void deleteMessage(Long messageId, User user) {
        DiscussionMessage message = messageRepository.findById(messageId)
            .orElseThrow(() -> new IllegalArgumentException("Message not found"));
        if (!message.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("Can only delete your own messages");
        }
        messageRepository.delete(message);
        log.info("Message {} deleted by user {}", messageId, user.getId());
    }
    /**
     * Convert room to DTO
     */
    private DiscussionRoomDTO toDTO(DiscussionRoom room) {
        return DiscussionRoomDTO.builder()
            .id(room.getId())
            .topicName(room.getTopicName())
            .title(room.getTitle())
            .description(room.getDescription())
            .memberCount(room.getMemberCount())
            .messageCount(room.getMessageCount())
            .createdAt(room.getCreatedAt())
            .createdBy(room.getCreatedBy() != null ? room.getCreatedBy().getEmail() : "Unknown")
            .build();
    }
    /**
     * Convert message to DTO
     */
    private DiscussionMessageDTO messageToDTO(DiscussionMessage message) {
        return DiscussionMessageDTO.builder()
            .id(message.getId())
            .content(message.getContent())
            .username(message.getUser().getEmail())
            .likesCount(message.getLikesCount())
            .createdAt(message.getCreatedAt())
            .build();
    }
}
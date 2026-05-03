package com.voiceassistant.dto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
/**
 * Data Transfer Object for discussion rooms.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DiscussionRoomDTO {
    private Long id;
    private String topicName;
    private String title;
    private String description;
    private Integer memberCount;
    private Integer messageCount;
    private String createdBy;
    private LocalDateTime createdAt;
}
package com.voiceassistant.dto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
/**
 * Data Transfer Object for discussion messages.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DiscussionMessageDTO {
    private Long id;
    private String content;
    private String username;
    private Integer likesCount;
    private LocalDateTime createdAt;
}
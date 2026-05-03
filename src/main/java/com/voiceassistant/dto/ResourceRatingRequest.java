package com.voiceassistant.dto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
/**
 * Request DTO for rating a learning resource.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResourceRatingRequest {
    private Integer rating;
    private String comment;
    private Boolean isHelpful;
}
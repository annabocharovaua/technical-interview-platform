package com.voiceassistant.dto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
/**
 * Data Transfer Object for hint response.
 * Contains hint content for coding task assistance.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HintResponse {
    private String hint;
}
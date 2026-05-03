package com.voiceassistant.dto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
/**
 * Data Transfer Object for solution response.
 * Contains solution code for completed coding tasks.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SolutionResponse {
    private String solution;
}
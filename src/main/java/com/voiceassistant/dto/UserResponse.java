package com.voiceassistant.dto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * Data Transfer Object for user profile response.
 * Returned in API responses with user profile information and account metadata.
 *
 * @see AuthResponse authentication response containing this DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserResponse {
    /** Unique user identifier */
    private Long id;

    /** User's full name */
    private String name;

    /** User's email address */
    private String email;

    /** User's phone number */
    private String phone;

    /** User's company name */
    private String company;

    /** User's job position */
    private String position;

    /** Account creation timestamp */
    private LocalDateTime createdAt;

    /** Last login timestamp */
    private LocalDateTime lastLogin;

    /** Trial usage status (true = already used, false = available) */
    private Boolean trialUsed;
}
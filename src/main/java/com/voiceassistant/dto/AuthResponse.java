package com.voiceassistant.dto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object for authentication response.
 * Returned after login/registration with JWT token and user information.
 *
 * @see UserResponse contains user profile information
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthResponse {
    /** Indicates whether authentication was successful */
    private boolean success;

    /** Response message (success message or error description) */
    private String message;

    /** User profile information (populated on successful authentication) */
    private UserResponse user;

    /** JWT authentication token for subsequent requests */
    private String token;
}
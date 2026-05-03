package com.voiceassistant.controller;
import com.voiceassistant.dto.*;
import com.voiceassistant.entity.User;
import com.voiceassistant.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
/**
 * REST controller for user authentication and authorization.
 * Handles user registration, login, and profile management.
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final UserService userService;
    /**
     * Registers a new user in the system.
     * Creates a new user account with the provided credentials and initializes their skill profile.
     *
     * @param request registration details containing name, email, password, company, and position
     * @return ResponseEntity with AuthResponse containing user data and JWT token on success,
     *         or HTTP 400 with error message if registration fails (e.g., email already exists)
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        log.info("Register request received for email: {}", request.getEmail());
        AuthResponse response = userService.register(request);
        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.badRequest().body(response);
        }
    }
    /**
     * Authenticates a user with email and password.
     * Verifies credentials and returns a JWT token if authentication is successful.
     *
     * @param request login credentials (email and password)
     * @return ResponseEntity with AuthResponse containing user data and JWT token on success,
     *         or HTTP 400 with error message if credentials are invalid
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("Login request received for email: {}", request.getEmail());
        AuthResponse response = userService.login(request);
        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.badRequest().body(response);
        }
    }
    /**
     * Retrieves user profile information.
     * Only authenticated users can access their own profile.
     *
     * @param id user ID to retrieve
     * @param user authenticated user from security context
     * @return ResponseEntity with UserResponse containing user profile data,
     *         HTTP 401 if not authenticated, HTTP 403 if unauthorized, HTTP 404 if user not found
     */
    @GetMapping("/user/{id}")
    public ResponseEntity<UserResponse> getUser(
            @PathVariable Long id,
            @AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(401).build();
        }
        if (!user.getId().equals(id)) {
            return ResponseEntity.status(403).build();
        }
        return userService.getUserById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    /**
     * Updates user profile information.
     * Allows users to modify their personal details and preferences.
     *
     * @param id user ID to update
     * @param request profile update details
     * @param user authenticated user from security context
     * @return ResponseEntity with AuthResponse confirming the update,
     *         HTTP 401 if not authenticated, HTTP 403 if unauthorized
     */
    @PutMapping("/user/{id}")
    public ResponseEntity<AuthResponse> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody UpdateProfileRequest request,
            @AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(401).body(AuthResponse.builder()
                    .success(false).message("Not authenticated").build());
        }
        if (!user.getId().equals(id)) {
            return ResponseEntity.status(403).body(AuthResponse.builder()
                    .success(false).message("Forbidden").build());
        }
        log.info("Update profile request for user id: {}", id);
        AuthResponse response = userService.updateProfile(id, request);
        return response.isSuccess() ? ResponseEntity.ok(response) : ResponseEntity.badRequest().body(response);
    }
    /**
     * Checks if a free trial is available for the user.
     * Trial can be used only once per user account.
     *
     * @param userId user ID to check trial status
     * @param user authenticated user from security context
     * @return ResponseEntity with map containing "available" boolean flag,
     *         HTTP 401 if not authenticated, HTTP 403 if unauthorized
     */
    @GetMapping("/trial/{userId}")
    public ResponseEntity<java.util.Map<String, Object>> checkTrial(@PathVariable Long userId,
                                                                    @AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(401).build();
        }
        if (!user.getId().equals(userId)) {
            return ResponseEntity.status(403).build();
        }
        boolean available = userService.isTrialAvailable(userId);
        return ResponseEntity.ok(java.util.Map.of("available", available));
    }
    /**
     * Uses the free trial for a user.
     * Marks the trial as used and cannot be called again for the same user.
     *
     * @param userId user ID to use trial for
     * @param user authenticated user from security context
     * @return ResponseEntity with success status,
     *         HTTP 400 if trial already used, HTTP 401 if not authenticated, HTTP 403 if unauthorized
     */
    @PostMapping("/trial/{userId}/use")
    public ResponseEntity<java.util.Map<String, Object>> useTrial(@PathVariable Long userId,
                                                                  @AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(401).build();
        }
        if (!user.getId().equals(userId)) {
            return ResponseEntity.status(403).build();
        }
        boolean success = userService.useTrial(userId);
        if (success) {
            return ResponseEntity.ok(java.util.Map.of("success", true));
        } else {
            return ResponseEntity.badRequest().body(java.util.Map.of("success", false, "message", "Trial already used"));
        }
    }
    /**
     * Initiates password reset process.
     * Sends an OTP (One-Time Password) to the user's email address for verification.
     *
     * @param request email address for password reset
     * @return ResponseEntity with AuthResponse confirming OTP has been sent,
     *         or HTTP 400 if email not found
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<AuthResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        log.info("Forgot password request for email: {}", request.getEmail());
        AuthResponse response = userService.requestPasswordReset(request.getEmail());
        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.badRequest().body(response);
        }
    }
    /**
     * Completes password reset with OTP verification.
     * Verifies the OTP code and updates the user's password if validation succeeds.
     *
     * @param request email, OTP code, and new password details
     * @return ResponseEntity with AuthResponse confirming password update,
     *         or HTTP 400 if OTP is invalid, expired, or passwords don't match
     */
    @PostMapping("/reset-password")
    public ResponseEntity<AuthResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        log.info("Reset password request for email: {}", request.getEmail());
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            return ResponseEntity.badRequest().body(AuthResponse.builder()
                    .success(false)
                    .message("Passwords do not match")
                    .build());
        }
        AuthResponse response = userService.verifyOtpAndResetPassword(
                request.getEmail(),
                request.getOtp(),
                request.getNewPassword()
        );
        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.badRequest().body(response);
        }
    }
    /**
     * Changes user password after authentication.
     * Requires the current password for verification before setting a new password.
     *
     * @param id user ID to change password for
     * @param request map containing "currentPassword" and "newPassword"
     * @param user authenticated user from security context
     * @return ResponseEntity with AuthResponse confirming password change,
     *         HTTP 400 if passwords don't match or validation fails,
     *         HTTP 401 if not authenticated, HTTP 403 if unauthorized
     */
    @PutMapping("/user/{id}/password")
    public ResponseEntity<AuthResponse> changePassword(
            @PathVariable Long id,
            @RequestBody Map<String, String> request,
            @AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(401).body(AuthResponse.builder()
                    .success(false).message("Not authenticated").build());
        }
        if (!user.getId().equals(id)) {
            return ResponseEntity.status(403).body(AuthResponse.builder()
                    .success(false).message("Forbidden").build());
        }
        String currentPassword = request.get("currentPassword");
        String newPassword = request.get("newPassword");
        if (currentPassword == null || newPassword == null) {
            return ResponseEntity.badRequest().body(AuthResponse.builder()
                    .success(false).message("Current and new password are required").build());
        }
        if (newPassword.length() < 6) {
            return ResponseEntity.badRequest().body(AuthResponse.builder()
                    .success(false).message("New password must be at least 6 characters").build());
        }
        log.info("Password change request for user id: {}", id);
        AuthResponse response = userService.changePassword(id, currentPassword, newPassword);
        return response.isSuccess() ? ResponseEntity.ok(response) : ResponseEntity.badRequest().body(response);
    }

    /**
     * Deletes user account permanently.
     * Removes all user data and associated records from the system.
     *
     * @param id user ID to delete
     * @param user authenticated user from security context
     * @return ResponseEntity with success status map,
     *         HTTP 401 if not authenticated, HTTP 403 if unauthorized, HTTP 404 if user not found
     */
    @DeleteMapping("/user/{id}")
    public ResponseEntity<Map<String, Object>> deleteUser(
            @PathVariable Long id,
            @AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "Not authenticated"));
        }
        if (!user.getId().equals(id)) {
            return ResponseEntity.status(403).body(Map.of("success", false, "message", "Forbidden"));
        }
        log.info("Delete account request for user id: {}", id);
        boolean deleted = userService.deleteUser(id);
        return deleted
                ? ResponseEntity.ok(Map.of("success", true))
                : ResponseEntity.notFound().build();
    }
}
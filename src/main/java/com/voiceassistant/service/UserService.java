package com.voiceassistant.service;
import com.voiceassistant.dto.*;
import com.voiceassistant.entity.*;
import com.voiceassistant.repository.*;
import com.voiceassistant.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;
/**
 * Service for user authentication, registration, and profile management.
 * Handles user credentials, login tracking, and profile updates.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final UserSkillProfileRepository userSkillProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private static final long OTP_VALIDITY_MINUTES = 10;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private final JwtService jwtService;
    private final ResourceInteractionRepository resourceInteractionRepository;
    private final LearningResourceRepository learningResourceRepository;
    private final CodingTopicRepository codingTopicRepository;
    private final CodingProgressRepository codingProgressRepository;
    private final InterviewWeakQuestionRepository weakQuestionRepository;
    private final DiscussionMessageRepository discussionMessageRepository;
    private final DiscussionRoomRepository discussionRoomRepository;

    /**
     * Registers a new user with email and password.
     * Creates user account, initializes skill profile with default settings, and generates JWT token.
     *
     * @param request registration details containing name, email, password, company, position
     * @return AuthResponse with success flag, message, user data, and JWT token on success;
     *         returns failure response if email already exists
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        log.info("Attempting to register user with email: {}", request.getEmail());
        if (userRepository.existsByEmail(request.getEmail())) {
            log.warn("Registration failed: email {} already exists", request.getEmail());
            return AuthResponse.builder()
                    .success(false)
                    .message("User with this email already exists")
                    .build();
        }
        User user = User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .password(passwordEncoder.encode(request.getPassword()))
                .company(request.getCompany())
                .position(request.getPosition())
                .active(true)
                .build();
        User savedUser = userRepository.save(user);
        UserSkillProfile skillProfile = UserSkillProfile.builder()
                .user(savedUser)
                .overallScore(0.0)
                .totalTasksCompleted(0)
                .totalTasksAttempted(0)
                .preferredLanguage("Java")
                .preferredDifficulty("MEDIUM")
                .currentLevel("BEGINNER")
                .build();
        userSkillProfileRepository.save(skillProfile);
        String token = jwtService.generateToken(savedUser.getId(), savedUser.getEmail());
        log.info("User registered successfully: {}", savedUser.getEmail());
        return AuthResponse.builder()
                .success(true)
                .message("Registration successful")
                .user(mapToUserResponse(savedUser))
                .token(token)
                .build();
    }
    /**
     * Authenticates user with email and password.
     * Verifies credentials, updates last login timestamp, and returns JWT token on success.
     *
     * @param request login credentials (email and password)
     * @return AuthResponse with success flag, user data, and JWT token on successful authentication;
     *         returns failure response if email not found, password incorrect, or account is deactivated
     */
    @Transactional
    public AuthResponse login(LoginRequest request) {
        log.info("Attempting to login user with email: {}", request.getEmail());
        Optional<User> userOpt = userRepository.findByEmail(request.getEmail());
        if (userOpt.isEmpty()) {
            log.warn("Login failed: user not found with email {}", request.getEmail());
            return AuthResponse.builder()
                    .success(false)
                    .message("Invalid email or password")
                    .build();
        }
        User user = userOpt.get();
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            log.warn("Login failed: incorrect password for email {}", request.getEmail());
            return AuthResponse.builder()
                    .success(false)
                    .message("Invalid email or password")
                    .build();
        }
        if (!user.getActive()) {
            log.warn("Login failed: user {} is deactivated", request.getEmail());
            return AuthResponse.builder()
                    .success(false)
                    .message("Account is deactivated")
                    .build();
        }
        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);
        String token = jwtService.generateToken(user.getId(), user.getEmail());
        log.info("User logged in successfully: {}", user.getEmail());
        return AuthResponse.builder()
                .success(true)
                .message("Login successful")
                .user(mapToUserResponse(user))
                .token(token)
                .build();
    }
    /**
     * Retrieves user profile information by ID.
     *
     * @param id user ID
     * @return Optional containing UserResponse if user exists, empty Optional otherwise
     */
    public Optional<UserResponse> getUserById(Long id) {
        return userRepository.findById(id)
                .map(this::mapToUserResponse);
    }
    /**
     * Updates user profile information.
     * Modifies name, phone, company, and position fields.
     *
     * @param id user ID to update
     * @param request profile update details
     * @return AuthResponse with updated user data and success flag
     */
    @Transactional
    public AuthResponse updateProfile(Long id, UpdateProfileRequest request) {
        log.info("Attempting to update profile for user id: {}", id);
        Optional<User> userOpt = userRepository.findById(id);
        if (userOpt.isEmpty()) {
            log.warn("Update failed: user not found with id {}", id);
            return AuthResponse.builder()
                    .success(false)
                    .message("User not found")
                    .build();
        }
        User user = userOpt.get();
        user.setName(request.getName());
        user.setPhone(request.getPhone());
        user.setCompany(request.getCompany());
        user.setPosition(request.getPosition());
        User savedUser = userRepository.save(user);
        log.info("Profile updated successfully for user: {}", savedUser.getEmail());
        return AuthResponse.builder()
                .success(true)
                .message("Profile updated successfully")
                .user(mapToUserResponse(savedUser))
                .build();
    }
    /**
     * Maps User entity to UserResponse DTO.
     * Converts all relevant user fields for API response.
     *
     * @param user User entity
     * @return UserResponse DTO with user profile information
     */
    private UserResponse mapToUserResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .company(user.getCompany())
                .position(user.getPosition())
                .createdAt(user.getCreatedAt())
                .lastLogin(user.getLastLogin())
                .trialUsed(user.getTrialUsed())
                .build();
    }
    /**
     * Checks if free trial is available for a user.
     * Trial is available only if it hasn't been used yet.
     *
     * @param userId user ID
     * @return true if trial is available, false if already used or user not found
     */
    public boolean isTrialAvailable(Long userId) {
        return userRepository.findById(userId)
                .map(user -> !Boolean.TRUE.equals(user.getTrialUsed()))
                .orElse(false);
    }
    /**
     * Marks trial as used for a user.
     * Trial can only be used once per user account.
     *
     * @param userId user ID
     * @return true if trial was successfully marked as used, false if user not found or already used
     */
    @Transactional
    public boolean useTrial(Long userId) {
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return false;
        }
        User user = userOpt.get();
        if (Boolean.TRUE.equals(user.getTrialUsed())) {
            return false;
        }
        user.setTrialUsed(true);
        userRepository.save(user);
        log.info("Trial marked as used for user {}", userId);
        return true;
    }
    /**
     * Initiates password reset process.
     * Generates OTP code valid for {@link #OTP_VALIDITY_MINUTES} and sends it via email.
     *
     * @param email user email address
     * @return AuthResponse with success flag and message; returns failure if email not found
     */
    @Transactional
    public AuthResponse requestPasswordReset(String email) {
        log.info("Password reset requested for email: {}", email);
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            log.warn("Password reset requested for non-existent email: {}", email);
            return AuthResponse.builder()
                    .success(false)
                    .message("User not found")
                    .build();
        }
        User user = userOpt.get();
        String otp = generateOtp();
        LocalDateTime expirationTime = LocalDateTime.now().plusMinutes(OTP_VALIDITY_MINUTES);
        user.setOtpCode(otp);
        user.setOtpExpirationTime(expirationTime);
        userRepository.save(user);
        boolean emailSent = emailService.sendOtpEmail(email, otp);
        if (emailSent) {
            log.info("OTP sent successfully to email: {}", email);
            return AuthResponse.builder()
                    .success(true)
                    .message("OTP sent to your email")
                    .build();
        } else {
            log.warn("Email sending failed, but OTP was saved. OTP for {}: {}", email, otp);
            log.warn("To use: enter this OTP in the reset form within 10 minutes");
            return AuthResponse.builder()
                    .success(true)
                    .message("OTP generated (check server logs - email service unavailable)")
                    .build();
        }
    }
    /**
     * Verify OTP and reset password
     */
    @Transactional
    public AuthResponse verifyOtpAndResetPassword(String email, String otp, String newPassword) {
        log.info("OTP verification and password reset requested for email: {}", email);
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            log.warn("OTP verification failed: user not found with email {}", email);
            return AuthResponse.builder()
                    .success(false)
                    .message("User not found")
                    .build();
        }
        User user = userOpt.get();
        if (user.getOtpCode() == null) {
            log.warn("OTP verification failed: no OTP found for email {}", email);
            return AuthResponse.builder()
                    .success(false)
                    .message("No OTP request found. Please request a new OTP.")
                    .build();
        }
        if (!user.getOtpCode().equals(otp)) {
            log.warn("OTP verification failed: incorrect OTP for email {}", email);
            return AuthResponse.builder()
                    .success(false)
                    .message("Incorrect OTP")
                    .build();
        }
        if (LocalDateTime.now().isAfter(user.getOtpExpirationTime())) {
            log.warn("OTP verification failed: OTP expired for email {}", email);
            return AuthResponse.builder()
                    .success(false)
                    .message("OTP has expired. Please request a new one.")
                    .build();
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setOtpCode(null);
        user.setOtpExpirationTime(null);
        userRepository.save(user);
        log.info("Password reset successfully for email: {}", email);
        return AuthResponse.builder()
                .success(true)
                .message("Password reset successfully")
                .build();
    }
    /**
     * Generate random 6-digit OTP
     */
    private String generateOtp() {
        int otp = 100000 + SECURE_RANDOM.nextInt(900000);
        return String.valueOf(otp);
    }
    /**
     * Send interview report with PDF to user's email
     */
    public boolean sendInterviewReportEmail(Long userId, byte[] pdfContent) {
        log.info("Attempting to send interview report with PDF for user id: {}", userId);
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            log.warn("User not found for sending report: {}", userId);
            return false;
        }
        User user = userOpt.get();
        boolean success = emailService.sendInterviewReportWithPdf(user.getEmail(), user.getName(), pdfContent);
        if (success) {
            log.info("Interview report with PDF sent successfully to user: {}", user.getEmail());
        } else {
            log.error("Failed to send interview report with PDF to user: {}", user.getEmail());
        }
        return success;
    }
    @Transactional
    public AuthResponse changePassword(Long userId, String currentPassword, String newPassword) {
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return AuthResponse.builder()
                    .success(false)
                    .message("User not found")
                    .build();
        }
        User user = userOpt.get();
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            log.warn("Password change failed: incorrect current password for user {}", userId);
            return AuthResponse.builder()
                    .success(false)
                    .message("Current password is incorrect")
                    .build();
        }
        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            return AuthResponse.builder()
                    .success(false)
                    .message("New password must be different from current")
                    .build();
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        log.info("Password changed successfully for user {}", userId);
        return AuthResponse.builder()
                .success(true)
                .message("Password changed successfully")
                .build();
    }

    @Transactional
    public boolean deleteUser(Long id) {
        Optional<User> userOpt = userRepository.findById(id);
        if (userOpt.isEmpty()) return false;
        User user = userOpt.get();

        List<ResourceInteraction> interactions = resourceInteractionRepository.findByUser(user);
        if (!interactions.isEmpty()) resourceInteractionRepository.deleteAll(interactions);

        discussionMessageRepository.deleteByUser(user);

        List<DiscussionRoom> rooms = discussionRoomRepository.findByCreatedBy(user);
        rooms.forEach(r -> r.setCreatedBy(null));
        discussionRoomRepository.saveAll(rooms);

        learningResourceRepository.nullifyCreatedByUser(user);

        List<InterviewWeakQuestion> weakQuestions = weakQuestionRepository.findByUserId(id);
        if (!weakQuestions.isEmpty()) weakQuestionRepository.deleteAll(weakQuestions);

        List<CodingTopic> topics = codingTopicRepository.findByUserId(id);
        if (!topics.isEmpty()) codingTopicRepository.deleteAll(topics);

        List<CodingProgress> progress = codingProgressRepository.findByUserId(id);
        if (!progress.isEmpty()) codingProgressRepository.deleteAll(progress);

        userSkillProfileRepository.findByUserId(id)
                .ifPresent(userSkillProfileRepository::delete);

        userRepository.delete(user);
        log.info("User deleted: {}", user.getEmail());
        return true;
    }
}
package com.voiceassistant.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Entity representing a user in the voice assistant application.
 * Stores user authentication credentials, profile information, and account status.
 * Automatically tracks creation and modification timestamps.
 *
 * @see com.voiceassistant.entity.UserSkillProfile one-to-one relationship with user skill profile
 */
@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    /** Unique identifier for the user */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** User's full name */
    @Column(nullable = false)
    private String name;

    /** User's email address (unique) */
    @Column(nullable = false, unique = true)
    private String email;

    /** User's phone number */
    @Column
    private String phone;

    /** Encrypted user password */
    @Column(nullable = false)
    private String password;

    /** Company name where user works */
    @Column
    private String company;

    /** User's job position */
    @Column
    private String position;

    /** Account creation timestamp */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Last modification timestamp */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** Last login timestamp */
    @Column(name = "last_login")
    private LocalDateTime lastLogin;

    /** Account active status (true = active, false = deactivated) */
    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    /** Trial usage status (true = trial already used, false = trial available) */
    @Column(name = "trial_used", nullable = false)
    @Builder.Default
    private Boolean trialUsed = false;

    /** One-Time Password for password reset */
    @Column(name = "otp_code")
    private String otpCode;

    /** OTP expiration timestamp */
    @Column(name = "otp_expiration_time")
    private LocalDateTime otpExpirationTime;

    /**
     * JPA lifecycle callback: sets creation and modification timestamps when entity is first persisted.
     */
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    /**
     * JPA lifecycle callback: updates modification timestamp before entity is updated.
     */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

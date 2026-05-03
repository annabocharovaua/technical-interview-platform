package com.voiceassistant.repository;

import com.voiceassistant.entity.LearningResource;
import com.voiceassistant.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
/**
 * Repository for LearningResource entity.
 */
@Repository
public interface LearningResourceRepository extends JpaRepository<LearningResource, Long> {
    /**
     * Find verified resources by topic name, ordered by rating
     */
    List<LearningResource> findByTopicNameContainingIgnoreCaseAndIsVerifiedTrueOrderByRatingDesc(
        String topicName
    );
    /**
     * Find all resources by topic name, ordered by rating
     */
    List<LearningResource> findByTopicNameContainingIgnoreCaseOrderByRatingDesc(
        String topicName
    );
    /**
     * Find resource by URL to check for duplicates
     */
    Optional<LearningResource> findByUrl(String url);
    /**
     * Find top resources by rating with pagination to avoid loading all records into memory
     */
    Page<LearningResource> findTopByOrderByRatingDesc(Pageable pageable);

    /**
     * Nullify created_by for all resources created by a specific user.
     * Used before user deletion to avoid FK constraint violations.
     */
    @Modifying
    @Query("UPDATE LearningResource lr SET lr.createdBy = NULL WHERE lr.createdBy = :user")
    void nullifyCreatedByUser(@Param("user") User user);
}
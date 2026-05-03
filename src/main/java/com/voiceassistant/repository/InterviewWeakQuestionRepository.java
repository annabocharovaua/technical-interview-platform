package com.voiceassistant.repository;

import com.voiceassistant.entity.InterviewWeakQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InterviewWeakQuestionRepository extends JpaRepository<InterviewWeakQuestion, Long> {

    List<InterviewWeakQuestion> findByUserIdAndCorrectAnswerGivenFalse(Long userId);

    /**
     * Find weak questions by userId and programming language (case-insensitive).
     * Used to fetch only relevant questions for the current interview language.
     */
    @Query("SELECT q FROM InterviewWeakQuestion q WHERE q.userId = :userId " +
            "AND q.correctAnswerGiven = false AND LOWER(q.language) = LOWER(:language)")
    List<InterviewWeakQuestion> findByUserIdAndLanguageIgnoreCase(
            @Param("userId") Long userId,
            @Param("language") String language);

    List<InterviewWeakQuestion> findByUserId(Long userId);

    /**
     * Bulk delete by IDs.
     * clearAutomatically=true ensures JPA persistence context is refreshed after JPQL DELETE.
     * flushAutomatically=true ensures pending changes are flushed before execution.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM InterviewWeakQuestion q WHERE q.id IN :ids")
    void deleteAllByIdInBatch(@Param("ids") List<Long> ids);
}
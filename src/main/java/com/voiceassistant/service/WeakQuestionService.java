package com.voiceassistant.service;

import com.voiceassistant.dto.WeakQuestionDto;
import com.voiceassistant.entity.InterviewWeakQuestion;
import com.voiceassistant.repository.InterviewWeakQuestionRepository;
import com.voiceassistant.util.TextNormalizationUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WeakQuestionService {

    private static final int WEAK_QUESTION_THRESHOLD = 70;
    private static final double KEYWORD_MATCH_THRESHOLD = 0.4;
    private static final String DEFAULT_TOPIC = "General";
    private static final String DEFAULT_LANGUAGE = "Unknown";

    private final InterviewWeakQuestionRepository weakQuestionRepository;

    /**
     * Process feedback and save/delete weak questions.
     *
     * @param userId user ID
     * @param feedback feedback map containing detailedAnswers
     * @param programmingLanguage the PROGRAMMING language (Java, Python, etc.) — NOT interview language
     * @param position job position
     */
    @Transactional
    public void processFeedback(Long userId, Map<String, Object> feedback,
                                String programmingLanguage, String position) {
        if (userId == null || feedback == null) {
            log.warn("Invalid parameters for processFeedback: userId={}, feedback={}", userId, feedback != null);
            return;
        }

        List<Map<String, Object>> detailedAnswers = extractDetailedAnswers(feedback);
        if (detailedAnswers.isEmpty()) {
            log.debug("No detailed answers found in feedback for user {}", userId);
            return;
        }

        String normalizedLang = (programmingLanguage != null && !programmingLanguage.isBlank())
                ? programmingLanguage.trim()
                : DEFAULT_LANGUAGE;

        log.debug("══════════════════════════════════════════════════════════════");
        log.debug("🔍 PROCESSING FEEDBACK for user {} | language: {} | position: {}",
                userId, normalizedLang, position);
        log.debug("══════════════════════════════════════════════════════════════");

        List<InterviewWeakQuestion> existingQuestions;
        if (!DEFAULT_LANGUAGE.equals(normalizedLang)) {
            existingQuestions = weakQuestionRepository
                    .findByUserIdAndLanguageIgnoreCase(userId, normalizedLang);
            log.debug("📋 Found {} existing weak questions for user {} with language '{}'",
                    existingQuestions.size(), userId, normalizedLang);
        } else {
            existingQuestions = weakQuestionRepository
                    .findByUserIdAndCorrectAnswerGivenFalse(userId);
            log.debug("📋 Found {} existing weak questions for user {} (all languages, no filter)",
                    existingQuestions.size(), userId);
        }

        log.debug("Processing {} answers against {} existing weak questions",
                detailedAnswers.size(), existingQuestions.size());

        for (int i = 0; i < existingQuestions.size(); i++) {
            InterviewWeakQuestion q = existingQuestions.get(i);
            log.debug("  📌 Existing[{}] id={} lang='{}' score={}% question='{}'",
                    i, q.getId(), q.getLanguage(), q.getInterviewScore(),
                    q.getQuestion().substring(0, Math.min(80, q.getQuestion().length())));
        }

        List<Long> idsToDelete = new ArrayList<>();
        List<InterviewWeakQuestion> toSave = new ArrayList<>();

        for (int idx = 0; idx < detailedAnswers.size(); idx++) {
            Map<String, Object> answer = detailedAnswers.get(idx);
            try {
                int accuracy = extractAccuracy(answer);
                String question = (String) answer.get("question");

                if (question == null || question.isBlank()) {
                    log.warn("  ⚠️ Skipping answer #{} — blank question text", idx + 1);
                    continue;
                }

                log.debug("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                log.debug("  📝 Answer #{}: accuracy={}%, question='{}'",
                        idx + 1, accuracy, question.substring(0, Math.min(80, question.length())));

                String normalizedQuestion = TextNormalizationUtil.normalizeText(question);
                InterviewWeakQuestion existing = findSimilarQuestion(
                        question, normalizedQuestion, existingQuestions
                );

                if (existing != null) {
                    log.debug("  🔗 MATCHED existing question id={}: '{}'",
                            existing.getId(),
                            existing.getQuestion().substring(0, Math.min(60, existing.getQuestion().length())));
                } else {
                    log.debug("  ❌ NO MATCH found in existing questions");
                }

                if (existing != null && idsToDelete.contains(existing.getId())) {
                    log.debug("  ⏭️ Matched id={} already queued for deletion — treating as no match", existing.getId());
                    existing = null;
                }

                if (accuracy >= WEAK_QUESTION_THRESHOLD) {
                    if (existing != null) {
                        idsToDelete.add(existing.getId());
                        log.debug("  ✅ QUEUED FOR DELETION (accuracy={}% >= {}%): id={} question='{}'",
                                accuracy, WEAK_QUESTION_THRESHOLD, existing.getId(),
                                existing.getQuestion().substring(0, Math.min(60, existing.getQuestion().length())));
                    } else {
                        log.debug("  ✅ Good answer ({}%) — no matching weak question to delete", accuracy);
                    }
                } else {
                    if (existing == null) {
                        InterviewWeakQuestion newQuestion = InterviewWeakQuestion.builder()
                                .userId(userId)
                                .question(question)
                                .topic(resolveTopic((String) answer.get("topic")))
                                .interviewScore(accuracy)
                                .language(normalizedLang)
                                .position(position)
                                .createdAt(LocalDateTime.now())
                                .correctAnswerGiven(false)
                                .incorrectAttempts(1)
                                .build();
                        toSave.add(newQuestion);
                        log.debug("  ❌ NEW weak question ({}%): lang='{}' topic='{}' question='{}'",
                                accuracy, normalizedLang, newQuestion.getTopic(),
                                question.substring(0, Math.min(60, question.length())));
                    } else {
                        existing.setIncorrectAttempts(existing.getIncorrectAttempts() + 1);
                        existing.setInterviewScore(Math.min(accuracy, existing.getInterviewScore()));
                        existing.setUpdatedAt(LocalDateTime.now());
                        toSave.add(existing);
                        log.debug("  🔁 UPDATED weak question id={} attempt=#{} score={}%: '{}'",
                                existing.getId(), existing.getIncorrectAttempts(), accuracy,
                                question.substring(0, Math.min(60, question.length())));
                    }
                }
            } catch (Exception e) {
                log.error("  💥 Error processing answer #{}: {}", idx + 1, e.getMessage(), e);
            }
        }

        Set<Long> deletedIds = new HashSet<>(idsToDelete);
        List<InterviewWeakQuestion> filteredToSave = toSave.stream()
                .filter(q -> q.getId() == null || !deletedIds.contains(q.getId()))
                .collect(Collectors.toList());

        log.debug("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log.debug("📊 EXECUTION PLAN: save/update={}, delete={}", filteredToSave.size(), idsToDelete.size());

        if (!filteredToSave.isEmpty()) {
            weakQuestionRepository.saveAll(filteredToSave);
            log.debug("💾 Saved/updated {} weak questions", filteredToSave.size());
            for (InterviewWeakQuestion q : filteredToSave) {
                log.debug("   💾 Saved: id={} lang='{}' question='{}'",
                        q.getId(), q.getLanguage(),
                        q.getQuestion().substring(0, Math.min(50, q.getQuestion().length())));
            }
        }

        if (!idsToDelete.isEmpty()) {
            log.debug("🗑️ DELETING {} weak questions with IDs: {}", idsToDelete.size(), idsToDelete);
            try {
                weakQuestionRepository.deleteAllByIdInBatch(idsToDelete);
                log.debug("✅ Successfully deleted {} weak questions", idsToDelete.size());
                
                for (Long id : idsToDelete) {
                    boolean stillExists = weakQuestionRepository.existsById(id);
                    if (stillExists) {
                        log.error("🚨 DELETION FAILED! Question id={} still exists in DB!", id);
                    } else {
                        log.debug("   ✅ Verified: id={} deleted from DB", id);
                    }
                }
            } catch (Exception e) {
                log.error("🚨 ERROR during deletion of IDs {}: {}", idsToDelete, e.getMessage(), e);
                log.debug("🔄 Attempting fallback: delete one by one...");
                for (Long id : idsToDelete) {
                    try {
                        weakQuestionRepository.deleteById(id);
                        log.debug("   ✅ Fallback delete successful for id={}", id);
                    } catch (Exception e2) {
                        log.error("   🚨 Fallback delete ALSO failed for id={}: {}", id, e2.getMessage());
                    }
                }
            }
        }

        log.debug("══════════════════════════════════════════════════════════════");
        log.debug("📊 SUMMARY for user {}: {} saved/updated, {} deleted | lang='{}'",
                userId, filteredToSave.size(), idsToDelete.size(), normalizedLang);
        log.debug("══════════════════════════════════════════════════════════════");
    }

    /**
     * Get weak questions for user, optionally filtered by programming language.
     */
    public List<WeakQuestionDto> getWeakQuestions(Long userId) {
        return getWeakQuestions(userId, null);
    }

    /**
     * Get weak questions for user filtered by programming language.
     * If programmingLanguage is null or blank — returns ALL weak questions.
     */
    public List<WeakQuestionDto> getWeakQuestions(Long userId, String programmingLanguage) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID must not be null");
        }

        log.debug("Getting weak questions for user: {} | programmingLanguage filter: '{}'",
                userId, programmingLanguage);

        List<InterviewWeakQuestion> questions;
        if (programmingLanguage != null && !programmingLanguage.isBlank()) {
            questions = weakQuestionRepository
                    .findByUserIdAndLanguageIgnoreCase(userId, programmingLanguage.trim());
            log.debug("Retrieved {} weak questions for user {} with language '{}'",
                    questions.size(), userId, programmingLanguage);
        } else {
            questions = weakQuestionRepository
                    .findByUserIdAndCorrectAnswerGivenFalse(userId);
            log.debug("Retrieved {} weak questions for user {} (all languages)", questions.size(), userId);
        }

        List<WeakQuestionDto> result = questions.stream()
                .map(WeakQuestionDto::fromEntity)
                .collect(Collectors.toList());

        return result;
    }

    /**
     * Improved matching — multiple strategies with extensive logging
     */
    private InterviewWeakQuestion findSimilarQuestion(
            String originalQuestion,
            String normalizedQuestion,
            List<InterviewWeakQuestion> candidates) {

        if (originalQuestion == null || candidates == null || candidates.isEmpty()) {
            log.debug("    🔎 findSimilarQuestion: null input or empty candidates");
            return null;
        }

        for (InterviewWeakQuestion candidate : candidates) {
            if (candidate.getQuestion().equalsIgnoreCase(originalQuestion.trim())) {
                log.debug("    🔎 Strategy 1 HIT: Exact match (id={})", candidate.getId());
                return candidate;
            }
        }

        for (InterviewWeakQuestion candidate : candidates) {
            String candidateNorm = TextNormalizationUtil.normalizeText(candidate.getQuestion());
            if (candidateNorm.equals(normalizedQuestion)) {
                log.debug("    🔎 Strategy 2 HIT: Normalized match (id={})", candidate.getId());
                return candidate;
            }
        }

        String[] keywords = TextNormalizationUtil.extractKeywords(originalQuestion);
        if (keywords.length > 0) {
            InterviewWeakQuestion bestMatch = null;
            double bestScore = 0;

            for (InterviewWeakQuestion candidate : candidates) {
                String[] candidateKeywords = TextNormalizationUtil
                        .extractKeywords(candidate.getQuestion());

                if (candidateKeywords.length == 0) continue;

                int matches = TextNormalizationUtil
                        .calculateKeywordMatches(keywords, candidateKeywords);

                int minLen = Math.min(keywords.length, candidateKeywords.length);
                double score = minLen > 0 ? (double) matches / minLen : 0;

                if (score > bestScore) {
                    bestScore = score;
                    bestMatch = candidate;
                }
            }

            if (bestScore >= KEYWORD_MATCH_THRESHOLD && bestMatch != null) {
                log.debug("    🔎 Strategy 3 HIT: Keyword match (id={}, score={:.2f}, threshold={})",
                        bestMatch.getId(), bestScore, KEYWORD_MATCH_THRESHOLD);
                return bestMatch;
            } else {
                log.debug("    🔎 Strategy 3 MISS: best score={:.2f} < threshold={}",
                        bestScore, KEYWORD_MATCH_THRESHOLD);
            }
        }

        for (InterviewWeakQuestion candidate : candidates) {
            String candidateNormalized = TextNormalizationUtil
                    .normalizeText(candidate.getQuestion());
            if (TextNormalizationUtil.isSimilarQuestion(normalizedQuestion, candidateNormalized)) {
                log.debug("    🔎 Strategy 4 HIT: Similarity match (id={})", candidate.getId());
                return candidate;
            }
        }

        log.debug("    🔎 ALL strategies MISS for: '{}'",
                originalQuestion.substring(0, Math.min(50, originalQuestion.length())));
        return null;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractDetailedAnswers(Map<String, Object> feedback) {
        Object raw = feedback.get("detailedAnswers");
        if (raw instanceof List<?> list && !list.isEmpty()) {
            return (List<Map<String, Object>>) list;
        }
        return List.of();
    }

    private int extractAccuracy(Map<String, Object> answer) {
        Object raw = answer.get("accuracy");
        return raw instanceof Number number ? number.intValue() : 0;
    }

    private String resolveTopic(String topic) {
        String normalized = TextNormalizationUtil.normalizeTopic(topic);
        return normalized != null ? normalized : DEFAULT_TOPIC;
    }
}
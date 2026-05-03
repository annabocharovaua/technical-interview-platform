package com.voiceassistant.util;
import lombok.extern.slf4j.Slf4j;
import java.util.Arrays;
import java.util.Set;
@Slf4j
public class TextNormalizationUtil {
    private static final int MIN_WORD_LENGTH = 3;
    private static final Set<String> STOPWORDS = Set.of(
            "the", "and", "but", "are", "was", "for", "from", "with",
            "what", "which", "who", "when", "where", "why", "how",
            "this", "that", "these", "those", "they", "them", "their",
            "you", "your", "its", "our", "can", "could", "would", "should",
            "have", "has", "had", "does", "did", "will", "been", "being",

            "tell", "explain", "describe", "define", "discuss",
            "elaborate", "mention", "please", "know", "understand",
            "about", "think", "mean", "say", "use", "used", "using"
    );
    private TextNormalizationUtil() {
    }
    public static String normalizeText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return text.toLowerCase()
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
    public static String normalizeTopic(String topic) {
        if (topic == null || topic.isBlank()) {
            return null;
        }
        String trimmed = topic.trim();
        return Character.toUpperCase(trimmed.charAt(0)) + trimmed.substring(1);
    }
    public static String[] extractKeywords(String text) {
        if (text == null || text.isBlank()) {
            return new String[0];
        }
        return Arrays.stream(
                        text.toLowerCase()
                                .replaceAll("[^a-z0-9\\s]", " ")
                                .split("\\s+")
                )
                .filter(word -> word.length() >= MIN_WORD_LENGTH && !STOPWORDS.contains(word))
                .toArray(String[]::new);
    }
    public static boolean isSimilarQuestion(String question1, String question2) {
        if (question1 == null || question2 == null
                || question1.isBlank() || question2.isBlank()) {
            return false;
        }
        String[] keywords1 = extractKeywords(question1);
        String[] keywords2 = extractKeywords(question2);
        if (keywords1.length == 0 || keywords2.length == 0) {
            return false;
        }
        int matches = calculateKeywordMatches(keywords1, keywords2);
        int total = Math.max(keywords1.length, keywords2.length);
        double similarityRatio = (double) matches / total;
        log.debug("Similarity between '{}' and '{}': {}/{} = {}",
                question1, question2, matches, total, similarityRatio);
        return similarityRatio >= 0.5;
    }
    public static int calculateKeywordMatches(String[] keywords1, String[] keywords2) {
        if (keywords1 == null || keywords2 == null) {
            return 0;
        }
        Set<String> set2 = Set.of(keywords2);
        return (int) Arrays.stream(keywords1)
                .filter(set2::contains)
                .count();
    }
}
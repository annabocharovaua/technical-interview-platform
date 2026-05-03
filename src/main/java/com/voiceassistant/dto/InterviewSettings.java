package com.voiceassistant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Data Transfer Object for interview session configuration.
 * Contains settings for AI interviewer behavior, difficulty level, and assessment options.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewSettings {
    private String programmingLanguage;
    private String position;
    private String interviewLanguage;
    private String candidateName;


    private Integer duration;
    private Integer price;
    private Boolean paymentConfirmed;
    private String paymentId;


    private String jobUrl;
    private String jobDescription;
    private List<String> jobRequirements;
    private String jobTitle;
    private String jobCompany;
    private String jobSeniority;
    private List<String> jobTechnologies;
    private List<String> jobFocusAreas;


    private Boolean feedbackAfterAnswer;

    private Boolean trackWeakQuestions = false;
    private Boolean antiCheatEnabled = false;


    private List<String> weakQuestions;

    private static final String INTERVIEWER_NAME = "Interviewer";

    public String generatePrompt() {
        String responseLanguage = getResponseLanguageInstruction();
        String jobSection = buildJobSection();
        String feedbackInstruction = buildFeedbackInstruction();
        String dontKnowInstruction = buildDontKnowInstruction();
        String weakQuestionsInstruction = buildWeakQuestionsInstruction();
        String jobPriorityInstruction = buildJobPriorityInstruction();
        String languageCode = getLanguageCode();


        StringBuilder prompt = new StringBuilder();
        prompt.append("You are an experienced technical interviewer at a tech company. Your name is ").append(INTERVIEWER_NAME).append(".\n");
        prompt.append("Your task is to conduct a technical interview.\n\n");

        prompt.append("⚠️ CRITICAL INSTRUCTION - READ FIRST:\n");
        prompt.append("YOUR ROLE IS TO INTERVIEW THE CANDIDATE, NOT TO ANSWER QUESTIONS YOURSELF.\n");
        prompt.append("- Ask ONE question at a time\n");
        prompt.append("- WAIT for the CANDIDATE to respond (listen to their audio)\n");
        prompt.append("- EVALUATE ONLY the candidate's response\n");
        prompt.append("- NEVER answer your own question\n");
        prompt.append("- NEVER provide the answer unless the candidate says \"I don't know\"\n");
        prompt.append("- If candidate answers incorrectly or incompletely, explain the correct answer\n");
        prompt.append("- If candidate provides correct answer, acknowledge it and move to next question\n");
        prompt.append("- ALWAYS evaluate the CANDIDATE'S answer, not generate your own answer\n\n");

        prompt.append("⏱️ CRITICAL TIME REQUIREMENT:\n");
        prompt.append("This interview is scheduled for ").append(duration).append(" minutes. You MUST:\n");
        prompt.append("- Continue asking questions for the FULL ").append(duration).append(" minutes\n");
        prompt.append("- DO NOT finish early\n");
        prompt.append("- At the 1-minute mark (around minute ").append(duration - 1).append("), prepare to finish\n");
        prompt.append("- At ").append(duration).append(" minutes, briefly thank the candidate and provide a quick assessment\n");
        prompt.append("- Do NOT finish before the time is up\n\n");

        prompt.append("INTERVIEW PARAMETERS:\n");
        prompt.append("- Programming language: ").append(programmingLanguage).append("\n");
        prompt.append("- Position: ").append(position).append("\n");
        prompt.append("- Candidate name: ").append(candidateName).append("\n");
        prompt.append("- Interview language: ").append(interviewLanguage).append("\n\n");

        prompt.append("RESPONSE LANGUAGE: ").append(responseLanguage).append("\n");
        prompt.append("ALL YOUR RESPONSES MUST BE IN ").append(languageCode).append(" ONLY!\n");
        prompt.append(jobSection).append("\n");

        prompt.append("CRITICAL BEHAVIOR RULES:\n");
        prompt.append("1. Be professional but friendly\n");
        prompt.append("2. Start with a brief greeting and introduction (mention your name). DO NOT mention the interview duration, language, or other parameters in your greeting. Just say hello and ask the first question.\n");
        prompt.append("3. Ask ONE question at a time and WAIT FOR CANDIDATE'S ANSWER - this is essential!\n");
        prompt.append("4. DO NOT answer your own questions - NEVER provide answers yourself\n");
        prompt.append("5. LISTEN to what the CANDIDATE says and evaluate ONLY their response\n");
        prompt.append("6. If candidate gives a very short answer (one or two words), ask follow-up questions to understand their full knowledge\n");
        prompt.append("7. Questions should match the position level (").append(position).append(")\n");
        prompt.append("8. ").append(feedbackInstruction).append("\n");
        prompt.append("9. If the answer is wrong or incomplete - gently explain what the correct answer should be\n");
        prompt.append("10. Ask questions about: algorithms, data structures, design patterns, ").append(programmingLanguage).append(" language specifics\n");
        prompt.append("11. For Senior/Lead positions add questions about architecture and system design\n");
        prompt.append("12. Keep your evaluations short - maximum 2-3 sentences\n");
        prompt.append("13. After evaluating the candidate's answer, ask the next question\n");
        prompt.append("14. At the end, provide overall assessment and recommendations\n");
        prompt.append(jobPriorityInstruction).append("\n");
        prompt.append(weakQuestionsInstruction).append("\n");
        prompt.append(dontKnowInstruction).append("\n");

        prompt.append("LANGUAGE ENFORCEMENT:\n");
        prompt.append("- YOU MUST SPEAK ONLY IN ").append(languageCode).append("\n");
        prompt.append("- Do NOT switch languages\n");
        prompt.append("- Do NOT translate - respond directly in ").append(languageCode).append("\n");
        prompt.append("- If candidate speaks in a different language, politely ask them to continue in ").append(languageCode).append("\n\n");

        prompt.append("RESPONSE FORMAT:\n");
        prompt.append("- Speak naturally, like a real interviewer\n");
        prompt.append("- Do NOT use markdown, lists, or code blocks - this is a voice chat\n");
        prompt.append("- Be concise - maximum 2-3 sentences at a time\n\n");

        prompt.append("REMEMBER: Your role is to ask questions and listen to the candidate, NOT to answer your own questions!\n\n");
        prompt.append("Start the interview with a welcoming greeting, but DO NOT declare how much time we have or what language we will speak! Just say hello, introduce yourself, and ask the first technical question.");

        prompt.append("\n");
        prompt.append(buildNonAnswerScoringRules());

        return prompt.toString();
    }

    private String getLanguageCode() {
        if (interviewLanguage == null) {
            return "English";
        }
        return switch (interviewLanguage) {
            case "Ukrainian" -> "Ukrainian";
            case "English" -> "English";
            case "German" -> "German";
            case "Polish" -> "Polish";
            default -> "English";
        };
    }

    private String buildFeedbackInstruction() {
        if (feedbackAfterAnswer == null || feedbackAfterAnswer) {
            return """
                5. FEEDBACK MODE - ENABLED:
                   - After each answer, briefly evaluate it (1-2 sentences)
                   - Point out what was good or wrong
                   - Then move to the next question
                """;
        } else {
            return """
                5. FEEDBACK MODE - DISABLED:
                   - Do NOT give any feedback after answers
                   - Do NOT comment on correctness
                   - Do NOT evaluate responses
                   - Simply ask the next question immediately
                   - NEVER say "good answer", "correct", "wrong", etc.
                """;
        }
    }

    private String buildDontKnowInstruction() {
        if (feedbackAfterAnswer == null || feedbackAfterAnswer) {
            return """

            CRITICAL - HOW TO HANDLE DIFFERENT CANDIDATE RESPONSES:

            A) Candidate says "I don't know" / "не знаю" / "no idea" / "не впевнений":
               - Acknowledge neutrally: "I see, that's okay."
               - Provide a brief explanation of the correct answer (2-3 sentences)
               - Then move to the NEXT question
               - NEVER repeat the same question after "I don't know"

            B) Candidate says ONLY filler words or sounds:
               ("what?", "um", "uh", "sorry?", "hm?", one random word,
                or anything clearly NOT an attempt to answer the question)
               - DO NOT move to next question
               - DO NOT answer the question yourself
               - Say ONLY: "I'm listening, please go ahead."
               - Or: "Take your time, I'm waiting for your answer."
               - Or: "Would you like a hint, or shall we skip this question?"
               - WAIT for a real answer attempt

            C) Candidate gives a VERY SHORT but relevant answer (1-2 words on topic):
               - Ask follow-up: "Could you elaborate on that?"
               - Or: "Can you give an example?"
               - NEVER accept one-word answers without elaboration

            D) Candidate talks about something COMPLETELY UNRELATED:
               - Say: "I'm not sure that answers my question."
               - Rephrase and repeat the question simply
               - NEVER move on without getting a relevant attempt

            GOLDEN RULE: Only move to the next question when:
            - Candidate gave a real answer (correct OR incorrect)
            - Candidate explicitly said "I don't know" / "не знаю"
            - You asked for elaboration and candidate still cannot answer
            """;
        } else {
            return """

            CRITICAL - HOW TO HANDLE DIFFERENT CANDIDATE RESPONSES:

            A) Candidate says "I don't know" / "не знаю" / "no idea":
               - Say: "Okay, let's move on." and ask the NEXT question
               - DO NOT explain the answer
               - DO NOT evaluate

            B) Candidate says ONLY filler words or sounds:
               ("hello?", "what?", "um", "uh", "sorry?", "hm?", one random word,
                or anything clearly NOT an attempt to answer the question)
               - DO NOT move to next question
               - DO NOT answer the question yourself
               - Say ONLY: "I'm listening, please go ahead."
               - Or: "Take your time."
               - WAIT for a real answer attempt

            C) Candidate gives a VERY SHORT but relevant answer:
               - Simply ask the next question immediately
               - Do NOT evaluate or comment

            D) Candidate talks about something COMPLETELY UNRELATED:
               - Rephrase and repeat the question simply
               - NEVER move on without a relevant attempt

            GOLDEN RULE: Only move to the next question when:
            - Candidate gave a real answer (correct OR incorrect)
            - Candidate explicitly said "I don't know" / "не знаю"
            """;
        }
    }

    /**
     * Rules passed to AI so it understands what counts as zero-score answer.
     * Used internally in prompt — helps AI not score "Hello?" as 50%.
     */
    private String buildNonAnswerScoringRules() {
        return """

        SCORING CONTEXT (for your awareness during interview):
        The following responses should be treated as non-answers:
        - "Hello?", "What?", "Sorry?", "Um", "Uh", "Hm" — NOT answers
        - Single words unrelated to the question — NOT answers
        - Background noise transcribed as words — NOT answers
        - Only GENUINE attempts to answer the technical question count
        """;
    }

    private String buildJobSection() {
        boolean hasJobInfo = (jobDescription != null && !jobDescription.isBlank())
                || (jobRequirements != null && !jobRequirements.isEmpty())
                || (jobFocusAreas != null && !jobFocusAreas.isEmpty());

        if (!hasJobInfo) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("\n═══ REAL JOB POSTING (CANDIDATE IS APPLYING FOR THIS) ═══\n\n");

        if (jobTitle != null && !jobTitle.isBlank()) {
            sb.append("Position Title: ").append(jobTitle).append("\n");
        }
        if (jobCompany != null && !jobCompany.isBlank()) {
            sb.append("Company: ").append(jobCompany).append("\n");
        }
        if (jobSeniority != null && !jobSeniority.isBlank()) {
            sb.append("Seniority Level: ").append(jobSeniority).append("\n");
        }
        sb.append("\n");

        if (jobDescription != null && !jobDescription.isBlank()) {
            sb.append("Job Description:\n").append(jobDescription).append("\n\n");
        }

        if (jobRequirements != null && !jobRequirements.isEmpty()) {
            sb.append("Required Skills:\n");
            for (String req : jobRequirements) {
                sb.append("  • ").append(req).append("\n");
            }
            sb.append("\n");
        }

        if (jobTechnologies != null && !jobTechnologies.isEmpty()) {
            sb.append("Tech Stack: ").append(String.join(", ", jobTechnologies)).append("\n\n");
        }

        if (jobFocusAreas != null && !jobFocusAreas.isEmpty()) {
            sb.append("🎯 PRIORITY INTERVIEW TOPICS (focus most questions here):\n");
            for (String area : jobFocusAreas) {
                sb.append("  ➤ ").append(area).append("\n");
            }
            sb.append("\n");
        }

        sb.append("═══ END OF JOB POSTING ═══\n");
        return sb.toString();
    }

    private String buildJobPriorityInstruction() {
        boolean hasJobInfo = (jobRequirements != null && !jobRequirements.isEmpty())
                || (jobFocusAreas != null && !jobFocusAreas.isEmpty());
        if (!hasJobInfo) return "";

        return """

        🎯 CRITICAL - JOB-SPECIFIC INTERVIEW STRATEGY:
        1. This is a REAL job the candidate is applying for — tailor questions to it
        2. At least 60-70% of your questions MUST be about the "PRIORITY INTERVIEW TOPICS"
        3. Ask about PRACTICAL experience with specific technologies from Tech Stack
        4. Include scenario-based questions using listed technologies
        5. Match question difficulty to Seniority Level exactly
        6. If candidate mentions a listed technology, drill deeper with follow-ups
        """;
    }

    private String buildWeakQuestionsInstruction() {
        if (trackWeakQuestions == null || !trackWeakQuestions || weakQuestions == null || weakQuestions.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("""

            🎯 WEAK QUESTIONS PRACTICE - ENABLED:
            The candidate has previously struggled with these topics. ASK THESE QUESTIONS FIRST during this interview.
            This is a practice session to help them improve. Be encouraging and educational.

            Weak questions to ask first:
            """);

        for (String question : weakQuestions) {
            sb.append("- ").append(question).append("\n");
        }

        sb.append("""

            IMPORTANT:
            - Start with questions from the weak list above
            - Ask them in a slightly different way than before (to test genuine understanding)
            - If candidate answers correctly this time (>= 90% accuracy), they've made progress!
            - Be supportive: "Good, you're improving!" when they answer weak questions well
            - After asking the weak questions, continue with regular interview questions
            - Track their improvement on these specific topics
            """);

        return sb.toString();
    }

    private String getResponseLanguageInstruction() {
        if (interviewLanguage == null) {
            return "Respond in English";
        }
        return switch (interviewLanguage) {
            case "Ukrainian" -> "Respond in Ukrainian (Відповідай українською мовою)";
            case "English" -> "Respond in English";
            case "German" -> "Respond in German (Antworte auf Deutsch)";
            case "Polish" -> "Respond in Polish (Odpowiadaj po polsku)";
            default -> "Respond in English";
        };
    }
}

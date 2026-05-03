package com.voiceassistant.service;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.voiceassistant.dto.CodeSubmission;
import com.voiceassistant.dto.EvaluationResult;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
/**
 * Service for compiling and executing code using JDoodle API.
 * Supports multiple programming languages (Java, Python, JavaScript, etc).
 * Provides execution results or compilation error messages.
 * Falls back to mock compilation if JDoodle API credentials are not configured.
 */
@Slf4j
@Service
public class CodeCompilerService {
    private static final int MAX_ERRORS_DISPLAYED = 5;
    private static final String JDOODLE_API_URL = "https://api.jdoodle.com/v1/execute";
    @Value("${jdoodle.client-id}")
    private String clientId;
    @Value("${jdoodle.client-secret}")
    private String clientSecret;
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();
    private final Gson gson = new Gson();

    /**
     * Compiles and executes submitted code via JDoodle API.
     * Returns execution output or error messages if compilation fails.
     * Falls back to mock compilation if API is not configured.
     *
     * @param submission code submission with source code and programming language
     * @return EvaluationResult with execution output, errors, and success status
     */
    public EvaluationResult compileAndRun(CodeSubmission submission) {
        if (!isApiConfigured()) {
            log.warn("JDoodle API not configured, using mock compilation");
            return mockCompile(submission);
        }
        try {
            log.info("Compiling code for language: {}", submission.getLanguage());
            String sanitizedCode = sanitizeCode(submission.getCode());
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("script", sanitizedCode);
            requestBody.addProperty("language", mapLanguage(submission.getLanguage()));
            requestBody.addProperty("versionIndex", "0");
            requestBody.addProperty("clientId", clientId);
            requestBody.addProperty("clientSecret", clientSecret);
            RequestBody body = RequestBody.create(gson.toJson(requestBody), MediaType.get("application/json"));
            Request request = new Request.Builder()
                    .url(JDOODLE_API_URL)
                    .post(body)
                    .build();
            try (Response response = httpClient.newCall(request).execute()) {
                String responseBody = response.body() != null ? response.body().string() : "";
                if (responseBody.isEmpty()) {
                    return new EvaluationResult(0, "", "Empty response from compilation service", false);
                }
                return parseJDoodleResponse(responseBody);
            }
        } catch (IOException e) {
            log.error("Error compiling code: {}", e.getMessage(), e);
            return new EvaluationResult(0, "", "Connection error: " + e.getMessage(), false);
        } catch (Exception e) {
            log.error("Unexpected error, falling back to mock compilation: {}", e.getMessage(), e);
            return mockCompile(submission);
        }
    }
    /**
     * Parses JDoodle API JSON response into an EvaluationResult.
     * Extracts execution output, errors, and status information.
     *
     * @param responseBody raw JSON response from JDoodle API
     * @return EvaluationResult containing parsed output and error messages
     */
    private EvaluationResult parseJDoodleResponse(String responseBody) {
        JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
        if (jsonResponse.has("error") && !jsonResponse.get("error").isJsonNull()) {
            String error = jsonResponse.get("error").getAsString();
            log.error("JDoodle API error: {}", error);
            return new EvaluationResult(0, "", "API error: " + error, false);
        }
        String output = getJsonString(jsonResponse, "output", "");
        output = sanitizeOutput(output);
        int statusCode = getJsonInt(jsonResponse, "statusCode", -1);
        if (statusCode == 200) {
            if (hasCompilationError(output)) {
                return new EvaluationResult(0, "", "Compilation Error:\n" + formatError(output), false);
            } else if (output.isEmpty()) {
                return new EvaluationResult(0, "", "Code executed successfully (no output)", true);
            } else {
                return new EvaluationResult(0, "", "Output:\n" + output, true);
            }
        } else {
            return new EvaluationResult(0, "",
                    output.isEmpty() ? "Compilation Error (code: " + statusCode + ")" : formatError(output),
                    false);
        }
    }
    /**
     * Checks whether JDoodle API credentials are configured.
     *
     * @return true if credentials are set and non-default
     */
    private boolean isApiConfigured() {
        return clientId != null && !clientId.equals("your-client-id")
                && clientSecret != null && !clientSecret.equals("your-client-secret");
    }
    /**
     * Strips non-ASCII characters from the source code replacing them with spaces.
     *
     * @param code raw source code
     * @return sanitized ASCII-only source code
     */
    private String sanitizeCode(String code) {
        if (code == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : code.toCharArray()) {
            sb.append(c < 128 ? c : ' ');
        }
        return sb.toString();
    }
    /**
     * Sanitizes compilation output by removing duplicate error lines
     * and limiting the number of displayed errors.
     *
     * @param output raw compilation output
     * @return sanitized output string
     */
    private String sanitizeOutput(String output) {
        if (output == null) return "";
        String[] lines = output.split("\n");
        StringBuilder sb = new StringBuilder();
        int errorCount = 0;
        String lastErrorLine = "";
        for (String line : lines) {
            if (line.contains("error:") && line.equals(lastErrorLine)) {
                continue;
            }
            if (line.contains("error:")) {
                errorCount++;
                if (errorCount > MAX_ERRORS_DISPLAYED) {
                    if (errorCount == MAX_ERRORS_DISPLAYED + 1) {
                        sb.append("... and ").append(lines.length - MAX_ERRORS_DISPLAYED).append(" more errors\n");
                    }
                    continue;
                }
                lastErrorLine = line;
            }
            sb.append(line).append("\n");
        }
        return sb.toString().trim();
    }
    /**
     * Checks whether the compilation output contains error indicators.
     *
     * @param output compilation output string
     * @return true if output contains compilation or runtime errors
     */
    private boolean hasCompilationError(String output) {
        String lower = output.toLowerCase();
        return lower.contains("error:")
                || lower.contains("exception in thread")
                || lower.contains("compilation failed")
                || lower.contains("syntaxerror")
                || lower.contains("traceback")
                || lower.contains("cannot find symbol")
                || lower.contains("unmappable character");
    }
    /**
     * Formats raw error output into a human-readable error message.
     *
     * @param output raw error output
     * @return formatted error string
     */
    private String formatError(String output) {
        if (output.contains("unmappable character")) {
            return "Code contains Unicode characters.\n\n"
                    + "JDoodle only supports ASCII.\n"
                    + "Please use English comments and strings.";
        }
        String[] lines = output.split("\n");
        StringBuilder result = new StringBuilder();
        int errorCount = 0;
        for (String line : lines) {
            if (line.trim().isEmpty() || line.trim().equals("^")) {
                continue;
            }
            if (line.contains("error:") || line.contains("Error:")) {
                errorCount++;
                if (errorCount <= MAX_ERRORS_DISPLAYED) {
                    result.append(parseErrorLine(line)).append("\n");
                }
            }
        }
        if (errorCount > MAX_ERRORS_DISPLAYED) {
            result.append("\n... and ").append(errorCount - MAX_ERRORS_DISPLAYED).append(" more errors\n");
        }
        if (result.length() == 0) {
            return output.length() > 500 ? output.substring(0, 500) + "..." : output;
        }
        return result.toString().trim();
    }
    /**
     * Parses a single error line into a human-readable format with line number.
     *
     * @param line raw error line from compiler output
     * @return formatted error string with line number and message
     */
    private String parseErrorLine(String line) {
        try {
            int firstColon = line.indexOf(':');
            if (firstColon == -1) return "• " + line;
            int secondColon = line.indexOf(':', firstColon + 1);
            if (secondColon == -1) return "• " + line;
            String lineNumber = line.substring(firstColon + 1, secondColon).trim();
            int errorIdx = line.indexOf("error:");
            if (errorIdx == -1) errorIdx = line.indexOf("Error:");
            String message;
            if (errorIdx != -1 && errorIdx + 6 < line.length()) {
                message = line.substring(errorIdx + 6).trim();
            } else {
                message = line.substring(secondColon + 1).trim();
            }
            return "Line " + lineNumber + ": " + message;
        } catch (Exception e) {
            return "• " + line;
        }
    }
    /**
     * Provides a mock compilation result when JDoodle API is not available.
     * Performs basic validation of the submitted code.
     *
     * @param submission code submission to mock-compile
     * @return mock evaluation result
     */
    private EvaluationResult mockCompile(CodeSubmission submission) {
        String code = submission.getCode();
        String lang = submission.getLanguage() != null ? submission.getLanguage().toLowerCase() : "java";
        if (code == null || code.trim().isEmpty()) {
            return new EvaluationResult(0, "", "Code is empty", false);
        }
        boolean hasUnicode = false;
        for (char c : code.toCharArray()) {
            if (c >= 128) {
                hasUnicode = true;
                break;
            }
        }
        if (hasUnicode) {
            return new EvaluationResult(0, "",
                    "Warning: Code contains Unicode characters.\n\n"
                            + "JDoodle supports ASCII only.\n"
                            + "Please use English comments and strings.\n\n"
                            + "Code compiled in mock mode.",
                    true);
        }
        if (lang.equals("java") && !code.contains("class ")) {
            return new EvaluationResult(0, "", "Error: Java code must contain a class declaration", false);
        }
        return new EvaluationResult(0, "",
                "Code compiled successfully\n\nTo enable real compilation, configure JDoodle API credentials.",
                true);
    }
    /**
     * Safely retrieves a string value from a JSON object.
     *
     * @param json         JSON object to read from
     * @param key          key to look up
     * @param defaultValue fallback value if key is missing or null
     * @return string value or default
     */
    private String getJsonString(JsonObject json, String key, String defaultValue) {
        if (json.has(key)) {
            JsonElement element = json.get(key);
            if (element != null && !element.isJsonNull()) {
                return element.getAsString();
            }
        }
        return defaultValue;
    }
    /**
     * Safely retrieves an integer value from a JSON object.
     *
     * @param json         JSON object to read from
     * @param key          key to look up
     * @param defaultValue fallback value if key is missing or null
     * @return integer value or default
     */
    private int getJsonInt(JsonObject json, String key, int defaultValue) {
        if (json.has(key)) {
            JsonElement element = json.get(key);
            if (element != null && !element.isJsonNull()) {
                return element.getAsInt();
            }
        }
        return defaultValue;
    }
    /**
     * Maps a human-readable language name to the JDoodle API language identifier.
     *
     * @param language language name from the client
     * @return JDoodle API language identifier
     */
    private String mapLanguage(String language) {
        if (language == null) return "python3";
        return switch (language.toLowerCase()) {
            case "java" -> "java";
            case "python" -> "python3";
            case "javascript" -> "nodejs";
            case "c++" -> "cpp17";
            case "c" -> "c";
            default -> "python3";
        };
    }
}
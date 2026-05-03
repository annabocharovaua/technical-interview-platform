package com.voiceassistant.controller;
import com.voiceassistant.service.JobParserService;
import com.voiceassistant.service.JobParserService.JobParseResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.Map;
@Slf4j
@RestController
@RequestMapping("/api/job")
@RequiredArgsConstructor
public class JobController {
    private final JobParserService jobParserService;
    @PostMapping("/parse")
    public ResponseEntity<Map<String, Object>> parseJob(@RequestBody Map<String, String> request) {
        String url = request.get("url");
        String rawText = request.get("text");
        if ((url == null || url.isBlank()) && (rawText == null || rawText.isBlank())) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", "URL or text is required"
            ));
        }
        try {
            JobParseResult result = (rawText != null && !rawText.isBlank())
                    ? jobParserService.parseJobText(rawText)
                    : jobParserService.parseJobUrl(url);
            Map<String, Object> response = new HashMap<>();
            response.put("success", result.success());
            if (result.success()) {
                response.put("title", result.title());
                response.put("company", result.company());
                response.put("seniority", result.seniority());
                response.put("description", result.description());
                response.put("requirements", result.requirements());
                response.put("technologies", result.technologies());
                response.put("focusAreas", result.focusAreas());
            } else {
                response.put("error", result.errorMessage());
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error parsing job", e);
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }
}
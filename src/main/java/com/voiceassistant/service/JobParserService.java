package com.voiceassistant.service;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.bonigarcia.wdm.WebDriverManager;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
/**
 * Service for parsing job postings using Selenium + headless Chrome.
 * Handles JavaScript-rendered SPA pages like LinkedIn, Djinni, DOU, Glassdoor.
 */
@Slf4j
@Service
public class JobParserService {
    private static final int PAGE_TIMEOUT_SEC = 30;
    private static final int MAX_CONTENT_LENGTH = 20000;
    private static final String OPENAI_URL = "https://api.openai.com/v1/chat/completions";
    @Value("${openai.api-key}")
    private String openAiKey;
    private boolean driverReady = false;
    private final ReentrantLock driverLock = new ReentrantLock();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();
    private final Gson gson = new Gson();
    @PostConstruct
    public void init() {
        try {
            log.info("Initializing Selenium WebDriverManager...");
            WebDriverManager.chromedriver().setup();
            driverReady = true;
            log.info("✅ Selenium ChromeDriver ready");
        } catch (Exception e) {
            log.error("❌ Failed to initialize WebDriverManager: {}", e.getMessage(), e);
            log.warn("⚠️ Job URL parsing via Selenium will NOT work. Text fallback still available.");
        }
    }
    @PreDestroy
    public void cleanup() {
        log.info("JobParserService shutting down");
    }
    /**
     * Create a fresh ChromeDriver for each request (safer than shared instance).
     */
    private WebDriver createDriver() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments(
                "--headless=new",
                "--disable-gpu",
                "--no-sandbox",
                "--disable-dev-shm-usage",
                "--disable-blink-features=AutomationControlled",
                "--window-size=1920,1080",
                "--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                        "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
                "--lang=en-US",
                "--disable-extensions",
                "--disable-notifications"
        );
        options.setExperimentalOption("prefs", java.util.Map.of(
                "profile.managed_default_content_settings.images", 2
        ));
        WebDriver driver = new ChromeDriver(options);
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(PAGE_TIMEOUT_SEC));
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(3));
        return driver;
    }
    /**
     * Parses a job posting URL by rendering it in headless Chrome.
     */
    public JobParseResult parseJobUrl(String url) {
        if (!driverReady) {
            return JobParseResult.failure(
                    "Browser parser is unavailable. Please paste the job text manually."
            );
        }
        log.info("🌐 Fetching job URL with Selenium: {}", url);
        WebDriver driver = null;
        try {
            driver = createDriver();
            driver.get(url);
            waitForJobContent(driver, url);
            dismissPopups(driver);
            Thread.sleep(1500);
            String content = extractJobContent(driver, url);
            if (content == null || content.length() < 300) {
                log.warn("Content too short ({}) for URL: {}",
                        content == null ? 0 : content.length(), url);
                return JobParseResult.failure(
                        "Page is blocked or requires authorization. Try pasting the text manually."
                );
            }
            if (content.length() > MAX_CONTENT_LENGTH) {
                content = content.substring(0, MAX_CONTENT_LENGTH);
            }
            log.info("✅ Extracted {} chars from {}", content.length(), url);
            return extractJobInfoWithAI(content);
        } catch (TimeoutException e) {
            log.error("Page load timeout for URL: {}", url);
            return JobParseResult.failure("Page did not load in time");
        } catch (Exception e) {
            log.error("Error parsing job URL {}: {}", url, e.getMessage(), e);
            return JobParseResult.failure("Loading error: " + e.getMessage());
        } finally {
            if (driver != null) {
                try {
                    driver.quit();
                } catch (Exception ignored) {}
            }
        }
    }
    /**
     * Wait for job-specific content based on URL.
     */
    private void waitForJobContent(WebDriver driver, String url) {
        String lowerUrl = url.toLowerCase();
        String selector = null;
        if (lowerUrl.contains("linkedin.com")) {
            selector = ".jobs-description, .job-view-layout, .show-more-less-html";
        } else if (lowerUrl.contains("djinni.co")) {
            selector = ".job-post, .job-post__description, section.page-section";
        } else if (lowerUrl.contains("dou.ua")) {
            selector = ".b-typo, .vacancy-section, article";
        } else if (lowerUrl.contains("glassdoor")) {
            selector = ".jobDescriptionContent, .desc";
        } else if (lowerUrl.contains("indeed.com")) {
            selector = "#jobDescriptionText, .jobsearch-JobComponent";
        } else if (lowerUrl.contains("work.ua")) {
            selector = "#job-description, .wordwrap";
        } else if (lowerUrl.contains("rabota.ua")) {
            selector = ".vacancy-description, [data-id='description']";
        }
        if (selector != null) {
            try {
                WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
                wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(selector)));
                log.debug("✅ Found job content selector: {}", selector);
            } catch (Exception e) {
                log.debug("Selector '{}' not found in 10s, continuing anyway", selector);
            }
        } else {
            try {
                WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(8));
                wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("body")));
            } catch (Exception ignored) {}
        }
    }
    /**
     * Try to close popups/cookie banners.
     */
    private void dismissPopups(WebDriver driver) {
        String[] selectors = {
                "button[aria-label*='Dismiss']",
                "button[aria-label*='Close']",
                "button[aria-label*='close']",
                ".cookie-banner button",
                "#onetrust-accept-btn-handler",
                ".modal button[class*='close']"
        };
        for (String sel : selectors) {
            try {
                List<WebElement> buttons = driver.findElements(By.cssSelector(sel));
                for (WebElement btn : buttons) {
                    if (btn.isDisplayed()) {
                        try {
                            btn.click();
                            log.debug("Closed popup: {}", sel);
                            Thread.sleep(300);
                            break;
                        } catch (Exception ignored) {}
                    }
                }
            } catch (Exception ignored) {}
        }
    }
    /**
     * Extract content from page using site-specific selectors.
     */
    private String extractJobContent(WebDriver driver, String url) {
        String lowerUrl = url.toLowerCase();
        String[] selectors = null;
        if (lowerUrl.contains("linkedin.com")) {
            selectors = new String[]{
                    ".jobs-description__content",
                    ".show-more-less-html__markup",
                    ".description__text",
                    "main"
            };
        } else if (lowerUrl.contains("djinni.co")) {
            selectors = new String[]{
                    ".job-post",
                    "main .page-section",
                    "main"
            };
        } else if (lowerUrl.contains("dou.ua")) {
            selectors = new String[]{
                    ".b-typo.vacancy-section",
                    ".l-vacancy",
                    "article",
                    "main"
            };
        } else if (lowerUrl.contains("glassdoor")) {
            selectors = new String[]{
                    ".jobDescriptionContent",
                    "main"
            };
        } else if (lowerUrl.contains("indeed.com")) {
            selectors = new String[]{
                    "#jobDescriptionText",
                    ".jobsearch-JobComponent-description",
                    "main"
            };
        } else if (lowerUrl.contains("work.ua")) {
            selectors = new String[]{"#job-description", ".card", "main"};
        } else if (lowerUrl.contains("rabota.ua")) {
            selectors = new String[]{".vacancy-description", "main"};
        }
        if (selectors != null) {
            for (String sel : selectors) {
                try {
                    WebElement el = driver.findElement(By.cssSelector(sel));
                    String text = el.getText();
                    if (text != null && text.length() > 300) {
                        log.debug("Extracted content via selector: {}", sel);
                        return cleanText(text);
                    }
                } catch (NoSuchElementException ignored) {
                } catch (Exception e) {
                    log.debug("Selector {} failed: {}", sel, e.getMessage());
                }
            }
        }
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            String text = (String) js.executeScript("""
                    const clone = document.body.cloneNode(true);
                    clone.querySelectorAll('nav, header, footer, script, style, noscript, iframe, aside').forEach(el => el.remove());
                    return clone.innerText;
                    """);
            return cleanText(text);
        } catch (Exception e) {
            log.warn("JS extraction failed: {}", e.getMessage());
            try {
                return cleanText(driver.findElement(By.tagName("body")).getText());
            } catch (Exception ex) {
                return null;
            }
        }
    }
    private String cleanText(String text) {
        if (text == null) return "";
        return text.replaceAll("\\s+", " ").trim();
    }
    /**
     * Parses job posting from pasted text (fallback).
     */
    public JobParseResult parseJobText(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return JobParseResult.failure("Job posting text is empty");
        }
        String text = rawText.length() > MAX_CONTENT_LENGTH
                ? rawText.substring(0, MAX_CONTENT_LENGTH)
                : rawText;
        return extractJobInfoWithAI(text);
    }
    /**
     * Uses OpenAI to extract structured job info from page content.
     */
    private JobParseResult extractJobInfoWithAI(String pageText) {
        try {
            String prompt = buildExtractionPrompt(pageText);
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("model", "gpt-4o-mini");
            requestBody.addProperty("temperature", 0.2);
            JsonObject responseFormat = new JsonObject();
            responseFormat.addProperty("type", "json_object");
            requestBody.add("response_format", responseFormat);
            JsonArray messages = new JsonArray();
            JsonObject systemMsg = new JsonObject();
            systemMsg.addProperty("role", "system");
            systemMsg.addProperty("content",
                    "You are a professional job posting parser. Extract structured " +
                            "information accurately. Respond with valid JSON only.");
            messages.add(systemMsg);
            JsonObject userMsg = new JsonObject();
            userMsg.addProperty("role", "user");
            userMsg.addProperty("content", prompt);
            messages.add(userMsg);
            requestBody.add("messages", messages);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(OPENAI_URL))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + openAiKey)
                    .timeout(Duration.ofSeconds(45))
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(requestBody)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.error("OpenAI API error {}: {}", response.statusCode(), response.body());
                return JobParseResult.failure("AI service is unavailable");
            }
            JsonObject responseJson = gson.fromJson(response.body(), JsonObject.class);
            String content = responseJson
                    .getAsJsonArray("choices")
                    .get(0).getAsJsonObject()
                    .getAsJsonObject("message")
                    .get("content").getAsString();
            return parseAiResponse(content);
        } catch (Exception e) {
            log.error("OpenAI parsing error: {}", e.getMessage(), e);
            return JobParseResult.failure("AI error: " + e.getMessage());
        }
    }
    private String buildExtractionPrompt(String pageText) {
        return """
                Analyze this job posting and extract structured information.
                STRICT RULES:
                - If this is NOT a job posting (404, login wall, homepage), return {"isJobPosting": false, "reason": "..."}
                - Extract ONLY information actually present — do not invent
                - Use exact technology names (e.g., "Spring Boot", "PostgreSQL")
                - For focusAreas: identify 4-7 TECHNICAL topics the interview should prioritize
                Return JSON:
                {
                  "isJobPosting": true,
                  "title": "Senior Java Developer",
                  "company": "Company Name",
                  "seniority": "Senior",
                  "description": "2-3 sentence role summary",
                  "responsibilities": ["responsibility 1", "responsibility 2"],
                  "requiredSkills": ["Java 17", "Spring Boot", "PostgreSQL"],
                  "niceToHave": ["Kubernetes", "AWS"],
                  "technologies": ["Java", "Spring", "Docker"],
                  "yearsOfExperience": "5+",
                  "focusAreas": ["microservices architecture", "concurrent programming", "REST API design", "database optimization"]
                }
                === JOB CONTENT ===
                """ + pageText + "\n=== END ===";
    }
    private JobParseResult parseAiResponse(String jsonContent) {
        try {
            JsonObject json = gson.fromJson(jsonContent, JsonObject.class);
            if (json.has("isJobPosting") && !json.get("isJobPosting").getAsBoolean()) {
                String reason = getString(json, "reason");
                return JobParseResult.failure(
                        "Could not find job posting on the page" +
                                (reason.isBlank() ? "" : ": " + reason)
                );
            }
            String title = getString(json, "title");
            String company = getString(json, "company");
            String seniority = getString(json, "seniority");
            String description = getString(json, "description");
            String yearsOfExperience = getString(json, "yearsOfExperience");
            List<String> responsibilities = getStringArray(json, "responsibilities");
            List<String> requiredSkills = getStringArray(json, "requiredSkills");
            List<String> technologies = getStringArray(json, "technologies");
            List<String> focusAreas = getStringArray(json, "focusAreas");
            List<String> allRequirements = new ArrayList<>(requiredSkills);
            if (!yearsOfExperience.isBlank()) {
                allRequirements.add(yearsOfExperience + " years experience");
            }
            StringBuilder fullDescription = new StringBuilder();
            if (!title.isBlank()) fullDescription.append("Position: ").append(title).append("\n");
            if (!company.isBlank()) fullDescription.append("Company: ").append(company).append("\n");
            if (!seniority.isBlank()) fullDescription.append("Level: ").append(seniority).append("\n\n");
            if (!description.isBlank()) fullDescription.append(description).append("\n\n");
            if (!responsibilities.isEmpty()) {
                fullDescription.append("Responsibilities:\n");
                responsibilities.forEach(r -> fullDescription.append("- ").append(r).append("\n"));
            }
            log.info("✅ Parsed: '{}' at '{}' | skills: {} | focus: {}",
                    title, company, requiredSkills.size(), focusAreas.size());
            return new JobParseResult(
                    true,
                    fullDescription.toString().trim(),
                    allRequirements,
                    title, company, seniority,
                    technologies, focusAreas,
                    null
            );
        } catch (Exception e) {
            log.error("Error parsing AI JSON: {}", e.getMessage(), e);
            return JobParseResult.failure("Could not parse structure");
        }
    }
    private String getString(JsonObject json, String key) {
        if (json.has(key) && !json.get(key).isJsonNull()) {
            return json.get(key).getAsString();
        }
        return "";
    }
    private List<String> getStringArray(JsonObject json, String key) {
        List<String> result = new ArrayList<>();
        if (json.has(key) && json.get(key).isJsonArray()) {
            json.getAsJsonArray(key).forEach(el -> {
                if (!el.isJsonNull()) result.add(el.getAsString());
            });
        }
        return result;
    }
    public record JobParseResult(
            boolean success,
            String description,
            List<String> requirements,
            String title,
            String company,
            String seniority,
            List<String> technologies,
            List<String> focusAreas,
            String errorMessage
    ) {
        public static JobParseResult failure(String error) {
            return new JobParseResult(
                    false, null, Collections.emptyList(),
                    null, null, null,
                    Collections.emptyList(), Collections.emptyList(),
                    error
            );
        }
    }
}
package com.voiceassistant.config;
import com.theokanning.openai.service.OpenAiService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Duration;
/**
 * Spring configuration class for OpenAI service initialization.
 * Provides OpenAiService bean with configured API key and timeout settings.
 */
@Configuration
public class OpenAIConfig {
  @Value("${openai.api-key}")
  private String apiKey;
  /**
   * Creates and configures OpenAiService bean with 60-second timeout.
   *
   * @return configured OpenAiService instance
   */
  @Bean
  public OpenAiService openAiService() {
    return new OpenAiService(apiKey, Duration.ofSeconds(60));
  }
}
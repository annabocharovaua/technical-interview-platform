package com.voiceassistant;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
/**
 * Main entry point for the Voice Assistant Spring Boot application.
 * Initializes and starts the application context.
 */
@SpringBootApplication
public class VoiceAssistantApplication {
  public static void main(String[] args) {
    SpringApplication.run(VoiceAssistantApplication.class, args);
  }
}
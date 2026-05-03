package com.voiceassistant.config;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ses.SesClient;
/**
 * Spring configuration class for AWS SES (Simple Email Service) initialization.
 * Provides SesClient bean for sending emails.
 * Uses environment variables for region and credentials configuration.
 * Falls back gracefully if AWS is not configured.
 */
@Slf4j
@Configuration
public class AwsSesConfig {
    @Value("${aws.region:us-east-1}")
    private String awsRegion;
    @Value("${aws.credentials.access-key:}")
    private String accessKey;
    @Value("${aws.credentials.secret-key:}")
    private String secretKey;
    /**
     * Creates and configures SesClient bean
     * Requires AWS credentials to be configured via environment variables:
     * - AWS_ACCESS_KEY_ID (or aws.credentials.access-key property)
     * - AWS_SECRET_ACCESS_KEY (or aws.credentials.secret-key property)
     * - AWS_REGION (or aws.region property, defaults to us-east-1)
     *
     * @return configured SesClient instance, or null if credentials not configured
     */
    @Bean
    public SesClient sesClient() {
        if (accessKey == null || accessKey.isEmpty() || secretKey == null || secretKey.isEmpty()) {
            log.warn("AWS credentials not configured (AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY required)");
            log.warn("Email service (AWS SES) will not be available");
            log.warn("Set environment variables or application properties to enable email functionality");
            return null;
        }
        try {
            log.info("Initializing AWS SES Client with region: {}", awsRegion);
            AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKey, secretKey);
            return SesClient.builder()
                    .region(Region.of(awsRegion))
                    .credentialsProvider(StaticCredentialsProvider.create(credentials))
                    .build();
        } catch (Exception e) {
            log.warn("Failed to initialize AWS SES Client with region {}: {}. Attempting with default region us-east-1",
                    awsRegion, e.getMessage());
            try {
                AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKey, secretKey);
                return SesClient.builder()
                        .region(Region.US_EAST_1)
                        .credentialsProvider(StaticCredentialsProvider.create(credentials))
                        .build();
            } catch (Exception e2) {
                log.error("Failed to initialize AWS SES Client. Email service will not be available.", e2);
                return null;
            }
        }
    }
}
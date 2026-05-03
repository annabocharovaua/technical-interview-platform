package com.voiceassistant.service;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.*;
import java.util.Base64;
/**
 * Service for sending emails via AWS SES (Simple Email Service).
 * Handles OTP delivery and interview reports with PDF attachments.
 * Gracefully handles cases when AWS SES is not configured.
 */
@Slf4j
@Service
public class EmailService {
    @Value("${aws.ses.sender-email:appliance.project1@gmail.com}")
    private String senderEmail;
    @Autowired(required = false)
    private SesClient sesClient;
    /**
     * Sends OTP code to user's email
     */
    public boolean sendOtpEmail(String recipientEmail, String otpCode) {
        if (sesClient == null) {
            log.warn("AWS SES not configured. OTP email not sent to {}", recipientEmail);
            log.warn("OTP Code for {} (expires in 10 minutes): {}", recipientEmail, otpCode);
            return false;
        }
        try {
            String subject = "Password Reset - Your OTP Code";
            String body = "<h2>Password Reset Request</h2>" +
                    "<p>Your OTP code is: <strong style='font-size: 24px; color: #667eea;'>" + otpCode + "</strong></p>" +
                    "<p>This code is valid for 10 minutes.</p>" +
                    "<p>If you didn't request this, please ignore this email.</p>";
            return sendEmail(recipientEmail, subject, body);
        } catch (Exception e) {
            log.error("Failed to send OTP email to {}: {}", recipientEmail, e.getMessage());
            return false;
        }
    }
    /**
     * Sends interview report with PDF attachment to user's email
     */
    public boolean sendInterviewReportWithPdf(String recipientEmail, String userName, byte[] pdfContent) {
        if (sesClient == null) {
            log.warn("AWS SES not configured. Interview report PDF not sent to {}", recipientEmail);
            log.info("PDF content size: {} bytes", pdfContent.length);
            return false;
        }
        try {
            String subject = "Your Interview Report 📋";
            String body = "<h2>Your Interview Report</h2>" +
                    "<p>Hi " + userName + ",</p>" +
                    "<p>Thank you for completing your interview! Your detailed PDF report is attached to this email.</p>" +
                    "<p><strong>What's in your report:</strong></p>" +
                    "<ul>" +
                    "<li>📊 Overall Performance Score</li>" +
                    "<li>📈 Category-wise Analysis</li>" +
                    "<li>💪 Strengths and Improvements</li>" +
                    "<li>📚 Personalized Learning Resources</li>" +
                    "<li>❓ Weak Questions for Practice</li>" +
                    "</ul>" +
                    "<p>You can also download your report anytime from your account dashboard.</p>" +
                    "<p>Best regards,<br><strong>Voice Assistant Team</strong></p>";
            return sendEmailWithAttachment(recipientEmail, subject, body, pdfContent, "Interview_Report.pdf");
        } catch (Exception e) {
            log.error("Failed to send interview report to {}: {}", recipientEmail, e.getMessage());
            return false;
        }
    }
    /**
     * Generic method to send email via AWS SES
     */
    private boolean sendEmail(String recipientEmail, String subject, String body) {
        if (sesClient == null) {
            log.warn("AWS SES client not available");
            return false;
        }
        try {
            SendEmailRequest request = SendEmailRequest.builder()
                    .source(senderEmail)
                    .destination(Destination.builder()
                            .toAddresses(recipientEmail)
                            .build())
                    .message(Message.builder()
                            .subject(Content.builder()
                                    .data(subject)
                                    .charset("UTF-8")
                                    .build())
                            .body(Body.builder()
                                    .html(Content.builder()
                                            .data(body)
                                            .charset("UTF-8")
                                            .build())
                                    .build())
                            .build())
                    .build();
            SendEmailResponse result = sesClient.sendEmail(request);
            log.info("Email sent successfully to {} with Message ID: {}", recipientEmail, result.messageId());
            return true;
        } catch (SesException e) {
            log.error("SES Error sending email: {}", e.awsErrorDetails().errorMessage());
            return false;
        } catch (Exception e) {
            log.error("Error sending email: {}", e.getMessage());
            return false;
        }
    }
    /**
     * Send email with PDF attachment using MIME format
     */
    private boolean sendEmailWithAttachment(String recipientEmail, String subject, String body,
                                          byte[] pdfContent, String fileName) {
        if (sesClient == null) {
            log.warn("AWS SES client not available");
            return false;
        }
        try {
            String boundary = "boundary_" + System.currentTimeMillis();
            String mimeBody = buildMimeMessage(recipientEmail, subject, body, pdfContent, fileName, boundary);
            SendRawEmailRequest request = SendRawEmailRequest.builder()
                    .rawMessage(RawMessage.builder()
                            .data(SdkBytes.fromByteArray(mimeBody.getBytes()))
                            .build())
                    .source(senderEmail)
                    .build();
            SendRawEmailResponse result = sesClient.sendRawEmail(request);
            log.info("Email with PDF attachment sent successfully to {} with Message ID: {}", recipientEmail, result.messageId());
            return true;
        } catch (SesException e) {
            log.error("SES Error sending email with attachment: {}", e.awsErrorDetails().errorMessage());
            return false;
        } catch (Exception e) {
            log.error("Error sending email with attachment: {}", e.getMessage());
            return false;
        }
    }
    /**
     * Build MIME message with PDF attachment
     */
    private String buildMimeMessage(String recipientEmail, String subject, String body,
                                   byte[] pdfContent, String fileName, String boundary) {
        StringBuilder sb = new StringBuilder();
        sb.append("From: ").append(senderEmail).append("\r\n");
        sb.append("To: ").append(recipientEmail).append("\r\n");
        sb.append("Subject: ").append(subject).append("\r\n");
        sb.append("MIME-Version: 1.0\r\n");
        sb.append("Content-Type: multipart/mixed; boundary=\"").append(boundary).append("\"\r\n\r\n");
        sb.append("--").append(boundary).append("\r\n");
        sb.append("Content-Type: text/html; charset=UTF-8\r\n");
        sb.append("Content-Transfer-Encoding: 7bit\r\n\r\n");
        sb.append(body).append("\r\n\r\n");
        sb.append("--").append(boundary).append("\r\n");
        sb.append("Content-Type: application/pdf; name=\"").append(fileName).append("\"\r\n");
        sb.append("Content-Transfer-Encoding: base64\r\n");
        sb.append("Content-Disposition: attachment; filename=\"").append(fileName).append("\"\r\n\r\n");
        String encodedPdf = Base64.getEncoder().encodeToString(pdfContent);
        int index = 0;
        while (index < encodedPdf.length()) {
            sb.append(encodedPdf, index, Math.min(index + 76, encodedPdf.length())).append("\r\n");
            index += 76;
        }
        sb.append("\r\n--").append(boundary).append("--");
        return sb.toString();
    }
}
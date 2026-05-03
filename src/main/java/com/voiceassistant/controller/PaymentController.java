package com.voiceassistant.controller;
import com.voiceassistant.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
/**
 * REST controller for payment processing.
 * Handles subscription and payment transactions.
 * All business logic delegated to {@link PaymentService}.
 */
@Slf4j
@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
public class PaymentController {
    private final PaymentService paymentService;
    /**
     * Processes a payment transaction.
     *
     * @param request payment details containing cardNumber, amount and duration
     * @return transaction result with status and ID
     */
    @PostMapping("/process")
    public ResponseEntity<Map<String, Object>> processPayment(@RequestBody Map<String, Object> request) {
        try {
            String cardNumber = (String) request.get("cardNumber");
            Number amount = (Number) request.get("amount");
            Number duration = (Number) request.get("duration");
            if (cardNumber == null || amount == null || duration == null) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", true,
                        "message", "Missing required fields: cardNumber, amount, duration"
                ));
            }
            Map<String, Object> result = paymentService.processPayment(cardNumber, amount, duration);
            if ((Boolean) result.get("success")) {
                return ResponseEntity.ok(result);
            } else {
                return ResponseEntity.badRequest().body(result);
            }
        } catch (Exception e) {
            log.error("Error processing payment: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                    "error", true,
                    "message", "Payment processing failed: " + e.getMessage()
            ));
        }
    }
    /**
     * Returns the status of a payment by its ID.
     *
     * @param paymentId payment ID to check
     * @return payment status information
     */
    @GetMapping("/status/{paymentId}")
    public ResponseEntity<Map<String, Object>> getPaymentStatus(@PathVariable String paymentId) {
        try {
            if (paymentId == null || paymentId.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", true,
                        "message", "Payment ID is required"
                ));
            }
            Map<String, Object> status = paymentService.getPaymentStatus(paymentId);
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            log.error("Error getting payment status: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                    "error", true,
                    "message", "Failed to get payment status: " + e.getMessage()
            ));
        }
    }
}
package com.voiceassistant.service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.UUID;
/**
 * Service for payment processing and transaction management.
 * Simulates card payment authorization and transaction tracking.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {
    private static final int MIN_CARD_NUMBER_LENGTH = 13;
    private static final String DECLINED_TEST_CARD = "4000000000000002";
    private static final String PAYMENT_ID_PREFIX = "PAY-";
    private static final int PAYMENT_ID_SUFFIX_LENGTH = 8;
    /**
     * Processes a card payment transaction for a given amount and interview duration.
     * Validates all input parameters before processing.
     *
     * @param cardNumber credit card number to charge
     * @param amount     payment amount (must be positive)
     * @param duration   interview duration in minutes being purchased (must be positive)
     * @return map containing success flag and either a paymentId or an error message
     * @throws IllegalArgumentException if any parameter fails validation
     */
    public Map<String, Object> processPayment(String cardNumber, Number amount, Number duration) {
        validatePaymentInputs(cardNumber, amount, duration);
        log.info("Processing payment: ${} for {} minutes", amount, duration);
        if (cardNumber.length() < MIN_CARD_NUMBER_LENGTH) {
            log.warn("Invalid card number provided");
            return Map.of(
                    "success", false,
                    "error", "Invalid card number"
            );
        }
        if (cardNumber.startsWith(DECLINED_TEST_CARD)) {
            log.warn("Card declined for number ending in: {}",
                    cardNumber.substring(cardNumber.length() - 4));
            return Map.of(
                    "success", false,
                    "error", "Card declined"
            );
        }
        String paymentId = generatePaymentId();
        log.info("Payment successful: {} for ${}", paymentId, amount);
        return Map.of(
                "success", true,
                "paymentId", paymentId,
                "amount", amount,
                "duration", duration
        );
    }
    /**
     * Validates all payment input parameters.
     *
     * @param cardNumber credit card number
     * @param amount     payment amount
     * @param duration   interview duration in minutes
     * @throws IllegalArgumentException if any validation fails
     */
    private void validatePaymentInputs(String cardNumber, Number amount, Number duration) {
        if (cardNumber == null || cardNumber.isBlank()) {
            throw new IllegalArgumentException("Card number cannot be null or empty");
        }
        if (amount == null) {
            throw new IllegalArgumentException("Amount cannot be null");
        }
        if (duration == null) {
            throw new IllegalArgumentException("Duration cannot be null");
        }
        double amountValue = amount.doubleValue();
        if (amountValue <= 0) {
            throw new IllegalArgumentException("Amount must be positive, got: " + amountValue);
        }
        double durationValue = duration.doubleValue();
        if (durationValue <= 0) {
            throw new IllegalArgumentException("Duration must be positive, got: " + durationValue);
        }
        if (amountValue > 999999) {
            throw new IllegalArgumentException("Amount exceeds maximum limit: " + amountValue);
        }
        if (durationValue > 1440) {
            throw new IllegalArgumentException("Duration exceeds maximum (24 hours): " + durationValue);
        }
    }
    /**
     * Retrieves the status of a previously initiated payment by its ID.
     *
     * @param paymentId payment ID returned from {@link #processPayment}
     * @return map containing the payment ID and its current status
     */
    public Map<String, Object> getPaymentStatus(String paymentId) {
        log.info("Getting status for payment: {}", paymentId);
        return Map.of(
                "paymentId", paymentId,
                "status", "completed"
        );
    }
    /**
     * Generates a unique payment identifier with the standard prefix.
     *
     * @return formatted payment ID string
     */
    private String generatePaymentId() {
        String suffix = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, PAYMENT_ID_SUFFIX_LENGTH)
                .toUpperCase();
        return PAYMENT_ID_PREFIX + suffix;
    }
}
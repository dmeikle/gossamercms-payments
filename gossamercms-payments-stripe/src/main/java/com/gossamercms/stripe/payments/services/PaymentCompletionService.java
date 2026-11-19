package com.gossamercms.stripe.payments.services;

import com.gossamercms.mvc.jwt.CurrentJwtUser;

import java.math.BigDecimal;
import java.util.UUID;

public interface PaymentCompletionService {
    //orderId, "stripe", session.getId(), paymentIntentId,
    //                amount, currency, false
    public void complete(
            UUID purchaseId,
            String provider,
            String checkoutId,
            String paymentId,
            BigDecimal amount,
            String currency,
            boolean cancelled
    );
}

package com.gossamercms.stripe.payments.providers;

import com.gossamercms.mvc.annotations.ModuleProvider;
import com.gossamercms.stripe.payments.payments.PaymentAdapter;
import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.param.RefundCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;

import java.math.BigDecimal;
import java.net.URI;
import java.util.*;

@Primary
@ModuleProvider
@ConditionalOnProperty(name = "gossamercms.stripe.mock.enabled", havingValue = "false", matchIfMissing = true)
public class StripePaymentProvider implements PaymentAdapter {

    private final StripeClient stripeClient;

    public StripePaymentProvider(StripeClient stripeClient) {
        this.stripeClient = stripeClient;
    }

    @Override
    public String provider() {
        return "stripe";
    }

    @Override
    public Checkout createCheckout(CheckoutRequest request) {
        ValidatedCheckout checkout = validate(request);
        SessionCreateParams.Builder params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setCustomerEmail(request.customerEmail())
                .setClientReferenceId(request.purchaseId().toString())
                .setSuccessUrl(request.successUrl())
                .setCancelUrl(request.cancelUrl())
                .putMetadata("order_id", request.purchaseId().toString())
                .setPaymentIntentData(SessionCreateParams.PaymentIntentData.builder()
                        .putMetadata("order_id", request.purchaseId().toString())
                        .build());

        for (Line item : request.items()) {

            SessionCreateParams.LineItem.PriceData.ProductData productData =
                    SessionCreateParams.LineItem.PriceData.ProductData.builder()
                            .setName(item.productName())
                            .putMetadata("product_id", item.productId().toString())
                            .build();

            int currencyDigits = Currency.getInstance(
                    request.currency().toUpperCase()
            ).getDefaultFractionDigits();

            SessionCreateParams.LineItem.PriceData priceData =
                    SessionCreateParams.LineItem.PriceData.builder()
                            .setCurrency(request.currency().toLowerCase())
                            .setUnitAmount(toMinorUnits(item.unitPrice(), currencyDigits))
                            .setProductData(productData)
                            .build();

            params.addLineItem(
                    SessionCreateParams.LineItem.builder()
                            .setQuantity((long) item.quantity())
                            .setPriceData(priceData)
                            .build()
            );
        }

        try {
            Session session = stripeClient.checkout().sessions().create(
                    params.build(), requestOptions(request.purchaseId()));
            if (session == null || session.getId() == null || session.getUrl() == null) {
                throw new IllegalStateException("Stripe did not return a checkout session ID and URL");
            }
            return new Checkout(session.getId(), session.getUrl());
        } catch (StripeException exception) {
            throw providerFailure("create Checkout Session", exception);
        }
    }

    @Override
    public boolean cancelCheckout(UUID operationId, String checkoutId) {
        requireOperationId(operationId);
        if (checkoutId == null || checkoutId.isBlank()) {
            throw new IllegalArgumentException("Stripe Checkout Session ID is required");
        }

        try {
            Session session = stripeClient.checkout().sessions().retrieve(checkoutId);
            if (session == null || !operationId.toString().equals(session.getClientReferenceId())) {
                throw new IllegalStateException("Stripe Checkout Session does not match the operation");
            }
            if ("expired".equals(session.getStatus())) {
                return true;
            }
            if (!"open".equals(session.getStatus())) {
                return false;
            }
            Session expired = stripeClient.checkout().sessions().expire(
                    checkoutId, requestOptions(operationId, "expire"));
            return "expired".equals(expired.getStatus());
        } catch (StripeException exception) {
            throw providerFailure("expire Checkout Session", exception);
        }
    }

    @Override
    public PaymentAdapter.Refund refund(
            UUID operationId, String paymentId, BigDecimal amount, String currency, String reason) {
        requireOperationId(operationId);
        if (paymentId == null || paymentId.isBlank()) {
            throw new IllegalArgumentException("Stripe PaymentIntent ID is required for a refund");
        }
        Currency stripeCurrency = currency(currency);
        long amountInMinorUnits = toMinorUnits(amount, stripeCurrency.getDefaultFractionDigits());
        if (amountInMinorUnits <= 0) {
            throw new IllegalArgumentException("Refund amount must be greater than zero");
        }

        RefundCreateParams.Builder params = RefundCreateParams.builder()
                .setPaymentIntent(paymentId)
                .setAmount(amountInMinorUnits)
                .putMetadata("refund_operation_id", operationId.toString());
        refundReason(reason).ifPresent(params::setReason);

        try {
            com.stripe.model.Refund refund =
                    stripeClient.refunds().create(params.build(), requestOptions(operationId, "refund"));
            if (refund == null || refund.getId() == null) {
                throw new IllegalStateException("Stripe did not return a refund ID");
            }
            return new PaymentAdapter.Refund(refund.getId(), "succeeded".equals(refund.getStatus()));
        } catch (StripeException exception) {
            throw providerFailure("create refund", exception);
        }
    }

    private static ValidatedCheckout validate(CheckoutRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Checkout request is required");
        }
        requireOperationId(request.purchaseId());
        if (request.customerEmail() == null || request.customerEmail().isBlank()) {
            throw new IllegalArgumentException("Customer email is required");
        }
        requireHttpUrl(request.successUrl(), "success URL");
        requireHttpUrl(request.cancelUrl(), "cancel URL");

        Currency stripeCurrency = currency(request.currency());
        int currencyDigits = stripeCurrency.getDefaultFractionDigits();
        if (currencyDigits < 0) {
            throw new IllegalArgumentException("Unsupported Stripe currency precision");
        }
        long expectedTotal = toMinorUnits(request.total(), currencyDigits);
        if (expectedTotal <= 0 || request.items() == null || request.items().isEmpty()) {
            throw new IllegalArgumentException("A paid checkout must have items and a positive total");
        }

        Set<UUID> productIds = new HashSet<>();
        long lineTotal = 0L;

        for (PaymentAdapter.Line line : request.items()) {

            if (line == null
                    || line.productId() == null
                    || line.productName() == null
                    || line.productName().isBlank()
                    || line.quantity() <= 0
                    || line.unitPrice() == null
                    || line.unitPrice().signum() < 0
                    || !productIds.add(line.productId())) {

                throw new IllegalArgumentException(
                        "Checkout items must have unique products and valid quantities and prices"
                );
            }

            long unitAmount = toMinorUnits(line.unitPrice(), currencyDigits);

            lineTotal = Math.addExact(
                    lineTotal,
                    Math.multiplyExact(unitAmount, line.quantity())
            );
        }
        if (lineTotal != expectedTotal) {
            throw new IllegalArgumentException("Checkout total does not match the ticket line items");
        }
        return new ValidatedCheckout(stripeCurrency.getCurrencyCode().toLowerCase(Locale.ROOT), currencyDigits);
    }

    private static Currency currency(String currencyCode) {
        if (currencyCode == null || currencyCode.isBlank()) {
            throw new IllegalArgumentException("Currency is required");
        }
        try {
            return Currency.getInstance(currencyCode.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unsupported currency: " + currencyCode, exception);
        }
    }

    private static long toMinorUnits(BigDecimal amount, int fractionDigits) {
        if (amount == null || amount.signum() < 0) {
            throw new IllegalArgumentException("Amount must not be null or negative");
        }
        try {
            return amount.movePointRight(fractionDigits).longValueExact();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Amount has unsupported precision or is too large", exception);
        }
    }

    private static void requireHttpUrl(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Checkout " + label + " is required");
        }
        try {
            String validationValue = value.replace("{CHECKOUT_SESSION_ID}", "checkout_session_id");
            URI uri = URI.create(validationValue);
            if (!uri.isAbsolute() || uri.getHost() == null || uri.getUserInfo() != null
                    || !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))) {
                throw new IllegalArgumentException("Checkout " + label + " must be an absolute HTTP(S) URL");
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Checkout " + label + " must be an absolute HTTP(S) URL", exception);
        }
    }

    private static java.util.Optional<RefundCreateParams.Reason> refundReason(String reason) {
        if (reason == null) {
            return java.util.Optional.empty();
        }
        return switch (reason.trim().toLowerCase(Locale.ROOT)) {
            case "duplicate" -> java.util.Optional.of(RefundCreateParams.Reason.DUPLICATE);
            case "fraudulent" -> java.util.Optional.of(RefundCreateParams.Reason.FRAUDULENT);
            case "requested_by_customer" -> java.util.Optional.of(RefundCreateParams.Reason.REQUESTED_BY_CUSTOMER);
            default -> java.util.Optional.empty();
        };
    }

    private static RequestOptions requestOptions(UUID operationId) {
        return requestOptions(operationId, "checkout");
    }

    private static RequestOptions requestOptions(UUID purchaseId, String action) {
        requireOperationId(purchaseId);
        return RequestOptions.builder()
                .setIdempotencyKey("gossamer-" + action + "-" + purchaseId)
                .build();
    }

    private static void requireOperationId(UUID purchaseId) {
        if (purchaseId == null) {
            throw new IllegalArgumentException("Purchase ID is required");
        }
    }

    private static IllegalStateException providerFailure(String action, StripeException cause) {
        return new IllegalStateException("Stripe could not " + action, cause);
    }

    private record ValidatedCheckout(String currencyCode, int currencyDigits) {}
}

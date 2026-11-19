package com.gossamercms.stripe.payments.adapters.inbound;

import com.gossamercms.stripe.payments.config.StripeCheckoutProperties;
import com.gossamercms.stripe.payments.dtos.requests.CheckoutRequest;
import com.gossamercms.stripe.payments.services.StripeService;
import com.gossamercms.stripe.payments.services.StripeWebhookService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/stripe")
@RequiredArgsConstructor
public class StripeCheckoutController {

    private final StripeService stripeService;
    private final StripeCheckoutProperties checkoutProperties;
    private final StripeWebhookService webhookService;

    @PostMapping(value = "/webhook", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> stripeWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String signature) {
        webhookService.handle(payload, signature);
        return ResponseEntity.noContent().build();
    }
//    public StripeCheckoutController(
//            StripeService stripeService,
//            EventCheckoutProperties checkoutProperties,
//            StripeWebhookService webhookService) {
//        this.stripeService = stripeService;
//        this.checkoutProperties = checkoutProperties;
//        this.webhookService = webhookService;
//    }

    @PostMapping("/create-checkout-session")
    public ResponseEntity<?> createCheckoutSession(@RequestBody CheckoutRequest request) {
        try {
            String checkoutUrl = stripeService.createCheckoutSession(
                    request.getAmount(),
                    request.getCurrency(),
                    checkoutProperties.getSuccessUrl(),
                    checkoutProperties.getCancelUrl()
            );

            return ResponseEntity.ok(Map.of("url", checkoutUrl));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

}

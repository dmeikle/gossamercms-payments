package com.gossamercms.stripe.payments.config;


import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "gossamercms.stripe.api")
public class StripeCheckoutProperties {
    private String successUrl;
    private String cancelUrl;
}

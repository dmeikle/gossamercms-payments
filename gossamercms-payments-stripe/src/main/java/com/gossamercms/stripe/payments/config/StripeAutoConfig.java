package com.gossamercms.stripe.payments.config;

import com.gossamercms.mvc.autoconfig.EnableModuleProviders;
import com.gossamercms.mvc.autoconfig.EnableModuleServices;
import com.gossamercms.stripe.payments.adapters.inbound.StripeCheckoutController;
import com.gossamercms.stripe.payments.providers.StripePaymentProvider;
import com.stripe.StripeClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@ConditionalOnProperty(name = "gossamercms.stripe.mock.enabled", havingValue = "false", matchIfMissing = true)
@EnableConfigurationProperties({StripeProperties.class, StripeCheckoutProperties.class})
@EnableModuleServices(basePackage = "com.gossamercms.stripe.payments.services")
@EnableModuleProviders(basePackage = "com.gossamercms.stripe.payments.providers")
@Import({StripePaymentProvider.class, StripeCheckoutController.class})
public class StripeAutoConfig {

    @Bean
    StripeClient stripeClient(StripeProperties properties) {
        String secretKey = properties.getSecretKey();
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException("Configure gossamercms.stripe.api.secret-key to enable Stripe payments");
        }
        return new StripeClient(secretKey);
    }
}

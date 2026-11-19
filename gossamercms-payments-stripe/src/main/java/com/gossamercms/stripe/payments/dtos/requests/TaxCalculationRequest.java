package com.gossamercms.stripe.payments.dtos.requests;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaxCalculationRequest {

    private String currency;

    private CustomerDetails customerDetails;

    private List<LineItem> lineItems;

    private BigDecimal shippingAmount;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CustomerDetails {
        private Address address;

        private AddressSource addressSource;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Address {
        private String line1;
        private String line2;
        private String city;
        private String state;
        private String postalCode;
        private String country;
    }

    public enum AddressSource {
        SHIPPING,
        BILLING
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LineItem {
        /**
         * Total line amount before tax, after discounts.
         */
        private BigDecimal amount;

        /**
         * Stripe Tax code, e.g. txcd_10000000.
         */
        private String taxCode;

        /**
         * Stable identifier for tracing the line back to the purchase item.
         */
        private String reference;
    }
}

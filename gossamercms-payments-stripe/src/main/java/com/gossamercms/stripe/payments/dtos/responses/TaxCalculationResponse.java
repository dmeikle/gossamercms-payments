package com.gossamercms.stripe.payments.dtos.responses;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaxCalculationResponse {

    private String calculationId;

    private String currency;

    /**
     * Total amount after taxes.
     */
    private BigDecimal amountTotal;

    /**
     * Tax charged on top of the item prices.
     */
    private BigDecimal taxAmountExclusive;

    /**
     * Tax already included in item prices.
     */
    private BigDecimal taxAmountInclusive;

    private BigDecimal totalTax;

    private List<TaxLineResponse> lineItems;

    /**
     * Overall tax breakdown returned by Stripe.
     */
    private List<TaxBreakdownResponse> taxBreakdown;

    private ShippingTaxResponse shipping;

    private Instant taxDate;

    private Instant expiresAt;

    private Boolean liveMode;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaxLineResponse {

        private String stripeLineItemId;

        /**
         * Reference supplied when creating the calculation.
         * This can be the ecommerce purchase item ID.
         */
        private String reference;

        private String taxCode;

        private Long quantity;

        private BigDecimal amount;

        private BigDecimal taxAmount;

        private BigDecimal totalAmount;

        private String taxBehavior;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaxBreakdownResponse {

        private String taxType;

        private String country;

        private String state;

        /**
         * Tax rate as a fraction.
         * 5% is represented as 0.05.
         */
        private BigDecimal taxRate;

        private BigDecimal taxableAmount;

        private BigDecimal taxAmount;

        private Boolean inclusive;

        private String taxabilityReason;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ShippingTaxResponse {

        private BigDecimal amount;

        private BigDecimal taxAmount;

        private BigDecimal totalAmount;
    }
}

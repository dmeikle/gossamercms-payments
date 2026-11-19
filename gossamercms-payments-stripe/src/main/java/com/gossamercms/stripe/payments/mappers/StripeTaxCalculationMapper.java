package com.gossamercms.stripe.payments.mappers;


import com.gossamercms.stripe.payments.dtos.responses.TaxCalculationResponse;
import com.stripe.model.tax.Calculation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

public class StripeTaxCalculationMapper {

    private StripeTaxCalculationMapper() {
    }

    public static TaxCalculationResponse toResponse(Calculation calculation) {

        if (calculation == null) {
            return null;
        }

        String currency = calculation.getCurrency();

        BigDecimal exclusiveTax = toMajorUnits(
                calculation.getTaxAmountExclusive(), currency
        );

        BigDecimal inclusiveTax = toMajorUnits(
                calculation.getTaxAmountInclusive(), currency
        );

        return TaxCalculationResponse.builder()
                .calculationId(calculation.getId())
                .currency(currency)
                .amountTotal(toMajorUnits(calculation.getAmountTotal(), currency))
                .taxAmountExclusive(exclusiveTax)
                .taxAmountInclusive(inclusiveTax)
                .totalTax(exclusiveTax.add(inclusiveTax))
                .lineItems(mapLineItems(calculation, currency))
                .taxBreakdown(mapTaxBreakdown(calculation, currency))
                .shipping(mapShipping(calculation, currency))
                .taxDate(toInstant(calculation.getTaxDate()))
                .expiresAt(toInstant(calculation.getExpiresAt()))
                .liveMode(calculation.getLivemode())
                .build();
    }

    private static List<TaxCalculationResponse.TaxLineResponse> mapLineItems(
            Calculation calculation,
            String currency
    ) {

        if (calculation.getLineItems() == null
                || calculation.getLineItems().getData() == null) {
            return Collections.emptyList();
        }

        return calculation.getLineItems()
                .getData()
                .stream()
                .map(item -> TaxCalculationResponse.TaxLineResponse.builder()
                        .stripeLineItemId(item.getId())
                        .reference(item.getReference())
                        .taxCode(item.getTaxCode())
                        .quantity(item.getQuantity())
                        .amount(toMajorUnits(item.getAmount(), currency))
                        .taxAmount(toMajorUnits(item.getAmountTax(), currency))
                        .totalAmount(
                                toMajorUnits(item.getAmount(), currency)
                                        .add(toMajorUnits(item.getAmountTax(), currency))
                        )
                        .taxBehavior(item.getTaxBehavior())
                        .build()
                )
                .toList();
    }

    private static List<TaxCalculationResponse.TaxBreakdownResponse>
    mapTaxBreakdown(Calculation calculation, String currency) {

        if (calculation.getTaxBreakdown() == null) {
            return Collections.emptyList();
        }

        return calculation.getTaxBreakdown()
                .stream()
                .map(item -> {
                    var rateDetails = item.getTaxRateDetails();

                    BigDecimal taxRate = null;

                    if (rateDetails != null
                            && rateDetails.getPercentageDecimal() != null) {

                        // Stripe returns a percentage such as "5.0".
                        // Our ecommerce tax schema stores it as 0.05.
                        taxRate = new BigDecimal(
                                rateDetails.getPercentageDecimal()
                        ).movePointLeft(2);
                    }

                    return TaxCalculationResponse.TaxBreakdownResponse.builder()
                            .taxType(rateDetails != null
                                    ? rateDetails.getTaxType()
                                    : null)
                            .country(rateDetails != null
                                    ? rateDetails.getCountry()
                                    : null)
                            .state(rateDetails != null
                                    ? rateDetails.getState()
                                    : null)
                            .taxRate(taxRate)
                            .taxableAmount(
                                    toMajorUnits(item.getTaxableAmount(), currency)
                            )
                            .taxAmount(
                                    toMajorUnits(item.getAmount(), currency)
                            )
                            .inclusive(item.getInclusive())
                            .taxabilityReason(item.getTaxabilityReason())
                            .build();
                })
                .toList();
    }

    private static TaxCalculationResponse.ShippingTaxResponse mapShipping(
            Calculation calculation,
            String currency
    ) {

        if (calculation.getShippingCost() == null) {
            return null;
        }

        BigDecimal amount = toMajorUnits(
                calculation.getShippingCost().getAmount(), currency
        );

        BigDecimal taxAmount = toMajorUnits(
                calculation.getShippingCost().getAmountTax(), currency
        );

        return TaxCalculationResponse.ShippingTaxResponse.builder()
                .amount(amount)
                .taxAmount(taxAmount)
                .totalAmount(amount.add(taxAmount))
                .build();
    }

    private static BigDecimal toMajorUnits(Long amount, String currency) {

        if (amount == null) {
            return BigDecimal.ZERO;
        }

        int fractionDigits = java.util.Currency
                .getInstance(currency.toUpperCase())
                .getDefaultFractionDigits();

        if (fractionDigits < 0) {
            fractionDigits = 2;
        }

        return BigDecimal.valueOf(amount)
                .movePointLeft(fractionDigits);
    }

    private static Instant toInstant(Long timestamp) {
        return timestamp == null
                ? null
                : Instant.ofEpochSecond(timestamp);
    }
}

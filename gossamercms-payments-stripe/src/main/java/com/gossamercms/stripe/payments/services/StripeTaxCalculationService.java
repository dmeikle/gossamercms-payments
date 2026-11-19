package com.gossamercms.stripe.payments.services;


import com.gossamercms.stripe.payments.dtos.requests.TaxCalculationRequest;
import com.gossamercms.stripe.payments.dtos.responses.TaxCalculationResponse;
import com.gossamercms.stripe.payments.mappers.StripeTaxCalculationMapper;
import com.stripe.StripeClient;
import com.stripe.model.tax.Calculation;
import com.stripe.param.tax.CalculationCreateParams;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
public class StripeTaxCalculationService {

    private final StripeClient stripeClient;

    /**
     * Calculates tax using the Stripe Tax Calculation API based on the provided request object.
     *
     * @param request
     * @return
     */
    public TaxCalculationResponse calculate(TaxCalculationRequest request) {

        CalculationCreateParams params = buildParams(request);

        try {
            Calculation calculation = stripeClient.v1()
                    .tax()
                    .calculations()
                    .create(params);

            return StripeTaxCalculationMapper.toResponse(calculation);

        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to calculate tax using Stripe Tax", e
            );
        }
    }

    /**
     * Builds the parameters for the Stripe Tax Calculation API request based on the provided request object.
     *
     * @param request
     * @return
     */
    private CalculationCreateParams buildParams(TaxCalculationRequest request) {

        CalculationCreateParams.Builder params =
                CalculationCreateParams.builder()
                        .setCurrency(request.getCurrency().toLowerCase())
                        .setCustomerDetails(
                                CalculationCreateParams.CustomerDetails.builder()
                                        .setAddress(
                                                CalculationCreateParams.CustomerDetails.Address.builder()
                                                        .setLine1(request.getCustomerDetails().getAddress().getLine1())
                                                        .setLine2(request.getCustomerDetails().getAddress().getLine2())
                                                        .setCity(request.getCustomerDetails().getAddress().getCity())
                                                        .setState(request.getCustomerDetails().getAddress().getState())
                                                        .setPostalCode(request.getCustomerDetails().getAddress().getPostalCode())
                                                        .setCountry(request.getCustomerDetails().getAddress().getCountry())
                                                        .build()
                                        )
                                        .setAddressSource(
                                                CalculationCreateParams.CustomerDetails.AddressSource.valueOf(
                                                        request.getCustomerDetails().getAddressSource().name()
                                                )
                                        )
                                        .build()
                        );

        if (request.getLineItems() != null) {
            for (TaxCalculationRequest.LineItem item : request.getLineItems()) {

                params.addLineItem(
                        CalculationCreateParams.LineItem.builder()
                                .setAmount(toMinorUnits(item.getAmount(), request.getCurrency()))
                                .setTaxCode(item.getTaxCode())
                                .setReference(item.getReference())
                                .build()
                );
            }
        }

        if (request.getShippingAmount() != null
                && request.getShippingAmount().compareTo(BigDecimal.ZERO) > 0) {

            params.setShippingCost(
                    CalculationCreateParams.ShippingCost.builder()
                            .setAmount(toMinorUnits(
                                    request.getShippingAmount(),
                                    request.getCurrency()
                            ))
                            .build()
            );
        }

        params.addExpand("line_items");

        return params.build();
    }

    /**
     * Converts a monetary amount from major units (e.g., dollars) to minor units (e.g., cents) based on the currency.
     *
     * @param amount   The amount in major units.
     * @param currency The currency code (e.g., "USD").
     * @return The amount in minor units.
     */
    private Long toMinorUnits(BigDecimal amount, String currency) {

        int exponent = getCurrencyExponent(currency);

        return amount
                .movePointRight(exponent)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    /**
     * Returns the currency exponent (number of decimal places) for the given currency code.
     *
     * @param currency The currency code (e.g., "USD").
     * @return The currency exponent.
     */
    private int getCurrencyExponent(String currency) {
        // Extend this using a currency metadata service if supporting
        // currencies with zero or three decimal places.
        return switch (currency.toUpperCase()) {
            case "JPY", "KRW", "CLP" -> 0;
            case "BHD", "KWD", "OMR", "JOD" -> 3;
            default -> 2;
        };
    }
}

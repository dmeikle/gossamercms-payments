package com.gossamercms.stripe.payments.dtos.requests;

import lombok.Data;

@Data
public class CheckoutRequest {
    private long amount;
    private String currency = "usd";

}
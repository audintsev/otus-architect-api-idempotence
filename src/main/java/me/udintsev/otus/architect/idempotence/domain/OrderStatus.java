package me.udintsev.otus.architect.idempotence.domain;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

public enum OrderStatus {
    CANCELLED,
    CREATED,
    CHECKED_OUT,
    PAYMENT_SUCCEEDED,
    PAYMENT_FAILED;

    @JsonValue
    public String getJsonValue() {
        return this.name().toLowerCase(Locale.ENGLISH);
    }
}

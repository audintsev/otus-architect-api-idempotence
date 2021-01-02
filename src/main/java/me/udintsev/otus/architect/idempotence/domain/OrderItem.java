package me.udintsev.otus.architect.idempotence.domain;

import lombok.Value;

@Value
public class OrderItem {
    long itemId;
    int quantity;
}

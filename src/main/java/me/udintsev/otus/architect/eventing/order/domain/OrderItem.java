package me.udintsev.otus.architect.eventing.order.domain;

import lombok.Value;

@Value
public class OrderItem {
    long itemId;
    int quantity;
}

package me.udintsev.otus.architect.idempotence.domain;

import lombok.Value;

import java.time.Instant;
import java.util.List;

@Value
public class Order {
    Long id;
    String userId;
    Instant createdAt;
    List<OrderItem> items;
    OrderStatus status;
    long price;

    public Order withId(long id) {
        return new Order(id, userId, createdAt, items, status, price);
    }
}

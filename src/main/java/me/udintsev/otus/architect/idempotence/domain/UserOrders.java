package me.udintsev.otus.architect.idempotence.domain;

import lombok.Value;

import java.util.List;

@Value
public class UserOrders {
    List<Order> orders;
    String fingerprint;
}

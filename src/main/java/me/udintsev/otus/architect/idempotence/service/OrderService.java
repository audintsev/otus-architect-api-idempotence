package me.udintsev.otus.architect.idempotence.service;

import io.vavr.Tuple2;
import me.udintsev.otus.architect.idempotence.domain.Order;
import me.udintsev.otus.architect.idempotence.domain.OrderItem;
import me.udintsev.otus.architect.idempotence.domain.UserOrders;

import java.util.List;
import java.util.Optional;

public interface OrderService {
    Tuple2<String, Order> createFirstOrder(String userId, List<OrderItem> items);
    Tuple2<String, Order> createOrder(String userId, List<OrderItem> items, String fingerprint);

    UserOrders getUserOrders(String userId);
    Optional<Order> getOrder(long id);
    String getUserOrdersFingerprint(String userId);
}

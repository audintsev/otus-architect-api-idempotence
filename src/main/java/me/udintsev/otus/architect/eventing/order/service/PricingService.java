package me.udintsev.otus.architect.eventing.order.service;

import me.udintsev.otus.architect.eventing.order.domain.OrderItem;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PricingService {
    // Calculate price as the number of position in the order * 10
    long calculatePriceForOrder(String userId, List<OrderItem> items) {
        if (items == null) {
            return 0L;
        }

        return items.stream()
                .mapToLong(i -> i.getQuantity() * 10L)
                .sum();
    }
}

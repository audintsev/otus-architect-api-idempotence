package me.udintsev.otus.architect.eventing.order;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import me.udintsev.otus.architect.eventing.order.domain.Order;
import me.udintsev.otus.architect.eventing.order.domain.OrderItem;
import me.udintsev.otus.architect.eventing.order.domain.UserOrders;
import me.udintsev.otus.architect.eventing.order.service.OrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@AllArgsConstructor
@RestController
@RequestMapping(OrderController.API_ROOT)
@Transactional(readOnly = true)
public class OrderController {
    public static final String API_ROOT = "/api/v1/orders";

    private final OrderService orderService;

    //
    // REST methods
    //

    @GetMapping
    public UserOrders listUserOrders(@RequestHeader("X-User-Id") String userId) {
        return orderService.getUserOrders(userId);
    }

    @GetMapping("fingerprint")
    public String getUserOrdersFingerprint(@RequestHeader("X-User-Id") String userId) {
        return orderService.getUserOrdersFingerprint(userId);
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class CreateOrderRequest {
        List<OrderItem> items;
        String fingerprint;
    }

    @PostMapping
    @Transactional
    public Order createOrder(@RequestHeader("X-User-Id") String userId,
                             @RequestBody CreateOrderRequest createOrderRequest) {
        if (createOrderRequest.getFingerprint() == null) {
            // Assume that's the first order that's being created
            return orderService.createFirstOrder(userId, createOrderRequest.getItems())._2();
        } else {
            // Assume some orders already exist
            return orderService.createOrder(userId, createOrderRequest.getItems(), createOrderRequest.getFingerprint())._2();
        }
    }

    @GetMapping("{orderId}")
    public ResponseEntity<Order> getOrder(@PathVariable long orderId) {
        return ResponseEntity.of(orderService.getOrder(orderId));
    }
}

package me.udintsev.otus.architect.eventing.order.service;

import io.vavr.Lazy;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import me.udintsev.otus.architect.eventing.order.domain.Order;
import me.udintsev.otus.architect.eventing.order.domain.OrderItem;
import me.udintsev.otus.architect.eventing.order.domain.OrderStatus;
import me.udintsev.otus.architect.eventing.order.domain.UserOrders;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class OrderServiceImpl implements OrderService {
    private final JdbcOperations jdbc;
    private final PricingService pricingService;
    private final Function<List<Order>, String> ordersFingerprintCalculator;

    private final Lazy<String> emptyOrdersFingerprint;

    public OrderServiceImpl(JdbcOperations jdbc,
                            PricingService pricingService,
                            Function<List<Order>, String> ordersFingerprintCalculator) {
        this.jdbc = jdbc;
        this.pricingService = pricingService;
        this.ordersFingerprintCalculator = ordersFingerprintCalculator;

        this.emptyOrdersFingerprint = Lazy.of(() -> ordersFingerprintCalculator.apply(List.of()));
    }

    @Override
    public Tuple2<String, Order> createFirstOrder(String userId, List<OrderItem> items) {
        Assert.notNull(userId, "userId must not be null");
        Assert.notEmpty(items, "items must not be empty");

        Order newOrder = new Order(
                null,
                userId,
                Instant.now().truncatedTo(ChronoUnit.MILLIS),
                List.copyOf(items),
                OrderStatus.CREATED,
                pricingService.calculatePriceForOrder(userId, items)
        );

        var fingerprint = ordersFingerprintCalculator.apply(List.of(newOrder));

        // First off, *insert* a new user_orders entry - our
        // idempotence implementation relies on this 'user_orders.user_id' being a unique key,
        // so this insert must fail if an order has been created in the meantime

        int userOrdersInserted;
        try {
            userOrdersInserted = jdbc.update("INSERT INTO user_orders (user_id, fingerprint) VALUES (?, ?)",
                    ps -> {
                        ps.setString(1, userId);
                        ps.setString(2, fingerprint);
                    });
        } catch (DuplicateKeyException e) {
            userOrdersInserted = 0;
        }
        if (userOrdersInserted != 1) {
            throw new FingerprintMismatchException("Expected no pre-existing fingerprint for user orders " + userId);
        }

        // OK, that succeeded, now go ahead and actually insert the order
        return Tuple.of(fingerprint, doInsertOrder(newOrder));
    }

    @Override
    public Tuple2<String, Order> createOrder(String userId, List<OrderItem> items, String fingerprint) {
        Assert.notNull(userId, "userId must not be null");
        Assert.notEmpty(items, "items must not be empty");
        Assert.notNull(fingerprint, "fingerprint must not be null");

        if (fingerprint.equals(emptyOrdersFingerprint.get())) {
            // That's actually the first order!
            return createFirstOrder(userId, items);
        }

        UserOrders orders = getUserOrders(userId);

        // Rough fingerprint check
        if (!Objects.equals(fingerprint, orders.getFingerprint())) {
            throw new FingerprintMismatchException("Supplied: " + fingerprint + ", expected: " + orders.getFingerprint());
        }

        // Fine check: rely on "UPDATE ... WHERE fingerprint={oldFingerprint}" DB query to update exactly one entry

        // First, calculate the new fingerprint
        Order newOrder = new Order(
                null,
                userId,
                Instant.now().truncatedTo(ChronoUnit.MILLIS),
                List.copyOf(items),
                OrderStatus.CREATED,
                pricingService.calculatePriceForOrder(userId, items)
        );

        var newOrders = new ArrayList<Order>(orders.getOrders().size() + 1);
        newOrders.addAll(orders.getOrders());
        newOrders.add(newOrder);
        var newFingerprint = ordersFingerprintCalculator.apply(newOrders);

        // Now update the fingerprint and fine-check the old fingerprint is still valid

        var userOrdersUpdated = jdbc.update("UPDATE user_orders SET fingerprint=? WHERE user_id=? and fingerprint=?",
                ps -> {
                    ps.setString(1, newFingerprint);
                    ps.setString(2, userId);
                    ps.setString(3, fingerprint);
                });

        if (userOrdersUpdated != 1) {
            throw new FingerprintMismatchException("Fingerprint is no longer valid");
        }

        // OK, that succeeded, now go ahead and actually insert the order
        return Tuple.of(fingerprint, doInsertOrder(newOrder));
    }

    private Order doInsertOrder(Order order) {
        var orderKeyHolder = new GeneratedKeyHolder();
        var ordersInserted = jdbc.update(
                conn -> {
                    var ps = conn.prepareStatement("INSERT INTO orders (user_id, created_at, status, price) VALUES (?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS );
                    ps.setString(1, order.getUserId());
                    ps.setLong(2, order.getCreatedAt().toEpochMilli());
                    ps.setString(3, order.getStatus().name());
                    ps.setLong(4, order.getPrice());
                    return ps;
                },
                orderKeyHolder
        );

        Assert.isTrue(ordersInserted == 1, "Expected a single orders entry to be inserted");

        var orderId = orderKeyHolder.getKeyAs(Long.class);
        Assert.notNull(orderId, "DB returned null orderId");

        // Now insert order items
        jdbc.batchUpdate("INSERT INTO order_item (order_id, item_id, quantity) VALUES (?, ?, ?)",
                order.getItems(), order.getItems().size(),
                (ps, item) -> {
                    ps.setLong(1, orderId);
                    ps.setLong(2, item.getItemId());
                    ps.setInt(3, item.getQuantity());
                });

        return order.withId(orderId);
    }

    //
    // Getting orders
    //

    private static final String USER_ORDERS_QUERY_BASE = """
            SELECT
                o.id as id,
                o.user_id as user_id,
                o.created_at as created_at,
                o.status as status,
                o.price as price,
                i.item_id as item_id,
                i.quantity as item_quantity,
                uo.fingerprint as fingerprint
            FROM orders o
            JOIN order_item i ON o.id = i.order_id
            JOIN user_orders uo ON o.user_id = uo.user_id
            """;

    private static final String ORDERS_BY_USER_ID = String.format("%s WHERE o.user_id=?", USER_ORDERS_QUERY_BASE);

    @Override
    public UserOrders getUserOrders(String userId) {
        var userOrders = jdbc.query(ORDERS_BY_USER_ID,
                ps -> ps.setString(1, userId),
                USER_ORDERS_EXTRACTOR
        );

        if (userOrders == null || userOrders.isEmpty()) {
            return new UserOrders(
                    List.of(),
                    emptyOrdersFingerprint.get()
            );
        }

        if (userOrders.size() > 1) {
            throw new IllegalStateException("Requested orders for a single user " + userId + ", got back " + userOrders.size());
        }

        return userOrders.get(0);
    }

    @Override
    public String getUserOrdersFingerprint(String userId) {
        return getUserOrders(userId).getFingerprint();
    }

    private static final String ORDER_BY_ID = String.format("%s WHERE o.id=?", USER_ORDERS_QUERY_BASE);

    @Override
    public Optional<Order> getOrder(long id) {
        var userOrders = jdbc.query(ORDER_BY_ID,
                ps -> ps.setLong(1, id),
                USER_ORDERS_EXTRACTOR
        );

        if (userOrders == null || userOrders.isEmpty() || userOrders.get(0).getOrders().isEmpty()) {
            return Optional.empty();
        }

        if (userOrders.size() > 1) {
            throw new IllegalStateException("Requested a single order " + id + ", got back " + userOrders.size());
        }

        return Optional.of(userOrders.get(0).getOrders().get(0));
    }


    private static class UserOrdersResultSetExtractor implements ResultSetExtractor<List<UserOrders>> {
        @Override
        public List<UserOrders> extractData(ResultSet rs) throws SQLException, DataAccessException {
            // map: userId -> {fingerprint, map: orderId -> order}
            Map<String, Map.Entry<String, Map<Long, Order>>> ordersAndFingerprintsByUserBy = new HashMap<>();
            while (rs.next()) {
                var id = rs.getLong("id");
                var userId = rs.getString("user_id");
                var createdAt = Instant.ofEpochMilli(rs.getLong("created_at"));
                var status = OrderStatus.valueOf(rs.getString("status"));
                var price = rs.getLong("price");
                var itemId = rs.getLong("item_id");
                var quantity = rs.getInt("quantity");
                var fingerprint = rs.getString("fingerprint");

                ordersAndFingerprintsByUserBy.computeIfAbsent(userId, k -> new AbstractMap.SimpleImmutableEntry<>(fingerprint, new HashMap<>()))
                        .getValue()
                        .computeIfAbsent(id, k -> new Order(
                                id,
                                userId,
                                createdAt,
                                new ArrayList<>(),
                                status,
                                price
                        ))
                        .getItems()
                        .add(new OrderItem(itemId, quantity));
            }

            return ordersAndFingerprintsByUserBy.values().stream()
                    .map(e -> {
                        var fingerprint = e.getKey();
                        var orders = e.getValue().values();
                        return new UserOrders(
                                List.copyOf(orders),
                                fingerprint
                        );
                    })
                    .collect(Collectors.toList());
        }
    }

    private static final ResultSetExtractor<List<UserOrders>> USER_ORDERS_EXTRACTOR = new UserOrdersResultSetExtractor();
}

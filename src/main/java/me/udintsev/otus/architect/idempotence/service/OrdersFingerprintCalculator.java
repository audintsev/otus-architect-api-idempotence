package me.udintsev.otus.architect.idempotence.service;

import me.udintsev.otus.architect.idempotence.domain.Order;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

@Component
public class OrdersFingerprintCalculator implements Function<List<Order>, String> {
    // NB: this must not depend on ID, because fingerprint may be calculated on orders that are not yet persisted!
    @Override
    public String apply(List<Order> orders) {
        // Calculate fingerprint as hash of all created timestamps, in order
        // It would be enough to just return the size of the orders collection, but let's make it a bit more exciting
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            orders.stream()
                    .sorted(Comparator.comparing(Order::getCreatedAt))
                    .forEachOrdered(order -> {
                        long epochMilli = order.getCreatedAt().toEpochMilli();
                        for (int i = 0; i < Long.BYTES; ++i) {
                            // do it 'little-endian': first process least significant byte
                            byte bt = (byte)(epochMilli & 0xff);
                            md.update(bt);
                            epochMilli >>= 8;
                        }
                    });
            var hash = md.digest();
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Failed to calculate hash", e);
        }
    }
}

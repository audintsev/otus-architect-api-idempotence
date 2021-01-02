package me.udintsev.otus.architect.eventing.order.service;

import java.util.ConcurrentModificationException;

public class FingerprintMismatchException extends ConcurrentModificationException {
    public FingerprintMismatchException(String message) {
        super(message);
    }
}

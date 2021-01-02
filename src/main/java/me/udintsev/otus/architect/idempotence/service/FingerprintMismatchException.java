package me.udintsev.otus.architect.idempotence.service;

import java.util.ConcurrentModificationException;

public class FingerprintMismatchException extends ConcurrentModificationException {
    public FingerprintMismatchException(String message) {
        super(message);
    }
}

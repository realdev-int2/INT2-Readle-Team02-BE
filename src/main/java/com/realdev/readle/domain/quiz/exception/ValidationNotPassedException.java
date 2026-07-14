package com.realdev.readle.domain.quiz.exception;

public class ValidationNotPassedException extends RuntimeException {
    public ValidationNotPassedException(String message) {
        super(message);
    }
}

package com.tommap.springaiapp.knowledge;

import org.springframework.http.HttpStatus;

/** An upload was refused; the status tells the client why (415 type, 413 size, 422 content, 409 duplicate). */
class KnowledgeRejectedException extends RuntimeException {

    private final HttpStatus status;

    KnowledgeRejectedException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    HttpStatus status() {
        return status;
    }
}

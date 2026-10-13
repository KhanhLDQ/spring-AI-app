package com.tommap.springaiapp.knowledge;

import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = KnowledgeController.class)
class KnowledgeExceptionHandler {

    @ExceptionHandler(KnowledgeRejectedException.class)
    ProblemDetail rejected(KnowledgeRejectedException e) {
        return ProblemDetail.forStatusAndDetail(e.status(), e.getMessage());
    }
}

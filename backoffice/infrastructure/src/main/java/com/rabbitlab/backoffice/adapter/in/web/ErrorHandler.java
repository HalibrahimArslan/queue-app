package com.rabbitlab.backoffice.adapter.in.web;

import com.rabbitlab.backoffice.application.DuplicateSkuException;
import com.rabbitlab.backoffice.application.ProductNotFoundException;
import com.rabbitlab.backoffice.application.port.out.ConcurrentUpdateException;
import com.rabbitlab.backoffice.domain.product.ProductInactiveException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Domain ve application hatalarını HTTP'ye çevirir (RFC 9457 Problem Details). Domain HTTP bilmez;
 * çeviri adapter'ın işi.
 */
@RestControllerAdvice
class ErrorHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail invalid(IllegalArgumentException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(ProductNotFoundException.class)
    ProblemDetail notFound(ProductNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    /** 409: istek geçerli ama ürünün mevcut durumuyla çelişiyor. */
    @ExceptionHandler({DuplicateSkuException.class, ProductInactiveException.class, ConcurrentUpdateException.class})
    ProblemDetail conflict(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }
}

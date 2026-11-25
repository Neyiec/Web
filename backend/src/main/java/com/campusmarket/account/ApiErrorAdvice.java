package com.campusmarket.account;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiErrorAdvice {
    @ExceptionHandler(ResponseStatusException.class)
    ProblemDetail responseError(ResponseStatusException error) {
        return error.getBody();
    }

    @ExceptionHandler({DataAccessException.class, CannotCreateTransactionException.class})
    ProblemDetail dataError() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "服务暂时不可用，请稍后重试。");
    }
}

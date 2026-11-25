package com.campusmarket.account;

import java.util.Map;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class RegistrationController {
    private final RegistrationService registration;

    public RegistrationController(RegistrationService registration) {
        this.registration = registration;
    }

    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("headerName", token.getHeaderName(), "token", token.getToken());
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public void register(@RequestBody RegistrationRequest request) {
        registration.register(request.phone(), request.nickname(), request.password());
    }

    @ExceptionHandler(ResponseStatusException.class)
    ProblemDetail handleRegistrationError(ResponseStatusException ex) {
        return ex.getBody();
    }

    @ExceptionHandler({DataAccessException.class, CannotCreateTransactionException.class})
    ProblemDetail handleDatabaseError() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "暂时无法注册，请稍后重试。");
    }

    public record RegistrationRequest(String phone, String nickname, String password) {
    }
}

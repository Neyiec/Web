package com.campusmarket.account;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import com.campusmarket.security.LoginSessions;
import com.campusmarket.security.MarketPrincipal;

@RestController
@RequestMapping("/api/auth")
public class LoginController {
    private final AccountIdentityMapper accounts;
    private final PasswordEncoder passwords;
    private final LoginSessions sessions;
    private final String dummyHash;

    public LoginController(AccountIdentityMapper accounts, PasswordEncoder passwords, LoginSessions sessions) {
        this.accounts = accounts;
        this.passwords = passwords;
        this.sessions = sessions;
        this.dummyHash = passwords.encode(java.util.UUID.randomUUID().toString());
    }

    @PostMapping("/login")
    public ResponseEntity<AccountView> login(@RequestBody LoginRequest input, HttpServletRequest request) {
        String phone = RegistrationService.normalizePhone(input.phone());
        String password = input.password();
        if (password == null || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw incorrectCredentials();
        }
        var account = accounts.credentials(phone);
        if (!passwords.matches(password, account == null ? dummyHash : account.passwordHash()) || account == null) {
            throw incorrectCredentials();
        }
        String nickname = accounts.activeNickname(account.id());
        if (nickname == null) {
            throw incorrectCredentials();
        }
        var view = new AccountView(account.id(), nickname, accounts.roles(account.id()));
        String cookie = cookie(sessions.issue(account.id()), LoginSessions.LIFETIME, request);
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie).body(view);
    }

    @GetMapping("/me")
    public AccountView me(@AuthenticationPrincipal MarketPrincipal identity) {
        return new AccountView(identity.id(), identity.nickname(), identity.roles());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal MarketPrincipal identity, HttpServletRequest request) {
        sessions.revoke(identity);
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO, request)).build();
    }

    private String cookie(String value, Duration lifetime, HttpServletRequest request) {
        return ResponseCookie.from(LoginSessions.COOKIE, value).httpOnly(true).secure(request.isSecure())
                .sameSite("Lax").path("/").maxAge(lifetime).build().toString();
    }

    private ResponseStatusException incorrectCredentials() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "手机号或密码错误。");
    }

    public record AccountView(long id, String nickname, List<String> roles) {}
    public record LoginRequest(String phone, String password) {}
}

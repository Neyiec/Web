package com.campusmarket.account;

import java.nio.charset.StandardCharsets;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RegistrationService {
    private final AccountMapper accounts;
    private final PasswordEncoder passwords;

    public RegistrationService(AccountMapper accounts, PasswordEncoder passwords) {
        this.accounts = accounts;
        this.passwords = passwords;
    }

    @Transactional
    public void register(String phone, String nickname, String password) {
        String normalizedPhone = normalizePhone(phone);
        String displayName = nickname == null ? "" : nickname.strip();
        if (displayName.isEmpty() || displayName.codePointCount(0, displayName.length()) > 40) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "昵称须为 1 至 40 个字符。");
        }
        if (password == null || password.isBlank() || password.codePointCount(0, password.length()) < 8
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "密码至少 8 个字符，UTF-8 编码最多 72 字节。");
        }

        try {
            accounts.createUser(normalizedPhone, passwords.encode(password), displayName);
        } catch (DuplicateKeyException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该手机号已注册。");
        }

        if (accounts.grantStudentRole(normalizedPhone) != 1) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "学生角色尚未初始化。");
        }
    }

    static String normalizePhone(String phone) {
        String value = phone == null ? "" : phone.strip();
        if (value.matches("1[3-9][0-9]{9}")) {
            return "+86" + value;
        }
        if (value.matches("\\+86(?!1[3-9][0-9]{9}$).*") || !value.matches("\\+[1-9][0-9]{1,14}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请输入有效的手机号。");
        }
        return value;
    }
}

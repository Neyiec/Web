package com.campusmarket.security;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.campusmarket.account.AccountIdentityMapper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class LoginSessions {
    public static final String COOKIE = "MARKET_SESSION";
    public static final Duration LIFETIME = Duration.ofHours(24);
    private static final String ISSUER = "campus-market";
    private static final String AUDIENCE = "campus-market-web";
    private static final RedisScript<Long> DELETE_IF_CURRENT = RedisScript.of("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                return redis.call('DEL', KEYS[1])
            end
            return 0
            """, Long.class);

    private final AccountIdentityMapper accounts;
    private final StringRedisTemplate redis;
    private final NimbusJwtEncoder encoder;
    private final NimbusJwtDecoder decoder;

    public LoginSessions(AccountIdentityMapper accounts, StringRedisTemplate redis,
                         @Value("${app.auth.jwt-secret}") String secret) {
        this.accounts = accounts;
        this.redis = redis;
        if (secret.isBlank()) {
            encoder = null;
            decoder = null;
            return;
        }
        byte[] bytes = Base64.getDecoder().decode(secret);
        if (bytes.length < 32) {
            throw new IllegalArgumentException("APP_JWT_SECRET must encode at least 32 bytes");
        }
        SecretKey key = new SecretKeySpec(bytes, "HmacSHA256");
        encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithValidators(
                new JwtIssuerValidator(ISSUER),
                new JwtClaimValidator<List<String>>("aud", audience -> audience != null && audience.contains(AUDIENCE))));
    }

    public String issue(long accountId) {
        if (encoder == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "登录服务尚未配置签名密钥。");
        }
        String sessionId = UUID.randomUUID().toString();
        Instant issuedAt = Instant.now();
        var claims = JwtClaimsSet.builder().issuer(ISSUER).subject(Long.toString(accountId)).id(sessionId)
                .audience(List.of(AUDIENCE)).issuedAt(issuedAt).expiresAt(issuedAt.plus(LIFETIME)).build();
        String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        redis.opsForValue().set(key(accountId), sessionId, LIFETIME);
        return token;
    }

    public MarketPrincipal authenticate(String token) {
        if (decoder == null) {
            return null;
        }
        try {
            var jwt = decoder.decode(token);
            long id = Long.parseLong(jwt.getSubject());
            String nickname = accounts.activeNickname(id);
            if (nickname == null || jwt.getId() == null || jwt.getExpiresAt() == null) {
                return null;
            }
            var identity = new MarketPrincipal(id, nickname, accounts.roles(id), jwt.getId(), jwt.getExpiresAt());
            return isCurrent(identity) ? identity : null;
        } catch (JwtException | IllegalArgumentException ex) {
            return null;
        }
    }

    public boolean isCurrent(MarketPrincipal identity) {
        return Instant.now().isBefore(identity.expiresAt())
                && identity.sessionId().equals(redis.opsForValue().get(key(identity.id())));
    }

    public boolean mayReceive(MarketPrincipal identity) {
        return isCurrent(identity) && accounts.activeNickname(identity.id()) != null
                && accounts.roles(identity.id()).contains("STUDENT");
    }

    public void revoke(MarketPrincipal identity) {
        redis.execute(DELETE_IF_CURRENT, List.of(key(identity.id())), identity.sessionId());
    }

    private String key(long accountId) {
        return "campus-market:session:" + accountId;
    }
}

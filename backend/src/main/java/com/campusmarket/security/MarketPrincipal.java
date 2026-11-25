package com.campusmarket.security;

import java.security.Principal;
import java.time.Instant;
import java.util.List;

public record MarketPrincipal(long id, String nickname, List<String> roles, String sessionId, Instant expiresAt)
        implements Principal {
    @Override
    public String getName() {
        return Long.toString(id);
    }
}

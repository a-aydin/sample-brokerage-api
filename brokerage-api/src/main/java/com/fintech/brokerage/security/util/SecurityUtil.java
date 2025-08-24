package com.fintech.brokerage.security.util;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

public class SecurityUtil {

    public static boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        for (GrantedAuthority a : auth.getAuthorities()) {
            if ("ROLE_ADMIN".equals(a.getAuthority())) return true;
        }
        return false;
    }

    public static Optional<UUID> currentCustomerId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return Optional.empty();
        Object details = auth.getDetails();
        if (details instanceof Map<?,?> map) {
            Object id = map.get("customerId");
            if (id instanceof String s) {
                try { return Optional.of(UUID.fromString(s)); } catch (Exception ignored) {}
            }
        }
        return Optional.empty();
    }
}

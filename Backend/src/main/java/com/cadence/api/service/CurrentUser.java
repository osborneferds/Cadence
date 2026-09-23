package com.cadence.api.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Resolves the signed-in username from the security context.
 * Returns null when the request is anonymous (open security mode).
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static String usernameOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null) {
            return null;
        }
        Object principal = auth.getPrincipal();
        if ("anonymousUser".equals(principal)) {
            return null;
        }
        return principal instanceof String s ? s : auth.getName();
    }
}
package com.akshat.ai.help_desk_bot.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class JwtUserContext {

    public String getUsername(Jwt jwt) {
        String email = jwt.getClaimAsString("email");
        if (email == null || email.isBlank() || !email.contains("@")) {
            throw new IllegalStateException("Valid email claim missing from JWT");
        }
        return email.substring(0, email.indexOf('@'));
    }

    public String getEmail(Jwt jwt) {
        return jwt.getClaimAsString("email");
    }

    public String getFullName(Jwt jwt) {
        return jwt.getClaimAsString("name");
    }

    public String getGoogleSubject(Jwt jwt) {
        return jwt.getSubject();
    }

    public String getCurrentUsername() {
        return getUsername(getCurrentJwt());
    }

    public String getCurrentEmail() {
        return getEmail(getCurrentJwt());
    }

    public String getCurrentSubject() {
        return getGoogleSubject(getCurrentJwt());
    }

    private Jwt getCurrentJwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new IllegalStateException("Authenticated Google JWT is required");
        }
        return jwt;
    }
}

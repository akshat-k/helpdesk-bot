package com.akshat.ai.help_desk_bot.security;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class JwtUserContext {

    public String getUsername(Jwt jwt) {
        // Google uses email as the unique identifier
        String email = jwt.getClaimAsString("email");
        if (email == null) throw new IllegalStateException("Email claim missing from JWT");
        // Use part before @ as username, or full email — your choice
        return email.split("@")[0]; // e.g. akshat.sharma@company.com → akshat.sharma
    }

    public String getEmail(Jwt jwt) {
        return jwt.getClaimAsString("email");
    }

    public String getFullName(Jwt jwt) {
        return jwt.getClaimAsString("name");
    }

    public String getGoogleSubject(Jwt jwt) {
        return jwt.getClaimAsString("sub"); // Google's unique user ID
    }
}

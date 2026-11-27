package com.akshat.ai.help_desk_bot.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Component("opsAuthorization")
public class OpsAuthorization {

    private final Set<String> adminSubjects;

    public OpsAuthorization(@Value("${app.ops.admin-subjects:}") String configuredSubjects) {
        this.adminSubjects = Arrays.stream(configuredSubjects.split(","))
                .map(String::trim)
                .filter(subject -> !subject.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean isAdmin(Authentication authentication) {
        return authentication instanceof JwtAuthenticationToken jwtAuthentication
                && adminSubjects.contains(jwtAuthentication.getToken().getSubject());
    }
}

package com.akshat.ai.help_desk_bot.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityConfigTest {

    private OAuth2TokenValidator<Jwt> validator;

    @BeforeEach
    void setUp() {
        SecurityConfig config = new SecurityConfig();
        ReflectionTestUtils.setField(config, "allowedDomain", "example.com");
        ReflectionTestUtils.setField(config, "googleClientId", "helpdesk-client");
        validator = config.googleJwtValidator();
    }

    @Test
    void acceptsGoogleTokenForConfiguredAudienceAndVerifiedDomainEmail() {
        assertFalse(validator.validate(validJwt()).hasErrors());
    }

    @Test
    void rejectsTokenFromAnotherIssuer() {
        assertTrue(validator.validate(jwt("https://attacker.example", List.of("helpdesk-client"),
                "employee@example.com", true)).hasErrors());
    }

    @Test
    void rejectsTokenForAnotherAudience() {
        assertTrue(validator.validate(jwt("https://accounts.google.com", List.of("another-client"),
                "employee@example.com", true)).hasErrors());
    }

    @Test
    void rejectsUnverifiedOrOutOfDomainEmail() {
        assertTrue(validator.validate(jwt("https://accounts.google.com", List.of("helpdesk-client"),
                "employee@example.com", false)).hasErrors());
        assertTrue(validator.validate(jwt("https://accounts.google.com", List.of("helpdesk-client"),
                "employee@notexample.com", true)).hasErrors());
    }

    private Jwt validJwt() {
        return jwt("https://accounts.google.com", List.of("helpdesk-client"), "employee@example.com", true);
    }

    private Jwt jwt(String issuer, List<String> audience, String email, boolean verified) {
        Instant now = Instant.now();
        return Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .issuer(issuer)
                .subject("google-user-id")
                .audience(audience)
                .issuedAt(now.minusSeconds(30))
                .expiresAt(now.plusSeconds(60))
                .claim("email", email)
                .claim("email_verified", verified)
                .build();
    }
}

package com.akshat.ai.help_desk_bot.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;
import java.util.Locale;
import java.util.Arrays;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Value("${app.security.allowed-domain:}")
    private String allowedDomain;

    @Value("${app.cors.allowed-origins:http://localhost:5173}")
    private String allowedOrigins;

    @Value("${spring.security.oauth2.client.registration.google.client-id:}")
    private String googleClientId;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/actuator/prometheus").permitAll()
                        .requestMatchers("/v1/auth/login", "/v1/auth/callback").permitAll()
                        .requestMatchers(
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs",
                                "/v3/api-docs/**"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                // Validates JWT on every API request
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.decoder(googleJwtDecoder()))
                );

        return http.build();
    }

    @Bean
    public JwtDecoder googleJwtDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withJwkSetUri("https://www.googleapis.com/oauth2/v3/certs")
                .build();
        decoder.setJwtValidator(googleJwtValidator());
        return decoder;
    }

    @Bean
    public OAuth2TokenValidator<Jwt> googleJwtValidator() {
        OAuth2TokenValidator<Jwt> issuerValidator = jwt -> {
            String issuer = jwt.getIssuer() == null ? null : jwt.getIssuer().toString();
            if ("https://accounts.google.com".equals(issuer) || "accounts.google.com".equals(issuer)) {
                return OAuth2TokenValidatorResult.success();
            }
            return validationFailure("invalid_issuer", "Token issuer is not Google");
        };

        OAuth2TokenValidator<Jwt> audienceValidator = jwt -> {
            if (googleClientId != null && !googleClientId.isBlank()
                    && jwt.getAudience().contains(googleClientId)) {
                return OAuth2TokenValidatorResult.success();
            }
            return validationFailure("invalid_audience", "Token audience is not configured for this application");
        };

        OAuth2TokenValidator<Jwt> identityValidator = jwt -> {
            String email = jwt.getClaimAsString("email");
            Boolean emailVerified = jwt.getClaimAsBoolean("email_verified");
            String normalizedDomain = allowedDomain == null
                    ? ""
                    : allowedDomain.trim().toLowerCase(Locale.ROOT);

            if (normalizedDomain.isEmpty() || email == null
                    || !email.toLowerCase(Locale.ROOT).endsWith("@" + normalizedDomain)) {
                return validationFailure("invalid_domain", "Account is not in the allowed email domain");
            }
            if (emailVerified == null || !emailVerified) {
                return validationFailure("email_not_verified", "Email must be verified");
            }
            if (jwt.getSubject() == null || jwt.getSubject().isBlank()) {
                return validationFailure("invalid_subject", "Token subject is required");
            }
            return OAuth2TokenValidatorResult.success();
        };

        return JwtValidators.createDefaultWithValidators(
                issuerValidator,
                audienceValidator,
                identityValidator
        );
    }

    private OAuth2TokenValidatorResult validationFailure(String code, String description) {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error(code, description, null));
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
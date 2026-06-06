package com.akshat.ai.help_desk_bot.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${app.security.allowed-domain}")
    private String allowedDomain;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/v1/auth/**").permitAll()
                        .requestMatchers("/v1/ai/**").permitAll()
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

        // Validate email domain — only allow company employees
        OAuth2TokenValidator<Jwt> domainValidator = jwt -> {
            String email = jwt.getClaimAsString("email");
            Boolean emailVerified = jwt.getClaimAsBoolean("email_verified");

            if (email == null || !email.endsWith("@" + allowedDomain)) {
                return OAuth2TokenValidatorResult.failure(
                        new OAuth2Error("invalid_domain",
                                "Only @" + allowedDomain + " accounts are allowed", null)
                );
            }
            if (emailVerified == null || !emailVerified) {
                return OAuth2TokenValidatorResult.failure(
                        new OAuth2Error("email_not_verified",
                                "Email must be verified", null)
                );
            }
            return OAuth2TokenValidatorResult.success();
        };

        decoder.setJwtValidator(JwtValidators.createDefaultWithValidators(domainValidator));
        return decoder;
    }
}
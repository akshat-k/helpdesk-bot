package com.akshat.ai.help_desk_bot.controller;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/v1/auth")
public class DevAuthController {

    @GetMapping("/login")
    public void login(HttpServletResponse response) throws IOException {
        // Redirect to Google login
        String googleAuthUrl = "https://accounts.google.com/o/oauth2/v2/auth" +
                "?client_id=" + clientId +
                "&redirect_uri=http://localhost:9191/v1/auth/callback" +
                "&response_type=code" +
                "&scope=openid email profile";
        response.sendRedirect(googleAuthUrl);
    }

    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String clientId;

    @Value("${spring.security.oauth2.client.registration.google.client-secret}")
    private String clientSecret;

    @GetMapping("/callback")
    public Map<String, String> callback(@RequestParam("code") String code) {
        // Exchange code for token
        RestTemplate restTemplate = new RestTemplate();

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("code", code);
        params.add("client_id", clientId);
        params.add("client_secret", clientSecret);
        params.add("redirect_uri", "http://localhost:9191/v1/auth/callback");
        params.add("grant_type", "authorization_code");

        Map response = restTemplate.postForObject(
                "https://oauth2.googleapis.com/token",
                params,
                Map.class
        );

        // Returns id_token — use this as Bearer token in Postman
        return Map.of(
                "id_token", (String) response.get("id_token"),
                "usage", "Use this as: Authorization: Bearer <id_token>"
        );
    }
}

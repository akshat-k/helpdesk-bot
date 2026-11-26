package com.akshat.ai.help_desk_bot.controller;

import com.akshat.ai.help_desk_bot.config.OpenApiConfig;
import com.akshat.ai.help_desk_bot.config.PaginationConfig;
import com.akshat.ai.help_desk_bot.config.SecurityConfig;
import com.akshat.ai.help_desk_bot.entity.Ticket;
import com.akshat.ai.help_desk_bot.enums.Priority;
import com.akshat.ai.help_desk_bot.enums.Status;
import com.akshat.ai.help_desk_bot.security.JwtUserContext;
import com.akshat.ai.help_desk_bot.service.HelpDeskService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TicketController.class)
@Import({SecurityConfig.class, OpenApiConfig.class, PaginationConfig.class, JwtUserContext.class})
@TestPropertySource(properties = {
        "spring.security.oauth2.client.registration.google.client-id=test-client",
        "app.security.allowed-domain=example.com"
})
class TicketControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HelpDeskService helpDeskService;

    @Test
    void ticketApiRequiresBearerAuthentication() throws Exception {
        mockMvc.perform(get("/v1/tickets"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listReturnsOnlyAuthenticatedOwnersTickets() throws Exception {
        when(helpDeskService.getTicketsForOwner(eq("google-sub"), any())).thenReturn(Page.empty());

        mockMvc.perform(get("/v1/tickets").with(authenticatedUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());

        verify(helpDeskService).getTicketsForOwner(eq("google-sub"), any());
    }

    @Test
    void listCapsRequestedPageSize() throws Exception {
        when(helpDeskService.getTicketsForOwner(eq("google-sub"), any())).thenReturn(Page.empty());

        mockMvc.perform(get("/v1/tickets?size=500").with(authenticatedUser()))
                .andExpect(status().isOk());

        verify(helpDeskService).getTicketsForOwner(eq("google-sub"), argThat(pageable -> pageable.getPageSize() == 100));
    }

    @Test
    void createValidatesRequestAndUsesJwtIdentityForOwner() throws Exception {
        mockMvc.perform(post("/v1/tickets")
                        .with(authenticatedUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":" ", "description":"Issue"}
                                """))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(helpDeskService);

        Ticket created = ticket(42L, "google-sub");
        when(helpDeskService.createTicket(any(), eq("google-sub"), eq("employee"), eq("employee@example.com")))
                .thenReturn(created);

        mockMvc.perform(post("/v1/tickets")
                        .with(authenticatedUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"VPN failure","description":"VPN login fails","priority":"HIGH","username":"other"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/v1/tickets/42"))
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.ownerSubject").doesNotExist());

        verify(helpDeskService).createTicket(any(), eq("google-sub"), eq("employee"), eq("employee@example.com"));
    }

    @Test
    void getHidesTicketsNotOwnedByAuthenticatedUser() throws Exception {
        when(helpDeskService.getTicketById(42L, "google-sub")).thenReturn(Optional.empty());

        mockMvc.perform(get("/v1/tickets/42").with(authenticatedUser()))
                .andExpect(status().isNotFound());

        verify(helpDeskService).getTicketById(42L, "google-sub");
    }

    @Test
    void patchRequiresAtLeastOneFieldAndScopesUpdateToJwtOwner() throws Exception {
        mockMvc.perform(patch("/v1/tickets/42")
                        .with(authenticatedUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(helpDeskService);

        Ticket updated = ticket(42L, "google-sub");
        updated.setStatus(Status.IN_PROGRESS);
        when(helpDeskService.updateTicket(eq(42L), eq("google-sub"), any(), eq("employee@example.com")))
                .thenReturn(Optional.of(updated));

        mockMvc.perform(patch("/v1/tickets/42")
                        .with(authenticatedUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"IN_PROGRESS"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        verify(helpDeskService).updateTicket(eq(42L), eq("google-sub"), any(), eq("employee@example.com"));
    }

    @Test
    void patchRejectsInvalidEnumAndOversizedFields() throws Exception {
        mockMvc.perform(patch("/v1/tickets/42")
                        .with(authenticatedUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"priority":"EXTREME"}
                                """))
                .andExpect(status().isBadRequest());

        String title = "x".repeat(201);
        mockMvc.perform(patch("/v1/tickets/42")
                        .with(authenticatedUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(helpDeskService);
    }

    private SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor authenticatedUser() {
        return SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(jwt -> jwt.subject("google-sub")
                        .claim("email", "employee@example.com")
                        .claim("email_verified", true));
    }

    private Ticket ticket(Long id, String ownerSubject) {
        return Ticket.builder()
                .id(id)
                .ownerSubject(ownerSubject)
                .username("employee")
                .title("VPN failure")
                .description("VPN login fails")
                .status(Status.OPEN)
                .priority(Priority.HIGH)
                .assignee("Unassigned")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }
}

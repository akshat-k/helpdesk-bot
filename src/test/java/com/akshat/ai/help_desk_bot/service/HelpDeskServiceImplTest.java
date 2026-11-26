package com.akshat.ai.help_desk_bot.service;

import com.akshat.ai.help_desk_bot.dto.TicketRequest;
import com.akshat.ai.help_desk_bot.entity.Ticket;
import com.akshat.ai.help_desk_bot.repository.TicketRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HelpDeskServiceImplTest {

    @Mock
    private TicketRepository ticketRepository;

    @InjectMocks
    private HelpDeskServiceImpl service;

    @Test
    void createsTicketForExplicitOwner() {
        TicketRequest request = new TicketRequest("VPN failure", "Cannot connect", null, null, null);
        when(ticketRepository.save(org.mockito.ArgumentMatchers.any(Ticket.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Ticket ticket = service.createTicket(request, "google-subject", "employee");

        assertEquals("employee", ticket.getUsername());
        assertEquals("google-subject", ticket.getOwnerSubject());
        assertEquals("VPN failure", ticket.getTitle());
    }

    @Test
    void loadsTicketOnlyByIdAndOwner() {
        Ticket ticket = Ticket.builder().id(10L).username("employee").title("VPN failure").build();
        when(ticketRepository.findByIdAndOwnerSubject(10L, "google-subject")).thenReturn(Optional.of(ticket));

        assertTrue(service.getTicketById(10L, "google-subject").isPresent());
        verify(ticketRepository).findByIdAndOwnerSubject(10L, "google-subject");
    }

    @Test
    void updatesTicketOnlyByIdAndOwner() {
        Ticket ticket = Ticket.builder().id(10L).ownerSubject("google-subject")
                .username("employee").title("VPN failure").build();
        when(ticketRepository.findByIdAndOwnerSubject(10L, "google-subject")).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(org.mockito.ArgumentMatchers.any(Ticket.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertTrue(service.updateTicket(10L, "google-subject", Ticket.builder().title("VPN login failure").build()).isPresent());
        verify(ticketRepository).findByIdAndOwnerSubject(10L, "google-subject");
        assertEquals("employee", ticket.getUsername());
        assertEquals("VPN login failure", ticket.getTitle());
    }
}

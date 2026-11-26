package com.akshat.ai.help_desk_bot.service;

import com.akshat.ai.help_desk_bot.dto.TicketRequest;
import com.akshat.ai.help_desk_bot.entity.Ticket;
import com.akshat.ai.help_desk_bot.enums.Status;
import com.akshat.ai.help_desk_bot.repository.TicketRepository;
import com.akshat.ai.help_desk_bot.event.TicketEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HelpDeskServiceImplTest {

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private TicketEventOutboxService ticketEventOutboxService;

    @InjectMocks
    private HelpDeskServiceImpl service;

    @Test
    void createsTicketForExplicitOwner() {
        TicketRequest request = new TicketRequest("VPN failure", "Cannot connect", null, null, null);
        when(ticketRepository.save(org.mockito.ArgumentMatchers.any(Ticket.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Ticket ticket = service.createTicket(request, "google-subject", "employee", "employee@example.com");

        assertEquals("employee", ticket.getUsername());
        assertEquals("google-subject", ticket.getOwnerSubject());
        assertEquals("VPN failure", ticket.getTitle());
        verify(ticketEventOutboxService).enqueue(ticket, TicketEvent.EventType.CREATED, "employee@example.com");
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

        assertTrue(service.updateTicket(10L, "google-subject",
                Ticket.builder().title("VPN login failure").build(), "employee@example.com").isPresent());
        verify(ticketRepository).findByIdAndOwnerSubject(10L, "google-subject");
        assertEquals("employee", ticket.getUsername());
        assertEquals("VPN login failure", ticket.getTitle());
        verify(ticketEventOutboxService).enqueue(ticket, TicketEvent.EventType.UPDATED, "employee@example.com");
    }

    @Test
    void emitsResolvedEventWhenTicketIsResolved() {
        Ticket ticket = Ticket.builder().id(10L).ownerSubject("google-subject")
                .username("employee").title("VPN failure").status(Status.IN_PROGRESS).build();
        when(ticketRepository.findByIdAndOwnerSubject(10L, "google-subject")).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(org.mockito.ArgumentMatchers.any(Ticket.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.updateTicket(10L, "google-subject",
                Ticket.builder().status(Status.RESOLVED).build(), "employee@example.com");

        verify(ticketEventOutboxService).enqueue(ticket, TicketEvent.EventType.RESOLVED, "employee@example.com");
    }

    @Test
    void doesNotPersistOrEmitEventWhenUpdateDoesNotChangeTicket() {
        Ticket ticket = Ticket.builder().id(10L).ownerSubject("google-subject")
                .username("employee").title("VPN failure").status(Status.OPEN).build();
        when(ticketRepository.findByIdAndOwnerSubject(10L, "google-subject")).thenReturn(Optional.of(ticket));

        service.updateTicket(10L, "google-subject",
                Ticket.builder().title("VPN failure").build(), "employee@example.com");

        verify(ticketRepository, never()).save(org.mockito.ArgumentMatchers.any(Ticket.class));
        verifyNoInteractions(ticketEventOutboxService);
    }
}

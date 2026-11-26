package com.akshat.ai.help_desk_bot.controller;

import com.akshat.ai.help_desk_bot.constants.IntegrationConstants;
import com.akshat.ai.help_desk_bot.dto.CreateTicketApiRequest;
import com.akshat.ai.help_desk_bot.dto.TicketRequest;
import com.akshat.ai.help_desk_bot.dto.TicketResponse;
import com.akshat.ai.help_desk_bot.dto.TicketUpdateRequest;
import com.akshat.ai.help_desk_bot.dto.UpdateTicketApiRequest;
import com.akshat.ai.help_desk_bot.entity.Ticket;
import com.akshat.ai.help_desk_bot.enums.Status;
import com.akshat.ai.help_desk_bot.security.JwtUserContext;
import com.akshat.ai.help_desk_bot.service.HelpDeskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.Optional;

@RestController
@RequestMapping("/v1/tickets")
@Tag(name = IntegrationConstants.TICKET_API_TAG, description = IntegrationConstants.TICKET_API_DESCRIPTION)
@SecurityRequirement(name = "bearerAuth")
public class TicketController {

    private final HelpDeskService helpDeskService;
    private final JwtUserContext jwtUserContext;

    public TicketController(HelpDeskService helpDeskService, JwtUserContext jwtUserContext) {
        this.helpDeskService = helpDeskService;
        this.jwtUserContext = jwtUserContext;
    }

    @GetMapping
    @Operation(summary = "List my tickets", description = IntegrationConstants.LIST_TICKETS_DESCRIPTION)
    public PagedModel<TicketResponse> listTickets(
            @PageableDefault(size = IntegrationConstants.DEFAULT_TICKET_PAGE_SIZE,
                    sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal Jwt jwt
    ) {
        Page<TicketResponse> tickets = helpDeskService
                .getTicketsForOwner(jwtUserContext.getGoogleSubject(jwt), pageable)
                .map(TicketResponse::from);
        return new PagedModel<>(tickets);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a ticket", description = IntegrationConstants.GET_TICKET_DESCRIPTION)
    public TicketResponse getTicket(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return helpDeskService.getTicketById(id, jwtUserContext.getGoogleSubject(jwt))
                .map(TicketResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, IntegrationConstants.TICKET_NOT_FOUND));
    }

    @PostMapping
    @Operation(summary = "Create a ticket", description = IntegrationConstants.CREATE_TICKET_DESCRIPTION)
    public ResponseEntity<TicketResponse> createTicket(
            @Valid @RequestBody CreateTicketApiRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        TicketRequest ticketRequest = TicketRequest.builder()
                .title(request.title())
                .description(request.description())
                .priority(Optional.ofNullable(request.priority()).map(Enum::name).orElse(null))
                .build();
        Ticket created = helpDeskService.createTicket(
                ticketRequest,
                jwtUserContext.getGoogleSubject(jwt),
                jwtUserContext.getUsername(jwt),
                jwtUserContext.getEmail(jwt)
        );
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest()
                        .path("/{id}")
                        .buildAndExpand(created.getId())
                        .toUri())
                .body(TicketResponse.from(created));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update a ticket", description = IntegrationConstants.UPDATE_TICKET_DESCRIPTION)
    public TicketResponse updateTicket(
            @PathVariable Long id,
            @Valid @RequestBody UpdateTicketApiRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        if (!request.hasUpdates()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, IntegrationConstants.UPDATE_TICKET_EMPTY_REQUEST);
        }

        Ticket updates = Ticket.builder()
                .title(request.title())
                .description(request.description())
                .priority(request.priority())
                .status(request.status())
                .assignee(request.assignee())
                .build();
        return helpDeskService.updateTicket(
                        id,
                        jwtUserContext.getGoogleSubject(jwt),
                        updates,
                        jwtUserContext.getEmail(jwt)
                )
                .map(TicketResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, IntegrationConstants.TICKET_NOT_FOUND));
    }
}

package com.akshat.ai.help_desk_bot.controller;

import com.akshat.ai.help_desk_bot.constants.IntegrationConstants;
import com.akshat.ai.help_desk_bot.dto.OutboxEventSummary;
import com.akshat.ai.help_desk_bot.entity.OutboxEvent;
import com.akshat.ai.help_desk_bot.service.OutboxOperationsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/v1/admin/outbox")
@Tag(name = "Operations", description = IntegrationConstants.ADMIN_OUTBOX_DESCRIPTION)
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("@opsAuthorization.isAdmin(authentication)")
public class OutboxAdminController {

    private final OutboxOperationsService operationsService;

    public OutboxAdminController(OutboxOperationsService operationsService) {
        this.operationsService = operationsService;
    }

    @GetMapping("/dead")
    @Operation(summary = "List dead outbox events")
    public PagedModel<OutboxEventSummary> listDeadEvents(
            @PageableDefault(size = IntegrationConstants.DEFAULT_ADMIN_PAGE_SIZE) Pageable pageable
    ) {
        Page<OutboxEventSummary> events = operationsService.findDeadEvents(pageable).map(OutboxEventSummary::from);
        return new PagedModel<>(events);
    }

    @PostMapping("/dead/{eventId}/replay")
    @Operation(summary = "Requeue a dead outbox event")
    public ResponseEntity<Void> replayDeadEvent(@PathVariable String eventId) {
        if (!operationsService.replayDeadEvent(eventId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    IntegrationConstants.OUTBOX_EVENT_NOT_REPLAYABLE
            );
        }
        return ResponseEntity.noContent().build();
    }
}

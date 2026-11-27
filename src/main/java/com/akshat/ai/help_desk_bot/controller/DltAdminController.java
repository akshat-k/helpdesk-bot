package com.akshat.ai.help_desk_bot.controller;

import com.akshat.ai.help_desk_bot.constants.IntegrationConstants;
import com.akshat.ai.help_desk_bot.dto.DltRecordSummary;
import com.akshat.ai.help_desk_bot.service.DltOperationsService;
import com.akshat.ai.help_desk_bot.service.DltOperationsService.ReplayResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/v1/admin/dlt")
@Tag(name = "Operations", description = IntegrationConstants.ADMIN_OUTBOX_DESCRIPTION)
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("@opsAuthorization.isAdmin(authentication)")
@Validated
public class DltAdminController {

    private final DltOperationsService dltOperationsService;

    public DltAdminController(DltOperationsService dltOperationsService) {
        this.dltOperationsService = dltOperationsService;
    }

    @GetMapping("/{sourceTopic}")
    @Operation(summary = "Inspect recent records in a ticket dead-letter topic")
    public List<DltRecordSummary> listRecords(
            @PathVariable String sourceTopic,
            @RequestParam(defaultValue = "" + IntegrationConstants.DEFAULT_DLT_PAGE_SIZE)
            @jakarta.validation.constraints.Min(1)
            @jakarta.validation.constraints.Max(IntegrationConstants.MAX_DLT_PAGE_SIZE) int limit
    ) {
        try {
            return dltOperationsService.listRecords(sourceTopic, limit);
        } catch (IllegalArgumentException invalidTopic) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, invalidTopic.getMessage(), invalidTopic);
        }
    }

    @PostMapping("/{sourceTopic}/{partition}/{offset}/replay")
    @Operation(summary = "Replay a retained dead-letter record")
    public ResponseEntity<Void> replay(
            @PathVariable String sourceTopic,
            @PathVariable int partition,
            @PathVariable long offset
    ) {
        ReplayResult result;
        try {
            result = dltOperationsService.replay(sourceTopic, partition, offset);
        } catch (IllegalArgumentException invalidRequest) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, invalidRequest.getMessage(), invalidRequest);
        }

        return switch (result) {
            case REPLAYED -> ResponseEntity.noContent().build();
            case ALREADY_REPLAYED -> ResponseEntity.status(HttpStatus.CONFLICT).build();
            case NOT_FOUND -> ResponseEntity.notFound().build();
        };
    }
}

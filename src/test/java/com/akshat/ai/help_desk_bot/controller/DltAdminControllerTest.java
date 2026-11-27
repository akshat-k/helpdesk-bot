package com.akshat.ai.help_desk_bot.controller;

import com.akshat.ai.help_desk_bot.config.SecurityConfig;
import com.akshat.ai.help_desk_bot.dto.DltRecordSummary;
import com.akshat.ai.help_desk_bot.security.OpsAuthorization;
import com.akshat.ai.help_desk_bot.service.DltOperationsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DltAdminController.class)
@Import({SecurityConfig.class, OpsAuthorization.class})
@TestPropertySource(properties = {
        "spring.security.oauth2.client.registration.google.client-id=test-client",
        "app.security.allowed-domain=example.com",
        "app.ops.admin-subjects=google-admin"
})
class DltAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DltOperationsService dltOperationsService;

    @Test
    void allowsOnlyConfiguredOperatorSubjectsToInspectAndReplayDltMessages() throws Exception {
        mockMvc.perform(get("/v1/admin/dlt/ticket-created").with(jwt().jwt(token -> token.subject("employee"))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(dltOperationsService);

        when(dltOperationsService.listRecords("ticket-created", 10)).thenReturn(List.of(
                new DltRecordSummary("ticket-created.DLT", 0, 17, 1000, "event-1", 42L, null)
        ));
        mockMvc.perform(get("/v1/admin/dlt/ticket-created?limit=10")
                        .with(jwt().jwt(token -> token.subject("google-admin"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eventId").value("event-1"))
                .andExpect(jsonPath("$[0].offset").value(17));

        when(dltOperationsService.replay("ticket-created", 0, 17))
                .thenReturn(DltOperationsService.ReplayResult.REPLAYED);
        mockMvc.perform(post("/v1/admin/dlt/ticket-created/0/17/replay")
                        .with(jwt().jwt(token -> token.subject("google-admin"))))
                .andExpect(status().isNoContent());

        when(dltOperationsService.replay("ticket-created", 0, 17))
                .thenReturn(DltOperationsService.ReplayResult.ALREADY_REPLAYED);
        mockMvc.perform(post("/v1/admin/dlt/ticket-created/0/17/replay")
                        .with(jwt().jwt(token -> token.subject("google-admin"))))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsPageSizesOutsideConfiguredRange() throws Exception {
        mockMvc.perform(get("/v1/admin/dlt/ticket-created?limit=1000")
                        .with(jwt().jwt(token -> token.subject("google-admin"))))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(dltOperationsService);
    }
}

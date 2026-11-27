package com.akshat.ai.help_desk_bot.controller;

import com.akshat.ai.help_desk_bot.config.SecurityConfig;
import com.akshat.ai.help_desk_bot.security.OpsAuthorization;
import com.akshat.ai.help_desk_bot.service.OutboxOperationsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OutboxAdminController.class)
@Import({SecurityConfig.class, OpsAuthorization.class})
@TestPropertySource(properties = {
        "spring.security.oauth2.client.registration.google.client-id=test-client",
        "app.security.allowed-domain=example.com",
        "app.ops.admin-subjects=google-admin"
})
class OutboxAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OutboxOperationsService operationsService;

    @Test
    void deniesOutboxOperationsToNonAdmins() throws Exception {
        mockMvc.perform(get("/v1/admin/outbox/dead").with(adminJwt("employee")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(operationsService);
    }

    @Test
    void adminCanInspectDeadEventsWithoutPayloadContents() throws Exception {
        when(operationsService.findDeadEvents(any())).thenReturn(Page.empty());

        mockMvc.perform(get("/v1/admin/outbox/dead").with(adminJwt("google-admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());

        verify(operationsService).findDeadEvents(any());
    }

    @Test
    void adminCanReplayDeadEventsButCannotReplayOtherStates() throws Exception {
        when(operationsService.replayDeadEvent("event-1")).thenReturn(true);
        when(operationsService.replayDeadEvent("missing")).thenReturn(false);

        mockMvc.perform(post("/v1/admin/outbox/dead/event-1/replay").with(adminJwt("google-admin")))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/v1/admin/outbox/dead/missing/replay").with(adminJwt("google-admin")))
                .andExpect(status().isNotFound());

        verify(operationsService).replayDeadEvent("event-1");
        verify(operationsService).replayDeadEvent("missing");
    }

    private SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor adminJwt(String subject) {
        return SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(jwt -> jwt.subject(subject)
                        .claim("email", subject + "@example.com")
                        .claim("email_verified", true));
    }
}

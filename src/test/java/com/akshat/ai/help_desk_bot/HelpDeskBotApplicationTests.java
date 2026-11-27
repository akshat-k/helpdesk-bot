package com.akshat.ai.help_desk_bot;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:app-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"spring.flyway.enabled=false",
		"spring.kafka.admin.auto-create=false",
		"spring.kafka.listener.auto-startup=false",
		"spring.ai.openai.api-key=test",
		"spring.security.oauth2.client.registration.google.client-id=test-client",
		"app.security.allowed-domain=example.com",
		"app.outbox.poll-interval-ms=3600000"
})
@AutoConfigureMockMvc
class HelpDeskBotApplicationTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void contextLoads() {
	}

	@Test
	void servesVersionedOpenApiContractWithoutAuthentication() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.info.version").value("v1"))
				.andExpect(jsonPath("$.paths['/v1/tickets']").exists())
				.andExpect(jsonPath("$.paths['/v1/admin/outbox/dead']").exists());
	}

	@Test
	void exposesReadinessAndPrometheusOutboxMetrics() throws Exception {
		mockMvc.perform(get("/actuator/health/readiness"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"));

		mockMvc.perform(get("/actuator/prometheus"))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString("helpdesk_outbox_events")));
	}

}

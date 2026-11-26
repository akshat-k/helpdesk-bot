package com.akshat.ai.help_desk_bot.config;

import com.akshat.ai.help_desk_bot.constants.IntegrationConstants;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.web.config.PageableHandlerMethodArgumentResolverCustomizer;

@Configuration
public class PaginationConfig {

    @Bean
    PageableHandlerMethodArgumentResolverCustomizer ticketPageSizeLimit() {
        return resolver -> resolver.setMaxPageSize(IntegrationConstants.MAX_TICKET_PAGE_SIZE);
    }
}

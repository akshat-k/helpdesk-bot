package com.akshat.ai.help_desk_bot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class HelpDeskBotApplication {

	public static void main(String[] args) {
		SpringApplication.run(HelpDeskBotApplication.class, args);
	}

}

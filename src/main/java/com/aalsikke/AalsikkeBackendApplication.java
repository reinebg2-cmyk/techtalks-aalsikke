package com.aalsikke;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling   // KAN-336: runs ReminderEmailJob every minute
public class AalsikkeBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(AalsikkeBackendApplication.class, args);
	}

}

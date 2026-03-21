package org.bruneel.thankyouboard;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ThankYouBoardApplication {

	private static final Logger log = LoggerFactory.getLogger(ThankYouBoardApplication.class);

	public static void main(String[] args) {
		log.info("Starting application");
		SpringApplication.run(ThankYouBoardApplication.class, args);
		log.info("Application started successfully");
	}

}

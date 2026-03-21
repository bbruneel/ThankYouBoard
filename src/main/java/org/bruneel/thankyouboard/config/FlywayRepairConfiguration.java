package org.bruneel.thankyouboard.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

/**
 * When profile {@code flyway-repair} is active, runs Flyway repair instead of migrate
 * and then exits. Use to fix checksum mismatches after editing an already-applied migration.
 * <p>
 * Run: {@code mvn spring-boot:run -Dspring-boot.run.profiles=flyway-repair}
 */
@Configuration
@Profile("flyway-repair")
public class FlywayRepairConfiguration {

	private static final Logger log = LoggerFactory.getLogger(FlywayRepairConfiguration.class);

	@Bean
	public FlywayMigrationStrategy flywayRepairStrategy() {
		return flyway -> {
			log.info("Running Flyway repair (profile flyway-repair active)");
			flyway.repair();
			log.info("Flyway repair completed");
		};
	}

	@Bean
	@Order(Ordered.LOWEST_PRECEDENCE)
	public org.springframework.boot.ApplicationRunner exitAfterRepair() {
		return args -> {
			log.info("Exiting after Flyway repair");
			System.exit(0);
		};
	}
}

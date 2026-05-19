package com.zhiyu.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "flyway.migration.mode", havingValue = "true")
public class FlywayMigrationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(FlywayMigrationRunner.class);

    private final ConfigurableApplicationContext context;

    public FlywayMigrationRunner(ConfigurableApplicationContext context) {
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("Flyway migrations completed — exiting (migration mode)");
        int exitCode = SpringApplication.exit(context, () -> 0);
        System.exit(exitCode);
    }
}

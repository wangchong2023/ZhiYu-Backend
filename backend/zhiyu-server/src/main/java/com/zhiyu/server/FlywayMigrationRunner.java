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
public final class FlywayMigrationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(FlywayMigrationRunner.class);

    private final ConfigurableApplicationContext appContext;

    public FlywayMigrationRunner(final ConfigurableApplicationContext ctx) {
        this.appContext = ctx;
    }

    @Override
    public void run(final ApplicationArguments args) {
        log.info("Flyway migrations completed — exiting (migration mode)");
        int exitCode = SpringApplication.exit(appContext, () -> 0);
        System.exit(exitCode);
    }
}

/**
 * 文件名: FlywayMigrationRunner.java
 * 描述: Flyway 数据库迁移模式运行器。在 flyway.migration.mode=true 时运行，并在迁移完成后主动关闭 Spring 容器并退出进程。
 */
package com.zhiyu.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

/**
 * 类名: FlywayMigrationRunner
 * 描述: 提供在迁移模式下运行后自动终止进程的功能，用于 CI/CD 中的数据库迁移步骤。
 */
@Component
@ConditionalOnProperty(name = "flyway.migration.mode", havingValue = "true")
public final class FlywayMigrationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(FlywayMigrationRunner.class);

    private final ConfigurableApplicationContext appContext;

    /**
     * 描述: 构造函数，注入 Spring 上下文容器
     * @param ctx Spring 上下文容器
     */
    public FlywayMigrationRunner(final ConfigurableApplicationContext ctx) {
        this.appContext = ctx;
    }

    /**
     * 描述: 执行 Runner 逻辑，触发 SpringApplication 退出，并返回退出状态码退出进程
     * @param args 命令行启动参数
     */
    @Override
    public void run(final ApplicationArguments args) {
        log.info("Flyway migrations completed — exiting (migration mode)");
        int exitCode = SpringApplication.exit(appContext, () -> 0);
        System.exit(exitCode);
    }
}


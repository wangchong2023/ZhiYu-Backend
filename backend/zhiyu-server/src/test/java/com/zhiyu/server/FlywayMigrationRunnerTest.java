package com.zhiyu.server;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

@ExtendWith(MockitoExtension.class)
class FlywayMigrationRunnerTest {

    @Mock
    private ConfigurableApplicationContext appContext;

    private SecurityManager originalSecurityManager;

    @BeforeEach
    void setUp() {
        originalSecurityManager = System.getSecurityManager();
        System.setSecurityManager(new NoExitSecurityManager());
    }

    @AfterEach
    void tearDown() {
        System.setSecurityManager(originalSecurityManager);
    }

    @Test
    void runExitsApplicationWithZeroCode() {
        FlywayMigrationRunner runner = new FlywayMigrationRunner(appContext);
        assertNotNull(runner);

        try (MockedStatic<SpringApplication> mockedSpring = mockStatic(SpringApplication.class)) {
            mockedSpring.when(() -> SpringApplication.exit(
                    any(ConfigurableApplicationContext.class), any(ExitCodeGenerator[].class)))
                    .thenAnswer(invocation -> {
                        Object raw = invocation.getArgument(1);
                        if (raw instanceof ExitCodeGenerator[] generators) {
                            generators[0].getExitCode();
                        } else if (raw instanceof ExitCodeGenerator gen) {
                            gen.getExitCode();
                        }
                        return 0;
                    });

            try {
                runner.run(new DefaultApplicationArguments(new String[]{}));
                fail("Expected SecurityException from System.exit(0)");
            } catch (SecurityException e) {
                assertEquals("0", e.getMessage());
            }
        }
    }

    private static class NoExitSecurityManager extends SecurityManager {
        @Override
        public void checkExit(int status) {
            throw new SecurityException(String.valueOf(status));
        }

        @Override
        public void checkPermission(java.security.Permission perm) {
        }

        @Override
        public void checkPermission(java.security.Permission perm, Object context) {
        }
    }
}

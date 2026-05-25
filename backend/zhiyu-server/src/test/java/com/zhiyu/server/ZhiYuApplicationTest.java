package com.zhiyu.server;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.SpringApplication;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

@ExtendWith(MockitoExtension.class)
class ZhiYuApplicationTest {

    @Test
    void mainCallsSpringApplicationRun() {
        try (MockedStatic<SpringApplication> mockedSpring = mockStatic(SpringApplication.class)) {
            mockedSpring.when(() -> SpringApplication.run(
                    eq(ZhiYuApplication.class), any(String[].class)))
                    .thenReturn(null);

            ZhiYuApplication.main(new String[]{});

            mockedSpring.verify(() -> SpringApplication.run(
                    eq(ZhiYuApplication.class), any(String[].class)));
        }
    }
}

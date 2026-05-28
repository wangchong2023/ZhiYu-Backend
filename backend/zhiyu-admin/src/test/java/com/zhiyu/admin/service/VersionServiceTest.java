/*
 * Copyright (c) 2026 ZhiYu. All rights reserved.
 * 文件名: VersionServiceTest.java
 * 创建时间: 2026-05-28
 * 描述: 版本服务 VersionService 的高规格单元测试，包含 Properties 解析与防御性格式化校验。
 */
package com.zhiyu.admin.service;

import com.zhiyu.admin.dto.VersionDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 类名: VersionServiceTest
 * 描述: 对后端微服务动态版本号、Git 提交以及构建日期格式化进行 Happy Path 与防御性极限条件测试。
 */
@ExtendWith(MockitoExtension.class)
class VersionServiceTest {

    @InjectMocks
    private VersionService versionService;

    private Path tempGitPropertiesPath;

    @BeforeEach
    void setUp() {
        // 在 test-classes classpath 根路径下寻找并定义临时 git.properties 的路径
        Path testClassesDir = Paths.get("target", "test-classes");
        if (Files.exists(testClassesDir)) {
            tempGitPropertiesPath = testClassesDir.resolve("git.properties");
        } else {
            // 兼容有些 IDE 或者独立构建环境的路径
            tempGitPropertiesPath = Paths.get("src", "test", "resources", "git.properties");
        }
    }

    @AfterEach
    void tearDown() throws IOException {
        // 测试完毕后物理删除临时 git.properties，保持 classpath 洁净，避免污染其它用例
        if (tempGitPropertiesPath != null && Files.exists(tempGitPropertiesPath)) {
            Files.delete(tempGitPropertiesPath);
        }
    }

    /**
     * 描述: 测试当没有 git.properties 配置文件（例如在本地开发测试）时，
     *       服务是否能防御并稳健降级返回 unknown 兜底信息。
     */
    @Test
    void shouldReturnDefaultFallbackVersionWhenPropertiesMissing() {
        // 确保临时配置文件不存在
        if (tempGitPropertiesPath != null && Files.exists(tempGitPropertiesPath)) {
            try {
                Files.delete(tempGitPropertiesPath);
            } catch (IOException ignored) {}
        }

        // 调用业务方法
        VersionDto dto = versionService.getVersion();

        // 校验退化降级兜底信息
        assertThat(dto).isNotNull();
        assertThat(dto.getServices()).hasSize(3);
        
        VersionDto.ServiceVersion gateway = dto.getServices().stream()
                .filter(s -> "ufp-gateway".equals(s.getName())).findFirst().orElseThrow();
        assertThat(gateway.getVersion()).isEqualTo("1.0.0");
        assertThat(gateway.getCommitId()).isEqualTo("unknown");
        assertThat(gateway.getBuildTime()).isEqualTo("unknown");
    }

    /**
     * 描述: 测试成功读取并完整解析 git.properties 的 Happy Path 场景。
     */
    @Test
    void shouldParseVersionDetailsSuccessfullyWhenPropertiesExist() throws IOException {
        // 1. 动态向 classpath 写入测试用的 git.properties
        String content = "git.commit.id.abbrev=abc1234\n" +
                "git.build.time=2026-05-28T10:15:30Z\n" +
                "git.build.version=1.2.0-SNAPSHOT\n";
        
        Files.createDirectories(tempGitPropertiesPath.getParent());
        Files.writeString(tempGitPropertiesPath, content);

        // 2. 执行调用
        VersionDto dto = versionService.getVersion();

        // 3. 校验字段映射与替换 SNAPSHOT 后缀逻辑
        assertThat(dto).isNotNull();
        assertThat(dto.getServices()).hasSize(3);
        
        VersionDto.ServiceVersion auth = dto.getServices().stream()
                .filter(s -> "ufp-auth".equals(s.getName())).findFirst().orElseThrow();
        // SNAPSHOT 被替换为 ""
        assertThat(auth.getVersion()).isEqualTo("1.2.0");
        assertThat(auth.getCommitId()).isEqualTo("abc1234");
        // 转换为了上海时区构建时间
        assertThat(auth.getBuildTime()).isEqualTo("2026-05-28 18:15:30");
    }

    /**
     * 描述: 测试当构建时间格式非法时，解析异常防御分支（formatBuildTime 的 Exception 分支）能够防崩溃并原样返回原始时间字符串。
     */
    @Test
    void shouldReturnRawStringWhenBuildTimeFormatIsInvalid() throws IOException {
        // 1. 写入带有非法构建时间的配置
        String content = "git.commit.id.abbrev=xyz9999\n" +
                "git.build.time=InvalidISOFormatString\n" +
                "git.build.version=1.0.0-SNAPSHOT\n";
        
        Files.createDirectories(tempGitPropertiesPath.getParent());
        Files.writeString(tempGitPropertiesPath, content);

        // 2. 调用服务
        VersionDto dto = versionService.getVersion();

        // 3. 验证是否完美触发 catch 并退化回退
        VersionDto.ServiceVersion admin = dto.getServices().stream()
                .filter(s -> "zhiyu-admin".equals(s.getName())).findFirst().orElseThrow();
        assertThat(admin.getBuildTime()).isEqualTo("InvalidISOFormatString");
    }
}

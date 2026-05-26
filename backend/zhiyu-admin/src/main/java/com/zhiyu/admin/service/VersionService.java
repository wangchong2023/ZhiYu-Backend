package com.zhiyu.admin.service;

import com.zhiyu.admin.dto.VersionDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Properties;

@Slf4j
@Service
public class VersionService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.of("Asia/Shanghai"));

    private static final List<String> SERVICE_NAMES = List.of("ufp-gateway", "ufp-auth", "zhiyu-admin");

    public VersionDto getVersion() {
        Properties props = loadGitProperties();

        String commitId = props.getProperty("git.commit.id.abbrev", "unknown");
        String buildTime = formatBuildTime(props.getProperty("git.build.time", ""));
        String rawVersion = props.getProperty("git.build.version", "1.0.0");
        String version = rawVersion.replace("-SNAPSHOT", "");

        List<VersionDto.ServiceVersion> services = SERVICE_NAMES.stream()
                .map(name -> VersionDto.ServiceVersion.builder()
                        .name(name)
                        .version(version)
                        .commitId(commitId)
                        .buildTime(buildTime)
                        .build())
                .toList();

        return VersionDto.builder().services(services).build();
    }

    private Properties loadGitProperties() {
        Properties props = new Properties();
        try (InputStream in = new ClassPathResource("git.properties").getInputStream()) {
            props.load(in);
        } catch (Exception e) {
            if (log.isDebugEnabled()) {
                log.debug("git.properties not available: {}", e.getMessage());
            }
        }
        return props;
    }

    private String formatBuildTime(final String raw) {
        if (raw == null || raw.isEmpty()) {
            return "unknown";
        }
        try {
            return FMT.format(Instant.parse(raw));
        } catch (Exception e) {
            if (log.isDebugEnabled()) {
                log.debug("Failed to parse build time: {}", e.getMessage());
            }
            return raw;
        }
    }
}

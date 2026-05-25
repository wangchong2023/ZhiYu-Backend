package com.zhiyu.admin.service;

import com.zhiyu.admin.dto.VersionDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Properties;

@Slf4j
@Service
public class VersionService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.of("Asia/Shanghai"));

    public VersionDto getVersion() {
        Properties props = new Properties();
        try (InputStream in = new ClassPathResource("git.properties").getInputStream()) {
            props.load(in);
        } catch (Exception e) {
            if (log.isDebugEnabled()) {
                log.debug("git.properties not available: {}", e.getMessage());
            }
        }

        String commitId = props.getProperty("git.commit.id.abbrev", "unknown");
        String buildTime = props.getProperty("git.build.time", "");
        if (!buildTime.isEmpty()) {
            try {
                buildTime = FMT.format(Instant.parse(buildTime));
            } catch (Exception e) {
                // keep as-is
                if (log.isDebugEnabled()) {
                    log.debug("Failed to parse build time: {}", e.getMessage());
                }
            }
        } else {
            buildTime = "unknown";
        }

        String rawVersion = props.getProperty("git.build.version", "1.0.0");
        String version = rawVersion.replace("-SNAPSHOT", "");
        return VersionDto.builder()
                .version(version)
                .buildTime(buildTime)
                .commitId(commitId)
                .branch(props.getProperty("git.branch", "unknown"))
                .build();
    }
}

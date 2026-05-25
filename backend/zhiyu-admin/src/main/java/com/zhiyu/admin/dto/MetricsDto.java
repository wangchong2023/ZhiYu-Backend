package com.zhiyu.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "进程资源快照")
public class MetricsDto {
    // ── CPU ──
    @Schema(description = "进程 CPU 负载 (0.0–1.0)") private double processCpuLoad;
    @Schema(description = "系统 CPU 负载 (0.0–1.0)") private double systemCpuLoad;
    @Schema(description = "CPU 逻辑核数") private int cpuCores;
    @Schema(description = "当前线程数") private int threadCount;
    @Schema(description = "JVM 启动以来峰值线程数") private int peakThreadCount;
    @Schema(description = "进程运行时长 (ms)") private long processUptimeMs;

    // ── Memory ──
    @Schema(description = "进程 RSS (bytes)") private long rssBytes;
    @Schema(description = "JVM 堆已用 (bytes)") private long heapUsedBytes;
    @Schema(description = "JVM 堆上限 (bytes)") private long heapMaxBytes;
    @Schema(description = "系统物理内存总量 (bytes)") private long systemMemoryTotal;
    @Schema(description = "系统空闲物理内存 (bytes)") private long systemMemoryFree;
}

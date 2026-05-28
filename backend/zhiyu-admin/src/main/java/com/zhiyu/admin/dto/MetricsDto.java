/*
 * Copyright (c) 2026 ZhiYu Company. All rights reserved.
 */

package com.zhiyu.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 平台运维监控：单节点进程级资源快照数据传输对象（DTO）。
 *
 * <p>用于封装单个微服务实例运行时捕获的核心物理指标快照，包括 CPU 负载、JVM 堆内存占比、
 * 系统总物理内存空闲度以及当前活动的 Java 线程计数等，供后台监控大盘渲染。</p>
 *
 * @author ZhiYu Architect
 * @version 1.0.0
 * @since 2026-05-28
 */
@Data
@Builder
@Schema(description = "进程资源快照")
public class MetricsDto {

    // ── CPU 计算资源指标 ──
    @Schema(description = "进程 CPU 负载 (0.0–1.0)") private double processCpuLoad;
    @Schema(description = "系统 CPU 负载 (0.0–1.0)") private double systemCpuLoad;
    @Schema(description = "CPU 逻辑核数") private int cpuCores;
    @Schema(description = "当前线程数") private int threadCount;
    @Schema(description = "JVM 启动以来峰值线程数") private int peakThreadCount;
    @Schema(description = "进程运行时长 (ms)") private long processUptimeMs;

    // ── 内存及存储资源指标 ──
    @Schema(description = "进程 RSS (bytes)") private long rssBytes;
    @Schema(description = "JVM 堆已用 (bytes)") private long heapUsedBytes;
    @Schema(description = "JVM 堆上限 (bytes)") private long heapMaxBytes;
    @Schema(description = "系统物理内存总量 (bytes)") private long systemMemoryTotal;
    @Schema(description = "系统空闲物理内存 (bytes)") private long systemMemoryFree;
}

package com.zhiyu.ufp.common.monitor;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CpuInfo {
    /** Process CPU load (0.0–1.0) */
    private double processCpuLoad;
    /** System-wide CPU load (0.0–1.0) */
    private double systemCpuLoad;
    /** CPU logical core count */
    private int cpuCores;
    /** Current thread count */
    private int threadCount;
    /** Peak thread count since JVM start */
    private int peakThreadCount;
    /** Process uptime in milliseconds */
    private long processUptimeMs;
}

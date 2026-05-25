package com.zhiyu.ufp.common.monitor;

import lombok.extern.slf4j.Slf4j;
import oshi.SystemInfo;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;

@Slf4j
public final class CpuInfoProvider {

    private static final SystemInfo SI = new SystemInfo();
    private static final ThreadMXBean THREAD_BEAN = ManagementFactory.getThreadMXBean();

    private CpuInfoProvider() {
    }

    public static CpuInfo snapshot() {
        var os = SI.getOperatingSystem();
        var proc = SI.getHardware().getProcessor();
        var osProcess = os.getProcess(os.getProcessId());

        return CpuInfo.builder()
                .processCpuLoad(osProcess.getProcessCpuLoadCumulative())
                .systemCpuLoad(proc.getSystemCpuLoad(500))
                .cpuCores(proc.getLogicalProcessorCount())
                .threadCount(THREAD_BEAN.getThreadCount())
                .peakThreadCount(THREAD_BEAN.getPeakThreadCount())
                .processUptimeMs(osProcess.getUpTime())
                .build();
    }
}

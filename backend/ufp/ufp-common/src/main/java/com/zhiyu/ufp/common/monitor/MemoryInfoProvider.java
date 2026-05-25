package com.zhiyu.ufp.common.monitor;

import lombok.extern.slf4j.Slf4j;
import oshi.SystemInfo;

@Slf4j
public final class MemoryInfoProvider {

    private static final SystemInfo SI = new SystemInfo();

    private MemoryInfoProvider() {
    }

    public static MemoryInfo snapshot() {
        var mem = SI.getHardware().getMemory();
        var osProcess = SI.getOperatingSystem().getProcess(SI.getOperatingSystem().getProcessId());
        var runtime = Runtime.getRuntime();

        return MemoryInfo.builder()
                .rssBytes(osProcess.getResidentMemory())
                .heapUsedBytes(runtime.totalMemory() - runtime.freeMemory())
                .heapMaxBytes(runtime.maxMemory())
                .systemMemoryTotal(mem.getTotal())
                .systemMemoryFree(mem.getAvailable())
                .build();
    }
}

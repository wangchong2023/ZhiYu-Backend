package com.zhiyu.ufp.common.monitor;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MemoryInfo {
    /** Process RSS in bytes */
    private long rssBytes;
    /** JVM heap used in bytes */
    private long heapUsedBytes;
    /** JVM heap max in bytes */
    private long heapMaxBytes;
    /** System total physical memory in bytes */
    private long systemMemoryTotal;
    /** System free physical memory in bytes */
    private long systemMemoryFree;
}

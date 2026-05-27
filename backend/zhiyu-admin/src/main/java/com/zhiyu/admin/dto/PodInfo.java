package com.zhiyu.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "Kubernetes Pod 状态")
public class PodInfo {
    @Schema(description = "Pod 名称") private String name;
    @Schema(description = "命名空间") private String namespace;
    @Schema(description = "状态: Running / Pending / Failed / Unknown") private String status;
    @Schema(description = "Pod 启动时间 (ISO 8601)") private String startTime;
    @Schema(description = "重启次数") private int restartCount;
    @Schema(description = "最后重启时间 (ISO 8601)，无重启则为 null") private String lastRestartTime;
}

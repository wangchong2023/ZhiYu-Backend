package com.zhiyu.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "zhiyu.security")
public class SecurityProperties {

    /**
     * 无需认证即可访问的路径列表（支持 Ant 风格通配符 ? * **）
     */
    private List<String> permitAllPaths = new ArrayList<>();

    /**
     * 认证失败的错误码
     */
    private int authErrorCode = 40101;

    /**
     * 认证失败的提示消息（国际化 key 或默认文本）
     */
    private String authErrorMessage = "未登录或 token 已过期";
}

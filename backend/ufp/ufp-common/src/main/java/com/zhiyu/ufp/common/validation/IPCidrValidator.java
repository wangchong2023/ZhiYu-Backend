/*
 * Copyright (c) 2026 ZhiYu. All rights reserved.
 * 文件名: IPCidrValidator.java
 * 创建时间: 2026-05-27
 * 描述: 自定义 CIDR 校验器实现类，委托 com.github.seancfoley.ipaddress 库对输入网段做合法性校验。
 */
package com.zhiyu.ufp.common.validation;
 
import inet.ipaddr.IPAddressString;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
 
/**
 * 类名: IPCidrValidator
 * 描述: 实现 ConstraintValidator 接口，对标注了 @IPCidr 的字段或参数进行运行时正则/语义合法性校验。
 */
public class IPCidrValidator implements ConstraintValidator<IPCidr, String> {
 
    /**
     * 描述: 核心校验逻辑。空值默认通过，由 @NotNull 或 @NotBlank 承担非空控制。
     *      使用 IPAddressString 来判断字符串是否是一个格式正确的 IPv4/IPv6 地址或 CIDR 网段。
     * @param value 待验证的字符串网段
     * @param context 校验上下文环境
     * @return 校验通过返回 true，否则返回 false
     */
    @Override
    public boolean isValid(final String value, final ConstraintValidatorContext context) {
        // 空值按校验契约直接放行，不触发强约束
        if (value == null || value.trim().isEmpty()) {
            return true;
        }
 
        // 委托 ipaddress 库解析并校验 IP 地址/网段格式的正确性
        IPAddressString ipAddressString = new IPAddressString(value.trim());
        return ipAddressString.isValid();
    }
}

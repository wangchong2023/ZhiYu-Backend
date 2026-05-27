/*
 * Copyright (c) 2026 ZhiYu. All rights reserved.
 * 文件名: IPCidr.java
 * 创建时间: 2026-05-27
 * 描述: 自定义 CIDR 格式 IP 段校验注解，基于 JSR-380 (Jakarta Validation) 标准。
 */
package com.zhiyu.ufp.common.validation;
 
import jakarta.validation.Constraint;
import jakarta.validation.Payload;
 
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
 
/**
 * 注解名: IPCidr
 * 描述: 用于对参数或字段进行 CIDR 网段格式校验（如 192.168.1.0/24 或 2001:db8::/32）。
 *      支持校验普通的单个 IP（如果不带掩码则作为 /32 校验），同时支持 IPv4 和 IPv6 格式。
 */
@Documented
@Constraint(validatedBy = IPCidrValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface IPCidr {
 
    /**
     * 描述: 校验失败时的默认提示信息。
     * @return 错误提示文案
     */
    String message() default "无效的 IP CIDR 网段格式";
 
    /**
     * 描述: 校验分组信息。
     * @return 分组类数组
     */
    Class<?>[] groups() default {};
 
    /**
     * 描述: 负载信息。
     * @return 负载类型数组
     */
    Class<? extends Payload>[] payload() default {};
}

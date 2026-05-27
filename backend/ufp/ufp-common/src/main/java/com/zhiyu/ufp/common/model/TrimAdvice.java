/*
 * Copyright (c) 2026 ZhiYu. All rights reserved.
 * 文件名: TrimAdvice.java
 * 创建时间: 2026-05-27
 * 描述: 参数自动去首尾空格 AOP 切面实现类。
 */
package com.zhiyu.ufp.common.model;

import cn.hutool.core.util.ReflectUtil;
import cn.hutool.core.util.StrUtil;
import com.zhiyu.ufp.common.annotation.Trim;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;

/**
 * 类名: TrimAdvice
 * 描述: AOP 参数去空切面。当方法加标了 @Trim 注解时，自动清除直接传入的 String 入参首尾空格，
 *      并反射遍历入参 DTO 对象中所有声明的 String 属性执行 trim() 净化。
 */
@Aspect
@Component
public class TrimAdvice {

    /**
     * 描述: 拦截带有 @Trim 注解的方法。遍历每个入参并对其进行去空处理。
     * @param pjp 代理切入点
     * @return 原业务方法返回值
     * @throws Throwable 业务抛出的异常
     */
    @Around("@annotation(com.zhiyu.ufp.common.annotation.Trim)")
    public Object trim(final ProceedingJoinPoint pjp) throws Throwable {
        Object[] args = pjp.getArgs();
        for (int i = 0; i < args.length; i++) {
            if (args[i] == null) {
                continue;
            }
            if (args[i] instanceof String str) {
                args[i] = StrUtil.trim(str);
            } else {
                // 如果是自定义 DTO 实体，则反射遍历其 String 属性并去空
                trimObjectFields(args[i]);
            }
        }
        return pjp.proceed(args);
    }

    /**
     * 描述: 反射遍历对象中所有声明的 String 类型的字段，执行 trim 净化。
     * @param obj 待去空的实体对象
     */
    private void trimObjectFields(final Object obj) {
        Class<?> clazz = obj.getClass();
        String pkgName = clazz.getPackageName();
        // 排查基本 JDK 类型与系统框架类，仅对自定义实体类属性进行 trim 遍历
        if (pkgName.startsWith("java.") || pkgName.startsWith("javax.") || pkgName.startsWith("org.springframework.")) {
            return;
        }
        
        Field[] fields = ReflectUtil.getFields(clazz);
        for (Field field : fields) {
            if (field.getType().equals(String.class)) {
                try {
                    String val = (String) ReflectUtil.getFieldValue(obj, field);
                    if (val != null) {
                        ReflectUtil.setFieldValue(obj, field, StrUtil.trim(val));
                    }
                } catch (Exception e) {
                    // 防御性捕获，避免安全策略反射受限导致核心流程阻断
                }
            }
        }
    }
}

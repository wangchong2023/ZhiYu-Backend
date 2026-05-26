package com.zhiyu.ufp.common.model;

import cn.hutool.core.util.StrUtil;
import com.zhiyu.ufp.common.annotation.Trim;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * AOP aspect that trims all String arguments of methods annotated with {@link Trim}.
 */
@Aspect
@Component
public class TrimAdvice {

    @Around("@annotation(com.zhiyu.ufp.common.annotation.Trim)")
    public Object trim(final ProceedingJoinPoint pjp) throws Throwable {
        Object[] args = pjp.getArgs();
        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof String str) {
                args[i] = StrUtil.trim(str);
            }
        }
        return pjp.proceed(args);
    }
}

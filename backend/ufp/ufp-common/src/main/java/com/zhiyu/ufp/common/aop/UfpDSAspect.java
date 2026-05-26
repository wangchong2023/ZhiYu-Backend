package com.zhiyu.ufp.common.aop;

import com.zhiyu.ufp.common.datasource.UfpDS;
import com.zhiyu.ufp.common.datasource.UfpDSContextHolder;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Intercepts {@code @UfpDS}-annotated classes and methods,
 * pushing the declared datasource key onto the thread-local
 * {@link UfpDSContextHolder} before execution and restoring
 * on completion.
 *
 * <p>Ordered at {@code Ordered.HIGHEST_PRECEDENCE + 10} so the
 * datasource is set before any other AOP advice (transaction,
 * logging, caching) needs it.
 */
@Aspect
@Component
@Order(AopOrder.UFP_DS)
public class UfpDSAspect {

    @Around("@within(ufpDS) && !@annotation(com.zhiyu.ufp.common.datasource.UfpDS)")
    public Object aroundClass(final ProceedingJoinPoint joinPoint, final UfpDS ufpDS) throws Throwable {
        return withDatasource(joinPoint, ufpDS.value());
    }

    @Around("@annotation(ufpDS)")
    public Object aroundMethod(final ProceedingJoinPoint joinPoint, final UfpDS ufpDS) throws Throwable {
        return withDatasource(joinPoint, ufpDS.value());
    }

    private Object withDatasource(final ProceedingJoinPoint joinPoint, final String dsKey) throws Throwable {
        UfpDSContextHolder.push(dsKey);
        try {
            return joinPoint.proceed();
        } finally {
            UfpDSContextHolder.poll();
        }
    }
}

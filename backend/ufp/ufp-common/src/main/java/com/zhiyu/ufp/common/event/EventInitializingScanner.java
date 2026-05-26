package com.zhiyu.ufp.common.event;

import com.google.common.eventbus.Subscribe;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.ReflectionUtils;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Scans all beans in the ApplicationContext for {@code @Subscribe}-annotated
 * methods and automatically registers them with the {@link EventUtils} buses.
 *
 * <p>Skips Spring internal beans (names starting with "org.spring") and
 * the scanner's own bean to avoid circular references.
 */
@Slf4j
public class EventInitializingScanner implements ApplicationContextAware, InitializingBean, DisposableBean {

    public static final String SCANNER_BEAN_NAME = "EventInitializingScanner";

    private final Set<Object> annotatedBeans = new LinkedHashSet<>(1);

    private ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(final ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public void afterPropertiesSet() {
        if (applicationContext == null) {
            log.warn("ApplicationContext is null — skipping @Subscribe bean scan");
            return;
        }

        for (String beanName : applicationContext.getBeanDefinitionNames()) {
            if (beanName.startsWith("org.spring")) {
                continue;
            }
            if (beanName.startsWith(EventInitializingScanner.class.getName())) {
                continue;
            }
            if (SCANNER_BEAN_NAME.equalsIgnoreCase(beanName)) {
                continue;
            }

            Object bean;
            try {
                bean = applicationContext.getBean(beanName);
            } catch (BeansException ex) {
                continue;
            }

            Class<?> targetClass = AopUtils.getTargetClass(bean);
            ReflectionUtils.doWithMethods(targetClass, method -> {
                Subscribe annotation = AnnotationUtils.getAnnotation(method, Subscribe.class);
                if (annotation != null) {
                    annotatedBeans.add(bean);
                }
            });
        }

        if (annotatedBeans.isEmpty()) {
            log.info("No @Subscribe-annotated beans found");
            return;
        }

        for (Object bean : annotatedBeans) {
            EventUtils.register(bean);
        }
        if (log.isInfoEnabled()) {
            log.info("Registered {} @Subscribe-annotated bean(s) with EventBus", annotatedBeans.size());
        }
    }

    @Override
    public void destroy() {
        for (Object bean : annotatedBeans) {
            EventUtils.unregister(bean);
        }
        if (log.isInfoEnabled()) {
            log.info("Unregistered {} bean(s) from EventBus", annotatedBeans.size());
        }
    }
}

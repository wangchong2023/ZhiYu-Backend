package com.zhiyu.common.tracing;

import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.aop.ObservedAspect;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.annotation.DefaultNewSpanParser;
import io.micrometer.tracing.annotation.ImperativeMethodInvocationProcessor;
import io.micrometer.tracing.annotation.MethodInvocationProcessor;
import io.micrometer.tracing.annotation.NewSpanParser;
import io.micrometer.tracing.annotation.SpanAspect;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(Tracer.class)
public class TracingAutoConfiguration {

    @Bean
    public ObservedAspect observedAspect(final ObservationRegistry registry) {
        return new ObservedAspect(registry);
    }

    @Bean
    public SpanAspect spanAspect(final Tracer tracer) {
        NewSpanParser parser = new DefaultNewSpanParser();
        MethodInvocationProcessor processor = new ImperativeMethodInvocationProcessor(parser, tracer);
        return new SpanAspect(processor);
    }

    @Bean
    public TracingWebControllerAspect tracingWebControllerAspect(final Tracer tracer) {
        return new TracingWebControllerAspect(tracer);
    }
}

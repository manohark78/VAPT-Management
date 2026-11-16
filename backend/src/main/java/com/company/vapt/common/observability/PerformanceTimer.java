package com.company.vapt.common.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Callable;

@Component
public class PerformanceTimer {

    private static final Logger log = LoggerFactory.getLogger(PerformanceTimer.class);

    private final MeterRegistry meterRegistry;

    public PerformanceTimer(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public <T> T record(String metricName, String stage, String result, Callable<T> callable) throws Exception {
        Instant start = Instant.now();
        try {
            T output = callable.call();
            Duration duration = Duration.between(start, Instant.now());
            recordMetric(metricName, stage, result, duration);
            log.info("Performance: operation={} stage={} result={} durationMs={}",
                    metricName, stage, result, duration.toMillis());
            return output;
        } catch (Exception e) {
            Duration duration = Duration.between(start, Instant.now());
            recordMetric(metricName, stage, "ERROR", duration);
            log.warn("Performance: operation={} stage={} result=ERROR durationMs={}",
                    metricName, stage, duration.toMillis());
            throw e;
        }
    }

    public void recordDuration(String metricName, String stage, String result, Duration duration) {
        recordMetric(metricName, stage, result, duration);
        log.info("Performance: operation={} stage={} result={} durationMs={}",
                metricName, stage, result, duration.toMillis());
    }

    private void recordMetric(String metricName, String stage, String result, Duration duration) {
        Timer.builder(metricName)
                .tag("stage", stage != null ? stage : "default")
                .tag("result", result != null ? result : "SUCCESS")
                .register(meterRegistry)
                .record(duration);
    }
}

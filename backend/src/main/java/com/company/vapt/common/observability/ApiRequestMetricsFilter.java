package com.company.vapt.common.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

/** Records API request duration without using request paths or other high-cardinality labels. */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class ApiRequestMetricsFilter extends OncePerRequestFilter {

    private final PerformanceTimer performanceTimer;

    public ApiRequestMetricsFilter(PerformanceTimer performanceTimer) {
        this.performanceTimer = performanceTimer;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long startedAt = System.nanoTime();
        String result = "SUCCESS";
        try {
            filterChain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException | Error ex) {
            result = "SERVER_ERROR";
            throw ex;
        } finally {
            if (!"SERVER_ERROR".equals(result)) {
                int status = response.getStatus();
                result = status >= 500 ? "SERVER_ERROR" : status >= 400 ? "CLIENT_ERROR" : "SUCCESS";
            }
            performanceTimer.recordDuration("api.request", "HTTP", result,
                    Duration.ofNanos(Math.max(0, System.nanoTime() - startedAt)));
        }
    }
}

package com.pixledger.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(AuthRateLimitFilter.class);
    private final StringRedisTemplate redis;

    public AuthRateLimitFilter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !(request.getMethod().equals("POST") && (request.getRequestURI().equals("/auth/login") || request.getRequestURI().equals("/auth/forgot-password")));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {
        String key = "rate:" + request.getRequestURI() + ":" + request.getRemoteAddr();
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1) redis.expire(key, Duration.ofMinutes(1));
        if (count != null && count > 10) {
            log.warn("auth.rate-limit rejected method={} uri={} clientIp={} count={}",
                    request.getMethod(), request.getRequestURI(), request.getRemoteAddr(), count);
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"操作过于频繁，请稍后再试\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}

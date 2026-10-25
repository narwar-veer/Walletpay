package com.walletpe.gateway.ratelimit;

import com.walletpe.gateway.security.JwtAuthenticationFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Rate limiting key resolution policies & Redis Rate Limiters:
 * 1. UserKeyResolver: Identifies authenticated clients by their validated X-User-Id.
 * 2. IpKeyResolver: Identifies public callers by remote IP; X-Forwarded-For is trusted ONLY
 *    if the direct remote socket is in the configured trusted-proxies allowlist.
 * 3. Dedicated RedisRateLimiters for auth vs authenticated API routes.
 */
@Configuration
public class RateLimitPolicy {

    private static final Logger log = LoggerFactory.getLogger(RateLimitPolicy.class);

    private final List<String> trustedProxies;

    public RateLimitPolicy(@Value("${gateway.trusted-proxies:}") String trustedProxiesConfig) {
        if (trustedProxiesConfig != null && !trustedProxiesConfig.isBlank()) {
            this.trustedProxies = Arrays.stream(trustedProxiesConfig.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
        } else {
            this.trustedProxies = List.of();
        }
    }

    /**
     * Primary KeyResolver for authenticated endpoints:
     * Resolves by X-User-Id (injected by JwtAuthenticationFilter).
     * Falls back to client IP if unauthenticated.
     */
    @Bean
    @Primary
    public KeyResolver userKeyResolver() {
        return exchange -> {
            String userId = exchange.getRequest().getHeaders().getFirst(JwtAuthenticationFilter.HEADER_USER_ID);
            if (userId != null && !userId.isBlank()) {
                return Mono.just("user:" + userId.trim());
            }
            return resolveClientIp(exchange).map(ip -> "ip:" + ip);
        };
    }

    /**
     * IP-based resolver for public authentication endpoints (login, signup, OTP).
     */
    @Bean
    public KeyResolver ipKeyResolver() {
        return this::resolveClientIp;
    }

    public Mono<String> resolveClientIp(ServerWebExchange exchange) {
        InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
        String directIp = (remoteAddress != null && remoteAddress.getAddress() != null)
                ? remoteAddress.getAddress().getHostAddress()
                : "127.0.0.1";

        // Only trust X-Forwarded-For if the direct remote IP matches a configured trusted proxy
        if (!trustedProxies.isEmpty() && trustedProxies.contains(directIp)) {
            String xForwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
            if (xForwardedFor != null && !xForwardedFor.isBlank()) {
                String clientIp = xForwardedFor.split(",")[0].trim();
                return Mono.just(clientIp);
            }
        }

        return Mono.just(directIp);
    }

    /**
     * Strict rate limiter for public auth endpoints (login, signup, OTP verification).
     * 5 requests/sec replenish, burst capacity 10.
     */
    @Bean
    public RedisRateLimiter authRedisRateLimiter() {
        return new RedisRateLimiter(5, 10, 1);
    }

    /**
     * Standard rate limiter for authenticated endpoints (KYC, Wallet, Ledger).
     * 20 requests/sec replenish, burst capacity 40.
     */
    @Bean
    @Primary
    public RedisRateLimiter userRedisRateLimiter() {
        return new RedisRateLimiter(20, 40, 1);
    }
}

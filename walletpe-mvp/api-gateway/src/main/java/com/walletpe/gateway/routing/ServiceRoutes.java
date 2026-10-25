package com.walletpe.gateway.routing;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Edge route definitions mapping inbound public URI path patterns to downstream microservice destinations.
 * Enforces per-route Redis rate limiting:
 * - Public auth routes: IP-based rate limiting (authRedisRateLimiter).
 * - Authenticated domain routes: User-ID-based rate limiting (userRedisRateLimiter).
 */
@Configuration
public class ServiceRoutes {

    @Value("${services.user-service.url:http://localhost:8085}")
    private String userServiceUrl;

    @Value("${services.kyc-service.url:http://localhost:8082}")
    private String kycServiceUrl;

    @Value("${services.wallet-service.url:http://localhost:8083}")
    private String walletServiceUrl;

    @Value("${services.transaction-service.url:http://localhost:8084}")
    private String transactionServiceUrl;

    private final RedisRateLimiter authRedisRateLimiter;
    private final RedisRateLimiter userRedisRateLimiter;
    private final KeyResolver ipKeyResolver;
    private final KeyResolver userKeyResolver;

    public ServiceRoutes(
            @Qualifier("authRedisRateLimiter") RedisRateLimiter authRedisRateLimiter,
            @Qualifier("userRedisRateLimiter") RedisRateLimiter userRedisRateLimiter,
            @Qualifier("ipKeyResolver") KeyResolver ipKeyResolver,
            @Qualifier("userKeyResolver") KeyResolver userKeyResolver) {
        this.authRedisRateLimiter = authRedisRateLimiter;
        this.userRedisRateLimiter = userRedisRateLimiter;
        this.ipKeyResolver = ipKeyResolver;
        this.userKeyResolver = userKeyResolver;
    }

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                // 1. Public Authentication & Registration Routes -> User Service (Rate limited by IP)
                .route("user-auth-route", r -> r
                        .path("/api/v1/auth/**")
                        .filters(f -> f.requestRateLimiter(c -> c
                                .setRateLimiter(authRedisRateLimiter)
                                .setKeyResolver(ipKeyResolver)))
                        .uri(userServiceUrl))

                // 2. User Profile Management Routes -> User Service (Rate limited by User ID)
                .route("user-service-route", r -> r
                        .path("/api/v1/users/**")
                        .filters(f -> f.requestRateLimiter(c -> c
                                .setRateLimiter(userRedisRateLimiter)
                                .setKeyResolver(userKeyResolver)))
                        .uri(userServiceUrl))

                // 3. Admin KYC Review & Audit Routes -> KYC Service
                .route("admin-kyc-route", r -> r
                        .path("/api/v1/admin/kyc/**")
                        .filters(f -> f.requestRateLimiter(c -> c
                                .setRateLimiter(userRedisRateLimiter)
                                .setKeyResolver(userKeyResolver)))
                        .uri(kycServiceUrl))

                // 4. User KYC Submission & Status Routes -> KYC Service
                .route("kyc-service-route", r -> r
                        .path("/api/v1/kyc/**")
                        .filters(f -> f.requestRateLimiter(c -> c
                                .setRateLimiter(userRedisRateLimiter)
                                .setKeyResolver(userKeyResolver)))
                        .uri(kycServiceUrl))

                // 5. Wallet & Balance Provisioning Routes -> Wallet Service
                .route("wallet-service-route", r -> r
                        .path("/api/v1/wallets/**")
                        .filters(f -> f.requestRateLimiter(c -> c
                                .setRateLimiter(userRedisRateLimiter)
                                .setKeyResolver(userKeyResolver)))
                        .uri(walletServiceUrl))

                // 6. Ledger & P2P Money Transfers -> Transaction Ledger Service
                .route("transaction-service-route", r -> r
                        .path("/api/v1/transactions/**", "/api/v1/transfers/**")
                        .filters(f -> f.requestRateLimiter(c -> c
                                .setRateLimiter(userRedisRateLimiter)
                                .setKeyResolver(userKeyResolver)))
                        .uri(transactionServiceUrl))

                .build();
    }
}

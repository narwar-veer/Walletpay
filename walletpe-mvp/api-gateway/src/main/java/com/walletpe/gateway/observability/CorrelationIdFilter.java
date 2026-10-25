package com.walletpe.gateway.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Global filter that guarantees every incoming request and outgoing response
 * has a valid, bounded correlation ID for end-to-end distributed tracing.
 *
 * Rejects client-supplied correlation IDs that violate safety constraints
 * (length 8-64, alphanumeric and hyphens only) to prevent log injection or memory pollution.
 */
@Component
public class CorrelationIdFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(CorrelationIdFilter.class);
    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

    // Strictly bounded: 8 to 64 chars, alphanumeric and hyphens only (safe for UUIDs and tracing IDs)
    private static final Pattern SAFE_CORRELATION_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9-]{8,64}$");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String rawHeader = request.getHeaders().getFirst(CORRELATION_ID_HEADER);

        String correlationId;
        if (rawHeader != null && SAFE_CORRELATION_ID_PATTERN.matcher(rawHeader.trim()).matches()) {
            correlationId = rawHeader.trim();
        } else {
            // Header is missing or unsafe; generate an authentic UUID
            correlationId = UUID.randomUUID().toString();
        }

        // Add sanitized correlation ID to downstream request headers
        ServerHttpRequest mutatedRequest = request.mutate()
                .header(CORRELATION_ID_HEADER, correlationId)
                .build();

        // Also append correlation ID to client response headers
        exchange.getResponse().getHeaders().set(CORRELATION_ID_HEADER, correlationId);

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}

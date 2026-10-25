package com.walletpe.gateway.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.walletpe.gateway.observability.CorrelationIdFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Edge role authorization filter:
 * Enforces role-based access control (RBAC) on admin-only routes.
 *
 * Uses EXACT normalized role matching (no substring contains).
 * Permitted admin roles are strictly: ADMIN, ROLE_ADMIN, SUPER_ADMIN, ROLE_SUPER_ADMIN.
 * Rejects non-admin callers (e.g. NOT_ADMIN, USER, ROLE_ADMINISTRATOR) with 403 FORBIDDEN.
 */
@Component
public class RoleAuthorizationFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RoleAuthorizationFilter.class);

    private static final Set<String> ALLOWED_ADMIN_ROLES = Set.of(
            "ADMIN", "ROLE_ADMIN", "SUPER_ADMIN", "ROLE_SUPER_ADMIN"
    );

    private final ObjectMapper objectMapper;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    private final List<String> adminPathPatterns = List.of(
            "/api/v1/admin/**",
            "/admin/**"
    );

    public RoleAuthorizationFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        if (isAdminPath(path)) {
            String rawRoles = exchange.getRequest().getHeaders().getFirst(JwtAuthenticationFilter.HEADER_USER_ROLES);
            Set<String> parsedRoles = parseRoles(rawRoles);

            boolean hasAdminRole = parsedRoles.stream().anyMatch(ALLOWED_ADMIN_ROLES::contains);

            if (!hasAdminRole) {
                String userId = exchange.getRequest().getHeaders().getFirst(JwtAuthenticationFilter.HEADER_USER_ID);
                log.warn("Access denied for userId [{}] with roles [{}] to admin path [{}]", userId, rawRoles, path);
                return writeForbiddenResponse(exchange, path);
            }
        }

        return chain.filter(exchange);
    }

    private Set<String> parseRoles(String rawRoles) {
        if (rawRoles == null || rawRoles.isBlank()) {
            return Collections.emptySet();
        }
        return Arrays.stream(rawRoles.split("[,;\\s]+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(String::toUpperCase)
                .collect(Collectors.toSet());
    }

    private boolean isAdminPath(String path) {
        return adminPathPatterns.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    private Mono<Void> writeForbiddenResponse(ServerWebExchange exchange, String path) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.FORBIDDEN);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String correlationId = exchange.getRequest().getHeaders().getFirst(CorrelationIdFilter.CORRELATION_ID_HEADER);
        if (correlationId == null) {
            correlationId = response.getHeaders().getFirst(CorrelationIdFilter.CORRELATION_ID_HEADER);
        }

        Map<String, Object> errorAttributes = new LinkedHashMap<>();
        errorAttributes.put("timestamp", Instant.now().toString());
        errorAttributes.put("status", HttpStatus.FORBIDDEN.value());
        errorAttributes.put("error", "INSUFFICIENT_PERMISSIONS");
        errorAttributes.put("message", "Access denied: Administrator privileges required");
        errorAttributes.put("path", path);
        if (correlationId != null) {
            errorAttributes.put("correlationId", correlationId);
        }

        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(errorAttributes);
        } catch (JsonProcessingException e) {
            bytes = "{\"error\":\"INSUFFICIENT_PERMISSIONS\",\"message\":\"Administrator privileges required\"}".getBytes(StandardCharsets.UTF_8);
        }

        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -50;
    }
}

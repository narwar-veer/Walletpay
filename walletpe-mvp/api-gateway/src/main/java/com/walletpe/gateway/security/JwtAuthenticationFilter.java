package com.walletpe.gateway.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Edge security filter:
 * 1. Unconditionally strips inbound spoofed headers (X-User-Id, X-User-Roles, X-User-Email, X-Internal-Secret).
 * 2. Allows public routes through without authentication.
 * 3. Enforces cryptographic JWT verification on protected routes:
 *    - Required issuer, audience, valid UUID subject, token ID (jti), and recognized role enum.
 * 4. Extracts verified claims and injects them as trusted downstream headers, along with X-Internal-Secret.
 * 5. Returns generic, safe error messages to clients to prevent information disclosure.
 */
@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_USER_ROLES = "X-User-Roles";
    public static final String HEADER_USER_EMAIL = "X-User-Email";
    public static final String HEADER_INTERNAL_SECRET = "X-Internal-Secret";
    public static final String HEADER_CORRELATION_ID = "X-Correlation-Id";

    private static final Set<String> VALID_ROLES = Set.of(
            "USER", "ADMIN", "SUPER_ADMIN",
            "ROLE_USER", "ROLE_ADMIN", "ROLE_SUPER_ADMIN"
    );

    private final SecretKey signingKey;
    private final String expectedIssuer;
    private final String expectedAudience;
    private final String internalServiceSecret;
    private final ObjectMapper objectMapper;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    // Whitelisted routes: explicitly limited; only health check is exposed, not /actuator/**
    private final List<String> publicPathPatterns = List.of(
            "/api/v1/auth/**",
            "/actuator/health",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/favicon.ico"
    );

    public JwtAuthenticationFilter(
            @Value("${jwt.secret:}") String secret,
            @Value("${jwt.issuer:walletpe-user-service}") String expectedIssuer,
            @Value("${jwt.audience:walletpe-api}") String expectedAudience,
            @Value("${internal-service.shared-secret:}") String internalServiceSecret,
            ObjectMapper objectMapper) {

        // Fail startup immediately if secret is not explicitly configured or too weak
        if (secret == null || secret.trim().length() < 32) {
            throw new IllegalStateException("FATAL: 'jwt.secret' must be explicitly configured with at least 32 characters (256 bits). Hardcoded defaults are disabled for security.");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expectedIssuer = expectedIssuer;
        this.expectedAudience = expectedAudience;
        this.internalServiceSecret = internalServiceSecret;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // 1. Sanitize incoming request by removing any client-supplied internal headers to prevent spoofing
        ServerHttpRequest.Builder sanitizedRequestBuilder = request.mutate()
                .headers(headers -> {
                    headers.remove(HEADER_USER_ID);
                    headers.remove(HEADER_USER_ROLES);
                    headers.remove(HEADER_USER_EMAIL);
                    headers.remove(HEADER_INTERNAL_SECRET);
                });

        // 2. Check if route is public
        if (isPublicPath(path)) {
            // Forward sanitized request with internal secret if configured
            if (internalServiceSecret != null && !internalServiceSecret.isBlank()) {
                sanitizedRequestBuilder.header(HEADER_INTERNAL_SECRET, internalServiceSecret);
            }
            return chain.filter(exchange.mutate().request(sanitizedRequestBuilder.build()).build());
        }

        // 3. Extract Bearer token for protected routes
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("Unauthorized access attempt to [{}]: missing Bearer token", path);
            return writeErrorResponse(exchange, HttpStatus.UNAUTHORIZED, "MISSING_OR_MALFORMED_TOKEN",
                    "Missing or malformed Authorization header. Expected format: 'Bearer <token>'");
        }

        String token = authHeader.substring(7).trim();

        // 4. Validate JWT signature and structure
        Claims claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException ex) {
            // Log underlying parser error on server, but return generic safe error to client
            log.warn("Invalid JWT token for path [{}]: {}", path, ex.getMessage());
            return writeErrorResponse(exchange, HttpStatus.UNAUTHORIZED, "INVALID_TOKEN",
                    "Invalid or expired authentication token");
        }

        // 5. Enforce Issuer
        if (expectedIssuer != null && !expectedIssuer.isBlank() && !expectedIssuer.equals(claims.getIssuer())) {
            log.warn("JWT rejected for path [{}]: unexpected issuer [{}], expected [{}]",
                    path, claims.getIssuer(), expectedIssuer);
            return writeErrorResponse(exchange, HttpStatus.UNAUTHORIZED, "INVALID_TOKEN",
                    "Invalid or expired authentication token");
        }

        // 6. Enforce Audience
        if (expectedAudience != null && !expectedAudience.isBlank()) {
            Set<String> audience = claims.getAudience();
            if (audience == null || !audience.contains(expectedAudience)) {
                log.warn("JWT rejected for path [{}]: missing audience [{}], present: {}",
                        path, expectedAudience, audience);
                return writeErrorResponse(exchange, HttpStatus.UNAUTHORIZED, "INVALID_TOKEN",
                        "Invalid or expired authentication token");
            }
        }

        // 7. Enforce Subject as a valid UUID
        String subject = claims.getSubject();
        if (subject == null || subject.isBlank()) {
            log.warn("JWT rejected for path [{}]: missing subject", path);
            return writeErrorResponse(exchange, HttpStatus.UNAUTHORIZED, "INVALID_TOKEN",
                    "Invalid or expired authentication token");
        }

        try {
            UUID.fromString(subject);
        } catch (IllegalArgumentException e) {
            log.warn("JWT rejected for path [{}]: subject [{}] is not a valid UUID", path, subject);
            return writeErrorResponse(exchange, HttpStatus.UNAUTHORIZED, "INVALID_TOKEN",
                    "Invalid or expired authentication token");
        }

        // 8. Enforce Token ID (jti) presence
        String tokenId = claims.getId();
        if (tokenId == null || tokenId.isBlank()) {
            log.warn("JWT rejected for path [{}]: missing token id (jti)", path);
            return writeErrorResponse(exchange, HttpStatus.UNAUTHORIZED, "INVALID_TOKEN",
                    "Invalid or expired authentication token");
        }

        // 9. Enforce Role Enum
        String rawRole = claims.get("role", String.class);
        String normalizedRole = normalizeRole(rawRole);
        if (normalizedRole == null) {
            log.warn("JWT rejected for path [{}]: role [{}] is not recognized", path, rawRole);
            return writeErrorResponse(exchange, HttpStatus.UNAUTHORIZED, "INVALID_TOKEN",
                    "Invalid or expired authentication token");
        }

        String email = claims.get("email", String.class);

        // 10. Inject verified identity headers + internal secret for downstream services
        ServerHttpRequest.Builder downstreamBuilder = sanitizedRequestBuilder
                .header(HEADER_USER_ID, subject)
                .header(HEADER_USER_ROLES, normalizedRole)
                .header(HEADER_USER_EMAIL, email != null ? email : "");

        if (internalServiceSecret != null && !internalServiceSecret.isBlank()) {
            downstreamBuilder.header(HEADER_INTERNAL_SECRET, internalServiceSecret);
        }

        return chain.filter(exchange.mutate().request(downstreamBuilder.build()).build());
    }

    private String normalizeRole(String rawRole) {
        if (rawRole == null || rawRole.isBlank()) {
            return null;
        }
        String trimmed = rawRole.trim().toUpperCase();
        if (VALID_ROLES.contains(trimmed)) {
            return trimmed;
        }
        return null;
    }

    private boolean isPublicPath(String path) {
        return publicPathPatterns.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    public Mono<Void> writeErrorResponse(ServerWebExchange exchange, HttpStatus status, String errorCode, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String correlationId = exchange.getRequest().getHeaders().getFirst(HEADER_CORRELATION_ID);
        if (correlationId == null) {
            correlationId = response.getHeaders().getFirst(HEADER_CORRELATION_ID);
        }

        Map<String, Object> errorAttributes = new LinkedHashMap<>();
        errorAttributes.put("timestamp", Instant.now().toString());
        errorAttributes.put("status", status.value());
        errorAttributes.put("error", errorCode);
        errorAttributes.put("message", message);
        errorAttributes.put("path", exchange.getRequest().getURI().getPath());
        if (correlationId != null) {
            errorAttributes.put("correlationId", correlationId);
        }

        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(errorAttributes);
        } catch (JsonProcessingException e) {
            bytes = ("{\"error\":\"" + errorCode + "\",\"message\":\"" + message + "\"}").getBytes(StandardCharsets.UTF_8);
        }

        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -100;
    }
}

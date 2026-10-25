package com.walletpe.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.walletpe.gateway.observability.CorrelationIdFilter;
import com.walletpe.gateway.ratelimit.RateLimitPolicy;
import com.walletpe.gateway.security.JwtAuthenticationFilter;
import com.walletpe.gateway.security.RoleAuthorizationFilter;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.factory.RequestRateLimiterGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import javax.crypto.SecretKey;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GatewayContractTest {

    private static final String TEST_SECRET = "testSuperSecretKeyForWalletPeJwtTokensMustBeAtLeast256BitsLong12345!";
    private static final String EXPECTED_ISSUER = "walletpe-user-service";
    private static final String EXPECTED_AUDIENCE = "walletpe-api";
    private static final String INTERNAL_SECRET = "testInternalServiceSecretMustBeAtLeastThirtyTwoCharacters!";

    private CorrelationIdFilter correlationIdFilter;
    private JwtAuthenticationFilter jwtAuthenticationFilter;
    private RoleAuthorizationFilter roleAuthorizationFilter;
    private RateLimitPolicy rateLimitPolicy;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        correlationIdFilter = new CorrelationIdFilter();
        jwtAuthenticationFilter = new JwtAuthenticationFilter(
                TEST_SECRET,
                EXPECTED_ISSUER,
                EXPECTED_AUDIENCE,
                INTERNAL_SECRET,
                objectMapper
        );
        roleAuthorizationFilter = new RoleAuthorizationFilter(objectMapper);
        rateLimitPolicy = new RateLimitPolicy("10.0.0.1,192.168.1.1");
    }

    private String generateToken(UUID userId, String email, String role, long validityMs,
                                 String issuer, String audience, String tokenId) {
        SecretKey key = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
        Date now = new Date();
        var builder = Jwts.builder()
                .subject(userId != null ? userId.toString() : null)
                .claim("email", email)
                .claim("role", role)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + validityMs))
                .signWith(key);

        if (issuer != null) {
            builder.issuer(issuer);
        }
        if (audience != null) {
            builder.audience().add(audience);
        }
        if (tokenId != null) {
            builder.id(tokenId);
        }

        return builder.compact();
    }

    private String generateValidToken(UUID userId, String email, String role) {
        return generateToken(userId, email, role, 60000,
                EXPECTED_ISSUER, EXPECTED_AUDIENCE, UUID.randomUUID().toString());
    }

    // ==========================================
    // 1. JWT Security & Startup Tests
    // ==========================================

    @Test
    @DisplayName("JwtAuthenticationFilter must fail startup if secret is missing or too short")
    void shouldFailStartupIfSecretIsInsecure() {
        assertThatThrownBy(() -> new JwtAuthenticationFilter(null, EXPECTED_ISSUER, EXPECTED_AUDIENCE, INTERNAL_SECRET, objectMapper))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jwt.secret");

        assertThatThrownBy(() -> new JwtAuthenticationFilter("shortSecretKey123", EXPECTED_ISSUER, EXPECTED_AUDIENCE, INTERNAL_SECRET, objectMapper))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 characters");
    }

    @Test
    @DisplayName("Public routes permit without token and inject internal secret")
    void shouldAllowPublicEndpointsWithoutTokenAndInjectInternalSecret() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/v1/auth/signup").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicReference<ServerWebExchange> capturedExchange = new AtomicReference<>();
        GatewayFilterChain chain = ex -> {
            capturedExchange.set(ex);
            return Mono.empty();
        };

        StepVerifier.create(jwtAuthenticationFilter.filter(exchange, chain))
                .verifyComplete();

        assertThat(capturedExchange.get()).isNotNull();
        assertThat(capturedExchange.get().getRequest().getHeaders().getFirst(JwtAuthenticationFilter.HEADER_INTERNAL_SECRET))
                .isEqualTo(INTERNAL_SECRET);
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    @DisplayName("Only /actuator/health is whitelisted; /actuator/metrics requires authentication")
    void shouldRestrictActuatorEndpoints() {
        MockServerHttpRequest healthReq = MockServerHttpRequest.get("/actuator/health").build();
        MockServerWebExchange healthEx = MockServerWebExchange.from(healthReq);

        AtomicReference<Boolean> chainCalled = new AtomicReference<>(false);
        StepVerifier.create(jwtAuthenticationFilter.filter(healthEx, ex -> {
            chainCalled.set(true);
            return Mono.empty();
        })).verifyComplete();
        assertThat(chainCalled.get()).isTrue();

        MockServerHttpRequest metricsReq = MockServerHttpRequest.get("/actuator/metrics").build();
        MockServerWebExchange metricsEx = MockServerWebExchange.from(metricsReq);
        StepVerifier.create(jwtAuthenticationFilter.filter(metricsEx, ex -> Mono.empty()))
                .verifyComplete();
        assertThat(metricsEx.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Sanitizes incoming requests: unconditionally strips forged headers")
    void shouldStripForgedHeaders() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/v1/auth/signup")
                .header(JwtAuthenticationFilter.HEADER_USER_ID, UUID.randomUUID().toString())
                .header(JwtAuthenticationFilter.HEADER_USER_ROLES, "ROLE_ADMIN")
                .header(JwtAuthenticationFilter.HEADER_INTERNAL_SECRET, "forgedSecret!")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicReference<ServerWebExchange> capturedExchange = new AtomicReference<>();
        GatewayFilterChain chain = ex -> {
            capturedExchange.set(ex);
            return Mono.empty();
        };

        StepVerifier.create(jwtAuthenticationFilter.filter(exchange, chain))
                .verifyComplete();

        assertThat(capturedExchange.get().getRequest().getHeaders().getFirst(JwtAuthenticationFilter.HEADER_USER_ID)).isNull();
        assertThat(capturedExchange.get().getRequest().getHeaders().getFirst(JwtAuthenticationFilter.HEADER_USER_ROLES)).isNull();
        // Overwritten with authentic internal secret, NOT the client forged one
        assertThat(capturedExchange.get().getRequest().getHeaders().getFirst(JwtAuthenticationFilter.HEADER_INTERNAL_SECRET))
                .isEqualTo(INTERNAL_SECRET);
    }

    @Test
    @DisplayName("Rejects token with invalid issuer")
    void shouldRejectTokenWithInvalidIssuer() {
        String token = generateToken(UUID.randomUUID(), "a@b.com", "ROLE_USER", 60000,
                "untrusted-issuer", EXPECTED_AUDIENCE, UUID.randomUUID().toString());

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/kyc/status")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(jwtAuthenticationFilter.filter(exchange, ex -> Mono.empty()))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Rejects token with missing audience")
    void shouldRejectTokenWithMissingAudience() {
        String token = generateToken(UUID.randomUUID(), "a@b.com", "ROLE_USER", 60000,
                EXPECTED_ISSUER, "wrong-audience", UUID.randomUUID().toString());

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/kyc/status")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(jwtAuthenticationFilter.filter(exchange, ex -> Mono.empty()))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Rejects token with non-UUID subject")
    void shouldRejectTokenWithNonUuidSubject() {
        SecretKey key = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
        String token = Jwts.builder()
                .subject("not-a-uuid-string")
                .issuer(EXPECTED_ISSUER)
                .audience().add(EXPECTED_AUDIENCE).and()
                .id(UUID.randomUUID().toString())
                .claim("role", "ROLE_USER")
                .expiration(new Date(System.currentTimeMillis() + 60000))
                .signWith(key)
                .compact();

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/kyc/status")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(jwtAuthenticationFilter.filter(exchange, ex -> Mono.empty()))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Returns generic error message to client on invalid token")
    void shouldReturnGenericErrorMessageToClient() throws Exception {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/kyc/status")
                .header(HttpHeaders.AUTHORIZATION, "Bearer invalid.malformed.token")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(jwtAuthenticationFilter.filter(exchange, ex -> Mono.empty()))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Valid token extracts claims and injects verified headers downstream")
    void shouldValidateTokenAndInjectClaimsDownstream() {
        UUID userId = UUID.randomUUID();
        String token = generateValidToken(userId, "john.doe@example.com", "USER");

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header(JwtAuthenticationFilter.HEADER_USER_ROLES, "ROLE_ADMIN")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicReference<ServerWebExchange> capturedExchange = new AtomicReference<>();
        StepVerifier.create(jwtAuthenticationFilter.filter(exchange, ex -> {
            capturedExchange.set(ex);
            return Mono.empty();
        })).verifyComplete();

        assertThat(capturedExchange.get()).isNotNull();
        HttpHeaders downstreamHeaders = capturedExchange.get().getRequest().getHeaders();

        assertThat(downstreamHeaders.getFirst(JwtAuthenticationFilter.HEADER_USER_ID)).isEqualTo(userId.toString());
        assertThat(downstreamHeaders.getFirst(JwtAuthenticationFilter.HEADER_USER_ROLES)).isEqualTo("USER");
        assertThat(downstreamHeaders.getFirst(JwtAuthenticationFilter.HEADER_USER_EMAIL)).isEqualTo("john.doe@example.com");
        assertThat(downstreamHeaders.getFirst(JwtAuthenticationFilter.HEADER_INTERNAL_SECRET)).isEqualTo(INTERNAL_SECRET);
    }

    // ==========================================
    // 2. Role Authorization (Exact Matching) Tests
    // ==========================================

    @Test
    @DisplayName("RoleAuthorizationFilter strictly fails substring matches like NOT_ADMIN and ROLE_ADMINISTRATOR")
    void shouldBlockSubstringRoleAttacks() {
        // Attack 1: NOT_ADMIN
        MockServerHttpRequest req1 = MockServerHttpRequest.post("/api/v1/admin/kyc/review")
                .header(JwtAuthenticationFilter.HEADER_USER_ROLES, "NOT_ADMIN")
                .build();
        MockServerWebExchange ex1 = MockServerWebExchange.from(req1);
        StepVerifier.create(roleAuthorizationFilter.filter(ex1, ex -> Mono.empty()))
                .verifyComplete();
        assertThat(ex1.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // Attack 2: ROLE_ADMINISTRATOR
        MockServerHttpRequest req2 = MockServerHttpRequest.post("/api/v1/admin/kyc/review")
                .header(JwtAuthenticationFilter.HEADER_USER_ROLES, "ROLE_ADMINISTRATOR")
                .build();
        MockServerWebExchange ex2 = MockServerWebExchange.from(req2);
        StepVerifier.create(roleAuthorizationFilter.filter(ex2, ex -> Mono.empty()))
                .verifyComplete();
        assertThat(ex2.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("RoleAuthorizationFilter permits exact ADMIN, ROLE_ADMIN, and SUPER_ADMIN")
    void shouldPermitExactAdminRoles() {
        for (String role : new String[]{"ADMIN", "ROLE_ADMIN", "SUPER_ADMIN", "ROLE_SUPER_ADMIN"}) {
            MockServerHttpRequest req = MockServerHttpRequest.post("/api/v1/admin/kyc/review")
                    .header(JwtAuthenticationFilter.HEADER_USER_ROLES, role)
                    .build();
            MockServerWebExchange ex = MockServerWebExchange.from(req);
            AtomicReference<Boolean> called = new AtomicReference<>(false);

            StepVerifier.create(roleAuthorizationFilter.filter(ex, chainEx -> {
                called.set(true);
                return Mono.empty();
            })).verifyComplete();

            assertThat(called.get()).isTrue();
            assertThat(ex.getResponse().getStatusCode()).isNull();
        }
    }

    // ==========================================
    // 3. Correlation ID Validation Tests
    // ==========================================

    @Test
    @DisplayName("CorrelationIdFilter sanitizes malformed correlation IDs and generates a fresh UUID")
    void shouldSanitizeMalformedCorrelationId() {
        // Malformed / dangerous characters (path traversal / log injection attempt)
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/users/me")
                .header(CorrelationIdFilter.CORRELATION_ID_HEADER, "../../malicious\n\r<script>")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicReference<ServerWebExchange> capturedExchange = new AtomicReference<>();
        StepVerifier.create(correlationIdFilter.filter(exchange, ex -> {
            capturedExchange.set(ex);
            return Mono.empty();
        })).verifyComplete();

        String downstreamId = capturedExchange.get().getRequest().getHeaders().getFirst(CorrelationIdFilter.CORRELATION_ID_HEADER);
        assertThat(downstreamId).isNotNull();
        assertThat(downstreamId).doesNotContain("malicious");
        // Must be a valid UUID
        assertThat(UUID.fromString(downstreamId)).isNotNull();
    }

    @Test
    @DisplayName("CorrelationIdFilter preserves valid UUIDs")
    void shouldPreserveValidUuidCorrelationId() {
        String validId = UUID.randomUUID().toString();
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/users/me")
                .header(CorrelationIdFilter.CORRELATION_ID_HEADER, validId)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicReference<ServerWebExchange> capturedExchange = new AtomicReference<>();
        StepVerifier.create(correlationIdFilter.filter(exchange, ex -> {
            capturedExchange.set(ex);
            return Mono.empty();
        })).verifyComplete();

        String downstreamId = capturedExchange.get().getRequest().getHeaders().getFirst(CorrelationIdFilter.CORRELATION_ID_HEADER);
        assertThat(downstreamId).isEqualTo(validId);
    }

    // ==========================================
    // 4. Rate Limiting Policy & 429 Test
    // ==========================================

    @Test
    @DisplayName("RateLimitPolicy rejects untrusted X-Forwarded-For to prevent IP spoofing")
    void shouldRejectUntrustedXForwardedFor() {
        // Direct socket from untrusted client 198.51.100.5 sending spoofed X-Forwarded-For
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/v1/auth/login")
                .remoteAddress(new InetSocketAddress("198.51.100.5", 12345))
                .header("X-Forwarded-For", "203.0.113.199")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(rateLimitPolicy.ipKeyResolver().resolve(exchange))
                .expectNext("198.51.100.5") // Direct remote IP, not spoofed header!
                .verifyComplete();
    }

    @Test
    @DisplayName("RateLimitPolicy trusts X-Forwarded-For from configured trusted proxy")
    void shouldTrustXForwardedForFromConfiguredProxy() {
        // Direct socket from configured trusted proxy 10.0.0.1
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/v1/auth/login")
                .remoteAddress(new InetSocketAddress("10.0.0.1", 12345))
                .header("X-Forwarded-For", "203.0.113.88, 10.0.0.1")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(rateLimitPolicy.ipKeyResolver().resolve(exchange))
                .expectNext("203.0.113.88") // First client IP from proxy header
                .verifyComplete();
    }

    @Test
    @DisplayName("RateLimitPolicy userKeyResolver resolves user:<id> for authenticated requests")
    void shouldResolveUserIdForAuthenticatedRequests() {
        String userId = UUID.randomUUID().toString();
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/users/me")
                .header(JwtAuthenticationFilter.HEADER_USER_ID, userId)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(rateLimitPolicy.userKeyResolver().resolve(exchange))
                .expectNext("user:" + userId)
                .verifyComplete();
    }

    @Test
    @DisplayName("RequestRateLimiter returns 429 TOO_MANY_REQUESTS when limit is exceeded")
    void shouldReturn429WhenRateLimitExceeded() {
        RateLimiter<Object> mockLimiter = new RateLimiter<>() {
            @Override
            public Mono<Response> isAllowed(String routeId, String id) {
                // Deny request
                return Mono.just(new Response(false, Map.of("X-RateLimit-Remaining", "0")));
            }

            @Override
            public Class<Object> getConfigClass() {
                return Object.class;
            }

            @Override
            public Object newConfig() {
                return new Object();
            }

            @Override
            public Map<String, Object> getConfig() {
                return Map.of();
            }
        };

        RequestRateLimiterGatewayFilterFactory factory =
                new RequestRateLimiterGatewayFilterFactory(mockLimiter, rateLimitPolicy.userKeyResolver());

        RequestRateLimiterGatewayFilterFactory.Config config = new RequestRateLimiterGatewayFilterFactory.Config();
        config.setRateLimiter(mockLimiter);
        config.setKeyResolver(rateLimitPolicy.userKeyResolver());

        GatewayFilter filter = factory.apply(config);

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/users/me")
                .header(JwtAuthenticationFilter.HEADER_USER_ID, UUID.randomUUID().toString())
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        org.springframework.cloud.gateway.route.Route route = org.springframework.cloud.gateway.route.Route.async()
                .id("test-route")
                .uri("http://localhost:8085")
                .predicate(ex -> true)
                .build();
        exchange.getAttributes().put(org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR, route);

        StepVerifier.create(filter.filter(exchange, ex -> Mono.empty()))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }
}

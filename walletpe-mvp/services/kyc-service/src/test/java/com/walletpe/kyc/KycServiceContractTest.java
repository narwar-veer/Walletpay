package com.walletpe.kyc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.walletpe.kyc.api.dto.KycHoldRequest;
import com.walletpe.kyc.api.dto.KycReviewRequest;
import com.walletpe.kyc.api.dto.KycStatusResponse;
import com.walletpe.kyc.api.dto.KycSubmitRequest;
import com.walletpe.kyc.domain.enums.KycStatus;
import com.walletpe.kyc.domain.enums.RejectionReason;
import com.walletpe.kyc.domain.models.KycApplication;
import com.walletpe.kyc.domain.models.KycOutboxEvent;
import com.walletpe.kyc.infrastructure.client.UserServiceClient;
import com.walletpe.kyc.infrastructure.persistence.KycApplicationRepository;
import com.walletpe.kyc.infrastructure.persistence.KycDocumentReferenceRepository;
import com.walletpe.kyc.infrastructure.persistence.KycOutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class KycServiceContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private KycApplicationRepository kycApplicationRepository;

    @Autowired
    private KycDocumentReferenceRepository documentReferenceRepository;

    @Autowired
    private KycOutboxRepository kycOutboxRepository;

    @MockitoBean
    private UserServiceClient userServiceClient;

    @BeforeEach
    void setUp() {
        documentReferenceRepository.deleteAll();
        kycOutboxRepository.deleteAll();
        kycApplicationRepository.deleteAll();

        // Default mock: user is eligible
        when(userServiceClient.checkEligibility(any(UUID.class)))
                .thenReturn(new UserServiceClient.UserEligibilityResponse(true, true, "ACTIVE", true));
    }

    @Test
    @DisplayName("Submit KYC succeeds when user is eligible, age >= 18, and stores masked Aadhaar")
    void testSuccessfulKycSubmission() throws Exception {
        UUID userId = UUID.randomUUID();
        String fakeDocBase64 = Base64.getEncoder().encodeToString("%PDF-1.4 fake document content".getBytes());

        KycSubmitRequest request = new KycSubmitRequest(
                "Alice Smith",
                LocalDate.now().minusYears(25),
                "123456789012",
                "AADHAAR",
                "application/pdf",
                fakeDocBase64
        );

        MvcResult result = mockMvc.perform(post("/api/v1/kyc/submit")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.aadhaarReference").value("XXXXXXXX9012"))
                .andReturn();

        KycStatusResponse response = objectMapper.readValue(result.getResponse().getContentAsString(), KycStatusResponse.class);
        assertNotNull(response.kycId());

        // Verify entity in repository: raw Aadhaar must NOT be stored
        KycApplication saved = kycApplicationRepository.findById(response.kycId()).orElseThrow();
        assertEquals("Alice Smith", saved.getFullName());
        assertEquals("XXXXXXXX9012", saved.getAadhaarReference());
        assertNotNull(saved.getAadhaarHash());
        assertFalse(saved.getAadhaarHash().contains("123456789012"));
    }

    @Test
    @DisplayName("Submit KYC fails with 400 when applicant is underage (< 18)")
    void testUnderageSubmissionRejected() throws Exception {
        UUID userId = UUID.randomUUID();
        String fakeDocBase64 = Base64.getEncoder().encodeToString("%PDF-1.4 fake doc".getBytes());

        KycSubmitRequest request = new KycSubmitRequest(
                "Minor Applicant",
                LocalDate.now().minusYears(16), // 16 years old
                "123456789012",
                "AADHAAR",
                "application/pdf",
                fakeDocBase64
        );

        mockMvc.perform(post("/api/v1/kyc/submit")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("at least 18 years old")));
    }

    @Test
    @DisplayName("Submit KYC fails with 400 when user is not eligible according to User Service")
    void testIneligibleUserSubmissionRejected() throws Exception {
        UUID userId = UUID.randomUUID();
        when(userServiceClient.checkEligibility(userId))
                .thenReturn(new UserServiceClient.UserEligibilityResponse(false, true, "ACTIVE", false));

        String fakeDocBase64 = Base64.getEncoder().encodeToString("%PDF-1.4 fake doc".getBytes());
        KycSubmitRequest request = new KycSubmitRequest(
                "Unverified User",
                LocalDate.now().minusYears(22),
                "123456789012",
                "AADHAAR",
                "application/pdf",
                fakeDocBase64
        );

        mockMvc.perform(post("/api/v1/kyc/submit")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("not eligible for KYC")));
    }

    @Test
    @DisplayName("Submit KYC fails with 401 when X-User-Id header is missing")
    void testMissingUserIdHeaderRejected() throws Exception {
        String fakeDocBase64 = Base64.getEncoder().encodeToString("%PDF-1.4 fake doc".getBytes());
        KycSubmitRequest request = new KycSubmitRequest(
                "No Header User",
                LocalDate.now().minusYears(22),
                "123456789012",
                "AADHAAR",
                "application/pdf",
                fakeDocBase64
        );

        mockMvc.perform(post("/api/v1/kyc/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Duplicate active KYC submission by same user is rejected with 409 Conflict")
    void testDuplicateActiveSubmissionRejected() throws Exception {
        UUID userId = UUID.randomUUID();
        String fakeDocBase64 = Base64.getEncoder().encodeToString("%PDF-1.4 doc".getBytes());

        KycSubmitRequest request = new KycSubmitRequest(
                "Alice",
                LocalDate.now().minusYears(25),
                "123456789012",
                "AADHAAR",
                "application/pdf",
                fakeDocBase64
        );

        // First submission succeeds
        mockMvc.perform(post("/api/v1/kyc/submit")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Second submission while first is still SUBMITTED fails with 409
        mockMvc.perform(post("/api/v1/kyc/submit")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("already under processing")));
    }

    @Test
    @DisplayName("Same Aadhaar used by two different users is rejected with 409 Conflict")
    void testDuplicateAadhaarAcrossUsersRejected() throws Exception {
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        String fakeDocBase64 = Base64.getEncoder().encodeToString("%PDF-1.4 doc".getBytes());

        KycSubmitRequest requestA = new KycSubmitRequest(
                "User A",
                LocalDate.now().minusYears(30),
                "999988887777",
                "AADHAAR",
                "application/pdf",
                fakeDocBase64
        );

        mockMvc.perform(post("/api/v1/kyc/submit")
                        .header("X-User-Id", userA.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestA)))
                .andExpect(status().isCreated());

        // User B attempts to register with same Aadhaar
        KycSubmitRequest requestB = new KycSubmitRequest(
                "User B",
                LocalDate.now().minusYears(28),
                "999988887777",
                "AADHAAR",
                "application/pdf",
                fakeDocBase64
        );

        mockMvc.perform(post("/api/v1/kyc/submit")
                        .header("X-User-Id", userB.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestB)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("already registered to another account")));
    }

    @Test
    @DisplayName("Admin approval transitions state to APPROVED and writes atomic KYC_APPROVED outbox event")
    void testAdminApprovalAndOutboxEventPersistence() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        String fakeDocBase64 = Base64.getEncoder().encodeToString("%PDF-1.4 doc".getBytes());

        KycSubmitRequest submitRequest = new KycSubmitRequest(
                "Approved User",
                LocalDate.now().minusYears(25),
                "555544443333",
                "AADHAAR",
                "application/pdf",
                fakeDocBase64
        );

        MvcResult submitResult = mockMvc.perform(post("/api/v1/kyc/submit")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(submitRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        KycStatusResponse submittedKyc = objectMapper.readValue(submitResult.getResponse().getContentAsString(), KycStatusResponse.class);

        // Review as Admin with APPROVE
        KycReviewRequest reviewRequest = new KycReviewRequest("APPROVE", null, null);

        mockMvc.perform(post("/api/v1/admin/kyc/" + submittedKyc.kycId() + "/review")
                        .header("X-User-Id", adminId.toString())
                        .header("X-User-Roles", "ADMIN")
                        .header("X-Correlation-Id", "corr-test-12345")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reviewRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        // Verify Outbox Event created
        List<KycOutboxEvent> outboxEvents = kycOutboxRepository.findAll();
        assertEquals(1, outboxEvents.size());
        KycOutboxEvent event = outboxEvents.get(0);
        assertEquals("KYC_APPROVED", event.getEventType());
        assertEquals("KYC", event.getAggregateType());
        assertEquals(submittedKyc.kycId().toString(), event.getAggregateId());
        assertEquals(userId.toString(), event.getPartitionKey());
        assertEquals("PENDING", event.getStatus());
        assertTrue(event.getPayload().contains("corr-test-12345"));
        assertTrue(event.getPayload().contains(userId.toString()));
    }

    @Test
    @DisplayName("Admin rejection records reason and permits user to re-submit")
    void testAdminRejectionAndReSubmission() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        String fakeDocBase64 = Base64.getEncoder().encodeToString("%PDF-1.4 doc".getBytes());

        KycSubmitRequest submitRequest = new KycSubmitRequest(
                "Reject User",
                LocalDate.now().minusYears(25),
                "777766665555",
                "AADHAAR",
                "application/pdf",
                fakeDocBase64
        );

        MvcResult submitResult = mockMvc.perform(post("/api/v1/kyc/submit")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(submitRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        KycStatusResponse submittedKyc = objectMapper.readValue(submitResult.getResponse().getContentAsString(), KycStatusResponse.class);

        // Admin rejects with DOCUMENT_UNCLEAR
        KycReviewRequest reviewRequest = new KycReviewRequest("REJECT", RejectionReason.DOCUMENT_UNCLEAR, "Image too blurry");

        mockMvc.perform(post("/api/v1/admin/kyc/" + submittedKyc.kycId() + "/review")
                        .header("X-User-Id", adminId.toString())
                        .header("X-User-Roles", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reviewRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("DOCUMENT_UNCLEAR"));

        // User re-submits with clear document -> must succeed!
        mockMvc.perform(post("/api/v1/kyc/submit")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(submitRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUBMITTED"));
    }

    @Test
    @DisplayName("Non-admin role calling admin review endpoint receives 403 Forbidden")
    void testNonAdminForbiddenFromAdminReview() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID kycId = UUID.randomUUID();
        KycReviewRequest reviewRequest = new KycReviewRequest("APPROVE", null, null);

        mockMvc.perform(post("/api/v1/admin/kyc/" + kycId + "/review")
                        .header("X-User-Id", userId.toString())
                        .header("X-User-Roles", "USER") // Not admin
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reviewRequest)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Get status returns NOT_STARTED if user has not submitted KYC")
    void testGetKycStatusNotStarted() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/kyc/status")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NOT_STARTED"));
    }

    @Test
    @DisplayName("Put KYC on hold updates status to ON_HOLD")
    void testPutKycOnHold() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        String fakeDocBase64 = Base64.getEncoder().encodeToString("%PDF-1.4 doc".getBytes());

        KycSubmitRequest submitRequest = new KycSubmitRequest(
                "Hold User",
                LocalDate.now().minusYears(25),
                "111122223333",
                "AADHAAR",
                "application/pdf",
                fakeDocBase64
        );

        MvcResult submitResult = mockMvc.perform(post("/api/v1/kyc/submit")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(submitRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        KycStatusResponse submittedKyc = objectMapper.readValue(submitResult.getResponse().getContentAsString(), KycStatusResponse.class);

        KycHoldRequest holdRequest = new KycHoldRequest("Requires manual verification with issuing authority");

        mockMvc.perform(post("/api/v1/admin/kyc/" + submittedKyc.kycId() + "/hold")
                        .header("X-User-Id", adminId.toString())
                        .header("X-User-Roles", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(holdRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ON_HOLD"))
                .andExpect(jsonPath("$.rejectionNotes").value("Requires manual verification with issuing authority"));
    }
}

package com.walletpe.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.walletpe.user.api.dto.*;
import com.walletpe.user.domain.enums.OtpPurpose;
import com.walletpe.user.domain.enums.UserStatus;
import com.walletpe.user.domain.models.OtpChallenge;
import com.walletpe.user.domain.models.User;
import com.walletpe.user.infrastructure.persistence.OtpRepository;
import com.walletpe.user.infrastructure.persistence.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class UserServiceContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OtpRepository otpRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setup() {
        otpRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("R1: Successful registration sets PENDING_EMAIL_VERIFICATION and generates hashed OTP")
    void testSuccessfulRegistration() throws Exception {
        SignupRequest request = new SignupRequest();
        request.setName("Alice Smith");
        request.setDateOfBirth(LocalDate.now().minusYears(20));
        request.setEmail("Alice.Smith@Example.COM");
        request.setMobileNumber("9876543210");
        request.setPassword("SecurePass123!");

        mockMvc.perform(post("/api/v1/users/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("alice.smith@example.com"))
                .andExpect(jsonPath("$.data.status").value("PENDING_EMAIL_VERIFICATION"))
                .andExpect(jsonPath("$.data.emailVerified").value(false));

        User user = userRepository.findByEmail("alice.smith@example.com").orElseThrow();
        assertNotEquals("SecurePass123!", user.getPasswordHash());
        assertTrue(passwordEncoder.matches("SecurePass123!", user.getPasswordHash()));

        OtpChallenge challenge = otpRepository
                .findFirstByTargetIdentifierAndPurposeAndConsumedFalseOrderByCreatedAtDesc(
                        "alice.smith@example.com", OtpPurpose.EMAIL_VERIFICATION).orElseThrow();
        assertNotNull(challenge.getOtpHash());
        assertFalse(challenge.isVerified());
    }

    @Test
    @DisplayName("R1: Reject registration if age is strictly <= 16 years")
    void testUnderAgeRegistrationRejected() throws Exception {
        SignupRequest request = new SignupRequest();
        request.setName("Young User");
        request.setDateOfBirth(LocalDate.now().minusYears(16)); // Exactly 16 years
        request.setEmail("young@example.com");
        request.setMobileNumber("9876543211");
        request.setPassword("SecurePass123!");

        mockMvc.perform(post("/api/v1/users/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("R1: Reject duplicate email or mobile number")
    void testDuplicateEmailOrMobileRejected() throws Exception {
        SignupRequest request1 = new SignupRequest();
        request1.setName("Alice");
        request1.setDateOfBirth(LocalDate.now().minusYears(25));
        request1.setEmail("duplicate@example.com");
        request1.setMobileNumber("9876543212");
        request1.setPassword("SecurePass123!");

        mockMvc.perform(post("/api/v1/users/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isCreated());

        SignupRequest request2 = new SignupRequest();
        request2.setName("Bob");
        request2.setDateOfBirth(LocalDate.now().minusYears(22));
        request2.setEmail("duplicate@example.com");
        request2.setMobileNumber("9876543213");
        request2.setPassword("SecurePass123!");

        mockMvc.perform(post("/api/v1/users/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("R2: Email verification activates account and invalidates OTP")
    void testEmailVerificationFlow() throws Exception {
        SignupRequest request = new SignupRequest();
        request.setName("Charlie");
        request.setDateOfBirth(LocalDate.now().minusYears(24));
        request.setEmail("charlie@example.com");
        request.setMobileNumber("9876543214");
        request.setPassword("SecurePass123!");

        mockMvc.perform(post("/api/v1/users/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Update challenge with known OTP for test verification
        OtpChallenge challenge = otpRepository
                .findFirstByTargetIdentifierAndPurposeAndConsumedFalseOrderByCreatedAtDesc(
                        "charlie@example.com", OtpPurpose.EMAIL_VERIFICATION).orElseThrow();
        challenge.setOtpHash(passwordEncoder.encode("123456"));
        otpRepository.save(challenge);

        VerifyEmailRequest verifyRequest = new VerifyEmailRequest();
        verifyRequest.setEmail("charlie@example.com");
        verifyRequest.setOtp("123456");

        mockMvc.perform(post("/api/v1/users/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifyRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        User user = userRepository.findByEmail("charlie@example.com").orElseThrow();
        assertTrue(user.isEmailVerified());
        assertEquals(UserStatus.ACTIVE, user.getStatus());

        // Verify single-use: subsequent verification with same OTP must fail
        mockMvc.perform(post("/api/v1/users/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifyRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("R3: Login with verified email succeeds, unverified fails")
    void testLoginFlow() throws Exception {
        User user = new User();
        user.setName("David");
        user.setEmail("david@example.com");
        user.setMobileNumber("9876543215");
        user.setPasswordHash(passwordEncoder.encode("Password123!"));
        user.setEmailVerified(false);
        user.setStatus(UserStatus.PENDING_EMAIL_VERIFICATION);
        userRepository.save(user);

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setIdentifier("david@example.com");
        loginRequest.setPassword("Password123!");

        // Unverified email login fails
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isConflict());

        // Verify email
        user.setEmailVerified(true);
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);

        // Verified login succeeds and issues JWT
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"));

        // Mobile login fails when mobile is unverified
        LoginRequest mobileLogin = new LoginRequest();
        mobileLogin.setIdentifier("9876543215");
        mobileLogin.setPassword("Password123!");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mobileLogin)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("R3: Forgot password and Reset Password flow")
    void testForgotPasswordAndReset() throws Exception {
        User user = new User();
        user.setName("Eve");
        user.setEmail("eve@example.com");
        user.setMobileNumber("9876543216");
        user.setPasswordHash(passwordEncoder.encode("OldPassword123!"));
        user.setEmailVerified(true);
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);

        ForgotPasswordRequest forgotRequest = new ForgotPasswordRequest();
        forgotRequest.setEmail("eve@example.com");

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(forgotRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        OtpChallenge challenge = otpRepository
                .findFirstByTargetIdentifierAndPurposeAndConsumedFalseOrderByCreatedAtDesc(
                        "eve@example.com", OtpPurpose.PASSWORD_RESET).orElseThrow();
        challenge.setOtpHash(passwordEncoder.encode("654321"));
        otpRepository.save(challenge);

        ResetPasswordRequest resetRequest = new ResetPasswordRequest();
        resetRequest.setEmail("eve@example.com");
        resetRequest.setOtp("654321");
        resetRequest.setNewPassword("NewSecurePassword456!");

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resetRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        User updatedUser = userRepository.findByEmail("eve@example.com").orElseThrow();
        assertTrue(passwordEncoder.matches("NewSecurePassword456!", updatedUser.getPasswordHash()));
        assertFalse(passwordEncoder.matches("OldPassword123!", updatedUser.getPasswordHash()));
    }

    @Test
    @DisplayName("R4: Mobile verification initiates and verifies for KYC prerequisite")
    void testDeferredMobileVerification() throws Exception {
        User user = new User();
        user.setName("Frank");
        user.setEmail("frank@example.com");
        user.setMobileNumber("9876543217");
        user.setPasswordHash(passwordEncoder.encode("Password123!"));
        user.setEmailVerified(true);
        user.setMobileVerified(false);
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setIdentifier("frank@example.com");
        loginRequest.setPassword("Password123!");

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        ApiResponse<AuthResponse> authResponse = objectMapper.readValue(
                loginResult.getResponse().getContentAsString(),
                objectMapper.getTypeFactory().constructParametricType(ApiResponse.class, AuthResponse.class));
        String token = authResponse.getData().getToken();

        // Initiate mobile verification
        mockMvc.perform(post("/api/v1/users/mobile-verification/initiate")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        OtpChallenge challenge = otpRepository
                .findFirstByTargetIdentifierAndPurposeAndConsumedFalseOrderByCreatedAtDesc(
                        "9876543217", OtpPurpose.MOBILE_VERIFICATION).orElseThrow();
        challenge.setOtpHash(passwordEncoder.encode("777888"));
        otpRepository.save(challenge);

        VerifyMobileRequest verifyReq = new VerifyMobileRequest();
        verifyReq.setOtp("777888");

        mockMvc.perform(post("/api/v1/users/mobile-verification/verify")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifyReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        User updatedUser = userRepository.findById(user.getUserId()).orElseThrow();
        assertTrue(updatedUser.isMobileVerified());
    }

    @Test
    @DisplayName("R4: Profile retrieval and update with user isolation")
    void testProfileManagement() throws Exception {
        User user = new User();
        user.setName("Grace Hopper");
        user.setEmail("grace@example.com");
        user.setMobileNumber("9876543218");
        user.setPasswordHash(passwordEncoder.encode("Password123!"));
        user.setEmailVerified(true);
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setIdentifier("grace@example.com");
        loginRequest.setPassword("Password123!");

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        ApiResponse<AuthResponse> authResponse = objectMapper.readValue(
                loginResult.getResponse().getContentAsString(),
                objectMapper.getTypeFactory().constructParametricType(ApiResponse.class, AuthResponse.class));
        String token = authResponse.getData().getToken();

        // Get Profile
        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Grace Hopper"))
                .andExpect(jsonPath("$.data.email").value("grace@example.com"));

        // Update Name
        UpdateProfileRequest updateReq = new UpdateProfileRequest();
        updateReq.setName("Admiral Grace Hopper");

        mockMvc.perform(patch("/api/v1/users/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Admiral Grace Hopper"));
    }
}

package com.walletpe.user.api.dto;

import jakarta.validation.constraints.NotBlank;

public class VerifyMobileRequest {
    @NotBlank(message = "OTP is required")
    private String otp;

    public VerifyMobileRequest() {}

    public String getOtp() { return otp; }
    public void setOtp(String otp) { this.otp = otp; }
}

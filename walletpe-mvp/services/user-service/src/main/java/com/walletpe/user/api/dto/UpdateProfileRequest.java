package com.walletpe.user.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;

public class UpdateProfileRequest {
    private String name;
    @Email(message = "Invalid email format")
    private String email;
    @Pattern(regexp = "^[0-9]{10}$", message = "Mobile number must be a valid 10-digit number")
    private String mobileNumber;

    public UpdateProfileRequest() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getMobileNumber() { return mobileNumber; }
    public void setMobileNumber(String mobileNumber) { this.mobileNumber = mobileNumber; }
}

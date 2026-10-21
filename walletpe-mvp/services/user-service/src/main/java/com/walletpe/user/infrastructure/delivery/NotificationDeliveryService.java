package com.walletpe.user.infrastructure.delivery;

public interface NotificationDeliveryService {
    void sendEmail(String to, String subject, String body);
    void sendSms(String mobileNumber, String message);
}

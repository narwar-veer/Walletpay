package com.walletpe.user.domain.models;

import jakarta.persistence.*;
import com.walletpe.user.domain.enums.UserRole;
import com.walletpe.user.domain.enums.UserStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User {

     @Id
     @GeneratedValue(strategy = GenerationType.UUID)
     @Column(name = "user_id", nullable = false, updatable = false)
     private UUID userId;

     @Column(name = "name", nullable = false)
     private String name;

     @Column(name = "email", nullable = false, unique = true)
     private String email;

     @Column(name = "mobile_number", nullable = false, unique = true)
     private String mobileNumber;

     @Column(name = "password_hash")
     private String passwordHash;

     @Column(name = "email_verified", nullable = false)
     private boolean emailVerified = false;

     @Column(name = "mobile_verified", nullable = false)
     private boolean mobileVerified = false;

     @Enumerated(EnumType.STRING)
     @Column(name = "role", nullable = false)
     private UserRole role = UserRole.USER;

     @Enumerated(EnumType.STRING)
     @Column(name = "status", nullable = false)
     private UserStatus status;

     @Column(name = "last_login_at")
     private LocalDateTime lastLoginAt;

     @CreationTimestamp
     @Column(name = "created_at", nullable = false, updatable = false)
     private LocalDateTime createdAt;

     @UpdateTimestamp
     @Column(name = "updated_at", nullable = false)
     private LocalDateTime updatedAt;

}

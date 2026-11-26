package com.foodie.ecommerce.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor

public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String fullName;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Column(length = 20)
    private String role = "ROLE_CUSTOMER";

    @Column(name = "active_retention_offer", length = 255)
    private String activeRetentionOffer;

    @Column(length = 40)
    private String phone;

    @Column(name = "delivery_zone", length = 120)
    private String deliveryZone;

    @Column(name = "delivery_address", length = 255)
    private String deliveryAddress;

    @Column(name = "default_payment_method", length = 60)
    private String defaultPaymentMethod;

    @Column(name = "mpesa_number", length = 40)
    private String mpesaNumber;

    @Column(name = "visa_last4", length = 10)
    private String visaLast4;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    public User(Long id, String fullName, String email, String passwordHash, String role, String activeRetentionOffer, LocalDateTime createdAt) {
        this.id = id;
        this.fullName = fullName;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.activeRetentionOffer = activeRetentionOffer;
        this.createdAt = createdAt;
    }

    public User(Long id, String fullName, String email, String passwordHash, String role, LocalDateTime createdAt) {
        this.id = id;
        this.fullName = fullName;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.createdAt = createdAt;
    }
}
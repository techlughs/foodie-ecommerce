package com.foodie.ecommerce.model;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
@Entity @Table(name = "orders") @Data @NoArgsConstructor @AllArgsConstructor
public class Order {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2) private BigDecimal totalAmount;
    @Column(length = 45) private String status = "AWAITING_KITCHEN";
    @Column(name = "delivery_zone", length = 100) private String deliveryZone;
    @Column(name = "delivery_address", length = 255) private String deliveryAddress;
    @Column(name = "kitchen_notes", length = 255) private String kitchenNotes;
    @Column(name = "rider_instructions", length = 255) private String riderInstructions;
    @Column(name = "admin_arrived_confirmed") private Boolean adminArrivedConfirmed = false;
    @Column(name = "customer_received_confirmed") private Boolean customerReceivedConfirmed = false;
    @Column(name = "created_at", updatable = false) private LocalDateTime createdAt = LocalDateTime.now();
    @Column(name = "delivered_at") private LocalDateTime deliveredAt;
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<OrderItem> items = new ArrayList<>();

    public LocalDateTime getDeliveredAt() { return deliveredAt; }
    public void setDeliveredAt(LocalDateTime deliveredAt) { this.deliveredAt = deliveredAt; }
}

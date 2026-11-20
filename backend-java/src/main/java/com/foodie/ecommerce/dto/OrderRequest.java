package com.foodie.ecommerce.dto;
import lombok.Data;
import java.util.List;
@Data public class OrderRequest {
    private Long userId;
    private String deliveryZone;
    private String deliveryAddress;
    private String kitchenNotes;
    private String riderInstructions;
    private List<OrderItemRequest> items;
    @Data public static class OrderItemRequest { private String productId; private Integer quantity; }
}

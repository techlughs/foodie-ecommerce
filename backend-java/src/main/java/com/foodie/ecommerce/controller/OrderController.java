package com.foodie.ecommerce.controller;
import com.foodie.ecommerce.dto.OrderRequest;
import com.foodie.ecommerce.model.*;
import com.foodie.ecommerce.repository.*;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
@RestController @RequestMapping("/api/orders") @CrossOrigin(origins = "*")
public class OrderController {
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    public OrderController(OrderRepository orderRepository, ProductRepository productRepository) {
        this.orderRepository = orderRepository; this.productRepository = productRepository;
    }
    @PostMapping @Transactional
    public ResponseEntity<?> createOrder(@RequestBody OrderRequest request) {
        if (request.getUserId() == null || request.getUserId() <= 0 || request.getItems() == null || request.getItems().isEmpty()) {
            return ResponseEntity.badRequest().body("Please sign in and select at least one dish for your tasting tray.");
        }
        if (request.getDeliveryAddress() == null || request.getDeliveryAddress().trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Please specify your Nairobi residence, office, or drop-off point.");
        }
        Order order = new Order();
        order.setUserId(request.getUserId());
        order.setStatus("AWAITING_KITCHEN");
        order.setDeliveryZone(request.getDeliveryZone() != null && !request.getDeliveryZone().isBlank() ? request.getDeliveryZone().trim() : "Westlands & Parklands");
        order.setDeliveryAddress(request.getDeliveryAddress().trim());
        order.setKitchenNotes(request.getKitchenNotes() != null ? request.getKitchenNotes().trim() : "");
        order.setRiderInstructions(request.getRiderInstructions() != null ? request.getRiderInstructions().trim() : "");
        order.setAdminArrivedConfirmed(false);
        order.setCustomerReceivedConfirmed(false);
        BigDecimal total = BigDecimal.ZERO;
        for (OrderRequest.OrderItemRequest itemReq : request.getItems()) {
            Product product = productRepository.findById(itemReq.getProductId()).orElseThrow(() -> new IllegalArgumentException("Dish not found: " + itemReq.getProductId()));
            int qty = (itemReq.getQuantity() != null && itemReq.getQuantity() > 0) ? itemReq.getQuantity() : 1;
            BigDecimal linePrice = product.getPrice();
            total = total.add(linePrice.multiply(BigDecimal.valueOf(qty)));
            OrderItem item = new OrderItem();
            item.setOrder(order); item.setProductId(product.getId()); item.setQuantity(qty); item.setPrice(linePrice);
            order.getItems().add(item);
        }
        order.setTotalAmount(total);
        return ResponseEntity.ok(orderRepository.save(order));
    }
    @GetMapping("/user/{userId}")
    public List<Order> getOrdersByUser(@PathVariable Long userId) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }
    @PostMapping("/{orderId}/confirm-delivery") @Transactional
    public ResponseEntity<?> confirmCustomerReceipt(@PathVariable Long orderId) {
        return orderRepository.findById(orderId).map(order -> {
            order.setCustomerReceivedConfirmed(true);
            if (Boolean.TRUE.equals(order.getAdminArrivedConfirmed()) || "AT_YOUR_DOORSTEP".equalsIgnoreCase(order.getStatus())) {
                order.setAdminArrivedConfirmed(true);
                order.setStatus("TABLE_SERVED");
            } else {
                order.setStatus("DINER_CONFIRMED_AWAITING_CONCIERGE");
            }
            Order saved = orderRepository.save(order);
            return ResponseEntity.ok(Map.of(
                "order", saved,
                "bothConfirmed", "TABLE_SERVED".equalsIgnoreCase(saved.getStatus()),
                "thankYouMessage", "Karibu Tena — Thank you for dining with Foodie. Both concierge arrival and your table receipt are confirmed."
            ));
        }).orElse(ResponseEntity.notFound().build());
    }
}

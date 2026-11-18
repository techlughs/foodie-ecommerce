package com.foodie.ecommerce.controller;

import com.foodie.ecommerce.dto.OrderRequest;
import com.foodie.ecommerce.model.Order;
import com.foodie.ecommerce.model.OrderItem;
import com.foodie.ecommerce.model.Product;
import com.foodie.ecommerce.repository.OrderRepository;
import com.foodie.ecommerce.repository.ProductRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/orders")
@CrossOrigin(origins = "*")
public class OrderController {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;

    public OrderController(OrderRepository orderRepository, ProductRepository productRepository) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
    }

    @PostMapping
    @Transactional
    public ResponseEntity<?> createOrder(@RequestBody OrderRequest request) {
        if (request.getUserId() == null || request.getItems() == null || request.getItems().isEmpty()) {
            return ResponseEntity.badRequest().body("userId and at least one item are required");
        }

        Order order = new Order();
        order.setUserId(request.getUserId());
        order.setStatus("CONFIRMED");

        BigDecimal total = BigDecimal.ZERO;

        for (OrderRequest.OrderItemRequest itemReq : request.getItems()) {
            Product product = productRepository.findById(itemReq.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException("Product not found: " + itemReq.getProductId()));

            int qty = (itemReq.getQuantity() != null && itemReq.getQuantity() > 0) ? itemReq.getQuantity() : 1;
            BigDecimal linePrice = product.getPrice();
            total = total.add(linePrice.multiply(BigDecimal.valueOf(qty)));

            OrderItem item = new OrderItem();
            item.setOrder(order);
            item.setProductId(product.getId());
            item.setQuantity(qty);
            item.setPrice(linePrice);
            order.getItems().add(item);
        }

        order.setTotalAmount(total);
        Order savedOrder = orderRepository.save(order);
        return ResponseEntity.ok(savedOrder);
    }

    @GetMapping("/user/{userId}")
    public List<Order> getOrdersByUser(@PathVariable Long userId) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }
}

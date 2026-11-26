package com.foodie.ecommerce.controller;

import com.foodie.ecommerce.dto.OrderRequest;
import com.foodie.ecommerce.model.*;
import com.foodie.ecommerce.repository.*;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/orders")
@CrossOrigin(origins = "*")
public class OrderController {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final ClickstreamRepository clickstreamRepository;

    public OrderController(OrderRepository orderRepository,
                           ProductRepository productRepository,
                           UserRepository userRepository,
                           ClickstreamRepository clickstreamRepository) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.clickstreamRepository = clickstreamRepository;
    }

    @GetMapping
    public ResponseEntity<?> getOrdersRoot(@RequestParam(required = false) Long userId) {
        if (userId != null) {
            return ResponseEntity.ok(orderRepository.findByUserIdOrderByCreatedAtDesc(userId));
        }
        return ResponseEntity.ok(List.of());
    }

    @PostMapping
    @Transactional
    public ResponseEntity<?> createOrder(@RequestBody OrderRequest request) {
        if (request.getUserId() == null || request.getUserId() <= 0 || request.getItems() == null || request.getItems().isEmpty()) {
            return ResponseEntity.badRequest().body("Please sign in and select at least one dish for your tasting tray.");
        }
        if (request.getDeliveryAddress() == null || request.getDeliveryAddress().trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Please specify your Nairobi residence, office, or drop-off point.");
        }

        Optional<User> userOpt = userRepository.findById(request.getUserId());
        String activeOffer = userOpt
                .map(User::getActiveRetentionOffer)
                .filter(o -> o != null && !o.isBlank())
                .orElse(null);

        Order order = new Order();
        order.setUserId(request.getUserId());
        order.setStatus("AWAITING_KITCHEN");
        order.setDeliveryZone(request.getDeliveryZone() != null && !request.getDeliveryZone().isBlank()
                ? request.getDeliveryZone().trim() : "Westlands & Parklands");
        order.setDeliveryAddress(request.getDeliveryAddress().trim());
        order.setRiderInstructions(request.getRiderInstructions() != null ? request.getRiderInstructions().trim() : "");
        order.setAdminArrivedConfirmed(false);
        order.setCustomerReceivedConfirmed(false);

        BigDecimal foodSubtotal = BigDecimal.ZERO;
        BigDecimal nyamaChomaSubtotal = BigDecimal.ZERO;

        for (OrderRequest.OrderItemRequest itemReq : request.getItems()) {
            Product product = productRepository.findById(itemReq.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException("Dish not found: " + itemReq.getProductId()));
            int qty = (itemReq.getQuantity() != null && itemReq.getQuantity() > 0) ? itemReq.getQuantity() : 1;
            BigDecimal linePrice = product.getPrice();
            BigDecimal lineTotal = linePrice.multiply(BigDecimal.valueOf(qty));

            foodSubtotal = foodSubtotal.add(lineTotal);
            if (product.getName() != null && product.getName().toLowerCase().contains("nyama choma")) {
                nyamaChomaSubtotal = nyamaChomaSubtotal.add(lineTotal);
            }

            OrderItem item = new OrderItem();
            item.setOrder(order);
            item.setProductId(product.getId());
            item.setQuantity(qty);
            item.setPrice(linePrice);
            order.getItems().add(item);
        }

        BigDecimal fee = (request.getDeliveryFee() != null && request.getDeliveryFee().compareTo(BigDecimal.ZERO) > 0)
                ? request.getDeliveryFee() : BigDecimal.ZERO;

        BigDecimal discount = BigDecimal.ZERO;
        String offerSummary = null;

        if (activeOffer != null) {
            String lower = activeOffer.toLowerCase();

            java.util.regex.Matcher pctMatcher = java.util.regex.Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*%").matcher(lower);
            java.util.regex.Matcher kesMatcher = java.util.regex.Pattern.compile("kes\\s*(\\d+(?:\\.\\d+)?)").matcher(lower);

            if (pctMatcher.find()) {
                BigDecimal pct = new BigDecimal(pctMatcher.group(1));
                BigDecimal rate = pct.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);
                BigDecimal targetAmount = (pct.compareTo(new BigDecimal("25")) == 0 && nyamaChomaSubtotal.compareTo(BigDecimal.ZERO) > 0)
                        ? nyamaChomaSubtotal : foodSubtotal;
                discount = targetAmount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
                offerSummary = pct.stripTrailingZeros().toPlainString() + "% Offer (-KES " + discount + ")";
            } else if (lower.contains("welcome")) {
                discount = foodSubtotal.multiply(new BigDecimal("0.15")).setScale(2, RoundingMode.HALF_UP);
                offerSummary = "15% Welcome Offer (-KES " + discount + ")";
            } else if (kesMatcher.find()) {
                BigDecimal flatOff = new BigDecimal(kesMatcher.group(1)).setScale(2, RoundingMode.HALF_UP);
                discount = flatOff.min(foodSubtotal);
                offerSummary = "KES " + discount + " Off";
            }

            if ((lower.contains("free") && lower.contains("delivery")) || lower.contains("welcome")) {
                fee = BigDecimal.ZERO;
                offerSummary = (offerSummary != null ? offerSummary + " + " : "") + "Free Delivery";
            }

            if (lower.contains("masala chai")) {
                productRepository.findAll().stream()
                        .filter(p -> p.getName() != null && p.getName().toLowerCase().contains("masala chai"))
                        .findFirst()
                        .ifPresent(chai -> {
                            OrderItem freeChai = new OrderItem();
                            freeChai.setOrder(order);
                            freeChai.setProductId(chai.getId());
                            freeChai.setQuantity(1);
                            freeChai.setPrice(BigDecimal.ZERO);
                            order.getItems().add(freeChai);
                        });
                offerSummary = (offerSummary != null ? offerSummary + " + " : "") + "Free Kenyan Masala Chai Pot";
            }

            userOpt.ifPresent(u -> {
                u.setActiveRetentionOffer(null);
                userRepository.save(u);
            });
        }

        String baseNotes = request.getKitchenNotes() != null ? request.getKitchenNotes().trim() : "";
        if (offerSummary != null) {
            String cleanOffer = activeOffer != null ? activeOffer.trim() : offerSummary;
            String noteText = cleanOffer.equalsIgnoreCase(offerSummary)
                    ? "Offer applied: " + cleanOffer
                    : "Offer applied: " + cleanOffer + " (" + offerSummary + ")";
            order.setKitchenNotes(baseNotes.isEmpty() ? noteText : baseNotes + " | " + noteText);
        } else {
            order.setKitchenNotes(baseNotes);
        }

        BigDecimal discountedFood = foodSubtotal.subtract(discount).max(BigDecimal.ZERO);
        order.setTotalAmount(discountedFood.add(fee));

        Order savedOrder = orderRepository.save(order);
        for (OrderItem savedItem : savedOrder.getItems()) {
            String dishName = productRepository.findById(savedItem.getProductId()).map(Product::getName).orElse("Signature Dish");
            clickstreamRepository.save(new ClickstreamLog(null, savedOrder.getUserId(), savedItem.getProductId(), dishName, "PURCHASE", java.time.LocalDateTime.now()));
        }
        return ResponseEntity.ok(savedOrder);
    }

    @GetMapping("/user/{userId}")
    public List<Order> getOrdersByUser(@PathVariable Long userId) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @PostMapping("/{orderId}/confirm-delivery")
    @Transactional
    public ResponseEntity<?> confirmCustomerReceipt(@PathVariable Long orderId) {
        return orderRepository.findById(orderId).map(order -> {
            order.setCustomerReceivedConfirmed(true);
            if (Boolean.TRUE.equals(order.getAdminArrivedConfirmed()) || "AT_YOUR_DOORSTEP".equalsIgnoreCase(order.getStatus())) {
                order.setAdminArrivedConfirmed(true);
                order.setStatus("TABLE_SERVED");
                order.setDeliveredAt(java.time.LocalDateTime.now());
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

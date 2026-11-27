package com.foodie.ecommerce.controller;

import com.foodie.ecommerce.config.DataSeeder;
import com.foodie.ecommerce.config.WebConfig;
import com.foodie.ecommerce.model.ClickstreamLog;
import com.foodie.ecommerce.model.Order;
import com.foodie.ecommerce.model.Product;
import com.foodie.ecommerce.model.User;
import com.foodie.ecommerce.repository.ClickstreamRepository;
import com.foodie.ecommerce.repository.OrderRepository;
import com.foodie.ecommerce.repository.ProductRepository;
import com.foodie.ecommerce.repository.UserRepository;
import com.foodie.ecommerce.service.AiIntegrationService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/portal")
public class PortalApiController {

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final ClickstreamRepository clickstreamRepository;
    private final OrderRepository orderRepository;
    private final AiIntegrationService aiIntegrationService;

    public PortalApiController(UserRepository userRepository,
                               ProductRepository productRepository,
                               ClickstreamRepository clickstreamRepository,
                               OrderRepository orderRepository,
                               AiIntegrationService aiIntegrationService) {
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.clickstreamRepository = clickstreamRepository;
        this.orderRepository = orderRepository;
        this.aiIntegrationService = aiIntegrationService;
    }

        private void attachAuthCookie(HttpServletResponse response, Long userId) {
        Cookie cookie = new Cookie("FOODIE_UID", userId != null ? String.valueOf(userId) : "");
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setMaxAge(userId != null ? 7 * 24 * 60 * 60 : 0);
        response.addCookie(cookie);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body, HttpSession session, HttpServletResponse response) {
        String email = body.getOrDefault("email", "").trim().toLowerCase();
        String password = body.getOrDefault("password", "");
        if (("diner@foodie.co.ke".equals(email) && "foodie123".equals(password)) || ("admin@foodie.co.ke".equals(email) && "admin123".equals(password))) {
            var demoUser = userRepository.findByEmail(email).orElseGet(() -> {
                com.foodie.ecommerce.model.User u = new com.foodie.ecommerce.model.User();
                u.setEmail(email);
                u.setFullName("admin@foodie.co.ke".equals(email) ? "Executive Chef Admin" : "Nairobi Diner");
                u.setRole("admin@foodie.co.ke".equals(email) ? "ROLE_ADMIN" : "ROLE_CUSTOMER");
                return u;
            });
            demoUser.setPasswordHash(DataSeeder.hashPassword(password));
            demoUser = userRepository.save(demoUser);
            session.setAttribute("loggedInUser", demoUser);
            attachAuthCookie(response, demoUser.getId());
            return ResponseEntity.ok(Map.of(
                    "id", demoUser.getId(),
                    "fullName", demoUser.getFullName(),
                    "email", demoUser.getEmail(),
                    "role", demoUser.getRole()
            ));
        }
        return userRepository.findByEmail(email)
                .filter(u -> u.getPasswordHash() != null && u.getPasswordHash().equals(DataSeeder.hashPassword(password)))
                .<ResponseEntity<?>>map(user -> {
                    session.setAttribute("loggedInUser", user);
                    attachAuthCookie(response, user.getId());
                    return ResponseEntity.ok(Map.of(
                            "id", user.getId(),
                            "fullName", user.getFullName(),
                            "email", user.getEmail(),
                            "role", user.getRole()
                    ));
                })
                .orElseGet(() -> ResponseEntity.status(401).body(Map.of("error", "Invalid email or password")));
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> body, HttpSession session, HttpServletResponse response) {
        String fullName = body.getOrDefault("fullName", "").trim();
        String email = body.getOrDefault("email", "").trim().toLowerCase();
        String password = body.getOrDefault("password", "");

        if (fullName.isEmpty() || email.isEmpty() || password.length() < 4) {
            return ResponseEntity.badRequest().body(Map.of("error", "Provide name, valid email, and password (4+ chars)."));
        }
        if (userRepository.findByEmail(email).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email is already registered."));
        }

        User created = userRepository.save(new User(
                null, fullName, email, DataSeeder.hashPassword(password), "ROLE_CUSTOMER", null
        ));
        session.setAttribute("loggedInUser", created);
        attachAuthCookie(response, created.getId());
        return ResponseEntity.ok(Map.of("id", created.getId(), "fullName", created.getFullName(), "role", created.getRole()));
    }

    @GetMapping("/logout")
    public void logoutGet(HttpSession session, HttpServletResponse response) throws java.io.IOException {
        session.invalidate();
        jakarta.servlet.http.Cookie cookie = new jakarta.servlet.http.Cookie("foodie_uid", "");
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
        response.sendRedirect("/store");
    }

    @DeleteMapping("/user/{id}")
    public ResponseEntity<?> deleteUserAccount(@PathVariable Long id, HttpSession session, HttpServletResponse response) {
        return userRepository.findById(id).map(user -> {
            String role = user.getRole() != null ? user.getRole().toUpperCase() : "";
            if ("ROLE_ADMIN".equals(role) || "ADMIN".equals(role) || "admin@foodie.co.ke".equalsIgnoreCase(user.getEmail())) {
                return ResponseEntity.status(403).body(Map.of("error", "Administrator accounts cannot be permanently deleted."));
            }
            List<Order> userOrders = orderRepository.findByUserIdOrderByCreatedAtDesc(id);
            if (!userOrders.isEmpty()) {
                orderRepository.deleteAll(userOrders);
            }
            List<ClickstreamLog> logs = clickstreamRepository.findAll().stream()
                    .filter(l -> id.equals(l.getUserId()))
                    .toList();
            if (!logs.isEmpty()) {
                clickstreamRepository.deleteAll(logs);
            }
            userRepository.delete(user);

            Object sessionObj = session.getAttribute("loggedInUser");
            if (sessionObj instanceof User sessionUser && id.equals(sessionUser.getId())) {
                session.invalidate();
                attachAuthCookie(response, null);
            }
            return ResponseEntity.ok(Map.of("deleted", true, "id", id));
        }).orElseGet(() -> ResponseEntity.status(404).body(Map.of("error", "User account not found.")));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpSession session, HttpServletResponse response) {
        session.invalidate();
        attachAuthCookie(response, null);
        return ResponseEntity.ok(Map.of("status", "logged_out"));
    }

    @PostMapping("/clickstream")
    public ResponseEntity<?> logClick(@RequestBody Map<String, Object> payload) {
        Long userId = 1L; if (payload.get("userId") != null) { try { userId = Long.valueOf(payload.get("userId").toString().trim()); } catch (NumberFormatException ignored) {} }
        String productId = String.valueOf(payload.getOrDefault("productId", ""));
        String productName = String.valueOf(payload.getOrDefault("productName", ""));
        Object rawType = payload.get("eventType") != null ? payload.get("eventType") : payload.get("action"); String eventType = (rawType != null && !rawType.toString().isBlank()) ? rawType.toString() : "VIEW";

        clickstreamRepository.save(new ClickstreamLog(null, userId, productId, productName, eventType, LocalDateTime.now()));
        return ResponseEntity.ok(Map.of("logged", true));
    }

    @DeleteMapping("/orders/user/{userId}")
    public ResponseEntity<?> clearUserOrders(@PathVariable Long userId) {
        List<Order> orders = orderRepository.findAll().stream().filter(o -> userId.equals(o.getUserId())).toList();
        orderRepository.deleteAll(orders);
        return ResponseEntity.ok(Map.of("cleared", orders.size()));
    }

    @PostMapping("/admin/products")
    public ResponseEntity<?> addProduct(@RequestBody Map<String, Object> body) {
        Product p = new Product();
        p.setName(String.valueOf(body.getOrDefault("name", "New Kenyan Dish")).trim());
        p.setCategory(String.valueOf(body.getOrDefault("category", "Main Course")).trim());
        p.setDescription(String.valueOf(body.getOrDefault("description", "Freshly prepared authentic dish.")).trim());
        p.setPrice(new BigDecimal(String.valueOf(body.getOrDefault("price", "450.00"))));
        p.setImageUrl(String.valueOf(body.getOrDefault("imageUrl", "https://images.unsplash.com/photo-1544025162-d76694265947?auto=format&fit=crop&w=800&q=80")).trim());
        p.setIngredients(parseIngredients(body.getOrDefault("ingredients", "Fresh Local Spices, Chef's Signature Blend")));
        Map<String, Object> nutrition = new java.util.LinkedHashMap<>();
        nutrition.put("calories", parseCaloriesValue(body.get("calories"), 540));
        nutrition.put("protein", parseNutritionText(body.get("protein"), "32g"));
        nutrition.put("fat", parseNutritionText(body.get("fat"), "18g"));
        nutrition.put("carbs", parseNutritionText(body.get("carbs"), "45g"));
        p.setNutritionalInfo(nutrition);
        p.setAvailable(true);
        return ResponseEntity.ok(productRepository.save(p));
    }

    @PutMapping("/admin/products/{id}")
    public ResponseEntity<?> updateProduct(@PathVariable String id, @RequestBody Map<String, Object> body) {
        return productRepository.findById(id).map(p -> {
            if (body.containsKey("name") && !String.valueOf(body.get("name")).isBlank()) {
                p.setName(String.valueOf(body.get("name")).trim());
            }
            if (body.containsKey("category") && !String.valueOf(body.get("category")).isBlank()) {
                p.setCategory(String.valueOf(body.get("category")).trim());
            }
            if (body.containsKey("description")) {
                p.setDescription(String.valueOf(body.get("description")).trim());
            }
            if (body.containsKey("ingredients")) {
                p.setIngredients(parseIngredients(body.get("ingredients")));
            }
            if (body.containsKey("price")) {
                p.setPrice(new BigDecimal(String.valueOf(body.get("price"))));
            }
            if (body.containsKey("available")) {
                p.setAvailable(Boolean.parseBoolean(String.valueOf(body.get("available"))));
            }
            if (body.containsKey("imageUrl") && !String.valueOf(body.get("imageUrl")).isBlank()) {
                p.setImageUrl(String.valueOf(body.get("imageUrl")).trim());
            }
            if (body.containsKey("calories") || body.containsKey("protein") || body.containsKey("fat") || body.containsKey("carbs")) {
                Map<String, Object> updatedNutrition = new java.util.LinkedHashMap<>(
                    p.getNutritionalInfo() != null ? p.getNutritionalInfo() : Map.of()
                );
                if (body.containsKey("calories")) {
                    updatedNutrition.put("calories", parseCaloriesValue(body.get("calories"), 540));
                }
                if (body.containsKey("protein")) {
                    updatedNutrition.put("protein", parseNutritionText(body.get("protein"), "32g"));
                }
                if (body.containsKey("fat")) {
                    updatedNutrition.put("fat", parseNutritionText(body.get("fat"), "18g"));
                }
                if (body.containsKey("carbs")) {
                    updatedNutrition.put("carbs", parseNutritionText(body.get("carbs"), "45g"));
                }
                p.setNutritionalInfo(updatedNutrition);
            }
            return ResponseEntity.ok(productRepository.save(p));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/admin/products/{id}/upload-image")
    public ResponseEntity<?> uploadProductImage(@PathVariable String id, @RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No file selected"));
        }
        return productRepository.findById(id).map(p -> {
            try {
                Path uploadDir = WebConfig.resolveUploadDir();
                Files.createDirectories(uploadDir);
                String ext = ".jpg";
                String orig = file.getOriginalFilename();
                if (orig != null && orig.contains(".")) {
                    ext = orig.substring(orig.lastIndexOf("."));
                }
                String filename = "dish-" + id + "-" + UUID.randomUUID().toString().substring(0, 6) + ext;
                Path target = uploadDir.resolve(filename);
                Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

                String publicUrl = "/uploads/" + filename;
                p.setImageUrl(publicUrl);
                productRepository.save(p);
                return ResponseEntity.ok(Map.of("imageUrl", publicUrl));
            } catch (Exception e) {
                return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
            }
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/admin/products/{id}")
    public ResponseEntity<?> deleteProduct(@PathVariable String id) {
        productRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("deleted", id));
    }

    @DeleteMapping("/admin/orders/{orderId}")
    public ResponseEntity<?> deleteCompletedOrder(@PathVariable Long orderId) {
        if (!orderRepository.existsById(orderId)) {
            return ResponseEntity.notFound().build();
        }
        orderRepository.deleteById(orderId);
        return ResponseEntity.ok(Map.of("deleted", true, "orderId", orderId));
    }

    @DeleteMapping("/admin/orders/completed")
    public ResponseEntity<?> clearCompletedOrders() {
        var completed = orderRepository.findAll().stream()
                .filter(o -> "TABLE_SERVED".equalsIgnoreCase(o.getStatus()))
                .toList();
        orderRepository.deleteAll(completed);
        return ResponseEntity.ok(Map.of("deletedCount", completed.size()));
    }

    @PostMapping("/admin/orders/{orderId}/status")
    public ResponseEntity<?> updateOrderLifecycle(@PathVariable Long orderId, @RequestBody Map<String, String> body) {
        String nextStatus = body.getOrDefault("status", "ACCEPTED_BY_CHEF").trim().toUpperCase();
        return orderRepository.findById(orderId).map(order -> {
            if ("AT_YOUR_DOORSTEP".equals(nextStatus) || "ARRIVED".equals(nextStatus)) {
                order.setAdminArrivedConfirmed(true);
                if (Boolean.TRUE.equals(order.getCustomerReceivedConfirmed())) {
                    order.setStatus("TABLE_SERVED");
                    order.setDeliveredAt(java.time.LocalDateTime.now());
                } else {
                    order.setStatus("AT_YOUR_DOORSTEP");
                }
            } else {
                order.setStatus(nextStatus);
                if ("TABLE_SERVED".equalsIgnoreCase(nextStatus)) { order.setDeliveredAt(java.time.LocalDateTime.now()); }
            }
            Order saved = orderRepository.save(order);
            return ResponseEntity.ok(Map.of(
                    "id", saved.getId(),
                    "status", saved.getStatus(),
                    "bothConfirmed", "TABLE_SERVED".equalsIgnoreCase(saved.getStatus())
            ));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/admin/retention/{userId}")
    public ResponseEntity<?> triggerRetention(@PathVariable Long userId, @RequestBody Map<String, String> body) {
        String offer = body.getOrDefault("offer", "20% Loyalty Voucher").trim();
        return userRepository.findById(userId).map(user -> {
            user.setActiveRetentionOffer(offer);
            userRepository.save(user);
            return ResponseEntity.ok(Map.of(
                    "status", "DISPATCHED",
                    "userId", userId,
                    "offer", offer,
                    "message", "Retention offer '" + offer + "' sent to " + user.getFullName()
            ));
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/user/{userId}/offer")
    public ResponseEntity<?> getUserOffer(@PathVariable Long userId) {
        if (userId == null || userId <= 0) {
            return ResponseEntity.ok(Map.of("offer", ""));
        }
        return userRepository.findById(userId)
                .map(u -> ResponseEntity.ok(Map.of("offer", u.getActiveRetentionOffer() != null ? u.getActiveRetentionOffer() : "")))
                .orElseGet(() -> ResponseEntity.ok(Map.of("offer", "")));
    }

    @PostMapping("/user/{userId}/offer/dismiss")
    public ResponseEntity<?> dismissUserOffer(@PathVariable Long userId) {
        return userRepository.findById(userId).map(u -> {
            u.setActiveRetentionOffer(null);
            userRepository.save(u);
            return ResponseEntity.ok(Map.of("dismissed", true));
        }).orElseGet(() -> ResponseEntity.ok(Map.of("dismissed", false)));
    }

    @GetMapping("/admin/live-metrics")
    public ResponseEntity<?> getLiveAdminMetrics() {
        List<User> allUsers = userRepository.findAll();
        java.util.Set<Long> adminIds = allUsers.stream()
                .filter(u -> "ROLE_ADMIN".equalsIgnoreCase(u.getRole()) || "ADMIN".equalsIgnoreCase(u.getRole()))
                .map(User::getId)
                .collect(java.util.stream.Collectors.toSet());

        List<User> customers = allUsers.stream()
                .filter(u -> !"ROLE_ADMIN".equalsIgnoreCase(u.getRole()) && !"ADMIN".equalsIgnoreCase(u.getRole()))
                .toList();

        List<Product> products = productRepository.findAll();

        List<Order> customerOrders = orderRepository.findAll().stream()
                .filter(o -> o.getUserId() != null && !adminIds.contains(o.getUserId()))
                .toList();

        java.util.List<java.util.Map<String, Object>> churnRows = new java.util.ArrayList<>();
        long highRiskCount = 0;
        LocalDateTime now = LocalDateTime.now();

        for (User customer : customers) {
            Long uid = customer.getId();
            List<Order> userHistory = customerOrders.stream()
                    .filter(o -> uid.equals(o.getUserId()))
                    .toList();

            int totalOrders = userHistory.size();
            int daysSinceLast = 0;
            int ordersLast30d = 0;
            double avgOrderValue = 0.0;

            if (totalOrders > 0) {
                LocalDateTime latestOrderAt = userHistory.stream()
                        .map(Order::getCreatedAt)
                        .filter(java.util.Objects::nonNull)
                        .max(LocalDateTime::compareTo)
                        .orElse(now);
                daysSinceLast = (int) Math.max(0, java.time.temporal.ChronoUnit.DAYS.between(latestOrderAt, now));

                LocalDateTime cutoff30d = now.minusDays(30);
                ordersLast30d = (int) userHistory.stream()
                        .filter(o -> o.getCreatedAt() == null || !o.getCreatedAt().isBefore(cutoff30d))
                        .count();

                double totalSpent = userHistory.stream()
                        .map(Order::getTotalAmount)
                        .filter(java.util.Objects::nonNull)
                        .mapToDouble(BigDecimal::doubleValue)
                        .sum();
                avgOrderValue = Math.round((totalSpent / totalOrders) * 100.0) / 100.0;
            }

            double abandonmentRate = totalOrders >= 3 ? 0.10 : (totalOrders == 0 ? 0.25 : 0.18);
            var pred = aiIntegrationService.getLiveChurnPrediction(
                    uid, totalOrders, daysSinceLast, ordersLast30d, avgOrderValue, abandonmentRate);

            double score = (pred != null && pred.getChurnRiskScore() != null) ? pred.getChurnRiskScore() : 0.14;
            String level = (pred != null && pred.getRiskLevel() != null) ? pred.getRiskLevel() : "Low";
            String action = (pred != null && pred.getRecommendedAction() != null) ? pred.getRecommendedAction() : "Standard loyalty points";

            if ("High".equalsIgnoreCase(level)) {
                highRiskCount++;
            }

            java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("userId", uid);
            row.put("customerName", customer.getFullName());
            row.put("customerEmail", customer.getEmail());
            row.put("scorePercent", Math.round(score * 1000.0) / 10.0);
            row.put("riskLevel", level);
            row.put("recommendedAction", action);
            row.put("activeOffer", customer.getActiveRetentionOffer() != null ? customer.getActiveRetentionOffer() : "");
            churnRows.add(row);
        }

        BigDecimal totalRevenue = customerOrders.stream()
                .map(Order::getTotalAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal averageOrderValue = customerOrders.isEmpty()
                ? BigDecimal.ZERO
                : totalRevenue.divide(BigDecimal.valueOf(customerOrders.size()), 0, java.math.RoundingMode.HALF_UP);

        double overallChurnRate = churnRows.isEmpty() ? 0.0 : Math.round((highRiskCount * 1000.0) / churnRows.size()) / 10.0;

        var categoryCounts = products.stream()
                .collect(java.util.stream.Collectors.groupingBy(Product::getCategory, java.util.stream.Collectors.counting()));

        java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("totalRevenue", totalRevenue.toPlainString());
        out.put("averageOrderValue", averageOrderValue.toPlainString());
        out.put("activeUsersCount", customers.size());
        out.put("totalOrdersCount", customerOrders.size());
        out.put("overallChurnRate", overallChurnRate);
        out.put("catalogCount", products.size());
        out.put("categoryLabels", new java.util.ArrayList<>(categoryCounts.keySet()));
        out.put("categoryValues", new java.util.ArrayList<>(categoryCounts.values()));
        out.put("churnRows", churnRows);
        return ResponseEntity.ok(out);
    }

    @GetMapping("/admin/activity")
    public ResponseEntity<?> getLiveStorefrontActivity() {
        Map<Long, String> customerNames = new java.util.HashMap<>();
        for (User u : userRepository.findAll()) {
            if (!"ROLE_ADMIN".equalsIgnoreCase(u.getRole())) {
                customerNames.put(u.getId(), u.getFullName());
            }
        }
        var formatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss");
        java.util.List<java.util.Map<String, Object>> events = clickstreamRepository.findTop20ByOrderByTimestampDesc().stream()
                .filter(log -> log.getUserId() != null && customerNames.containsKey(log.getUserId()))
                .limit(10)
                .map(log -> Map.<String, Object>of(
                        "eventType", log.getEventType() != null ? log.getEventType() : "VIEW",
                        "customerName", customerNames.getOrDefault(log.getUserId(), "Customer"),
                        "productName", log.getProductName() != null ? log.getProductName() : "Dish",
                        "time", log.getTimestamp() != null ? log.getTimestamp().format(formatter) : ""
                ))
                .toList();
        return ResponseEntity.ok(events);
    }

    private List<String> parseIngredients(Object raw) {
        if (raw instanceof List<?> list) {
            return list.stream().map(String::valueOf).map(String::trim).filter(s -> !s.isEmpty()).toList();
        }
        if (raw == null) return List.of();
        return Arrays.stream(String.valueOf(raw).split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private int parseCaloriesValue(Object raw, int fallback) {
        if (raw == null) return fallback;
        String digits = String.valueOf(raw).replaceAll("[^0-9]", "");
        if (digits.isBlank()) return fallback;
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

        @GetMapping("/user/{userId}/profile")
    public ResponseEntity<?> getUserProfile(@PathVariable Long userId) {
        return userRepository.findById(userId).map(u -> {
            java.util.Map<String, Object> data = new java.util.HashMap<>();
            data.put("phone", u.getPhone() != null ? u.getPhone() : "");
            data.put("zone", u.getDeliveryZone() != null ? u.getDeliveryZone() : "");
            data.put("address", u.getDeliveryAddress() != null ? u.getDeliveryAddress() : "");
            data.put("paymentMethod", u.getDefaultPaymentMethod() != null ? u.getDefaultPaymentMethod() : "");
            data.put("mpesaNumber", u.getMpesaNumber() != null ? u.getMpesaNumber() : "");
            data.put("visaLast4", u.getVisaLast4() != null ? u.getVisaLast4() : "");
            return ResponseEntity.ok(data);
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/user/{userId}/profile")
    public ResponseEntity<?> saveUserProfile(@PathVariable Long userId, @RequestBody Map<String, Object> payload) {
        return userRepository.findById(userId).map(u -> {
            if (payload.containsKey("phone")) u.setPhone(String.valueOf(payload.get("phone")).trim());
            if (payload.containsKey("zone")) u.setDeliveryZone(String.valueOf(payload.get("zone")).trim());
            if (payload.containsKey("address")) u.setDeliveryAddress(String.valueOf(payload.get("address")).trim());
            if (payload.containsKey("paymentMethod")) u.setDefaultPaymentMethod(String.valueOf(payload.get("paymentMethod")).trim());
            if (payload.containsKey("mpesaNumber")) u.setMpesaNumber(String.valueOf(payload.get("mpesaNumber")).trim());
            if (payload.containsKey("visaLast4")) u.setVisaLast4(String.valueOf(payload.get("visaLast4")).trim());
            userRepository.save(u);
            return ResponseEntity.ok(Map.of("saved", true));
        }).orElse(ResponseEntity.notFound().build());
    }
private String parseNutritionText(Object raw, String fallback) {
        if (raw == null) return fallback;
        String val = String.valueOf(raw).trim();
        return val.isBlank() ? fallback : val;
    }
}
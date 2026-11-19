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

    public PortalApiController(UserRepository userRepository,
                               ProductRepository productRepository,
                               ClickstreamRepository clickstreamRepository,
                               OrderRepository orderRepository) {
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.clickstreamRepository = clickstreamRepository;
        this.orderRepository = orderRepository;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body, HttpSession session) {
        String email = body.getOrDefault("email", "").trim().toLowerCase();
        String password = body.getOrDefault("password", "");
        return userRepository.findByEmail(email)
                .filter(u -> u.getPasswordHash() != null && u.getPasswordHash().equals(DataSeeder.hashPassword(password)))
                .<ResponseEntity<?>>map(user -> {
                    session.setAttribute("loggedInUser", user);
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
    public ResponseEntity<?> register(@RequestBody Map<String, String> body, HttpSession session) {
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
        return ResponseEntity.ok(Map.of("id", created.getId(), "fullName", created.getFullName(), "role", created.getRole()));
    }

    @PostMapping("/google-login")
    public ResponseEntity<?> googleLogin(@RequestBody Map<String, String> payload, HttpSession session) {
        String email = payload.getOrDefault("email", "trevor@foodie.co.ke").trim().toLowerCase();
        String fullName = payload.getOrDefault("fullName", "Trevor Lamar").trim();
        if (fullName.isEmpty()) {
            fullName = "Trevor Lamar";
        }
        final String resolvedName = fullName;
        User user = userRepository.findByEmail(email).orElseGet(() -> {
            User created = new User(null, resolvedName, email, "GOOGLE_OAUTH_USER", "ROLE_CUSTOMER", null);
            return userRepository.save(created);
        });
        session.setAttribute("LOGGED_IN_USER_ID", user.getId());
        return ResponseEntity.ok(Map.of(
                "id", user.getId(),
                "fullName", user.getFullName(),
                "email", user.getEmail(),
                "role", user.getRole()
        ));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpSession session) {
        session.invalidate();
        return ResponseEntity.ok(Map.of("status", "logged_out"));
    }

    @PostMapping("/clickstream")
    public ResponseEntity<?> logClick(@RequestBody Map<String, Object> payload) {
        Long userId = payload.get("userId") != null ? Long.valueOf(payload.get("userId").toString()) : 1L;
        String productId = String.valueOf(payload.getOrDefault("productId", ""));
        String productName = String.valueOf(payload.getOrDefault("productName", ""));
        String eventType = String.valueOf(payload.getOrDefault("eventType", "VIEW"));

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
        p.setNutritionalInfo(Map.of("calories", 540, "protein", "32g"));
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

    @PostMapping("/admin/retention/{userId}")
    public ResponseEntity<?> triggerRetention(@PathVariable Long userId, @RequestBody Map<String, String> body) {
        String offer = body.getOrDefault("offer", "20% Loyalty Voucher");
        return ResponseEntity.ok(Map.of(
                "status", "DISPATCHED",
                "userId", userId,
                "message", "Retention offer '" + offer + "' sent to Customer #" + userId
        ));
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
}
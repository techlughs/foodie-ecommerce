package com.foodie.ecommerce.config;

import com.foodie.ecommerce.model.Product;
import com.foodie.ecommerce.model.User;
import com.foodie.ecommerce.repository.ClickstreamRepository;
import com.foodie.ecommerce.repository.OrderRepository;
import com.foodie.ecommerce.repository.ProductRepository;
import com.foodie.ecommerce.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final ClickstreamRepository clickstreamRepository;

    public DataSeeder(UserRepository userRepository,
                      ProductRepository productRepository,
                      OrderRepository orderRepository,
                      ClickstreamRepository clickstreamRepository) {
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.clickstreamRepository = clickstreamRepository;
    }

    @Override
    public void run(String... args) {
        List<String> syntheticEmails = List.of(
                "trevor@foodie.co.ke",
                "amina@foodie.co.ke",
                "brian@foodie.co.ke",
                "faith@foodie.co.ke",
                "diner@foodie.co.ke"
        );
        userRepository.findAll().stream()
                .filter(u -> u.getEmail() != null && syntheticEmails.contains(u.getEmail().toLowerCase()))
                .forEach(stale -> {
                    orderRepository.findByUserIdOrderByCreatedAtDesc(stale.getId())
                            .forEach(orderRepository::delete);
                    userRepository.delete(stale);
                });

        userRepository.findByEmail("admin@foodie.co.ke").ifPresentOrElse(
                existing -> {
                    existing.setFullName("Kitchen Admin");
                    existing.setPasswordHash(hashPassword("admin123"));
                    existing.setRole("ROLE_ADMIN");
                    userRepository.save(existing);
                },
                () -> userRepository.save(new User(
                        null, "Kitchen Admin", "admin@foodie.co.ke", hashPassword("admin123"), "ROLE_ADMIN", null
                ))
        );

        // Purge any clickstream logs belonging to deleted/non-existent or admin accounts
        Set<Long> validCustomerIds = userRepository.findAll().stream()
                .filter(u -> !"ROLE_ADMIN".equalsIgnoreCase(u.getRole()))
                .map(User::getId)
                .collect(Collectors.toSet());

        clickstreamRepository.findAll().stream()
                .filter(log -> log.getUserId() == null || !validCustomerIds.contains(log.getUserId()))
                .forEach(clickstreamRepository::delete);

        if (productRepository.count() == 0) {
            productRepository.saveAll(List.of(
                    createDish("Charcoal Nyama Choma Platter", "Main Course",
                            "Slow-roasted prime goat ribs basted with rosemary, garlic, and coarse sea salt, served with fresh kachumbari.",
                            "850.00", "https://images.unsplash.com/photo-1544025162-d76694265947?auto=format&fit=crop&w=800&q=80",
                            List.of("Prime Goat Ribs", "Kachumbari", "Roasted Garlic", "Sea Salt"), 720, "48g"),
                    createDish("Coastal Swahili Beef Pilau", "Main Course",
                            "Fragrant long-grain basmati rice simmered in caramelized onions, cardamom, cloves, cinnamon, and tender beef cubes.",
                            "450.00", "https://images.unsplash.com/photo-1563379091339-03b21ab4a4f8?auto=format&fit=crop&w=800&q=80",
                            List.of("Basmati Rice", "Tender Beef", "Pilau Masala", "Caramelized Onions"), 640, "34g"),
                    createDish("Lake Victoria Fried Tilapia", "Main Course",
                            "Crispy whole golden tilapia seasoned with lemon-herb marinade, paired with rich coconut sukuma wiki.",
                            "780.00", "https://images.unsplash.com/photo-1519708227418-c8fd9a32b7a2?auto=format&fit=crop&w=800&q=80",
                            List.of("Whole Tilapia", "Lemon Herb Glaze", "Coconut Sukuma", "Cherry Tomatoes"), 610, "44g"),
                    createDish("Spiced Beef Samosa Trio", "Sides",
                            "Crisp hand-folded golden pastry parcels filled with cumin-spiced minced beef, spring onions, and fresh coriander.",
                            "250.00", "https://images.unsplash.com/photo-1601050690597-df0568f70950?auto=format&fit=crop&w=800&q=80",
                            List.of("Minced Beef", "Cumin", "Coriander", "Crispy Pastry"), 340, "16g"),
                    createDish("Kenyan Masala Chai Pot", "Beverages",
                            "Kericho black tea leaves brewed with creamy farm milk, crushed green cardamom pods, fresh ginger, and cinnamon.",
                            "220.00", "https://images.unsplash.com/photo-1561336313-0bd5e0b27ec8?auto=format&fit=crop&w=800&q=80",
                            List.of("Kericho Tea", "Green Cardamom", "Fresh Ginger", "Whole Milk"), 180, "6g"),
                    createDish("Cardamom Coconut Mandazi", "Desserts",
                            "Warm pillowy coastal doughnuts infused with freshly grated coconut milk and aromatic cardamom sugar.",
                            "200.00", "https://images.unsplash.com/photo-1551024601-bec78aea704b?auto=format&fit=crop&w=800&q=80",
                            List.of("Coconut Milk", "Cardamom", "Soft Wheat Flour", "Raw Cane Sugar"), 310, "5g")
            ));
        }
    }

    private Product createDish(String name, String category, String description, String price,
                               String imageUrl, List<String> ingredients, int calories, String protein) {
        Product p = new Product();
        p.setName(name);
        p.setCategory(category);
        p.setDescription(description);
        p.setPrice(new BigDecimal(price));
        p.setImageUrl(imageUrl);
        p.setIngredients(ingredients);
        p.setNutritionalInfo(Map.of("calories", calories, "protein", protein));
        return p;
    }

    public static String hashPassword(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("Password hashing failed", e);
        }
    }
}
package com.foodie.ecommerce.config;

import com.foodie.ecommerce.model.Product;
import com.foodie.ecommerce.model.User;
import com.foodie.ecommerce.repository.ProductRepository;
import com.foodie.ecommerce.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class DataSeeder implements CommandLineRunner {

    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public DataSeeder(ProductRepository productRepository, UserRepository userRepository) {
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    @Override
    public void run(String... args) {
        if (userRepository.count() == 0) {
            User sampleCustomer = new User(
                null,
                "Test Customer",
                "customer@foodie.com",
                "hashed_placeholder_password",
                "ROLE_CUSTOMER",
                null
            );
            userRepository.save(sampleCustomer);
            System.out.println("Sample user seeded into MySQL!");
        }

        List<Product> fullCatalog = List.of(
            new Product(null, "Swahili Beef Pilau", "Fragrant basmati rice slow-cooked in a spiced beef broth with caramelized onions",
                new BigDecimal("400.00"), "Main Course", "/images/beef-pilau.jpg",
                List.of("Basmati Rice", "Beef", "Pilau Masala", "Caramelized Onions", "Garlic"),
                Map.of("calories", 650, "protein", "35g", "carbs", "78g", "fat", "28g"), true),
            new Product(null, "Grilled Tilapia & Ugali", "Whole Lake Victoria tilapia marinated in honey lemon and herbs, served with sukuma wiki",
                new BigDecimal("600.00"), "Main Course", "/images/grilled-tilapia.jpg",
                List.of("Tilapia", "Maize Meal", "Sukuma Wiki", "Lemon", "Honey Glaze", "Garlic", "Ginger"),
                Map.of("calories", 760, "protein", "60g", "carbs", "75g", "fat", "19g"), true),
            new Product(null, "Fresh Mango Passion Smoothie", "Chilled blend of ripe Apple mangoes and tangy passion fruit",
                new BigDecimal("250.00"), "Beverages", "/images/mango-smoothie.jpg",
                List.of("Mango", "Passion Fruit", "Greek Yogurt", "Honey"),
                Map.of("calories", 210, "protein", "8g", "carbs", "38g", "fat", "6g"), true),
            new Product(null, "Nyama Choma Platter", "Charcoal-roasted prime beef ribs served with kachumbari and roasted potatoes",
                new BigDecimal("850.00"), "Main Course", "/images/nyama-choma.jpg",
                List.of("Beef Ribs", "Tomatoes", "Red Onion", "Coriander", "Lemon", "Sea Salt"),
                Map.of("calories", 890, "protein", "68g", "carbs", "32g", "fat", "52g"), true),
            new Product(null, "Chicken Biryani", "Aromatic spiced coastal chicken layered with saffron basmati rice and fried onions",
                new BigDecimal("500.00"), "Main Course", "/images/chicken-biryani.jpg",
                List.of("Chicken", "Basmati Rice", "Yogurt", "Saffron", "Biryani Masala", "Mint"),
                Map.of("calories", 710, "protein", "44g", "carbs", "82g", "fat", "22g"), true),
            new Product(null, "Masala Chips", "Crispy double-fried potato chips tossed in tangy tomato-garlic masala sauce",
                new BigDecimal("220.00"), "Sides", "/images/masala-chips.jpg",
                List.of("Potatoes", "Tomato Paste", "Chili", "Garlic", "Coriander", "Lemon"),
                Map.of("calories", 430, "protein", "6g", "carbs", "58g", "fat", "19g"), true),
            new Product(null, "Beef Samosa Trio", "Three golden crispy pastry pockets stuffed with spiced minced beef and herbs",
                new BigDecimal("180.00"), "Sides", "/images/beef-samosa.jpg",
                List.of("Minced Beef", "Wheat Pastry", "Cumin", "Spring Onions", "Coriander"),
                Map.of("calories", 340, "protein", "16g", "carbs", "34g", "fat", "15g"), true),
            new Product(null, "Tamarind Juice (Ukwaju)", "Refreshing chilled coastal tamarind nectar lightly sweetened with raw honey",
                new BigDecimal("150.00"), "Beverages", "/images/ukwaju-juice.jpg",
                List.of("Tamarind Pulp", "Honey", "Ginger", "Filtered Water"),
                Map.of("calories", 130, "protein", "1g", "carbs", "32g", "fat", "0g"), true),
            new Product(null, "Kenyan Masala Chai", "Rich Kenyan black tea brewed with creamy milk, cardamom, ginger, and cinnamon",
                new BigDecimal("120.00"), "Beverages", "/images/masala-chai.jpg",
                List.of("Kenyan Tea Leaves", "Whole Milk", "Cardamom", "Ginger", "Cinnamon"),
                Map.of("calories", 145, "protein", "5g", "carbs", "18g", "fat", "6g"), true),
            new Product(null, "Coconut Mahamri & Dip", "Fluffy cardamom-coconut Swahili doughnuts paired with sweet mango glaze",
                new BigDecimal("200.00"), "Desserts", "/images/mahamri.jpg",
                List.of("Wheat Flour", "Coconut Milk", "Cardamom", "Yeast", "Mango Glaze"),
                Map.of("calories", 390, "protein", "7g", "carbs", "62g", "fat", "12g"), true)
        );

        Set<String> existingNames = productRepository.findAll().stream()
                .map(Product::getName)
                .collect(Collectors.toSet());

        List<Product> missingProducts = fullCatalog.stream()
                .filter(p -> !existingNames.contains(p.getName()))
                .collect(Collectors.toList());

        if (!missingProducts.isEmpty()) {
            productRepository.saveAll(missingProducts);
            System.out.println("Seeded " + missingProducts.size() + " additional menu products into MongoDB Atlas!");
        }
    }
}
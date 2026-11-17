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

        if (productRepository.count() == 0) {
            List<Product> sampleProducts = List.of(
                new Product(
                    null,
                    "Swahili Beef Pilau",
                    "Fragrant basmati rice slow-cooked in a spiced beef broth with caramelized onions",
                    new BigDecimal("400.00"),
                    "Main Course",
                    "/images/beef-pilau.jpg",
                    List.of("Basmatic Rice", "Beef", "Pilau Masala", "Caramelized Onions", "Garlic"),
                    Map.of("calories", 650, "protein", "35g", "carbs", "78g", "fat", "28g"),
                    true
              ),
              new Product(
                null,
                "Grilled Tilapia & Ugali",
                "Whole Lake Victoria tilapia marinated in honey lemon and herbs, served with sukuma wiki",
                new BigDecimal("600.00"),
                "Main Course",
                "/images/grilled-tilapia.jpg",
                List.of("Tilapia", "Maize Meal", "Sukuma Wiki", "Lemon", "Honey Glaze", "Spring Onions", "Garlic", "Ginger", "Coriander"),
                Map.of("calories", 760, "protein", "60g", "carbs", "75g", "fat", "19g"),
                true
              ),
              new Product(
                null,
                "Fresh Mango Passion Smoothie",
                "Chilled blend of ripe Apple mangoes and tangy passion fruit",
                new BigDecimal("250.00"),
                "Beverages",
                "/images/mango-smoothie.jpg",
                List.of("Mango", "Passion Fruit", "Greek Yogurt", "Honey"),
                Map.of("calories", 210, "protein", "8g", "fat", "6g"),
                true
              )
            );
            productRepository.saveAll(sampleProducts);
            System.out.println("Sample catalog products seeded into MongoDB Atlas!");
        }
    }
}
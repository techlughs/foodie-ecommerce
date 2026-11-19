package com.foodie.ecommerce.controller;

import com.foodie.ecommerce.dto.ChurnPredictionResponse;
import com.foodie.ecommerce.dto.RecommendationResponse;
import com.foodie.ecommerce.model.Order;
import com.foodie.ecommerce.model.Product;
import com.foodie.ecommerce.model.User;
import com.foodie.ecommerce.repository.ClickstreamRepository;
import com.foodie.ecommerce.repository.OrderRepository;
import com.foodie.ecommerce.repository.ProductRepository;
import com.foodie.ecommerce.repository.UserRepository;
import com.foodie.ecommerce.service.AiIntegrationService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
public class StorefrontController {

    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final ClickstreamRepository clickstreamRepository;
    private final AiIntegrationService aiIntegrationService;

    public StorefrontController(ProductRepository productRepository,
                                UserRepository userRepository,
                                OrderRepository orderRepository,
                                ClickstreamRepository clickstreamRepository,
                                AiIntegrationService aiIntegrationService) {
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.orderRepository = orderRepository;
        this.clickstreamRepository = clickstreamRepository;
        this.aiIntegrationService = aiIntegrationService;
    }

    @GetMapping("/")
    public String welcome() {
        return "welcome";
    }

    @GetMapping("/store")
    public String home(@RequestParam(required = false) Long userId, HttpSession session, Model model) {
        User loggedIn = (User) session.getAttribute("loggedInUser");
        Long activeId = (loggedIn != null) ? loggedIn.getId() : (userId != null ? userId : 0L);

        List<Product> allProducts = productRepository.findAll();
        List<User> allUsers = userRepository.findAll();
        User activeUser = (activeId > 0L)
                ? userRepository.findById(activeId).orElse(new User(activeId, "Guest Foodie", "guest@foodie.co.ke", "", "ROLE_CUSTOMER", null))
                : new User(0L, "Guest Foodie", "guest@foodie.co.ke", "", "ROLE_CUSTOMER", null);

        RecommendationResponse aiRecs = aiIntegrationService.getRecommendations(activeId > 0L ? activeId : 1L, 3);
        Set<String> recNames = (aiRecs != null && aiRecs.getRecommendedItems() != null)
                ? Set.copyOf(aiRecs.getRecommendedItems())
                : Set.of();

        List<Product> recommendedProducts = allProducts.stream()
                .filter(p -> recNames.contains(p.getName()))
                .collect(Collectors.toList());

        if (recommendedProducts.isEmpty()) {
            recommendedProducts = allProducts.stream().limit(3).collect(Collectors.toList());
        }

        List<String> categories = allProducts.stream()
                .map(Product::getCategory)
                .distinct()
                .collect(Collectors.toList());

        List<Order> userOrders = (activeId > 0L) ? orderRepository.findByUserIdOrderByCreatedAtDesc(activeId) : List.of();
        long clickCount = (activeId > 0L) ? clickstreamRepository.countByUserId(activeId) : 0L;

        model.addAttribute("userId", activeId);
        model.addAttribute("activeUser", activeUser);
        model.addAttribute("loggedInUser", loggedIn);
        model.addAttribute("allUsers", allUsers);
        model.addAttribute("products", allProducts);
        model.addAttribute("recommendedProducts", recommendedProducts);
        model.addAttribute("categories", categories);
        model.addAttribute("orderCount", userOrders.size());
        model.addAttribute("clickCount", clickCount);
        return "index";
    }

    @GetMapping("/admin/churn")
    public String adminPortal(HttpSession session, Model model) {
        User loggedIn = (User) session.getAttribute("loggedInUser");
        if (loggedIn == null || !"ROLE_ADMIN".equalsIgnoreCase(loggedIn.getRole())) {
            return "redirect:/store?adminLogin=1";
        }
        List<User> users = userRepository.findAll();
        List<Product> products = productRepository.findAll();
        List<Order> orders = orderRepository.findAll();

        var churnRows = new ArrayList<>();
        long highRiskCount = 0;

        long maxId = Math.max(5, users.size());
        for (long uid = 1; uid <= maxId; uid++) {
            ChurnPredictionResponse pred = aiIntegrationService.getChurnPrediction(uid);
            final long lookupId = uid;
            String customerName = users.stream()
                    .filter(u -> u.getId().equals(lookupId))
                    .map(User::getFullName)
                    .findFirst()
                    .orElse("Customer #" + uid);

            double score = (pred != null && pred.getChurnRiskScore() != null) ? pred.getChurnRiskScore() : 0.15;
            String level = (pred != null && pred.getRiskLevel() != null) ? pred.getRiskLevel() : "Low";
            String action = (pred != null && pred.getRecommendedAction() != null) ? pred.getRecommendedAction() : "Standard loyalty points";

            if ("High".equalsIgnoreCase(level)) {
                highRiskCount++;
            }

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("userId", uid);
            row.put("customerName", customerName);
            row.put("scorePercent", Math.round(score * 1000.0) / 10.0);
            row.put("riskLevel", level);
            row.put("recommendedAction", action);
            churnRows.add(row);
        }

        BigDecimal totalRevenue = orders.stream()
                .map(Order::getTotalAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        double overallChurnRate = churnRows.isEmpty() ? 0.0 : Math.round((highRiskCount * 1000.0) / churnRows.size()) / 10.0;

        Map<String, Long> categoryCounts = products.stream()
                .collect(Collectors.groupingBy(Product::getCategory, Collectors.counting()));

        model.addAttribute("churnRows", churnRows);
        model.addAttribute("products", products);
        model.addAttribute("totalRevenue", totalRevenue);
        model.addAttribute("activeUsersCount", Math.max(users.size(), 5));
        model.addAttribute("totalOrdersCount", orders.size());
        model.addAttribute("overallChurnRate", overallChurnRate);
        model.addAttribute("categoryLabels", new ArrayList<>(categoryCounts.keySet()));
        model.addAttribute("categoryValues", new ArrayList<>(categoryCounts.values()));
        model.addAttribute("recentLogs", clickstreamRepository.findTop20ByOrderByTimestampDesc());
        return "churn-dashboard";
    }
}
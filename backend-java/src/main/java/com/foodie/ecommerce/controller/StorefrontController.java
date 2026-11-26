package com.foodie.ecommerce.controller;

import com.foodie.ecommerce.dto.ChurnPredictionResponse;
import com.foodie.ecommerce.dto.RecommendationResponse;
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
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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

    private User resolveCurrentUser(HttpSession session, HttpServletRequest request) {
        Object attr = session.getAttribute("loggedInUser");
        if (attr instanceof User u) {
            return userRepository.findById(u.getId()).orElse(u);
        }
        if (request.getCookies() != null) {
            for (Cookie c : request.getCookies()) {
                if ("FOODIE_UID".equals(c.getName()) && c.getValue() != null && !c.getValue().isBlank()) {
                    try {
                        Long uid = Long.parseLong(c.getValue().trim());
                        User found = userRepository.findById(uid).orElse(null);
                        if (found != null) {
                            session.setAttribute("loggedInUser", found);
                            return found;
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        return null;
    }

    @GetMapping("/")
    public String welcome() {
        return "redirect:/store";
    }

    @GetMapping("/logout")
    public String rootLogout(jakarta.servlet.http.HttpSession session, jakarta.servlet.http.HttpServletResponse response) {
        session.invalidate();
        jakarta.servlet.http.Cookie cookie = new jakarta.servlet.http.Cookie("FOODIE_UID", "");
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
        return "redirect:/store";
    }

    @GetMapping("/store")
    public String home(@RequestParam(required = false) Long userId,
                       HttpSession session,
                       HttpServletRequest request,
                       Model model) {
        User loggedIn = resolveCurrentUser(session, request);
        if (loggedIn != null && "ROLE_ADMIN".equalsIgnoreCase(loggedIn.getRole())) {
            return "redirect:/admin/churn";
        }
        Long activeId = (loggedIn != null) ? loggedIn.getId() : (userId != null ? userId : 0L);

        List<Product> allProducts = productRepository.findAll();
        List<User> allUsers = userRepository.findAll();
        User activeUser = (activeId > 0L)
                ? userRepository.findById(activeId).orElse(new User(activeId, "Guest Foodie", "guest@foodie.co.ke", "", "ROLE_CUSTOMER", null, null))
                : new User(0L, "Guest Foodie", "guest@foodie.co.ke", "", "ROLE_CUSTOMER", null, null);

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
        model.addAttribute("activeRetentionOffer", activeUser.getActiveRetentionOffer() != null ? activeUser.getActiveRetentionOffer() : "");
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
    public String adminPortal(HttpSession session, HttpServletRequest request, Model model) {
        User loggedIn = resolveCurrentUser(session, request);
        if (loggedIn == null || !"ROLE_ADMIN".equalsIgnoreCase(loggedIn.getRole())) {
            return "redirect:/store?adminLogin=1";
        }

        List<User> allUsers = userRepository.findAll();
        Set<Long> adminIds = allUsers.stream()
                .filter(u -> "ROLE_ADMIN".equalsIgnoreCase(u.getRole()))
                .map(User::getId)
                .collect(Collectors.toSet());

        List<User> customers = allUsers.stream()
                .filter(u -> !"ROLE_ADMIN".equalsIgnoreCase(u.getRole()))
                .collect(Collectors.toList());

        List<Product> products = productRepository.findAll();

        List<Order> customerOrders = orderRepository.findAll().stream()
                .filter(o -> o.getUserId() != null && !adminIds.contains(o.getUserId()))
                .sorted((a, b) -> Long.compare(b.getId(), a.getId()))
                .collect(Collectors.toList());

        var churnRows = new ArrayList();
        long highRiskCount = 0;

        for (User customer : customers) {
            Long uid = customer.getId();
            ChurnPredictionResponse pred = aiIntegrationService.getChurnPrediction(uid);

            double score = (pred != null && pred.getChurnRiskScore() != null) ? pred.getChurnRiskScore() : 0.14;
            String level = (pred != null && pred.getRiskLevel() != null) ? pred.getRiskLevel() : "Low";
            String action = (pred != null && pred.getRecommendedAction() != null) ? pred.getRecommendedAction() : "Standard loyalty points";

            if ("High".equalsIgnoreCase(level)) {
                highRiskCount++;
            }

            var row = new LinkedHashMap();
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
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal averageOrderValue = customerOrders.isEmpty()
                ? BigDecimal.ZERO
                : totalRevenue.divide(BigDecimal.valueOf(customerOrders.size()), 0, RoundingMode.HALF_UP);

        double overallChurnRate = churnRows.isEmpty() ? 0.0 : Math.round((highRiskCount * 1000.0) / churnRows.size()) / 10.0;

        var categoryCounts = products.stream()
                .collect(Collectors.groupingBy(Product::getCategory, Collectors.counting()));

        var userNames = new LinkedHashMap();
        for (User u : customers) {
            userNames.put(u.getId(), u.getFullName());
        }

        var dishNames = new LinkedHashMap();
        for (Product p : products) {
            dishNames.put(p.getId(), p.getName());
        }

        List<ClickstreamLog> customerLogs = clickstreamRepository.findTop20ByOrderByTimestampDesc().stream()
                .filter(log -> log.getUserId() != null && userNames.containsKey(log.getUserId()))
                .limit(10)
                .collect(Collectors.toList());

        model.addAttribute("churnRows", churnRows);
        model.addAttribute("products", products);
        model.addAttribute("totalRevenue", totalRevenue);
        model.addAttribute("averageOrderValue", averageOrderValue);
        model.addAttribute("activeUsersCount", customers.size());
        model.addAttribute("totalOrdersCount", customerOrders.size());
        model.addAttribute("overallChurnRate", overallChurnRate);
        model.addAttribute("mlOnline", aiIntegrationService.isMlServiceOnline());
        model.addAttribute("categoryLabels", new ArrayList(categoryCounts.keySet()));
        model.addAttribute("categoryValues", new ArrayList(categoryCounts.values()));
        model.addAttribute("allOrders", customerOrders);
        model.addAttribute("userNames", userNames);
        model.addAttribute("dishNames", dishNames);
        model.addAttribute("recentLogs", customerLogs);
        return "admin";
    }
}
package com.foodie.ecommerce.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    public static Path resolveUploadDir() {
        Path projectRootUploads = Paths.get("C:", "Users", "User", "foodie-ecommerce", "uploads");
        if (projectRootUploads.getParent() != null && projectRootUploads.getParent().toFile().exists()) {
            return projectRootUploads;
        }
        return Paths.get("uploads").toAbsolutePath().normalize();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = resolveUploadDir().toUri().toString();
        if (!location.endsWith("/")) {
            location += "/";
        }
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(location, "file:uploads/", "file:../uploads/");
    }
}
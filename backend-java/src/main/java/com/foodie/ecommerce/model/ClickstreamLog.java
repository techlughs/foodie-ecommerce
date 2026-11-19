package com.foodie.ecommerce.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "clickstream_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClickstreamLog {
    @Id
    private String id;
    private Long userId;
    private String productId;
    private String productName;
    private String eventType;
    private LocalDateTime timestamp = LocalDateTime.now();
}
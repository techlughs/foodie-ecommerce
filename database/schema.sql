CREATE DATABASE IF NOT EXISTS foodecommerce_db;
USE foodecommerce_db;

CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    full_name VARCHAR(100) NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) DEFAULT 'ROLE_CUSTOMER',
    active_retention_offer VARCHAR(255),
    phone VARCHAR(40),
    delivery_zone VARCHAR(120),
    delivery_address VARCHAR(255),
    default_payment_method VARCHAR(60),
    mpesa_number VARCHAR(40),
    visa_last4 VARCHAR(10),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    total_amount DECIMAL(10, 2) NOT NULL,
    status VARCHAR(45) DEFAULT 'AWAITING_KITCHEN',
    delivery_zone VARCHAR(100),
    delivery_address VARCHAR(255),
    kitchen_notes VARCHAR(255),
    rider_instructions VARCHAR(255),
    admin_arrived_confirmed BOOLEAN DEFAULT FALSE,
    customer_received_confirmed BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    delivered_at TIMESTAMP NULL,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS order_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    product_id VARCHAR(50) NOT NULL,
    quantity INT NOT NULL,
    price DECIMAL(10, 2) NOT NULL,
    FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE
);

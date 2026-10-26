-- =====================================================================
-- ViaMe: Intelligent Multi-Role Urban Transport & Taxi Booking System
-- Phase 3 Deliverable: Physical Relational Database Schema (3NF) & Seed Data
-- Target DBMS: MySQL 8.0+ | Engine: InnoDB | Charset: utf8mb4
-- Course: Java Programming II | Institut Saint Jean (ISJ)
-- =====================================================================

DROP DATABASE IF EXISTS viame_db;
CREATE DATABASE viame_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE viame_db;

-- ---------------------------------------------------------------------
-- 1. BASE USERS TABLE (Core Identity & RBAC Authentication)
-- ---------------------------------------------------------------------
CREATE TABLE user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(150) NOT NULL UNIQUE,
    phone VARCHAR(20) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    password_salt VARCHAR(64) NOT NULL,
    role ENUM('PASSENGER', 'DRIVER', 'ADMIN') NOT NULL,
    status ENUM('ACTIVE', 'PENDING', 'SUSPENDED') NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_email (email),
    INDEX idx_user_role (role)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- 2. PASSENGER PROFILES TABLE (1-to-1 Subtype of user)
-- ---------------------------------------------------------------------
CREATE TABLE passenger_profile (
    user_id BIGINT PRIMARY KEY,
    full_name VARCHAR(120) NOT NULL,
    default_payment_method VARCHAR(20) DEFAULT 'CASH',
    wallet_balance DECIMAL(10, 2) NOT NULL DEFAULT 0.00 CHECK (wallet_balance >= 0.00),
    CONSTRAINT fk_passenger_user FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- 3. DRIVER PROFILES TABLE (1-to-1 Subtype of user)
-- ---------------------------------------------------------------------
CREATE TABLE driver_profile (
    user_id BIGINT PRIMARY KEY,
    full_name VARCHAR(120) NOT NULL,
    licence_number VARCHAR(40) NOT NULL UNIQUE,
    verification_status ENUM('PENDING', 'APPROVED', 'REJECTED') NOT NULL DEFAULT 'PENDING',
    rating_avg DECIMAL(3, 2) DEFAULT 5.00 CHECK (rating_avg BETWEEN 1.00 AND 5.00),
    availability ENUM('ONLINE', 'OFFLINE', 'ON_TRIP') NOT NULL DEFAULT 'OFFLINE',
    current_lat DECIMAL(9, 6) DEFAULT 4.051056,
    current_lng DECIMAL(9, 6) DEFAULT 9.767869,
    total_earnings DECIMAL(12, 2) NOT NULL DEFAULT 0.00 CHECK (total_earnings >= 0.00),
    CONSTRAINT fk_driver_user FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE,
    INDEX idx_driver_availability (availability)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- 4. VEHICLES TABLE (Vehicles owned/driven by drivers)
-- ---------------------------------------------------------------------
CREATE TABLE vehicle (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    driver_id BIGINT NOT NULL UNIQUE,
    plate_number VARCHAR(20) NOT NULL UNIQUE,
    make VARCHAR(40) NOT NULL,
    model VARCHAR(40) NOT NULL,
    seats INT NOT NULL CHECK (seats BETWEEN 1 AND 15),
    status ENUM('PENDING', 'ACTIVE', 'INACTIVE') NOT NULL DEFAULT 'PENDING',
    CONSTRAINT fk_vehicle_driver FOREIGN KEY (driver_id) REFERENCES driver_profile(user_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- 5. PRICING RULES TABLE (Dynamic Tariff & Surge Pricing Engine)
-- ---------------------------------------------------------------------
CREATE TABLE pricing_rule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(60) NOT NULL,
    base_fare DECIMAL(10, 2) NOT NULL CHECK (base_fare >= 0.00),
    per_km DECIMAL(10, 2) NOT NULL CHECK (per_km >= 0.00),
    per_min DECIMAL(10, 2) NOT NULL CHECK (per_min >= 0.00),
    surge_multiplier DECIMAL(4, 2) NOT NULL DEFAULT 1.00 CHECK (surge_multiplier BETWEEN 1.00 AND 3.00),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    valid_from TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- 6. RIDES TABLE (Core Lifecycle Entity with Optimistic Concurrency Control)
-- ---------------------------------------------------------------------
CREATE TABLE ride (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    passenger_id BIGINT NOT NULL,
    driver_id BIGINT DEFAULT NULL,
    vehicle_id BIGINT DEFAULT NULL,
    pricing_rule_id BIGINT NOT NULL,
    pickup_lat DECIMAL(9, 6) NOT NULL,
    pickup_lng DECIMAL(9, 6) NOT NULL,
    pickup_address VARCHAR(255) NOT NULL,
    dropoff_lat DECIMAL(9, 6) NOT NULL,
    dropoff_lng DECIMAL(9, 6) NOT NULL,
    dropoff_address VARCHAR(255) NOT NULL,
    distance_km DECIMAL(6, 2) NOT NULL CHECK (distance_km > 0.00),
    duration_min INT NOT NULL CHECK (duration_min >= 0),
    status ENUM('REQUESTED', 'ASSIGNED', 'DRIVER_EN_ROUTE', 'ARRIVED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED', 'NO_DRIVER_FOUND') NOT NULL DEFAULT 'REQUESTED',
    requested_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    accepted_at TIMESTAMP NULL DEFAULT NULL,
    started_at TIMESTAMP NULL DEFAULT NULL,
    completed_at TIMESTAMP NULL DEFAULT NULL,
    cancelled_at TIMESTAMP NULL DEFAULT NULL,
    version INT NOT NULL DEFAULT 1, -- Optimistic concurrency lock
    CONSTRAINT fk_ride_passenger FOREIGN KEY (passenger_id) REFERENCES passenger_profile(user_id),
    CONSTRAINT fk_ride_driver FOREIGN KEY (driver_id) REFERENCES driver_profile(user_id),
    CONSTRAINT fk_ride_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicle(id),
    CONSTRAINT fk_ride_pricing FOREIGN KEY (pricing_rule_id) REFERENCES pricing_rule(id),
    INDEX idx_ride_status (status),
    INDEX idx_ride_requested_at (requested_at)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- 7. FARES TABLE (Immutable Financial Breakdown per Ride)
-- ---------------------------------------------------------------------
CREATE TABLE fare (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ride_id BIGINT NOT NULL UNIQUE,
    base_fare DECIMAL(10, 2) NOT NULL CHECK (base_fare >= 0.00),
    distance_component DECIMAL(10, 2) NOT NULL CHECK (distance_component >= 0.00),
    time_component DECIMAL(10, 2) NOT NULL CHECK (time_component >= 0.00),
    surge_multiplier DECIMAL(4, 2) NOT NULL CHECK (surge_multiplier >= 1.00),
    total_amount DECIMAL(10, 2) NOT NULL CHECK (total_amount >= 0.00),
    currency VARCHAR(3) NOT NULL DEFAULT 'XAF',
    CONSTRAINT fk_fare_ride FOREIGN KEY (ride_id) REFERENCES ride(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- 8. PAYMENTS TABLE (Financial Transactions & Settlement Audit)
-- ---------------------------------------------------------------------
CREATE TABLE payment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ride_id BIGINT NOT NULL,
    amount DECIMAL(10, 2) NOT NULL CHECK (amount >= 0.00),
    method ENUM('CARD', 'MOBILE_MONEY', 'CASH', 'WALLET') NOT NULL,
    status ENUM('PENDING', 'PAID', 'FAILED', 'REFUNDED') NOT NULL DEFAULT 'PENDING',
    transaction_ref VARCHAR(80) UNIQUE,
    paid_at TIMESTAMP NULL DEFAULT NULL,
    CONSTRAINT fk_payment_ride FOREIGN KEY (ride_id) REFERENCES ride(id) ON DELETE CASCADE,
    INDEX idx_payment_status (status)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- 9. RATINGS TABLE (Two-way Reputation System)
-- ---------------------------------------------------------------------
CREATE TABLE rating (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ride_id BIGINT NOT NULL,
    rater_id BIGINT NOT NULL,
    ratee_id BIGINT NOT NULL,
    score TINYINT NOT NULL CHECK (score BETWEEN 1 AND 5),
    comment VARCHAR(500) DEFAULT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_rating_ride FOREIGN KEY (ride_id) REFERENCES ride(id) ON DELETE CASCADE,
    CONSTRAINT fk_rating_rater FOREIGN KEY (rater_id) REFERENCES user(id),
    CONSTRAINT fk_rating_ratee FOREIGN KEY (ratee_id) REFERENCES user(id)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- 10. SESSIONS TABLE (Token-based Session State Management)
-- ---------------------------------------------------------------------
CREATE TABLE session (
    token_hash VARCHAR(64) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    ip_address VARCHAR(45) DEFAULT NULL,
    CONSTRAINT fk_session_user FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- 11. AUDIT LOGS TABLE (Regulatory & Security Event Trail)
-- ---------------------------------------------------------------------
CREATE TABLE audit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT DEFAULT NULL,
    action VARCHAR(60) NOT NULL,
    entity VARCHAR(40) DEFAULT NULL,
    entity_id BIGINT DEFAULT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_audit_user FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE SET NULL
) ENGINE=InnoDB;

-- =====================================================================
-- DATABASE TRIGGERS
-- =====================================================================

-- Trigger: Automatically update driver's average rating when a new rating is submitted
DELIMITER //
CREATE TRIGGER trg_after_rating_insert
AFTER INSERT ON rating
FOR EACH ROW
BEGIN
    DECLARE avg_score DECIMAL(3, 2);
    
    -- Check if ratee is a driver
    IF EXISTS (SELECT 1 FROM driver_profile WHERE user_id = NEW.ratee_id) THEN
        SELECT AVG(score) INTO avg_score
        FROM rating
        WHERE ratee_id = NEW.ratee_id;
        
        UPDATE driver_profile
        SET rating_avg = ROUND(avg_score, 2)
        WHERE user_id = NEW.ratee_id;
    END IF;
END //
DELIMITER ;

-- =====================================================================
-- SEED DATA FOR TESTING, DEMONSTRATION & DEFENSE
-- =====================================================================

-- 1. Insert Base Users (BCrypt hashes for password: 'Password123!')
-- BCrypt sample hash: $2a$12$7kP.WjA8Zg7oK0nE7xKxUeL3K3h7RkX9P6uL0p8nF3qK4eL2mJ5aO
INSERT INTO user (id, email, phone, password_hash, password_salt, role, status) VALUES
(1, 'admin@viame.cm', '+237670000001', '$2a$12$e8Y6lO7yM9R2K8X8kM3sve6tM1U1jL8qF2oK7eL2mJ5aO8nF3qK4e', 'salt_adm_01', 'ADMIN', 'ACTIVE'),
(2, 'driver.samuel@viame.cm', '+237670000002', '$2a$12$e8Y6lO7yM9R2K8X8kM3sve6tM1U1jL8qF2oK7eL2mJ5aO8nF3qK4e', 'salt_drv_02', 'DRIVER', 'ACTIVE'),
(3, 'driver.eric@viame.cm', '+237670000003', '$2a$12$e8Y6lO7yM9R2K8X8kM3sve6tM1U1jL8qF2oK7eL2mJ5aO8nF3qK4e', 'salt_drv_03', 'DRIVER', 'ACTIVE'),
(4, 'passenger.alice@viame.cm', '+237670000004', '$2a$12$e8Y6lO7yM9R2K8X8kM3sve6tM1U1jL8qF2oK7eL2mJ5aO8nF3qK4e', 'salt_pas_04', 'PASSENGER', 'ACTIVE'),
(5, 'passenger.bob@viame.cm', '+237670000005', '$2a$12$e8Y6lO7yM9R2K8X8kM3sve6tM1U1jL8qF2oK7eL2mJ5aO8nF3qK4e', 'salt_pas_05', 'PASSENGER', 'ACTIVE');

-- 2. Insert Passenger Profiles
INSERT INTO passenger_profile (user_id, full_name, default_payment_method, wallet_balance) VALUES
(4, 'Alice Mengue', 'WALLET', 25000.00),
(5, 'Bob Nkwenti', 'CASH', 5000.00);

-- 3. Insert Driver Profiles
INSERT INTO driver_profile (user_id, full_name, licence_number, verification_status, rating_avg, availability, current_lat, current_lng, total_earnings) VALUES
(2, 'Samuel Eto''o', 'CMR-LT-2023-8891', 'APPROVED', 4.85, 'ONLINE', 4.051056, 9.767869, 142000.00),
(3, 'Eric Kamdem', 'CMR-CE-2024-4412', 'APPROVED', 4.60, 'ONLINE', 4.059200, 9.712100, 89500.00);

-- 4. Insert Vehicles
INSERT INTO vehicle (id, driver_id, plate_number, make, model, seats, status) VALUES
(1, 2, 'LT 452 AB', 'Toyota', 'Yaris', 4, 'ACTIVE'),
(2, 3, 'CE 918 CZ', 'Hyundai', 'Elantra', 4, 'ACTIVE');

-- 5. Insert Pricing Rules
INSERT INTO pricing_rule (id, name, base_fare, per_km, per_min, surge_multiplier, active, valid_from) VALUES
(1, 'Standard Urban Day Tariff', 500.00, 200.00, 25.00, 1.00, TRUE, '2026-01-01 00:00:00'),
(2, 'Peak Hours Evening Tariff', 700.00, 250.00, 35.00, 1.40, FALSE, '2026-01-01 17:00:00');

-- 6. Insert Historical and Active Sample Rides
INSERT INTO ride (id, passenger_id, driver_id, vehicle_id, pricing_rule_id, pickup_lat, pickup_lng, pickup_address, dropoff_lat, dropoff_lng, dropoff_address, distance_km, duration_min, status, requested_at, accepted_at, started_at, completed_at, version) VALUES
(1, 4, 2, 1, 1, 4.051056, 9.767869, 'Akwa Palace Hotel, Douala', 4.075833, 9.718611, 'Bonamoussadi Market, Douala', 8.20, 22, 'COMPLETED', '2026-10-25 08:30:00', '2026-10-25 08:31:15', '2026-10-25 08:36:00', '2026-10-25 08:58:00', 4),
(2, 5, 3, 2, 1, 4.043500, 9.691200, 'Bonanjo Administrative Zone', 4.092100, 9.735400, 'Makepe Saint Tropez', 11.50, 31, 'COMPLETED', '2026-10-25 14:10:00', '2026-10-25 14:11:00', '2026-10-25 14:18:00', '2026-10-25 14:49:00', 4);

-- 7. Insert Fares
INSERT INTO fare (id, ride_id, base_fare, distance_component, time_component, surge_multiplier, total_amount, currency) VALUES
(1, 1, 500.00, 1640.00, 550.00, 1.00, 2690.00, 'XAF'),
(2, 2, 500.00, 2300.00, 775.00, 1.00, 3575.00, 'XAF');

-- 8. Insert Payments
INSERT INTO payment (id, ride_id, amount, method, status, transaction_ref, paid_at) VALUES
(1, 1, 2690.00, 'WALLET', 'PAID', 'TXN-20261025-001', '2026-10-25 08:58:15'),
(2, 2, 3575.00, 'CASH', 'PAID', 'TXN-20261025-002', '2026-10-25 14:49:30');

-- 9. Insert Ratings
INSERT INTO rating (id, ride_id, rater_id, ratee_id, score, comment, created_at) VALUES
(1, 1, 4, 2, 5, 'Smooth driving, clean car and very polite driver!', '2026-10-25 09:00:00'),
(2, 2, 5, 3, 4, 'Quick pickup and avoided traffic bottlenecks.', '2026-10-25 14:52:00');

-- 10. Insert Audit Logs
INSERT INTO audit_log (id, user_id, action, entity, entity_id, created_at) VALUES
(1, 1, 'SYSTEM_INIT', 'DATABASE', NULL, '2026-10-25 00:00:00'),
(2, 1, 'PRICING_RULE_CREATE', 'pricing_rule', 1, '2026-10-25 00:05:00');

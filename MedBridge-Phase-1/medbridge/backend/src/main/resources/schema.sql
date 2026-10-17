-- Phase 1: schema initialization is idempotent. MySQL 8.0+ / InnoDB.
CREATE TABLE IF NOT EXISTS users (
    user_id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(120) NOT NULL,
    email VARCHAR(190) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role ENUM('DONOR','NGO','ADMIN') NOT NULL,
    latitude DECIMAL(10,7) NULL,
    longitude DECIMAL(10,7) NULL,
    verified BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id),
    UNIQUE KEY uq_users_email (email),
    KEY idx_users_role_verified (role, verified),
    CONSTRAINT chk_latitude CHECK (latitude IS NULL OR latitude BETWEEN -90 AND 90),
    CONSTRAINT chk_longitude CHECK (longitude IS NULL OR longitude BETWEEN -180 AND 180)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ngo_profiles (
    ngo_id BIGINT NOT NULL,
    org_name VARCHAR(200) NOT NULL,
    license_no VARCHAR(100) NOT NULL,
    service_area VARCHAR(200) NOT NULL,
    PRIMARY KEY (ngo_id),
    UNIQUE KEY uq_ngo_license (license_no),
    CONSTRAINT fk_ngo_user FOREIGN KEY (ngo_id) REFERENCES users(user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS medicines (
    medicine_id BIGINT NOT NULL AUTO_INCREMENT,
    donor_id BIGINT NOT NULL,
    name VARCHAR(200) NOT NULL,
    batch_no VARCHAR(100) NOT NULL,
    expiry_date DATE NOT NULL,
    quantity INT NOT NULL,
    category VARCHAR(100) NOT NULL,
    status ENUM('LISTED','FLAGGED','APPROVED','MATCHED','REQUESTED','COMPLETED','REJECTED') NOT NULL DEFAULT 'LISTED',
    image_url VARCHAR(500) NULL,
    estimated_value DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (medicine_id),
    KEY idx_medicines_donor (donor_id),
    KEY idx_medicines_status_expiry (status, expiry_date),
    CONSTRAINT fk_medicine_donor FOREIGN KEY (donor_id) REFERENCES users(user_id),
    CONSTRAINT chk_medicine_quantity CHECK (quantity > 0),
    CONSTRAINT chk_medicine_value CHECK (estimated_value >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS requests (
    request_id BIGINT NOT NULL AUTO_INCREMENT,
    medicine_id BIGINT NOT NULL,
    receiver_id BIGINT NOT NULL,
    status ENUM('PENDING','APPROVED','DECLINED','COLLECTED','CANCELLED') NOT NULL DEFAULT 'PENDING',
    requested_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    approved_at TIMESTAMP NULL DEFAULT NULL,
    PRIMARY KEY (request_id),
    UNIQUE KEY uq_request_medicine_receiver (medicine_id, receiver_id),
    KEY idx_requests_receiver_status (receiver_id, status),
    CONSTRAINT fk_request_medicine FOREIGN KEY (medicine_id) REFERENCES medicines(medicine_id),
    CONSTRAINT fk_request_receiver FOREIGN KEY (receiver_id) REFERENCES users(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS notifications (
    notif_id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    type VARCHAR(60) NOT NULL,
    message VARCHAR(500) NOT NULL,
    sent_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `read` BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (notif_id),
    KEY idx_notifications_user_read (user_id, `read`, sent_at),
    CONSTRAINT fk_notification_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

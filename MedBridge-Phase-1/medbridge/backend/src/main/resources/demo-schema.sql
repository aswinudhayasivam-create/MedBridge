-- Local-only additive migration. Loaded only after the unchanged local-auth guard.
CREATE TABLE IF NOT EXISTS demo_medicine_details (
 medicine_id BIGINT PRIMARY KEY, unit VARCHAR(30) NOT NULL, medicine_form VARCHAR(50) NOT NULL,
 storage_note VARCHAR(300) NOT NULL, sealed BOOLEAN NOT NULL, declared BOOLEAN NOT NULL,
 photo_confirmed BOOLEAN NOT NULL, photo_id BIGINT NULL, reserved INT NOT NULL DEFAULT 0,
 review_note VARCHAR(300) NULL, reviewed_at TIMESTAMP NULL,
 FOREIGN KEY (medicine_id) REFERENCES medicines(medicine_id) ON DELETE CASCADE
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS demo_photos (
 photo_id BIGINT PRIMARY KEY AUTO_INCREMENT, donor_id BIGINT NOT NULL, image_bytes MEDIUMBLOB NOT NULL,
 mime VARCHAR(30) NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY (donor_id) REFERENCES users(user_id)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS demo_request_details (
 request_id BIGINT PRIMARY KEY, quantity INT NOT NULL, note VARCHAR(500) NOT NULL,
 scheduled_for DATETIME NULL, collection_note VARCHAR(200) NULL,
 code_hash VARCHAR(100) NULL, code_cipher VARCHAR(300) NULL, code_expires DATETIME NULL,
 code_attempts INT NOT NULL DEFAULT 0, scheduled_at TIMESTAMP NULL, completed_at TIMESTAMP NULL,
 FOREIGN KEY (request_id) REFERENCES requests(request_id) ON DELETE CASCADE
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS demo_events (
 event_id BIGINT PRIMARY KEY AUTO_INCREMENT, actor_id BIGINT NOT NULL,
 recipient_id BIGINT NULL, medicine_id BIGINT NULL, request_id BIGINT NULL,
 event_type VARCHAR(50) NOT NULL, message VARCHAR(500) NOT NULL,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY (actor_id) REFERENCES users(user_id), FOREIGN KEY (recipient_id) REFERENCES users(user_id)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS demo_ngo_applications (
 ngo_id BIGINT PRIMARY KEY, status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
 applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, reviewed_at TIMESTAMP NULL,
 FOREIGN KEY (ngo_id) REFERENCES users(user_id)
) ENGINE=InnoDB;

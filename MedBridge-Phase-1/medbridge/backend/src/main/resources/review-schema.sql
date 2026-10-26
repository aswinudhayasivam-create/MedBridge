-- Apply only after approval to the EMPTY dedicated review database, after schema.sql and demo-schema.sql.
-- Runtime users must have only SELECT, INSERT, UPDATE and DELETE on this database.
CREATE TABLE IF NOT EXISTS review_environment (
    environment_id VARCHAR(80) NOT NULL PRIMARY KEY,
    purpose VARCHAR(80) NOT NULL,
    schema_version INT NOT NULL,
    expires_at VARCHAR(30) NOT NULL
) ENGINE=InnoDB;
INSERT IGNORE INTO review_environment VALUES
('medbridge-review-20261026','FICTIONAL_COLLEGE_REVIEW_ONLY',1,'2026-11-22T18:30:00Z');

-- Cognica Clinical Decision Support Database Schema
-- Separate database for clinical DPs — fact-free templates used by the Clinical Query Assistant only.
-- The main cognica_db is NOT involved; this schema is entirely standalone.

CREATE DATABASE IF NOT EXISTS cognica_clinical_db;
USE cognica_clinical_db;

CREATE TABLE IF NOT EXISTS stored_cognica_requests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    natural_language LONGTEXT,
    action_type VARCHAR(50) DEFAULT 'getPrediction',
    original_policy_in_cognica_dp_cnl LONGTEXT COMMENT 'Original rule text before normalization',
    policy_in_cognica_dp_cnl LONGTEXT NOT NULL,
    `query` VARCHAR(500) NOT NULL,
    complementary_options_json LONGTEXT NOT NULL COMMENT 'JSON array of complementary options',
    facts_json LONGTEXT NOT NULL COMMENT 'Always empty ([]) — clinical DPs carry no pre-set facts; facts are built at query time from patient data',
    dp_version VARCHAR(50) NOT NULL DEFAULT 'clinical',
    last_response_json LONGTEXT COMMENT 'JSON response from last execution',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_name (name),
    INDEX idx_created_at (created_at),
    INDEX idx_updated_at (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

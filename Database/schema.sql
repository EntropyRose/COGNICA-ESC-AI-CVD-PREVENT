-- Cognica Decision Support Database Schema
-- MySQL Database Schema for storing COGNICA API requests and responses

CREATE DATABASE IF NOT EXISTS cognica_db;
USE cognica_db;

-- Table for storing COGNICA API requests
CREATE TABLE IF NOT EXISTS stored_cognica_requests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    natural_language LONGTEXT,
    action_type VARCHAR(50) DEFAULT 'getPrediction',
    original_policy_in_cognica_dp_cnl LONGTEXT COMMENT 'Original rule text before normalization',
    policy_in_cognica_dp_cnl LONGTEXT NOT NULL,
    `query` VARCHAR(500) NOT NULL,
    complementary_options_json LONGTEXT NOT NULL COMMENT 'JSON array of complementary options',
    facts_json LONGTEXT NOT NULL COMMENT 'JSON array of Fact objects: {fact, factHold, factStatus (true|false|unknown), factType (external|internal), internalSourceDp, rpSource}',
    dp_version VARCHAR(50) NOT NULL DEFAULT 'default',
    last_response_json LONGTEXT COMMENT 'JSON response from last execution',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    INDEX idx_name (name),
    INDEX idx_created_at (created_at),
    INDEX idx_updated_at (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Migration for existing databases: rename description -> natural_language once.
SET @has_old_column := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'stored_cognica_requests'
      AND COLUMN_NAME = 'description'
);

SET @has_new_column := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'stored_cognica_requests'
      AND COLUMN_NAME = 'natural_language'
);

SET @rename_sql := IF(
    @has_old_column = 1 AND @has_new_column = 0,
    'ALTER TABLE stored_cognica_requests CHANGE COLUMN description natural_language LONGTEXT',
    'SELECT 1'
);

PREPARE stmt FROM @rename_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_original_policy_column := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'stored_cognica_requests'
      AND COLUMN_NAME = 'original_policy_in_cognica_dp_cnl'
);

SET @add_original_policy_sql := IF(
    @has_original_policy_column = 0,
    'ALTER TABLE stored_cognica_requests ADD COLUMN original_policy_in_cognica_dp_cnl LONGTEXT AFTER action_type',
    'SELECT 1'
);

PREPARE stmt2 FROM @add_original_policy_sql;
EXECUTE stmt2;
DEALLOCATE PREPARE stmt2;

SET @has_dp_version_column := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'stored_cognica_requests'
      AND COLUMN_NAME = 'dp_version'
);

SET @add_dp_version_sql := IF(
    @has_dp_version_column = 0,
    'ALTER TABLE stored_cognica_requests ADD COLUMN dp_version VARCHAR(50) NOT NULL DEFAULT ''default'' AFTER facts_json',
    'SELECT 1'
);

PREPARE stmt3 FROM @add_dp_version_sql;
EXECUTE stmt3;
DEALLOCATE PREPARE stmt3;

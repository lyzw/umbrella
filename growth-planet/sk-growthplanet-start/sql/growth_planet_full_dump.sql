/*
 Navicat Premium Dump SQL

 Source Server         : localhost
 Source Server Type    : MySQL
 Source Server Version : 90600 (9.6.0)
 Source Host           : localhost:3306
 Source Schema         : growth_planet

 Target Server Type    : MySQL
 Target Server Version : 90600 (9.6.0)
 File Encoding         : 65001

 Date: 22/09/2026 13:50:45
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for life_allowance_log
-- ----------------------------
DROP TABLE IF EXISTS `life_allowance_log`;
CREATE TABLE `life_allowance_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `wallet_id` bigint NOT NULL,
  `child_id` bigint NOT NULL,
  `family_id` bigint NOT NULL,
  `operator_id` bigint NOT NULL,
  `trans_type` varchar(16) NOT NULL,
  `scene` varchar(32) NOT NULL,
  `ref_id` bigint DEFAULT NULL,
  `amount` decimal(10,2) NOT NULL,
  `balance_before` decimal(10,2) NOT NULL,
  `balance_after` decimal(10,2) NOT NULL,
  `wallet_version` int NOT NULL,
  `usage_date` date NOT NULL,
  `request_key` varchar(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `request_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `reason` varchar(100) DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `delete_at` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ledger_business` (`wallet_id`,`scene`,`ref_id`,`trans_type`),
  UNIQUE KEY `uk_grant_request` (`operator_id`,`scene`,`request_key`),
  KEY `idx_ledger_child_date` (`child_id`,`usage_date`,`id`),
  KEY `idx_ledger_family_date` (`family_id`,`usage_date`,`id`),
  CONSTRAINT `ck_ledger_amount` CHECK (((`amount` >= 0) and (`balance_before` >= 0) and (`balance_after` >= 0))),
  CONSTRAINT `ck_ledger_ref` CHECK (((`trans_type` <> _utf8mb4'DEDUCT') or (`ref_id` is not null)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Records of life_allowance_log
-- ----------------------------
BEGIN;
COMMIT;

-- ----------------------------
-- Table structure for life_allowance_rule
-- ----------------------------
DROP TABLE IF EXISTS `life_allowance_rule`;
CREATE TABLE `life_allowance_rule` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `child_id` bigint NOT NULL,
  `family_id` bigint NOT NULL,
  `single_limit` decimal(10,2) NOT NULL DEFAULT '30.00',
  `daily_limit` decimal(10,2) NOT NULL DEFAULT '30.00',
  `weekly_limit` decimal(10,2) NOT NULL DEFAULT '150.00',
  `daily_used` decimal(14,2) NOT NULL DEFAULT '0.00',
  `weekly_used` decimal(14,2) NOT NULL DEFAULT '0.00',
  `daily_period` date NOT NULL,
  `weekly_period` date NOT NULL,
  `version` int NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `delete_at` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rule_child` (`child_id`),
  KEY `idx_rule_family` (`family_id`),
  CONSTRAINT `ck_rule_limits` CHECK (((`single_limit` >= 0) and (`single_limit` <= `daily_limit`) and (`daily_limit` <= `weekly_limit`) and (`weekly_limit` <= 9999.99))),
  CONSTRAINT `ck_rule_usage` CHECK (((`daily_used` >= 0) and (`weekly_used` >= 0)))
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Records of life_allowance_rule
-- ----------------------------
BEGIN;
INSERT INTO `life_allowance_rule` (`id`, `child_id`, `family_id`, `single_limit`, `daily_limit`, `weekly_limit`, `daily_used`, `weekly_used`, `daily_period`, `weekly_period`, `version`, `create_time`, `update_time`, `delete_at`) VALUES (1, 3, 1, 30.00, 30.00, 150.00, 0.00, 0.00, '2026-09-22', '2026-09-21', 0, '2026-09-22 13:43:44', '2026-09-22 13:43:44', 0);
COMMIT;

-- ----------------------------
-- Table structure for life_check_item
-- ----------------------------
DROP TABLE IF EXISTS `life_check_item`;
CREATE TABLE `life_check_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `family_id` bigint NOT NULL,
  `name` varchar(32) NOT NULL,
  `icon` varchar(64) DEFAULT NULL,
  `unit` varchar(8) DEFAULT NULL,
  `daily_target` int NOT NULL DEFAULT '0',
  `sort_order` int NOT NULL DEFAULT '0',
  `version` int NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `delete_at` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_check_item_family` (`family_id`,`sort_order`,`id`),
  CONSTRAINT `fk_check_item_family` FOREIGN KEY (`family_id`) REFERENCES `usr_family` (`id`),
  CONSTRAINT `chk_check_item_target` CHECK ((`daily_target` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Records of life_check_item
-- ----------------------------
BEGIN;
COMMIT;

-- ----------------------------
-- Table structure for life_check_record
-- ----------------------------
DROP TABLE IF EXISTS `life_check_record`;
CREATE TABLE `life_check_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `family_id` bigint NOT NULL,
  `child_id` bigint NOT NULL,
  `item_id` bigint NOT NULL,
  `item_name` varchar(32) NOT NULL,
  `check_date` date NOT NULL,
  `check_time` datetime NOT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `delete_at` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_check_record_child_date` (`family_id`,`child_id`,`check_date`),
  KEY `idx_check_record_item` (`family_id`,`item_id`,`check_date`),
  KEY `fk_check_record_item` (`item_id`),
  CONSTRAINT `fk_check_record_family` FOREIGN KEY (`family_id`) REFERENCES `usr_family` (`id`),
  CONSTRAINT `fk_check_record_item` FOREIGN KEY (`item_id`) REFERENCES `life_check_item` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Records of life_check_record
-- ----------------------------
BEGIN;
COMMIT;

-- ----------------------------
-- Table structure for life_chore_instance
-- ----------------------------
DROP TABLE IF EXISTS `life_chore_instance`;
CREATE TABLE `life_chore_instance` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `task_id` bigint NOT NULL,
  `family_id` bigint NOT NULL,
  `child_id` bigint NOT NULL,
  `status` varchar(16) NOT NULL DEFAULT 'CLAIMED',
  `version` int NOT NULL DEFAULT '0',
  `claim_date` date NOT NULL,
  `submit_time` datetime DEFAULT NULL,
  `confirm_time` datetime DEFAULT NULL,
  `parent_id` bigint DEFAULT NULL,
  `reward_amount` decimal(10,2) NOT NULL DEFAULT '0.00',
  `reward_granted` tinyint NOT NULL DEFAULT '0',
  `medal_code` varchar(32) DEFAULT NULL,
  `reject_reason` varchar(255) DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `delete_at` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_chore_instance_child` (`child_id`,`status`,`claim_date`),
  KEY `idx_chore_instance_task` (`task_id`),
  CONSTRAINT `chk_chore_instance_status` CHECK ((`status` in (_utf8mb4'CLAIMED',_utf8mb4'SUBMITTED',_utf8mb4'CONFIRMED',_utf8mb4'REJECTED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Records of life_chore_instance
-- ----------------------------
BEGIN;
COMMIT;

-- ----------------------------
-- Table structure for life_chore_streak
-- ----------------------------
DROP TABLE IF EXISTS `life_chore_streak`;
CREATE TABLE `life_chore_streak` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `child_id` bigint NOT NULL,
  `current_streak` int NOT NULL DEFAULT '0',
  `longest_streak` int NOT NULL DEFAULT '0',
  `last_claim_date` date DEFAULT NULL,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `delete_at` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_chore_streak_child` (`child_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Records of life_chore_streak
-- ----------------------------
BEGIN;
COMMIT;

-- ----------------------------
-- Table structure for life_chore_task
-- ----------------------------
DROP TABLE IF EXISTS `life_chore_task`;
CREATE TABLE `life_chore_task` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `family_id` bigint NOT NULL,
  `title` varchar(64) NOT NULL,
  `description` varchar(255) NOT NULL DEFAULT '',
  `icon` varchar(32) NOT NULL DEFAULT '',
  `estimated_minutes` int NOT NULL DEFAULT '0',
  `reward_amount` decimal(10,2) NOT NULL DEFAULT '0.00',
  `cycle` varchar(16) NOT NULL DEFAULT 'ONCE',
  `sort_order` int NOT NULL DEFAULT '0',
  `status` varchar(16) NOT NULL DEFAULT 'NORMAL',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `delete_at` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_chore_task_family` (`family_id`,`status`,`sort_order`),
  CONSTRAINT `chk_chore_task_cycle` CHECK ((`cycle` in (_utf8mb4'ONCE',_utf8mb4'DAILY',_utf8mb4'WEEKLY'))),
  CONSTRAINT `chk_chore_task_status` CHECK ((`status` in (_utf8mb4'NORMAL',_utf8mb4'ARCHIVED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Records of life_chore_task
-- ----------------------------
BEGIN;
COMMIT;

-- ----------------------------
-- Table structure for life_confirm_approval
-- ----------------------------
DROP TABLE IF EXISTS `life_confirm_approval`;
CREATE TABLE `life_confirm_approval` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `confirm_id` bigint NOT NULL,
  `parent_id` bigint NOT NULL,
  `action` varchar(20) NOT NULL,
  `before_status` varchar(20) NOT NULL,
  `after_status` varchar(20) NOT NULL,
  `is_over_limit` tinyint NOT NULL,
  `reason` varchar(255) DEFAULT NULL,
  `suggested_items` json DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `delete_at` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_approval_confirm` (`confirm_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Records of life_confirm_approval
-- ----------------------------
BEGIN;
COMMIT;

-- ----------------------------
-- Table structure for life_dish
-- ----------------------------
DROP TABLE IF EXISTS `life_dish`;
CREATE TABLE `life_dish` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `category_id` bigint NOT NULL,
  `name` varchar(64) NOT NULL,
  `image_url` varchar(255) DEFAULT NULL,
  `virtual_price` decimal(10,2) NOT NULL DEFAULT '0.00',
  `calories` int DEFAULT NULL,
  `tags` varchar(255) DEFAULT NULL,
  `allergens` json NOT NULL DEFAULT (json_array()),
  `allergen_status` varchar(20) NOT NULL DEFAULT 'UNKNOWN',
  `spice_level` tinyint NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'ON_SALE',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `delete_at` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_category` (`category_id`,`id`),
  KEY `idx_price` (`virtual_price`,`id`),
  CONSTRAINT `fk_dish_category` FOREIGN KEY (`category_id`) REFERENCES `life_dish_category` (`id`),
  CONSTRAINT `chk_dish_allergen_status` CHECK ((`allergen_status` in (_utf8mb4'UNKNOWN',_utf8mb4'DECLARED'))),
  CONSTRAINT `chk_dish_allergens` CHECK ((json_type(`allergens`) = _utf8mb4'ARRAY')),
  CONSTRAINT `chk_dish_calories` CHECK (((`calories` is null) or (`calories` >= 0))),
  CONSTRAINT `chk_dish_price` CHECK ((`virtual_price` >= 0)),
  CONSTRAINT `chk_dish_spice` CHECK ((`spice_level` between 0 and 3)),
  CONSTRAINT `chk_dish_status` CHECK ((`status` in (_utf8mb4'ON_SALE',_utf8mb4'OFF_SALE')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Records of life_dish
-- ----------------------------
BEGIN;
COMMIT;

-- ----------------------------
-- Table structure for life_dish_category
-- ----------------------------
DROP TABLE IF EXISTS `life_dish_category`;
CREATE TABLE `life_dish_category` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(32) NOT NULL,
  `sort` int NOT NULL DEFAULT '0',
  `status` varchar(20) NOT NULL DEFAULT 'ENABLED',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `delete_at` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_sort` (`sort`,`id`),
  CONSTRAINT `chk_category_sort` CHECK ((`sort` >= 0)),
  CONSTRAINT `chk_category_status` CHECK ((`status` in (_utf8mb4'ENABLED',_utf8mb4'DISABLED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Records of life_dish_category
-- ----------------------------
BEGIN;
COMMIT;

-- ----------------------------
-- Table structure for life_family_dish
-- ----------------------------
DROP TABLE IF EXISTS `life_family_dish`;
CREATE TABLE `life_family_dish` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `family_id` bigint NOT NULL,
  `category_id` bigint NOT NULL,
  `name` varchar(64) NOT NULL,
  `image_url` varchar(255) DEFAULT NULL,
  `virtual_price` decimal(10,2) NOT NULL DEFAULT '0.00',
  `calories` int DEFAULT NULL,
  `tags` varchar(255) DEFAULT NULL,
  `allergens` json NOT NULL DEFAULT (json_array()),
  `allergen_status` varchar(20) NOT NULL DEFAULT 'UNKNOWN',
  `spice_level` tinyint NOT NULL,
  `visibility` varchar(20) NOT NULL DEFAULT 'PRIVATE',
  `status` varchar(20) NOT NULL DEFAULT 'ON_SALE',
  `version` int NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `delete_at` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_family_dish_status` (`family_id`,`status`,`id`),
  KEY `idx_family_dish_category` (`family_id`,`category_id`,`id`),
  KEY `idx_family_dish_update` (`family_id`,`update_time`),
  KEY `fk_family_dish_category` (`category_id`),
  CONSTRAINT `fk_family_dish_category` FOREIGN KEY (`category_id`) REFERENCES `life_dish_category` (`id`),
  CONSTRAINT `fk_family_dish_family` FOREIGN KEY (`family_id`) REFERENCES `usr_family` (`id`),
  CONSTRAINT `chk_fdish_allergen_status` CHECK ((`allergen_status` in (_utf8mb4'UNKNOWN',_utf8mb4'DECLARED'))),
  CONSTRAINT `chk_fdish_allergens` CHECK ((json_type(`allergens`) = _utf8mb4'ARRAY')),
  CONSTRAINT `chk_fdish_calories` CHECK (((`calories` is null) or (`calories` >= 0))),
  CONSTRAINT `chk_fdish_price` CHECK ((`virtual_price` >= 0)),
  CONSTRAINT `chk_fdish_spice` CHECK ((`spice_level` between 0 and 3)),
  CONSTRAINT `chk_fdish_status` CHECK ((`status` in (_utf8mb4'ON_SALE',_utf8mb4'OFF_SALE'))),
  CONSTRAINT `chk_fdish_visibility` CHECK ((`visibility` in (_utf8mb4'PRIVATE',_utf8mb4'PUBLIC')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Records of life_family_dish
-- ----------------------------
BEGIN;
COMMIT;

-- ----------------------------
-- Table structure for life_medal_award
-- ----------------------------
DROP TABLE IF EXISTS `life_medal_award`;
CREATE TABLE `life_medal_award` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `definition_id` bigint NOT NULL,
  `family_id` bigint NOT NULL,
  `child_id` bigint NOT NULL,
  `awarded_at` bigint NOT NULL,
  `consecutive_count` int NOT NULL DEFAULT '0',
  `ref_id` bigint NOT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `delete_at` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_medal_award_unique` (`definition_id`,`child_id`,`ref_id`),
  KEY `idx_medal_award_child` (`child_id`,`definition_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Records of life_medal_award
-- ----------------------------
BEGIN;
COMMIT;

-- ----------------------------
-- Table structure for life_medal_definition
-- ----------------------------
DROP TABLE IF EXISTS `life_medal_definition`;
CREATE TABLE `life_medal_definition` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(32) NOT NULL,
  `name` varchar(64) NOT NULL,
  `description` varchar(255) NOT NULL DEFAULT '',
  `icon` varchar(32) NOT NULL DEFAULT '',
  `category` varchar(16) NOT NULL,
  `condition_type` varchar(16) NOT NULL,
  `threshold` int NOT NULL DEFAULT '1',
  `sort_order` int NOT NULL DEFAULT '0',
  `status` varchar(16) NOT NULL DEFAULT 'NORMAL',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `delete_at` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_medal_code` (`code`),
  CONSTRAINT `chk_medal_category` CHECK ((`category` in (_utf8mb4'CHORE',_utf8mb4'HEALTH',_utf8mb4'EXCHANGE',_utf8mb4'STREAK',_utf8mb4'MEAL'))),
  CONSTRAINT `chk_medal_condition` CHECK ((`condition_type` in (_utf8mb4'EVENT',_utf8mb4'COUNT',_utf8mb4'STREAK'))),
  CONSTRAINT `chk_medal_status` CHECK ((`status` in (_utf8mb4'NORMAL',_utf8mb4'ARCHIVED')))
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Records of life_medal_definition
-- ----------------------------
BEGIN;
INSERT INTO `life_medal_definition` (`id`, `code`, `name`, `description`, `icon`, `category`, `condition_type`, `threshold`, `sort_order`, `status`, `create_time`, `update_time`, `delete_at`) VALUES (1, 'CHORE_FIRST', '初次当家', '完成第一次家务', '🌟', 'CHORE', 'COUNT', 1, 10, 'NORMAL', '2026-09-22 13:46:47', '2026-09-22 13:46:47', 0);
INSERT INTO `life_medal_definition` (`id`, `code`, `name`, `description`, `icon`, `category`, `condition_type`, `threshold`, `sort_order`, `status`, `create_time`, `update_time`, `delete_at`) VALUES (2, 'CHORE_10', '家务小能手', '累计完成 10 次家务', '🏅', 'CHORE', 'COUNT', 10, 20, 'NORMAL', '2026-09-22 13:46:47', '2026-09-22 13:46:47', 0);
INSERT INTO `life_medal_definition` (`id`, `code`, `name`, `description`, `icon`, `category`, `condition_type`, `threshold`, `sort_order`, `status`, `create_time`, `update_time`, `delete_at`) VALUES (3, 'CHORE_STREAK3', '连做三日', '连续 3 天完成家务', '🔥', 'CHORE', 'STREAK', 3, 30, 'NORMAL', '2026-09-22 13:46:47', '2026-09-22 13:46:47', 0);
INSERT INTO `life_medal_definition` (`id`, `code`, `name`, `description`, `icon`, `category`, `condition_type`, `threshold`, `sort_order`, `status`, `create_time`, `update_time`, `delete_at`) VALUES (4, 'HEALTH_STREAK_3', '健康打卡 3 天', '连续 3 天打卡', '🌱', 'HEALTH', 'STREAK', 3, 40, 'NORMAL', '2026-09-22 13:47:45', '2026-09-22 13:47:45', 0);
INSERT INTO `life_medal_definition` (`id`, `code`, `name`, `description`, `icon`, `category`, `condition_type`, `threshold`, `sort_order`, `status`, `create_time`, `update_time`, `delete_at`) VALUES (5, 'HEALTH_STREAK_7', '健康打卡一周', '连续 7 天打卡', '🔥', 'HEALTH', 'STREAK', 7, 50, 'NORMAL', '2026-09-22 13:47:45', '2026-09-22 13:47:45', 0);
INSERT INTO `life_medal_definition` (`id`, `code`, `name`, `description`, `icon`, `category`, `condition_type`, `threshold`, `sort_order`, `status`, `create_time`, `update_time`, `delete_at`) VALUES (6, 'HEALTH_STREAK_14', '健康打卡双周', '连续 14 天打卡', '⭐', 'HEALTH', 'STREAK', 14, 60, 'NORMAL', '2026-09-22 13:47:45', '2026-09-22 13:47:45', 0);
INSERT INTO `life_medal_definition` (`id`, `code`, `name`, `description`, `icon`, `category`, `condition_type`, `threshold`, `sort_order`, `status`, `create_time`, `update_time`, `delete_at`) VALUES (7, 'HEALTH_STREAK_30', '健康打卡满月', '连续 30 天打卡', '🏆', 'HEALTH', 'STREAK', 30, 70, 'NORMAL', '2026-09-22 13:47:45', '2026-09-22 13:47:45', 0);
INSERT INTO `life_medal_definition` (`id`, `code`, `name`, `description`, `icon`, `category`, `condition_type`, `threshold`, `sort_order`, `status`, `create_time`, `update_time`, `delete_at`) VALUES (8, 'MEAL_COOKED_1', '心愿达成', '第一个想要吃的菜被做出来', '🌟', 'MEAL', 'COUNT', 1, 80, 'NORMAL', '2026-09-22 13:47:58', '2026-09-22 13:47:58', 0);
INSERT INTO `life_medal_definition` (`id`, `code`, `name`, `description`, `icon`, `category`, `condition_type`, `threshold`, `sort_order`, `status`, `create_time`, `update_time`, `delete_at`) VALUES (9, 'MEAL_COOKED_10', '小小心愿官', '累计 10 次想吃被做出来', '🍽', 'MEAL', 'COUNT', 10, 90, 'NORMAL', '2026-09-22 13:47:58', '2026-09-22 13:47:58', 0);
INSERT INTO `life_medal_definition` (`id`, `code`, `name`, `description`, `icon`, `category`, `condition_type`, `threshold`, `sort_order`, `status`, `create_time`, `update_time`, `delete_at`) VALUES (10, 'MEAL_COOKED_30', '餐餐有回应', '累计 30 次想吃被做出来', '🏆', 'MEAL', 'COUNT', 30, 100, 'NORMAL', '2026-09-22 13:47:58', '2026-09-22 13:47:58', 0);
COMMIT;

-- ----------------------------
-- Table structure for life_menu_confirm
-- ----------------------------
DROP TABLE IF EXISTS `life_menu_confirm`;
CREATE TABLE `life_menu_confirm` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `confirm_no` varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `child_id` bigint NOT NULL,
  `family_id` bigint NOT NULL,
  `menu_id` bigint NOT NULL,
  `menu_date` date NOT NULL,
  `meal_type` varchar(20) NOT NULL,
  `total_amount` decimal(10,2) NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'PENDING',
  `version` int NOT NULL DEFAULT '0',
  `previous_confirm_id` bigint DEFAULT NULL,
  `request_key` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `request_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `approval_request_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `is_over_limit` tinyint NOT NULL DEFAULT '0',
  `remark` varchar(255) DEFAULT NULL,
  `parent_id` bigint DEFAULT NULL,
  `completed_balance` decimal(10,2) DEFAULT NULL,
  `completed_wallet_version` int DEFAULT NULL,
  `estimated_balance` decimal(10,2) NOT NULL,
  `submit_time` datetime NOT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `delete_at` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_confirm_no` (`confirm_no`),
  UNIQUE KEY `uk_confirm_request` (`child_id`,`request_key`),
  KEY `idx_confirm_scope` (`family_id`,`child_id`,`status`,`id`),
  KEY `idx_confirm_menu` (`menu_id`),
  KEY `idx_confirm_previous` (`previous_confirm_id`),
  CONSTRAINT `ck_confirm_amount` CHECK ((`total_amount` >= 0)),
  CONSTRAINT `ck_confirm_status` CHECK ((`status` in (_utf8mb4'PENDING',_utf8mb4'REJECTED',_utf8mb4'CANCELLED',_utf8mb4'COMPLETED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Records of life_menu_confirm
-- ----------------------------
BEGIN;
COMMIT;

-- ----------------------------
-- Table structure for life_menu_daily
-- ----------------------------
DROP TABLE IF EXISTS `life_menu_daily`;
CREATE TABLE `life_menu_daily` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `source_type` varchar(10) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `owner_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `school` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL,
  `family_id` bigint DEFAULT NULL,
  `menu_date` date NOT NULL,
  `meal_type` varchar(20) NOT NULL,
  `dish_ids` json NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'PUBLISHED',
  `version` int NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `delete_at` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_source_owner_date_meal` (`source_type`,`owner_key`,`menu_date`,`meal_type`),
  KEY `idx_family` (`family_id`,`menu_date`),
  CONSTRAINT `fk_menu_family` FOREIGN KEY (`family_id`) REFERENCES `usr_family` (`id`),
  CONSTRAINT `chk_menu_dishes` CHECK (((json_type(`dish_ids`) = _utf8mb4'ARRAY') and (json_length(`dish_ids`) between 1 and 50))),
  CONSTRAINT `chk_menu_meal` CHECK ((`meal_type` in (_utf8mb4'BREAKFAST',_utf8mb4'LUNCH',_utf8mb4'DINNER'))),
  CONSTRAINT `chk_menu_owner` CHECK ((((`source_type` = _ascii'FAMILY') and (`family_id` is not null) and (`school` is null) and (`owner_key` = cast(`family_id` as char charset utf8mb4))) or ((`source_type` = _ascii'SCHOOL') and (`family_id` is null) and (`school` is not null) and (char_length(trim(`school`)) > 0) and (`owner_key` = `school`)))),
  CONSTRAINT `chk_menu_status` CHECK ((`status` in (_utf8mb4'DRAFT',_utf8mb4'PUBLISHED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Records of life_menu_daily
-- ----------------------------
BEGIN;
COMMIT;

-- ----------------------------
-- Table structure for life_menu_item
-- ----------------------------
DROP TABLE IF EXISTS `life_menu_item`;
CREATE TABLE `life_menu_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `confirm_id` bigint NOT NULL,
  `dish_id` bigint NOT NULL,
  `source_type` varchar(20) NOT NULL DEFAULT 'PRESET',
  `dish_name` varchar(64) NOT NULL,
  `quantity` int NOT NULL,
  `unit_price` decimal(10,2) NOT NULL,
  `subtotal` decimal(10,2) NOT NULL,
  `note` varchar(255) DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `delete_at` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_confirm_dish` (`confirm_id`,`dish_id`,`source_type`),
  CONSTRAINT `ck_item_amount` CHECK (((`unit_price` >= 0) and (`subtotal` = (`unit_price` * `quantity`)))),
  CONSTRAINT `ck_item_quantity` CHECK ((`quantity` between 1 and 9))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Records of life_menu_item
-- ----------------------------
BEGIN;
COMMIT;

-- ----------------------------
-- Table structure for life_schedule
-- ----------------------------
DROP TABLE IF EXISTS `life_schedule`;
CREATE TABLE `life_schedule` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `family_id` bigint NOT NULL,
  `child_id` bigint NOT NULL,
  `creator_id` bigint NOT NULL,
  `title` varchar(64) NOT NULL,
  `category` varchar(16) NOT NULL DEFAULT 'OTHER',
  `note` varchar(255) NOT NULL DEFAULT '',
  `schedule_date` date NOT NULL,
  `schedule_time` varchar(5) NOT NULL,
  `repeat_type` varchar(16) NOT NULL DEFAULT 'ONCE',
  `repeat_weekdays` varchar(32) NOT NULL DEFAULT '',
  `remind_minutes` int NOT NULL DEFAULT '0',
  `status` varchar(16) NOT NULL DEFAULT 'NORMAL',
  `last_remind_date` date DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `delete_at` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_schedule_child_date` (`child_id`,`status`,`schedule_date`),
  KEY `idx_schedule_scan` (`status`,`schedule_date`,`last_remind_date`),
  CONSTRAINT `chk_schedule_category` CHECK ((`category` in (_utf8mb4'HOMEWORK',_utf8mb4'CLASS',_utf8mb4'MEDICINE',_utf8mb4'OTHER'))),
  CONSTRAINT `chk_schedule_remind` CHECK ((`remind_minutes` between 0 and 1440)),
  CONSTRAINT `chk_schedule_repeat` CHECK ((`repeat_type` in (_utf8mb4'ONCE',_utf8mb4'DAILY',_utf8mb4'WEEKLY'))),
  CONSTRAINT `chk_schedule_status` CHECK ((`status` in (_utf8mb4'NORMAL',_utf8mb4'CANCELLED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Records of life_schedule
-- ----------------------------
BEGIN;
COMMIT;

-- ----------------------------
-- Table structure for life_wallet
-- ----------------------------
DROP TABLE IF EXISTS `life_wallet`;
CREATE TABLE `life_wallet` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `child_id` bigint NOT NULL,
  `family_id` bigint NOT NULL,
  `balance` decimal(10,2) NOT NULL DEFAULT '0.00',
  `version` int NOT NULL DEFAULT '0',
  `status` varchar(20) NOT NULL DEFAULT 'NORMAL',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `delete_at` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_wallet_child` (`child_id`),
  KEY `idx_wallet_family` (`family_id`),
  CONSTRAINT `ck_wallet_balance` CHECK ((`balance` >= 0))
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Records of life_wallet
-- ----------------------------
BEGIN;
INSERT INTO `life_wallet` (`id`, `child_id`, `family_id`, `balance`, `version`, `status`, `create_time`, `update_time`, `delete_at`) VALUES (1, 3, 1, 0.00, 0, 'NORMAL', '2026-09-22 13:43:44', '2026-09-22 13:43:44', 0);
COMMIT;

-- ----------------------------
-- Table structure for life_wish_menu
-- ----------------------------
DROP TABLE IF EXISTS `life_wish_menu`;
CREATE TABLE `life_wish_menu` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `child_id` bigint NOT NULL,
  `family_id` bigint NOT NULL,
  `menu_date` date NOT NULL,
  `status` varchar(16) NOT NULL COMMENT 'SUBMITTED/WITHDRAWN；无行=孩子当天未创建（可选功能）',
  `max_dishes` tinyint NOT NULL COMMENT '提交时快照的家长上限，便于回看当时口径',
  `dish_count` int NOT NULL DEFAULT '0',
  `submit_version` int NOT NULL DEFAULT '0' COMMENT '每次有效提交 +1；通知幂等键第二段',
  `submit_time` datetime DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `delete_at` bigint NOT NULL DEFAULT '0',
  `version` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_wish_child_date` (`child_id`,`menu_date`,`delete_at`),
  KEY `idx_wish_family_date` (`family_id`,`menu_date`),
  CONSTRAINT `fk_wish_family` FOREIGN KEY (`family_id`) REFERENCES `usr_family` (`id`),
  CONSTRAINT `chk_wish_status` CHECK ((`status` in (_utf8mb4'SUBMITTED',_utf8mb4'WITHDRAWN')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='儿童心愿菜单提交单（每天一份）';

-- ----------------------------
-- Records of life_wish_menu
-- ----------------------------
BEGIN;
COMMIT;

-- ----------------------------
-- Table structure for sys_audit_log
-- ----------------------------
DROP TABLE IF EXISTS `sys_audit_log`;
CREATE TABLE `sys_audit_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `actor_user_id` bigint DEFAULT NULL,
  `family_id` bigint DEFAULT NULL,
  `action` varchar(32) NOT NULL,
  `target_type` varchar(32) DEFAULT NULL,
  `target_id` bigint DEFAULT NULL,
  `ip` varchar(64) DEFAULT NULL,
  `detail` varchar(1024) DEFAULT NULL,
  `request_id` varchar(64) DEFAULT NULL,
  `result` varchar(16) DEFAULT NULL,
  `error_code` varchar(16) DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `delete_at` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_actor` (`actor_user_id`),
  KEY `idx_action` (`action`),
  KEY `idx_request` (`request_id`)
) ENGINE=InnoDB AUTO_INCREMENT=885 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Records of sys_audit_log
-- ----------------------------
BEGIN;
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (1, 1, NULL, 'LOGIN', 'USER', 1, '127.0.0.1', 'wx-login', 'e374c400-ebff-4f0b-91dd-51ca19faf37d', 'SUCCESS', NULL, '2026-09-20 13:32:04', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (2, 2, NULL, 'LOGIN', 'USER', 2, '0:0:0:0:0:0:0:1', 'wx-login', 'd798aac4-39b4-42c8-a8ee-61a5964ddbaf', 'SUCCESS', NULL, '2026-09-20 13:35:20', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (3, 2, NULL, 'ROLE', 'USER', 2, '0:0:0:0:0:0:0:1', 'select-role=PARENT', 'da085b07-d0ea-41b4-a0e9-3dfb3dcb75d1', 'SUCCESS', NULL, '2026-09-20 13:35:25', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (4, 2, NULL, 'REQUEST_FAILED', NULL, NULL, '0:0:0:0:0:0:0:1', NULL, '219e0ff0-382a-43c8-a255-e525fa9b2ecc', 'DENIED', 'E-009', '2026-09-20 13:35:26', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (5, 2, 1, 'CREATE_FAMILY', 'FAMILY', 1, '0:0:0:0:0:0:0:1', 'create family', '44f9f6ec-7aa9-4ac6-be6d-9f39a8927a58', 'SUCCESS', NULL, '2026-09-20 13:35:31', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (6, 2, 1, 'FAMILY_QUERY', 'FAMILY', 1, '0:0:0:0:0:0:0:1', 'page=1;pageSize=100', 'fa14aca8-a085-4a57-90e0-75c1aab7029e', 'SUCCESS', NULL, '2026-09-20 13:35:38', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (7, 2, 1, 'REQUEST_FAILED', NULL, NULL, '0:0:0:0:0:0:0:1', NULL, 'e267c79c-7c8c-4296-ad2b-724784e6a7e2', 'DENIED', 'E-400', '2026-09-20 13:35:42', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (8, 2, 1, 'REQUEST_FAILED', NULL, NULL, '0:0:0:0:0:0:0:1', NULL, '208cc35c-1020-4e70-86a7-cd0025d82cf5', 'DENIED', 'E-400', '2026-09-20 13:37:35', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (9, 2, 1, 'REQUEST_FAILED', NULL, NULL, '0:0:0:0:0:0:0:1', NULL, '76ef6485-faf0-4419-bcd7-d89261a8fca3', 'DENIED', 'E-400', '2026-09-20 13:38:19', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (10, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '0:0:0:0:0:0:0:1', NULL, '8bc7a316-b5f7-4b8b-a523-0398fb65e3bf', 'DENIED', 'E-001', '2026-09-20 13:38:23', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (11, 2, 1, 'FAMILY_QUERY', 'FAMILY', 1, '0:0:0:0:0:0:0:1', 'page=1;pageSize=100', '4e5c5353-e2ad-4cbb-91df-b81fd6e52e04', 'SUCCESS', NULL, '2026-09-20 13:38:33', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (12, 2, 1, 'FAMILY_QUERY', 'FAMILY', 1, '0:0:0:0:0:0:0:1', 'page=1;pageSize=100', '5904a72c-decd-4073-91c8-f2f0b76fa24c', 'SUCCESS', NULL, '2026-09-20 13:38:42', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (13, 2, 1, 'FAMILY_QUERY', 'FAMILY', 1, '0:0:0:0:0:0:0:1', 'page=1;pageSize=100', '969eaa66-e6b5-42d1-83c4-8e01e6243a45', 'SUCCESS', NULL, '2026-09-20 13:39:45', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (14, 2, NULL, 'LOGIN', 'USER', 2, '0:0:0:0:0:0:0:1', 'wx-login', '541b529b-6f10-46d4-a2ac-073ea8ea718b', 'SUCCESS', NULL, '2026-09-20 13:39:58', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (15, 2, 1, 'REQUEST_FAILED', NULL, NULL, '0:0:0:0:0:0:0:1', NULL, 'f2570219-bca4-4578-a83d-e3bb9839ec09', 'DENIED', 'E-400', '2026-09-20 13:39:58', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (16, 2, 1, 'FAMILY_QUERY', 'FAMILY', 1, '0:0:0:0:0:0:0:1', 'page=1;pageSize=100', '3522e963-6be9-4c8d-9e34-c243c815d390', 'SUCCESS', NULL, '2026-09-20 13:48:09', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (17, 2, 1, 'FAMILY_QUERY', 'FAMILY', 1, '0:0:0:0:0:0:0:1', 'page=1;pageSize=100', 'dcc2918b-a921-4a60-bb6c-82e7a382b5ae', 'SUCCESS', NULL, '2026-09-20 13:48:14', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (18, 2, 1, 'FAMILY_QUERY', 'FAMILY', 1, '0:0:0:0:0:0:0:1', 'page=1;pageSize=100', '9a627133-b713-4808-b229-d92adbaa78ae', 'SUCCESS', NULL, '2026-09-20 13:48:28', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (19, 2, 1, 'FAMILY_QUERY', 'FAMILY', 1, '0:0:0:0:0:0:0:1', 'page=1;pageSize=100', 'e585ffd5-87a1-429d-879d-dbbfee96a54c', 'SUCCESS', NULL, '2026-09-20 13:49:50', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (20, 2, 1, 'FAMILY_QUERY', 'FAMILY', 1, '0:0:0:0:0:0:0:1', 'page=1;pageSize=100', '679f24a2-a228-4cc4-843f-4c32e5e53dd1', 'SUCCESS', NULL, '2026-09-20 13:55:36', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (21, 2, 1, 'FAMILY_QUERY', 'FAMILY', 1, '0:0:0:0:0:0:0:1', 'page=1;pageSize=100', '0a6f9f7b-aa19-4e26-b279-f07b6ca36024', 'SUCCESS', NULL, '2026-09-20 13:55:47', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (22, 2, 1, 'REQUEST_FAILED', NULL, NULL, '0:0:0:0:0:0:0:1', NULL, 'ec684075-dd80-4c29-99a5-a2b53b5c5497', 'DENIED', 'E-400', '2026-09-20 13:55:50', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (23, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, 'a0749ee3-999c-42df-81b5-9f508af1025e', 'DENIED', 'E-404', '2026-09-20 13:57:46', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (24, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, 'cf8a5f8b-963d-4d6e-90a7-88d1785c2141', 'DENIED', 'E-404', '2026-09-20 13:57:46', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (25, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, 'b652bfb9-d3c7-42ef-ae29-dc3c23f56bed', 'DENIED', 'E-404', '2026-09-20 13:57:46', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (26, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, 'f7b139da-767f-428e-aee9-bfb4bd17f9f3', 'DENIED', 'E-404', '2026-09-20 13:57:46', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (27, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '3e77e78c-7c34-4459-aeb0-149bc544c098', 'DENIED', 'E-404', '2026-09-20 13:57:46', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (28, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, 'ebbb8dce-887d-4c8e-a19d-e1c1240a43f4', 'DENIED', 'E-404', '2026-09-20 13:57:46', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (29, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '559cd0c5-5124-4fdc-8eaa-f214ad78646c', 'DENIED', 'E-404', '2026-09-20 13:57:46', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (30, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '66759248-5f50-40b5-aa72-1544ec30ae53', 'DENIED', 'E-404', '2026-09-20 13:57:46', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (31, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '84adb861-15a2-47d7-a80a-d4cfb4082f59', 'DENIED', 'E-404', '2026-09-20 13:57:46', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (32, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, 'ccc87be2-2003-4e3d-a0e9-734ff140b119', 'DENIED', 'E-404', '2026-09-20 13:57:46', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (33, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, 'dd090e85-7b51-43b9-9e1e-e270d6068b05', 'DENIED', 'E-404', '2026-09-20 13:57:46', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (34, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '3bfc7e6a-d592-42c0-a156-dda2b6dbc07a', 'DENIED', 'E-404', '2026-09-20 13:57:47', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (35, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '72a3922a-2cf9-4f87-bb76-c74c6c87dc1d', 'DENIED', 'E-404', '2026-09-20 13:57:47', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (36, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '9ab87c3a-1709-4569-ab97-6dbda6cc8eed', 'DENIED', 'E-404', '2026-09-20 13:57:47', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (37, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '4ec27119-cb26-4f75-970f-9e92b885a72a', 'DENIED', 'E-404', '2026-09-20 13:57:47', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (38, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '5b4bacb9-c5c6-4b4f-be5c-8c39b85896c4', 'DENIED', 'E-404', '2026-09-20 13:57:47', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (39, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '9f0cb92b-6be4-40d2-bf92-0de1b143b992', 'DENIED', 'E-404', '2026-09-20 13:57:47', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (40, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '070fe1a9-2605-4004-960e-8a709e3eefd6', 'DENIED', 'E-404', '2026-09-20 13:57:47', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (41, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '56f5917b-16c5-4d62-a7a2-951a38667c81', 'DENIED', 'E-404', '2026-09-20 13:57:47', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (42, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, 'b99e047b-902f-4e31-a39d-9d7c2b608c1f', 'DENIED', 'E-404', '2026-09-20 13:57:47', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (43, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, 'b21e7c9d-56c6-46ab-a355-9548a72117a3', 'DENIED', 'E-404', '2026-09-20 13:57:47', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (44, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '53b353b7-2124-41f8-89b5-313558fb48d7', 'DENIED', 'E-404', '2026-09-20 13:57:48', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (45, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '01f5772e-e275-4d3c-a985-32248b7c3e1c', 'DENIED', 'E-404', '2026-09-20 13:57:48', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (46, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '0c988d36-e920-4ba7-81f9-f6b9cd6743e6', 'DENIED', 'E-404', '2026-09-20 13:57:48', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (47, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, 'e6537847-bcd9-4d5c-b938-5353e6dea0fb', 'DENIED', 'E-404', '2026-09-20 13:57:48', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (48, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, 'a0d3b8e4-90bb-4aed-b84a-fc525d22fd63', 'DENIED', 'E-404', '2026-09-20 13:57:48', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (49, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, 'bf8fa0b7-9eae-4161-8ba1-dab76abe734a', 'DENIED', 'E-404', '2026-09-20 13:57:48', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (50, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '8035d374-f970-474b-9af4-ee02444ea0ca', 'DENIED', 'E-404', '2026-09-20 13:57:48', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (51, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, 'd03e0d2a-79c7-4f7f-a8d9-2a4e012d7af6', 'DENIED', 'E-404', '2026-09-20 13:57:48', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (52, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, 'e4e853d3-0f50-4888-8b63-6b8cccc8e521', 'DENIED', 'E-404', '2026-09-20 13:57:48', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (53, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, 'c4416f77-014c-4023-867f-24af1141d1a4', 'DENIED', 'E-404', '2026-09-20 13:57:48', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (54, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, 'a3dcd478-573f-4af1-a9b4-caddd09f56e9', 'DENIED', 'E-404', '2026-09-20 13:57:48', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (55, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '371991bd-206d-4424-99e9-2d7895f21e40', 'DENIED', 'E-404', '2026-09-20 13:57:48', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (56, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, 'b7539290-2299-4e6b-9323-7be5b1675941', 'DENIED', 'E-404', '2026-09-20 13:57:49', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (57, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, 'fcedef5e-de65-4536-a0b5-524ee7d2408a', 'DENIED', 'E-404', '2026-09-20 13:57:49', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (58, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '7ba99a67-7fab-46e9-aff1-7d27ac145468', 'DENIED', 'E-404', '2026-09-20 13:57:49', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (59, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, 'fd1ee59f-84ba-46fe-a486-7452a4b1c353', 'DENIED', 'E-404', '2026-09-20 13:57:49', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (60, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '07b73b2d-79d8-4137-a8f5-4d6deb92cecc', 'DENIED', 'E-404', '2026-09-20 13:57:49', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (61, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '70a0476f-78e9-40e5-a764-665b57bb5161', 'DENIED', 'E-404', '2026-09-20 13:57:49', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (62, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '3aa0297d-ad26-498c-9514-0c93a7103ed8', 'DENIED', 'E-404', '2026-09-20 13:57:49', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (63, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, 'c22efee7-edf1-4985-a7ac-1d82692cb6e2', 'DENIED', 'E-404', '2026-09-20 13:57:49', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (64, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, 'e3675e61-41b4-4356-be57-6fa9454f45a7', 'DENIED', 'E-404', '2026-09-20 13:57:49', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (65, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '2baf1d93-fe48-469e-abb3-401846026e26', 'DENIED', 'E-404', '2026-09-20 13:57:49', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (66, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, 'b27d61c3-db6d-46c7-982f-2645b5c9103b', 'DENIED', 'E-404', '2026-09-20 13:57:49', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (67, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '39613b2d-0bc4-4573-8053-49c2d62d80ef', 'DENIED', 'E-404', '2026-09-20 13:57:49', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (68, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '7baea17c-4bb5-4461-8f75-e15cd6080c33', 'DENIED', 'E-404', '2026-09-20 13:57:49', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (69, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, 'c21dd131-45cf-4593-a2cd-8c757fcec2b6', 'DENIED', 'E-404', '2026-09-20 13:57:50', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (70, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '31cf3729-fe32-4753-ab67-1fdb1c42437b', 'DENIED', 'E-404', '2026-09-20 13:57:50', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (71, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, 'ce1a5aed-4cd9-4de3-970d-63ad87a2ba94', 'DENIED', 'E-404', '2026-09-20 13:57:50', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (72, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '9216920c-15bd-4187-9318-e34bc7d6c278', 'DENIED', 'E-404', '2026-09-20 13:57:50', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (73, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '4cf9f4ea-da3b-49c5-a992-43ab24fb58b3', 'DENIED', 'E-404', '2026-09-20 13:57:50', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (74, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, 'f8d13c4d-bb29-4527-85fb-7d884745e8b7', 'DENIED', 'E-404', '2026-09-20 13:57:50', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (75, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '0f35d417-aba9-4942-b453-472657bd36ae', 'DENIED', 'E-404', '2026-09-20 13:57:50', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (76, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '62fdd144-8beb-4c42-b72e-0cb61254cc07', 'DENIED', 'E-404', '2026-09-20 13:57:50', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (77, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '3896847e-aa57-48cb-af7d-322ae443f260', 'DENIED', 'E-404', '2026-09-20 13:57:50', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (78, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '7aa7f0df-19f8-4cd6-b201-b0a5886f616a', 'DENIED', 'E-404', '2026-09-20 13:57:50', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (79, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '47bdd34e-4c32-49a7-9225-df9a4b642867', 'DENIED', 'E-404', '2026-09-20 13:57:50', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (80, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, 'bd3f24ec-68df-4c6d-b51f-a5a502945c84', 'DENIED', 'E-404', '2026-09-20 13:57:50', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (81, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '30fbde3f-a473-45f2-9c4b-435ca597b021', 'DENIED', 'E-404', '2026-09-20 13:57:50', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (82, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '7f5817b7-7fc0-42c5-8839-05027e18d72d', 'DENIED', 'E-404', '2026-09-20 13:57:51', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (83, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '38e86941-f512-4d08-98fb-a10728b9e7d6', 'DENIED', 'E-404', '2026-09-20 13:57:51', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (84, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, 'ae0f6d86-ecda-45ac-a855-a1d8bff4ea1e', 'DENIED', 'E-404', '2026-09-20 13:57:51', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (85, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '14e3e0e6-1db6-4f43-baf4-25d708203827', 'DENIED', 'E-404', '2026-09-20 13:57:51', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (86, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '0adc9284-21ad-490c-b674-e0ced2e44628', 'DENIED', 'E-404', '2026-09-20 13:57:51', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (87, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '082ce768-f268-4075-a673-173ea736a600', 'DENIED', 'E-404', '2026-09-20 13:57:51', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (88, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '6159d0ba-9e5d-4548-b57f-74072da5e4c7', 'DENIED', 'E-404', '2026-09-20 13:57:51', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (89, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '3edf09f3-ea90-4b2f-93b0-fa9579bed6f8', 'DENIED', 'E-404', '2026-09-20 13:57:51', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (90, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, 'a0f5a49f-d625-4102-88cb-a7b78df96186', 'DENIED', 'E-404', '2026-09-20 13:57:51', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (91, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, 'b567cc7e-6f9c-42ac-98cd-ab650500c05a', 'DENIED', 'E-404', '2026-09-20 13:57:51', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (92, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '419950e3-4e4a-4d95-b522-1a82eac1b637', 'DENIED', 'E-404', '2026-09-20 13:57:51', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (93, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '6cccbb36-0d11-4731-a841-d471ec793ff7', 'DENIED', 'E-404', '2026-09-20 13:57:51', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (94, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '4056f957-bd07-47af-81f2-f29315a7e005', 'DENIED', 'E-404', '2026-09-20 13:57:52', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (95, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '2d159135-69ae-4db8-a59b-336654c5ad0a', 'DENIED', 'E-404', '2026-09-20 13:57:52', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (96, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '00f43c5c-927e-40f6-9666-4deb4fde7043', 'DENIED', 'E-404', '2026-09-20 13:57:52', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (97, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, 'b1da9a2a-9ade-4a0a-af60-b045d1c760df', 'DENIED', 'E-404', '2026-09-20 13:57:52', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (98, 2, NULL, 'LOGIN', 'USER', 2, '0:0:0:0:0:0:0:1', 'wx-login', '7d19ecd0-9e4a-4991-9798-ece311a5364b', 'SUCCESS', NULL, '2026-09-20 14:22:59', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (99, 2, 1, 'FAMILY_QUERY', 'FAMILY', 1, '0:0:0:0:0:0:0:1', 'page=1;pageSize=100', '2ea49c52-45bf-4d08-8dc9-cdcc7d6a647d', 'SUCCESS', NULL, '2026-09-20 14:23:05', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (100, 2, 1, 'FAMILY_QUERY', 'FAMILY', 1, '0:0:0:0:0:0:0:1', 'page=1;pageSize=100', 'd3010a9a-534c-4e12-b2e9-efe0134ce1e1', 'SUCCESS', NULL, '2026-09-20 14:23:10', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (101, 2, 1, 'FAMILY_QUERY', 'FAMILY', 1, '0:0:0:0:0:0:0:1', 'page=1;pageSize=100', 'e3aa9293-22d4-443f-bf02-414ecf26df63', 'SUCCESS', NULL, '2026-09-20 14:23:18', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (102, 2, 1, 'REQUEST_FAILED', NULL, NULL, '0:0:0:0:0:0:0:1', NULL, '8b83192d-ab8c-4a8a-866f-c6fd9afa82a6', 'DENIED', 'E-404', '2026-09-20 14:32:40', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (103, 2, 1, 'REQUEST_FAILED', NULL, NULL, '0:0:0:0:0:0:0:1', NULL, '3b0792b0-b1c0-4d57-948b-420838b27b97', 'DENIED', 'E-404', '2026-09-20 14:32:47', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (104, 2, NULL, 'LOGIN', 'USER', 2, '0:0:0:0:0:0:0:1', 'wx-login', '595bf2fb-df55-4b45-86af-b06404ed9a31', 'SUCCESS', NULL, '2026-09-20 14:35:40', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (105, 2, 1, 'REQUEST_FAILED', NULL, NULL, '0:0:0:0:0:0:0:1', NULL, 'aa684c7c-6715-47ea-93b4-c314bcb14e41', 'DENIED', 'E-404', '2026-09-20 14:35:40', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (106, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '43d3ff76-28b9-4df2-8291-5ce8d5ff4081', 'DENIED', 'E-404', '2026-09-20 14:36:38', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (107, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '39693eab-7f9f-4c76-9964-750b0b2d600a', 'DENIED', 'E-404', '2026-09-20 14:36:38', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (108, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '6f765f00-ef58-482d-8da7-6bc34c77bcc1', 'DENIED', 'E-404', '2026-09-20 14:36:38', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (109, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, 'a3578c7f-3c78-459a-8e8f-4b7015c28380', 'DENIED', 'E-404', '2026-09-20 14:36:38', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (110, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '46b598da-fede-43a5-bf54-d5613cb6efe6', 'DENIED', 'E-404', '2026-09-20 14:36:38', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (111, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '28377dad-1804-46e8-914c-2afd2f6eb8d9', 'DENIED', 'E-404', '2026-09-20 14:36:38', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (112, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '5b764a65-2ff1-4753-8d04-0c6d84fa0e2e', 'DENIED', 'E-404', '2026-09-20 14:36:39', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (113, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '413740be-fe53-4fa5-9065-916072b3a139', 'DENIED', 'E-404', '2026-09-20 14:36:39', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (114, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, 'c6a7ea5b-d1d7-45bf-a26b-3e4826a4bbe9', 'DENIED', 'E-404', '2026-09-20 14:36:39', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (115, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '5e8ba159-a0e2-42f0-a18a-8019bf582161', 'DENIED', 'E-404', '2026-09-20 14:36:39', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (116, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '98bb74fb-87af-49d9-a475-9111e48823cf', 'DENIED', 'E-404', '2026-09-20 14:36:39', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (117, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, 'b3baab61-e73f-48fc-8108-abc7b04aea6e', 'DENIED', 'E-404', '2026-09-20 14:36:39', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (118, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '0e328686-be90-4c94-845d-992cf116ba94', 'DENIED', 'E-404', '2026-09-20 14:36:39', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (119, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, 'f02c18d2-08f6-40cf-9460-2eb046481d2e', 'DENIED', 'E-404', '2026-09-20 14:36:39', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (120, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '89aa71a5-9490-4575-8220-151f30090eb2', 'DENIED', 'E-404', '2026-09-20 14:36:39', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (121, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '7b9eabe0-d32e-487e-b180-5fb7e3131af9', 'DENIED', 'E-404', '2026-09-20 14:36:39', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (122, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, 'd452f305-7d00-4ca2-8eb2-ebc942e2c448', 'DENIED', 'E-404', '2026-09-20 14:36:39', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (123, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '44aa1f7e-2b02-4fd7-8cdf-a076e4d885d0', 'DENIED', 'E-404', '2026-09-20 14:36:39', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (124, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '93f96603-161a-4ed1-9633-b5b5981093cb', 'DENIED', 'E-404', '2026-09-20 14:36:40', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (125, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '09ca22e1-ebe0-4d6c-a1e7-3c1f0afa695b', 'DENIED', 'E-404', '2026-09-20 14:36:40', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (126, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '3046e7e6-7681-4cae-b660-cca4b70db703', 'DENIED', 'E-404', '2026-09-20 14:36:40', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (127, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '1e60ade3-b59b-4870-b05e-8d9e85e27b4e', 'DENIED', 'E-404', '2026-09-20 14:36:40', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (128, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '16ff15c2-696d-47af-b35c-496e449492c0', 'DENIED', 'E-404', '2026-09-20 14:36:40', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (129, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '8903f50a-067c-44a3-bbc1-822f7d99482f', 'DENIED', 'E-404', '2026-09-20 14:36:40', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (130, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '9668469f-c41e-4124-8323-cc883e2bfd6b', 'DENIED', 'E-404', '2026-09-20 14:36:41', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (131, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '64547329-c47b-45c8-8a4f-90ef050cfdde', 'DENIED', 'E-404', '2026-09-20 14:36:41', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (132, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '5521a24e-283f-4e51-9266-88b61d4ad2df', 'DENIED', 'E-404', '2026-09-20 14:36:41', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (133, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, 'f7ae3d4e-191e-44ed-b6ef-84ce35523cf9', 'DENIED', 'E-404', '2026-09-20 14:36:41', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (134, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, 'ef5ecda9-aca4-438c-8255-35a78e613e88', 'DENIED', 'E-404', '2026-09-20 14:36:41', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (135, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '6bf47d93-25b5-46a5-a616-a737d654abab', 'DENIED', 'E-404', '2026-09-20 14:36:41', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (136, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '158b5e65-3d52-4caa-948e-e4b33a1211c5', 'DENIED', 'E-404', '2026-09-20 14:36:41', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (137, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '8deb9ba4-9f90-4b81-a6f4-7960a039987e', 'DENIED', 'E-404', '2026-09-20 14:36:41', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (138, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '310a0cdd-8c2e-48f0-a9a3-6ecfc5a80d3c', 'DENIED', 'E-404', '2026-09-20 14:36:41', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (139, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '42a67bcc-0a45-4c1e-9817-2a98b5ffbc9c', 'DENIED', 'E-404', '2026-09-20 14:36:41', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (140, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, 'a890d57f-bf7d-469c-b505-4c2a6833c255', 'DENIED', 'E-404', '2026-09-20 14:36:41', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (141, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, 'ab528e14-609e-4b75-b7d1-67c3ece2a57c', 'DENIED', 'E-404', '2026-09-20 14:36:41', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (142, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, 'e453c04c-a7c7-4615-9bf0-aab50146152b', 'DENIED', 'E-404', '2026-09-20 14:36:41', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (143, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, 'fb8ac328-5e3c-4651-964b-7b868f50bed4', 'DENIED', 'E-404', '2026-09-20 14:36:42', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (144, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '7d982be6-f7e4-46c0-9f7c-36a80ef9046c', 'DENIED', 'E-404', '2026-09-20 14:36:42', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (145, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, 'b733be35-a2d8-41fa-bbc0-8d206adb2fc5', 'DENIED', 'E-404', '2026-09-20 14:36:42', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (146, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '4f8ff13d-4f94-47e4-a600-088140db0757', 'DENIED', 'E-404', '2026-09-20 14:36:42', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (147, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '603aca8c-f963-4346-83b9-a3a5e1fb3170', 'DENIED', 'E-404', '2026-09-20 14:36:42', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (148, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '266153b2-a6eb-42da-a3f9-be670246cfb6', 'DENIED', 'E-404', '2026-09-20 14:36:42', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (149, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '26ada773-286e-4188-85bb-e3bed340064a', 'DENIED', 'E-404', '2026-09-20 14:36:42', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (150, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '27a66946-8e30-4ed6-ba4d-8362d6781b3e', 'DENIED', 'E-404', '2026-09-20 14:36:42', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (151, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '32d22bc9-467b-4b03-8209-d2a22b746cd8', 'DENIED', 'E-404', '2026-09-20 14:36:42', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (152, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, 'ac2f7be6-8c65-486a-8954-8174972ac7cc', 'DENIED', 'E-404', '2026-09-20 14:36:42', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (153, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '3fc9d269-7c2e-4e7c-87ca-268014377a09', 'DENIED', 'E-404', '2026-09-20 14:36:42', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (154, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '033ce76c-69fd-4c0d-a980-05c71f3320d3', 'DENIED', 'E-404', '2026-09-20 14:36:42', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (155, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, 'f1b239ea-d754-4536-9d80-2a271b762b5c', 'DENIED', 'E-404', '2026-09-20 14:36:42', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (156, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, 'd01e5418-42fc-4695-81c8-1474df2fe342', 'DENIED', 'E-404', '2026-09-20 14:36:43', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (157, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, 'cd994c5a-c5d6-429a-b761-14840261c469', 'DENIED', 'E-404', '2026-09-20 14:36:43', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (158, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '46ef5bff-3c0a-4c8f-92f1-a2a0af54442b', 'DENIED', 'E-404', '2026-09-20 14:36:43', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (159, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '1821499e-fba3-4014-8e6c-6d5b15726499', 'DENIED', 'E-404', '2026-09-20 14:36:43', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (160, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '59d7709d-b2af-4e5d-97a1-f87e1f75c9f8', 'DENIED', 'E-404', '2026-09-20 14:36:43', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (161, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, 'f00fd710-063d-4e0a-a867-a24bf562afdb', 'DENIED', 'E-404', '2026-09-20 14:36:43', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (162, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '35653452-a49b-41e2-abcb-dae83da19190', 'DENIED', 'E-404', '2026-09-20 14:36:43', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (163, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '8b8f5511-6d8b-43be-9a67-83cd3f650fcb', 'DENIED', 'E-404', '2026-09-20 14:36:43', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (164, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '134eabd5-7bae-4558-b883-f483dcba3e00', 'DENIED', 'E-404', '2026-09-20 14:36:43', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (165, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, 'f0cd07d6-4138-43a6-9b1a-4fe505c14e35', 'DENIED', 'E-404', '2026-09-20 14:36:43', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (166, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '44900fb7-7338-49ee-8e09-a81ba4d666ee', 'DENIED', 'E-404', '2026-09-20 14:36:43', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (167, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '4a45f543-24d5-4634-bdc2-a311605791a4', 'DENIED', 'E-404', '2026-09-20 14:36:43', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (168, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '7c122e7a-4ba3-4083-8a50-c36291fb2886', 'DENIED', 'E-404', '2026-09-20 14:36:44', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (169, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, 'dc659ee4-bd8c-44fe-9bc6-f4d4ab6a2492', 'DENIED', 'E-404', '2026-09-20 14:36:44', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (170, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, 'ee100e6a-c1fe-4cec-908c-d52ca9215207', 'DENIED', 'E-404', '2026-09-20 14:36:44', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (171, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, 'ab50e618-9e5c-47c8-855f-a0ac10c71b95', 'DENIED', 'E-404', '2026-09-20 14:36:44', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (172, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, 'f22977fe-f442-44e1-a11f-49726a3baf53', 'DENIED', 'E-404', '2026-09-20 14:36:44', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (173, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, 'ec989ab4-d378-41d0-bd57-025d05c28656', 'DENIED', 'E-404', '2026-09-20 14:36:44', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (174, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, 'acb5d707-796c-441f-bf66-1a75af106c15', 'DENIED', 'E-404', '2026-09-20 14:36:44', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (175, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, 'f34b6c48-8e37-4a82-93d5-07fec9f9823c', 'DENIED', 'E-404', '2026-09-20 14:36:44', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (176, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '9399bdea-5e77-41f0-b898-24e85fe0cc49', 'DENIED', 'E-404', '2026-09-20 14:36:45', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (177, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '9983f989-1b8d-40f7-afdd-f3a285664881', 'DENIED', 'E-404', '2026-09-20 14:36:45', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (178, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '186aa3af-9100-4de5-b21b-cd38d05d7d75', 'DENIED', 'E-404', '2026-09-20 14:36:45', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (179, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, 'b0cc6464-569b-45a2-bfdc-0e5cacfbc777', 'DENIED', 'E-404', '2026-09-20 14:36:45', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (180, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, 'd5fc3109-3231-4d14-8202-3cc55f680588', 'DENIED', 'E-404', '2026-09-20 14:36:45', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (181, 2, 1, 'REQUEST_FAILED', NULL, NULL, '0:0:0:0:0:0:0:1', NULL, '40ad2389-07e6-436c-8db0-d6bb6a8d5736', 'DENIED', 'E-404', '2026-09-20 14:36:55', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (182, 2, NULL, 'LOGIN', 'USER', 2, '0:0:0:0:0:0:0:1', 'wx-login', '89466854-9f51-41a3-aa06-8fe913d48673', 'SUCCESS', NULL, '2026-09-20 14:39:01', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (183, 2, NULL, 'LOGIN', 'USER', 2, '0:0:0:0:0:0:0:1', 'wx-login', 'f11f8d89-e8a8-458b-aad8-2cc1c7a2297a', 'SUCCESS', NULL, '2026-09-20 15:05:15', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (184, 2, 1, 'FAMILY_QUERY', 'FAMILY', 1, '0:0:0:0:0:0:0:1', 'page=1;pageSize=100', '374b71ee-955e-4004-8c6f-ee9e591e2f4b', 'SUCCESS', NULL, '2026-09-20 15:05:23', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (185, 2, 1, 'REQUEST_FAILED', NULL, NULL, '0:0:0:0:0:0:0:1', NULL, '6933cfc9-d28e-4e6f-9666-d1ab06aa495c', 'DENIED', 'E-400', '2026-09-20 15:05:30', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (186, 2, 1, 'MENU_MAINTENANCE_QUERY', 'MENU', NULL, '0:0:0:0:0:0:0:1', 'empty', '34ee0d69-a9bd-4242-8e1d-b8764a75493c', 'SUCCESS', NULL, '2026-09-20 15:05:37', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (187, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, '2985f87d-809e-4e46-827f-7002f43854e0', 'DENIED', 'E-404', '2026-09-20 15:06:38', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (188, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, '4c2388e1-413e-458e-b39a-4c6d18609d7d', 'DENIED', 'E-404', '2026-09-20 15:06:38', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (189, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, '2375fb75-84b0-4abe-a7a1-3acffd3bdbf7', 'DENIED', 'E-404', '2026-09-20 15:06:38', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (190, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, '732c2c12-3b33-4f02-a7af-4fd327a7400a', 'DENIED', 'E-404', '2026-09-20 15:06:38', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (191, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, '2dfaf434-51ef-49bc-aabd-c0b2cefbed84', 'DENIED', 'E-404', '2026-09-20 15:06:38', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (192, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, '5981f079-a97a-4740-9db5-d6450b796bb6', 'DENIED', 'E-404', '2026-09-20 15:06:39', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (193, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, 'c7e1366a-8c09-47da-b2c3-f7e816691960', 'DENIED', 'E-404', '2026-09-20 15:06:39', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (194, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, '91dec092-63f5-445e-beac-e50675e4cec5', 'DENIED', 'E-404', '2026-09-20 15:06:39', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (195, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, '7d27cdbe-820c-4fd9-9f95-75a95e1e33ed', 'DENIED', 'E-404', '2026-09-20 15:06:39', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (196, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, '2d1a53d2-ba07-4500-8e8a-1b8676abc5aa', 'DENIED', 'E-404', '2026-09-20 15:06:39', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (197, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, '568a3915-544e-4f90-8c54-0ac9d560fce1', 'DENIED', 'E-404', '2026-09-20 15:06:39', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (198, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, '96c9e4d5-ddeb-46de-b294-483407ae72a3', 'DENIED', 'E-404', '2026-09-20 15:06:40', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (199, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, 'e667836a-66e3-498c-b407-a2ada5c31778', 'DENIED', 'E-404', '2026-09-20 15:06:40', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (200, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, 'c08c5a30-2eb9-4964-9ead-214495438d34', 'DENIED', 'E-404', '2026-09-20 15:06:40', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (201, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, 'd8afaf1b-0a66-496e-aa77-efdae54fd789', 'DENIED', 'E-404', '2026-09-20 15:06:40', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (202, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, '9df67697-62b0-47d0-bc12-21265d15bc65', 'DENIED', 'E-404', '2026-09-20 15:06:41', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (203, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, '0ec366fe-279e-442e-ae78-69c4c55b0f14', 'DENIED', 'E-404', '2026-09-20 15:06:41', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (204, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, '6d55e90f-7980-47ce-9944-a2d77e519668', 'DENIED', 'E-404', '2026-09-20 15:06:41', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (205, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, '05092837-db25-459d-80cc-18fec90c050e', 'DENIED', 'E-404', '2026-09-20 15:06:41', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (206, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, '24c5c7a3-ff40-4aac-bc94-3fe6cec0eeb2', 'DENIED', 'E-404', '2026-09-20 15:06:42', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (207, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, '189d9e15-0db4-47a2-8d28-fc5724016810', 'DENIED', 'E-404', '2026-09-20 15:06:42', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (208, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, 'a799b77c-62f4-4d79-88b3-2bb8164cc4ea', 'DENIED', 'E-404', '2026-09-20 15:06:42', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (209, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, '0df9b326-573a-4e89-b291-59c068de15fb', 'DENIED', 'E-404', '2026-09-20 15:06:42', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (210, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, '8d2858a9-6f5d-48f2-b6ff-7680e91eda57', 'DENIED', 'E-404', '2026-09-20 15:06:42', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (211, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, '72fe3094-4722-4c89-988f-4be8e58a8cab', 'DENIED', 'E-404', '2026-09-20 15:06:42', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (212, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, '1fd46576-aefd-4bf5-870b-7a36b64d27f8', 'DENIED', 'E-404', '2026-09-20 15:06:42', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (213, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, '47c1d812-fa26-4c3e-be10-8c7b65f7be20', 'DENIED', 'E-404', '2026-09-20 15:06:42', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (214, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, 'b67fad01-f7dd-4427-b019-ee1c34a584a1', 'DENIED', 'E-404', '2026-09-20 15:06:42', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (215, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, '30376519-eacd-4233-8943-4c9914296271', 'DENIED', 'E-404', '2026-09-20 15:06:42', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (216, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, '8c656430-a5ad-4d80-affc-3827347f4375', 'DENIED', 'E-404', '2026-09-20 15:06:43', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (217, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, '7c441810-9d08-4b87-8587-9e004a1b4e02', 'DENIED', 'E-404', '2026-09-20 15:06:43', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (218, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, '2bbc0033-8c89-432d-8125-8e4f09cfb19d', 'DENIED', 'E-404', '2026-09-20 15:06:43', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (219, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, 'a5161c90-20dc-4741-8320-c2435e1aa098', 'DENIED', 'E-404', '2026-09-20 15:06:43', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (220, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, 'f4e8d80b-935f-4cde-9830-d7e68a5e06bb', 'DENIED', 'E-404', '2026-09-20 15:06:43', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (221, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, 'baedc005-f476-457e-bff9-f1c68c3ba6bf', 'DENIED', 'E-404', '2026-09-20 15:06:43', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (222, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, '124d6f8e-8e2f-4633-a6ff-e62c611fa312', 'DENIED', 'E-404', '2026-09-20 15:06:43', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (223, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, '7d72036d-36f7-48ae-a9f4-00f6c18952f1', 'DENIED', 'E-404', '2026-09-20 15:06:43', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (224, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, 'a967dc0a-2dcc-469d-9922-ea0c0738a1df', 'DENIED', 'E-404', '2026-09-20 15:06:43', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (225, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, '82f44602-0379-4af6-9b5d-a246ae750e68', 'DENIED', 'E-404', '2026-09-20 15:06:43', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (226, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, '6abfe210-0917-4e35-8751-32dc2b34f0b0', 'DENIED', 'E-404', '2026-09-20 15:06:43', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (227, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, '0d607e65-2521-428f-8a05-b8194d17a477', 'DENIED', 'E-404', '2026-09-20 15:06:43', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (228, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, 'a5680613-5289-4302-951f-b2d8cdf35869', 'DENIED', 'E-404', '2026-09-20 15:06:43', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (229, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, '8eb62ff2-8f49-4c5a-95e2-41ee8fa9b897', 'DENIED', 'E-404', '2026-09-20 15:06:44', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (230, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, 'c8dc9173-e071-4fad-8d35-ba7879d98b3a', 'DENIED', 'E-404', '2026-09-20 15:06:44', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (231, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, '81bf7ab4-8c15-43ca-9011-95698d39adc4', 'DENIED', 'E-404', '2026-09-20 15:06:44', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (232, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, '304e8df7-4340-4835-b69f-a1acbd72f111', 'DENIED', 'E-404', '2026-09-20 15:06:44', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (233, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, 'd8bf154a-fba2-4423-b4ea-11640a6fca4c', 'DENIED', 'E-404', '2026-09-20 15:06:44', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (234, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, '0a4cb24a-fbb0-4dc7-8d5b-904c742b1320', 'DENIED', 'E-404', '2026-09-20 15:06:44', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (235, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, '9d3a409b-22d7-44a9-8477-4a0885a16674', 'DENIED', 'E-404', '2026-09-20 15:06:44', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (236, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, 'e97f7ae8-b4e2-4bd1-bb59-aa68451f25e1', 'DENIED', 'E-404', '2026-09-20 15:06:44', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (237, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, 'ea86af32-3c88-44bf-b906-b8e72bec6215', 'DENIED', 'E-404', '2026-09-20 15:06:44', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (238, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, 'aedfcfeb-e0bf-4c2c-880e-fb6eed185a31', 'DENIED', 'E-404', '2026-09-20 15:06:44', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (239, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, 'f4fb57e3-3716-4162-9ec2-233bbc37b2ad', 'DENIED', 'E-404', '2026-09-20 15:06:44', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (240, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, '9f0ed0b6-20e8-4d29-b125-69c70c6ad9c6', 'DENIED', 'E-404', '2026-09-20 15:06:44', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (241, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, 'a9926c2c-6e8e-462d-b4ec-c3666b5d902f', 'DENIED', 'E-404', '2026-09-20 15:06:44', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (242, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, 'd2d3d62c-c4e6-4eb4-852b-452bdb4c8bb5', 'DENIED', 'E-404', '2026-09-20 15:06:44', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (243, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, 'c0a6b220-fdce-478c-8c89-6597746e8c67', 'DENIED', 'E-404', '2026-09-20 15:06:44', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (244, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, '561b6343-0973-4d96-9b50-429714d6ebed', 'DENIED', 'E-404', '2026-09-20 15:06:45', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (245, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, '5a6dccbf-9d29-4010-8451-bd37411f10d0', 'DENIED', 'E-404', '2026-09-20 15:06:45', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (246, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, '4f34eda4-140e-4dc1-a9bf-b8b29c1075c4', 'DENIED', 'E-404', '2026-09-20 15:06:45', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (247, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, '382433cf-78c7-4134-9ced-438da522a8ff', 'DENIED', 'E-404', '2026-09-20 15:06:45', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (248, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, '5f9da834-9c67-4747-9f17-164a5f497b40', 'DENIED', 'E-404', '2026-09-20 15:06:45', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (249, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, '9b9cb7eb-0e71-4031-aa5a-238a205882d0', 'DENIED', 'E-404', '2026-09-20 15:06:45', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (250, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, 'cc7abbf2-c098-4448-bee4-8114e604ed12', 'DENIED', 'E-404', '2026-09-20 15:06:45', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (251, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, '3711d8db-2dcb-4048-aa13-959b367d0c02', 'DENIED', 'E-404', '2026-09-20 15:06:45', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (252, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, '5072f814-4e7b-461b-b0cf-6a75c9416b52', 'DENIED', 'E-404', '2026-09-20 15:06:45', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (253, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, '28907d52-c168-4205-9f75-315cbe800c5b', 'DENIED', 'E-404', '2026-09-20 15:06:45', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (254, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, 'c102d3d3-4f4b-4885-a009-ce9681d7ce75', 'DENIED', 'E-404', '2026-09-20 15:06:45', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (255, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, 'ac6f3e92-c7e4-4d51-a679-21f1bf3fdbbd', 'DENIED', 'E-404', '2026-09-20 15:06:45', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (256, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, '97955187-337b-4860-b285-577c4cccae7d', 'DENIED', 'E-404', '2026-09-20 15:06:45', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (257, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, '909f9bc4-f282-4820-ba88-860087aef7f7', 'DENIED', 'E-404', '2026-09-20 15:06:46', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (258, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, 'bf7c4b3b-7b39-4613-91cd-13f5297b66ac', 'DENIED', 'E-404', '2026-09-20 15:06:46', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (259, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, 'a67c941a-0363-49a2-9a3c-77d178875d7d', 'DENIED', 'E-404', '2026-09-20 15:06:46', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (260, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, '97accb17-a6dd-4698-8bb5-e11a7dcd897f', 'DENIED', 'E-404', '2026-09-20 15:06:46', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (261, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, '1fcf6e44-74f8-442e-82df-cdd539e63f1f', 'DENIED', 'E-404', '2026-09-20 15:06:46', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (262, 2, 1, 'MENU_MAINTENANCE_QUERY', 'MENU', NULL, '0:0:0:0:0:0:0:1', 'empty', 'd2aa3f9a-6624-4198-809c-fa9ce00a0e1a', 'SUCCESS', NULL, '2026-09-20 15:21:55', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (263, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, '35af0e99-96af-4629-984e-2f12a43f07e1', 'DENIED', 'E-404', '2026-09-20 17:06:50', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (264, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, 'e41bb193-bd1b-4448-9536-5c6f4030ab35', 'DENIED', 'E-404', '2026-09-20 17:06:50', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (265, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, '95b44ad1-3dd5-4cac-a71e-c50fa3ca6d16', 'DENIED', 'E-404', '2026-09-20 17:06:51', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (266, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, 'f323a043-5926-4fd5-97e2-341085c0962a', 'DENIED', 'E-404', '2026-09-20 17:06:51', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (267, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, '9ad9799d-1671-4600-889a-e1fe341f5bd4', 'DENIED', 'E-404', '2026-09-20 17:06:51', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (268, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, '3d839afb-dc08-444a-83fc-98c2b634301a', 'DENIED', 'E-404', '2026-09-20 17:06:51', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (269, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, '1d055aba-4542-4c75-9c37-99cbffdfaddd', 'DENIED', 'E-404', '2026-09-20 17:06:51', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (270, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, '3424c71a-cb58-482b-baae-a7cb779d7a21', 'DENIED', 'E-404', '2026-09-20 17:06:51', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (271, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, 'd8f1032a-7262-4a28-8287-c4e9c8bd845e', 'DENIED', 'E-404', '2026-09-20 17:06:51', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (272, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, '65194e36-8566-4930-8c06-3b39440cad3e', 'DENIED', 'E-404', '2026-09-20 17:06:51', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (273, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, 'd6cdd276-b5de-464d-b8d4-1e43e5d7af37', 'DENIED', 'E-404', '2026-09-20 17:06:51', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (274, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, '0016d216-e074-423c-9a88-fdf59f4e231c', 'DENIED', 'E-404', '2026-09-20 17:06:51', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (275, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, 'b4794ef1-b2ac-46cd-b7ba-53de5dfc5de5', 'DENIED', 'E-404', '2026-09-20 17:06:51', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (276, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, 'd58e467e-23e4-494b-b9ce-6b42358949ac', 'DENIED', 'E-404', '2026-09-20 17:06:52', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (277, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, '03174a74-59e3-4a47-bf20-030f32979774', 'DENIED', 'E-404', '2026-09-20 17:06:52', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (278, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, '9a2e5386-5d6f-40dd-b767-34f2d1387473', 'DENIED', 'E-404', '2026-09-20 17:06:52', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (279, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, '168f6be0-a9d1-43f6-8d8b-297791dd9c94', 'DENIED', 'E-404', '2026-09-20 17:06:52', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (280, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, 'c7d64d52-3b81-4931-8e16-3a227f774247', 'DENIED', 'E-404', '2026-09-20 17:06:52', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (281, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, '6f87d5ce-905b-403b-89cb-970fe3f03b71', 'DENIED', 'E-404', '2026-09-20 17:06:52', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (282, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, 'ddff677f-d445-410e-ad79-d6b7654e0cd5', 'DENIED', 'E-404', '2026-09-20 17:06:53', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (283, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, '63c6c0b5-9ea8-4862-9d5e-d66cd1c32b21', 'DENIED', 'E-404', '2026-09-20 17:06:53', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (284, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, 'dc162ccc-7f40-4337-ab87-56570bb3d45c', 'DENIED', 'E-404', '2026-09-20 17:06:53', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (285, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, '5b78c314-b86c-4169-8572-31505072f24e', 'DENIED', 'E-404', '2026-09-20 17:06:53', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (286, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, 'ad15f4c2-a80d-42c9-b8e0-7f6f6d35dcd6', 'DENIED', 'E-404', '2026-09-20 17:06:53', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (287, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, '8b257034-480e-4c91-bf9a-91fa91f49d29', 'DENIED', 'E-404', '2026-09-20 17:06:53', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (288, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, '8251c8ce-69f9-449d-9bd9-1cf30d2837e6', 'DENIED', 'E-404', '2026-09-20 17:06:53', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (289, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, '3e96bef5-cbc8-4b28-95e8-cd5d6269e7ad', 'DENIED', 'E-404', '2026-09-20 17:06:53', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (290, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, 'dd2a1c98-3dc1-4cbc-a42e-53113f3f7cc5', 'DENIED', 'E-404', '2026-09-20 17:06:53', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (291, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, 'c2451941-80d0-4f3a-91c8-fd41bd722909', 'DENIED', 'E-404', '2026-09-20 17:06:54', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (292, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, '07d624f8-ec50-4318-b3c5-2abcde982d77', 'DENIED', 'E-404', '2026-09-20 17:06:54', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (293, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, '9af845ee-fb5a-48d1-8a6c-37cf8bd55107', 'DENIED', 'E-404', '2026-09-20 17:06:54', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (294, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, 'a17ff302-c7bc-4042-9e54-384743b0c8cb', 'DENIED', 'E-404', '2026-09-20 17:06:54', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (295, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, '703f80b6-825d-428e-9c2a-ad9885af2bf4', 'DENIED', 'E-404', '2026-09-20 17:06:54', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (296, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, '18cfccd9-6647-4de9-be41-62d3bd083e88', 'DENIED', 'E-404', '2026-09-20 17:06:54', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (297, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, 'cff34d36-639f-4147-a7dd-600b99144b73', 'DENIED', 'E-404', '2026-09-20 17:06:54', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (298, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, 'e4cf7673-be88-4c61-b44f-5445ddcd0b8b', 'DENIED', 'E-404', '2026-09-20 17:06:54', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (299, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, 'a0dfd0d9-1bea-42ce-912f-73df0736ab0a', 'DENIED', 'E-404', '2026-09-20 17:06:54', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (300, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, '03aa347c-2bfa-457c-a382-df635c3705d6', 'DENIED', 'E-404', '2026-09-20 17:06:54', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (301, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, 'ebe9143c-03c2-457d-9829-1dc840944d52', 'DENIED', 'E-404', '2026-09-20 17:06:54', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (302, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, 'c78944b0-fa20-44cf-8fec-e04a93b986a7', 'DENIED', 'E-404', '2026-09-20 17:06:54', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (303, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, '283ac140-069c-4ab0-b00f-02cee5e85584', 'DENIED', 'E-404', '2026-09-20 17:06:54', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (304, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, 'a38d1157-9f65-477b-8712-4730bfdfe612', 'DENIED', 'E-404', '2026-09-20 17:06:55', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (305, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, 'ae347469-4eba-4653-99e2-97285a8799e7', 'DENIED', 'E-404', '2026-09-20 17:06:55', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (306, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, 'eb65c27d-f228-41fc-a262-2ced8bfb270c', 'DENIED', 'E-404', '2026-09-20 17:06:55', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (307, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, '5ba6437b-e5bf-493d-8c6d-5d91ed731e3a', 'DENIED', 'E-404', '2026-09-20 17:06:55', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (308, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, '86d13b97-a48a-40c2-abb9-2e60b509dc02', 'DENIED', 'E-404', '2026-09-20 17:06:55', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (309, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, 'af0e283a-e998-447a-a5a3-0f3f280c8aa7', 'DENIED', 'E-404', '2026-09-20 17:06:55', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (310, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, '399f7d7b-6429-431c-b117-50459468e55a', 'DENIED', 'E-404', '2026-09-20 17:06:55', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (311, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, '06f6b155-8b4a-4b4f-9eee-8a9b3fd1bc1c', 'DENIED', 'E-404', '2026-09-20 17:06:55', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (312, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, 'ace5d04d-beae-44e9-981f-75978578179c', 'DENIED', 'E-404', '2026-09-20 17:06:55', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (313, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, '147b3a65-04c1-4492-b83c-63e3e6498380', 'DENIED', 'E-404', '2026-09-20 17:06:55', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (314, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, 'eb1edaa7-3a18-4916-92a0-c03f9c331e66', 'DENIED', 'E-404', '2026-09-20 17:06:55', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (315, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, '608962ba-684b-4e2f-871c-a075904008d8', 'DENIED', 'E-404', '2026-09-20 17:06:55', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (316, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, 'b57fc957-d5f5-409d-a2c9-2faa1b8913de', 'DENIED', 'E-404', '2026-09-20 17:06:55', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (317, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, '8882964a-39a0-4520-a8bd-2f6c4712d963', 'DENIED', 'E-404', '2026-09-20 17:06:55', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (318, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, 'e074f6d3-7b5c-42db-9f70-b8365938c530', 'DENIED', 'E-404', '2026-09-20 17:06:56', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (319, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, '62840b92-e8cc-4e28-a0bb-89c7cebebf8f', 'DENIED', 'E-404', '2026-09-20 17:06:56', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (320, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, '9ff95746-de50-4924-8ded-8528b9a01b5f', 'DENIED', 'E-404', '2026-09-20 17:06:56', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (321, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, 'f8b9f5e2-8bc4-4aab-b173-31cd7e346624', 'DENIED', 'E-404', '2026-09-20 17:06:56', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (322, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, '28e2ec72-0d80-49cd-8433-271d27b77fc9', 'DENIED', 'E-404', '2026-09-20 17:06:56', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (323, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, 'df341fc7-4238-4ffd-9bc5-8a800ca2f9d7', 'DENIED', 'E-404', '2026-09-20 17:06:56', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (324, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, '52cecf05-fa90-4f20-9f00-77eb1aef2b32', 'DENIED', 'E-404', '2026-09-20 17:06:56', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (325, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, 'c3657042-271a-41cf-b010-5b8da889f09f', 'DENIED', 'E-404', '2026-09-20 17:06:56', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (326, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, '31e12351-1830-4491-ad65-c4c126478c5b', 'DENIED', 'E-404', '2026-09-20 17:06:56', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (327, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, '184f3293-d88f-4777-b9f2-690e894948fc', 'DENIED', 'E-404', '2026-09-20 17:06:56', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (328, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, '32da2143-cc1c-4ff7-925e-4bc5940f186d', 'DENIED', 'E-404', '2026-09-20 17:06:56', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (329, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, '24f27826-b261-44ba-a7da-d63710119972', 'DENIED', 'E-404', '2026-09-20 17:06:56', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (330, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, 'a50ce3d9-6e8c-458e-a890-b38be72cbd85', 'DENIED', 'E-404', '2026-09-20 17:06:57', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (331, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, 'c64f31d5-214c-421e-a871-2ca24e78d00f', 'DENIED', 'E-404', '2026-09-20 17:06:57', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (332, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, 'c321225d-33b4-40ea-a742-006da1a6ccd3', 'DENIED', 'E-404', '2026-09-20 17:06:57', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (333, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, '33e78220-516f-4ec7-94c3-13824ab65633', 'DENIED', 'E-404', '2026-09-20 17:06:57', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (334, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, '607778cf-7231-4c62-a094-148f5e241ca9', 'DENIED', 'E-404', '2026-09-20 17:06:57', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (335, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, 'e6217432-ec7f-4a89-a859-a25b0c7a6743', 'DENIED', 'E-404', '2026-09-20 17:06:57', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (336, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, '9059a65a-1968-4f53-8483-0896f2cd5004', 'DENIED', 'E-404', '2026-09-20 17:06:57', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (337, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.150', NULL, 'ded49951-8967-419e-b4b4-e8e85b67e2cd', 'DENIED', 'E-404', '2026-09-20 17:06:57', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (338, 2, NULL, 'LOGIN', 'USER', 2, '0:0:0:0:0:0:0:1', 'wx-login', 'baf5f4a0-fd4c-4c3e-813b-5c6ad9042d9d', 'SUCCESS', NULL, '2026-09-20 17:17:46', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (339, 2, 1, 'FAMILY_QUERY', 'FAMILY', 1, '0:0:0:0:0:0:0:1', 'page=1;pageSize=100', '434ed1ec-45c0-4c80-b48c-7425ff848752', 'SUCCESS', NULL, '2026-09-20 17:17:52', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (340, 2, 1, 'FAMILY_QUERY', 'FAMILY', 1, '0:0:0:0:0:0:0:1', 'page=1;pageSize=100', 'bee324f1-80f8-4281-a218-a1824d902f0c', 'SUCCESS', NULL, '2026-09-20 17:18:06', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (341, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.161', NULL, 'f5d3c89f-043f-48d1-8648-ee8f9254a1ee', 'DENIED', 'E-404', '2026-09-20 17:36:39', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (342, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.161', NULL, '8c7a9625-63ae-4ed1-afac-50a8714ea594', 'DENIED', 'E-404', '2026-09-20 17:36:39', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (343, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.161', NULL, '4e0744d9-3e21-46e1-934c-95fcd0ee2cbb', 'DENIED', 'E-404', '2026-09-20 17:36:39', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (344, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.161', NULL, '249f9203-24bc-4071-8a77-410b36ee9084', 'DENIED', 'E-404', '2026-09-20 17:36:39', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (345, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.161', NULL, '11df36c0-52b9-47b2-8818-ab370c3f7550', 'DENIED', 'E-404', '2026-09-20 17:36:40', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (346, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.161', NULL, '8689f595-9aea-4176-be2e-a4a06d175c80', 'DENIED', 'E-404', '2026-09-20 17:36:40', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (347, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.161', NULL, '2d2995bf-6a65-4c95-9553-d02b54765813', 'DENIED', 'E-404', '2026-09-20 17:36:40', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (348, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.161', NULL, 'b894b262-b34a-4c14-ad47-d447c2b6c9e0', 'DENIED', 'E-404', '2026-09-20 17:36:40', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (349, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.161', NULL, '2c76fb5f-390b-4270-ab97-f0574e59b684', 'DENIED', 'E-404', '2026-09-20 17:36:40', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (350, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.161', NULL, '99cc7357-6258-43c4-8ded-53a69960f9fc', 'DENIED', 'E-404', '2026-09-20 17:36:40', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (351, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.161', NULL, 'a87cafac-7f4a-4361-810e-d8e161aa02bd', 'DENIED', 'E-404', '2026-09-20 17:36:40', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (352, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.161', NULL, 'e4ce3697-48cd-4428-84a8-6b62ce09fb60', 'DENIED', 'E-404', '2026-09-20 17:36:40', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (353, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.161', NULL, 'd68d26e2-25ce-449c-84b0-bf496643c86a', 'DENIED', 'E-404', '2026-09-20 17:36:40', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (354, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.161', NULL, '143ba1bd-7ea1-4887-880b-4580e733d6bb', 'DENIED', 'E-404', '2026-09-20 17:36:41', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (355, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.161', NULL, 'f013acc0-7e93-445e-815b-0cc4070ec1b2', 'DENIED', 'E-404', '2026-09-20 17:36:41', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (356, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.161', NULL, 'ce46bc45-974f-4325-ae33-47301110e48a', 'DENIED', 'E-404', '2026-09-20 17:36:41', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (357, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.161', NULL, 'a775fb2c-128f-4378-a101-d9111f8538d2', 'DENIED', 'E-404', '2026-09-20 17:36:41', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (358, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.161', NULL, 'a0ab5d53-0adc-490f-9fec-4c961b071528', 'DENIED', 'E-404', '2026-09-20 17:36:41', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (359, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.161', NULL, '7be27c5d-6cce-4cec-87db-d0216dab782c', 'DENIED', 'E-404', '2026-09-20 17:36:41', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (360, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.161', NULL, 'faea8499-cb36-4527-b1a6-ad7e2d0e9302', 'DENIED', 'E-404', '2026-09-20 17:36:41', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (361, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.161', NULL, '10fea88a-95c9-4f23-8d5b-b3a909dd1bf9', 'DENIED', 'E-404', '2026-09-20 17:36:41', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (362, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.161', NULL, '8c6641c2-98fd-44eb-a751-ff88a51237d2', 'DENIED', 'E-404', '2026-09-20 17:36:42', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (363, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.161', NULL, '39e341de-e044-4b73-a133-6c699c6e5b29', 'DENIED', 'E-404', '2026-09-20 17:36:42', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (364, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.161', NULL, '5afac746-f528-49c5-a1e6-0bceb0c4eba7', 'DENIED', 'E-404', '2026-09-20 17:36:42', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (365, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.161', NULL, '2b126401-c545-4d4d-b925-b722f65f200d', 'DENIED', 'E-404', '2026-09-20 17:36:42', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (366, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.161', NULL, '35ef91cb-fe16-4a9e-8074-57e2f07f7a03', 'DENIED', 'E-404', '2026-09-20 17:36:42', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (367, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.161', NULL, '45671070-ee5b-482e-96b1-97ad35edcdf0', 'DENIED', 'E-404', '2026-09-20 17:36:42', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (368, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.161', NULL, '390dca9c-5316-4caf-aa75-7b622f3bf9e2', 'DENIED', 'E-404', '2026-09-20 17:36:42', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (369, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.161', NULL, '9f5c395f-c4ff-4741-9051-385777401eae', 'DENIED', 'E-404', '2026-09-20 17:36:42', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (370, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.161', NULL, '5ae44896-4e62-473c-92f2-7d080b2dd50d', 'DENIED', 'E-404', '2026-09-20 17:36:42', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (371, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.161', NULL, '6590a925-cd26-4713-bc9b-c4f34a076e3c', 'DENIED', 'E-404', '2026-09-20 17:36:42', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (372, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.161', NULL, '33ca3bdd-18ab-4bc6-8740-0a74f2de3255', 'DENIED', 'E-404', '2026-09-20 17:36:42', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (373, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.161', NULL, '95cfe5d3-ccba-4d6e-a2fa-ddc88cff4c07', 'DENIED', 'E-404', '2026-09-20 17:36:42', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (374, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.161', NULL, 'd371fb48-e3fd-49e8-81c7-c4e11aea3283', 'DENIED', 'E-404', '2026-09-20 17:36:43', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (375, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.161', NULL, '08373143-8a9b-4938-b02c-b747fc84ee56', 'DENIED', 'E-404', '2026-09-20 17:36:43', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (376, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.161', NULL, '1ea9a0a0-1df9-4156-9853-2a92d2a9b8ad', 'DENIED', 'E-404', '2026-09-20 17:36:43', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (377, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.161', NULL, '86ab593b-ae17-4044-8d69-ab0d64531de5', 'DENIED', 'E-404', '2026-09-20 17:36:43', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (378, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.161', NULL, '554ce639-a82d-46b2-8348-46000a4ab57c', 'DENIED', 'E-404', '2026-09-20 17:36:43', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (379, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.161', NULL, '2f346959-ef8c-430b-9444-c9ac8bcfa66b', 'DENIED', 'E-404', '2026-09-20 17:36:43', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (380, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.161', NULL, 'f1585a66-bca0-4924-9fde-4bab990a1a76', 'DENIED', 'E-404', '2026-09-20 17:36:43', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (381, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.161', NULL, '9079cb11-7e89-4d22-8950-0726e704ab21', 'DENIED', 'E-404', '2026-09-20 17:36:43', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (382, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.161', NULL, 'd07df09e-2b2f-4d40-b466-95a80f38ed86', 'DENIED', 'E-404', '2026-09-20 17:36:43', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (383, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.161', NULL, '1d134599-4c0c-46cc-9da9-c7a244c737bd', 'DENIED', 'E-404', '2026-09-20 17:36:43', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (384, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.161', NULL, '771e556b-3ede-44c6-8743-ca4be448fc1f', 'DENIED', 'E-404', '2026-09-20 17:36:43', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (385, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.161', NULL, 'd4c9d951-76d7-4ec9-b7c6-a33b8ffc1c58', 'DENIED', 'E-404', '2026-09-20 17:36:44', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (386, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.161', NULL, '6b4f755d-75e2-4b97-872a-ae533f4c1066', 'DENIED', 'E-404', '2026-09-20 17:36:44', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (387, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.161', NULL, '1b54c06b-78b0-49f7-b3a3-0a79b321bd4f', 'DENIED', 'E-404', '2026-09-20 17:36:44', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (388, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.161', NULL, 'b67c0e23-0e2f-4325-bab8-6650726a1e0b', 'DENIED', 'E-404', '2026-09-20 17:36:44', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (389, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.161', NULL, 'b25cfa3d-0633-4cc3-a53f-d84a9f406086', 'DENIED', 'E-404', '2026-09-20 17:36:44', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (390, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.161', NULL, 'da3d48b9-21c0-49ea-b055-4a6c74d85854', 'DENIED', 'E-404', '2026-09-20 17:36:44', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (391, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.161', NULL, '19728c75-3e07-4d2e-be7b-1d2cb85e4dad', 'DENIED', 'E-404', '2026-09-20 17:36:44', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (392, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.161', NULL, '8f0ba804-d775-4429-90b6-c4979529e7c1', 'DENIED', 'E-404', '2026-09-20 17:36:44', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (393, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.161', NULL, 'fd608587-3925-446d-a1ef-2f70b36f18bb', 'DENIED', 'E-404', '2026-09-20 17:36:44', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (394, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.161', NULL, '0c316b02-df80-46d0-8deb-75f1d4df9dde', 'DENIED', 'E-404', '2026-09-20 17:36:44', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (395, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.161', NULL, '5333da51-91f8-4e3e-b2e2-f605fcc24553', 'DENIED', 'E-404', '2026-09-20 17:36:44', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (396, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.161', NULL, '4774e59b-7f42-415f-adc8-2bc351ceb161', 'DENIED', 'E-404', '2026-09-20 17:36:44', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (397, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.161', NULL, 'c0b9cb03-2fef-46b5-b97b-5cf7d2add54d', 'DENIED', 'E-404', '2026-09-20 17:36:45', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (398, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.161', NULL, '9870e04e-8169-48d0-a812-8c09b6d042fe', 'DENIED', 'E-404', '2026-09-20 17:36:45', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (399, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.161', NULL, '4291b691-98af-43f8-bea0-893577c29a59', 'DENIED', 'E-404', '2026-09-20 17:36:45', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (400, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.161', NULL, '489210a7-afd9-49ca-81f0-57a4c07be3f0', 'DENIED', 'E-404', '2026-09-20 17:36:45', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (401, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.161', NULL, 'be93ce23-26f7-406f-8624-4029ed99054c', 'DENIED', 'E-404', '2026-09-20 17:36:45', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (402, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.161', NULL, '76d58d3d-515a-4ee1-9f0f-087a31066b4e', 'DENIED', 'E-404', '2026-09-20 17:36:45', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (403, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.161', NULL, '8bd696b7-a16a-4f78-aaaa-b9ceae763ddc', 'DENIED', 'E-404', '2026-09-20 17:36:45', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (404, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.161', NULL, '91d61471-80cb-4c91-ab90-18a0c18b9c72', 'DENIED', 'E-404', '2026-09-20 17:36:45', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (405, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.161', NULL, 'eaed5d39-a319-452f-b505-ec36447da618', 'DENIED', 'E-404', '2026-09-20 17:36:45', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (406, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.161', NULL, '08c18d30-6320-40cf-ad01-5207ee556cc8', 'DENIED', 'E-404', '2026-09-20 17:36:45', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (407, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.161', NULL, '6083cb62-8ab8-4cb7-8677-e7c91f442c28', 'DENIED', 'E-404', '2026-09-20 17:36:45', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (408, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.161', NULL, '23f7fd1c-09ea-4d1d-a79a-b32e1036bb2c', 'DENIED', 'E-404', '2026-09-20 17:36:45', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (409, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.161', NULL, '1cd5bce7-5d01-4e9b-88e8-97173ff0b6a7', 'DENIED', 'E-404', '2026-09-20 17:36:46', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (410, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.161', NULL, '6b879690-5208-49d6-9be5-6170daa32472', 'DENIED', 'E-404', '2026-09-20 17:36:46', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (411, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.161', NULL, '9d279273-d922-4751-ac98-b493566a9ce2', 'DENIED', 'E-404', '2026-09-20 17:36:46', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (412, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.161', NULL, 'f7d80312-0a9f-42fa-9632-875ce1203626', 'DENIED', 'E-404', '2026-09-20 17:36:46', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (413, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.161', NULL, 'ebb33898-0aac-4c49-af25-69083d142c45', 'DENIED', 'E-404', '2026-09-20 17:36:46', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (414, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.161', NULL, '11e3b389-eafa-43aa-bbd4-81bc54948183', 'DENIED', 'E-404', '2026-09-20 17:36:46', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (415, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.161', NULL, 'fb46ac27-097b-4e6f-9350-9857c49a3208', 'DENIED', 'E-404', '2026-09-20 17:36:46', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (416, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.155', NULL, '58d851eb-6457-466f-a20b-aebdb6a6e8df', 'DENIED', 'E-404', '2026-09-21 11:06:49', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (417, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.155', NULL, 'c86d5b82-e8e1-452e-83ec-558ae5b2ce39', 'DENIED', 'E-404', '2026-09-21 11:06:50', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (418, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.155', NULL, '921f081d-4265-4271-8b57-8e77d76462c3', 'DENIED', 'E-404', '2026-09-21 11:06:50', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (419, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.155', NULL, 'dacced04-8251-40d8-baed-e7d4236b0135', 'DENIED', 'E-404', '2026-09-21 11:06:50', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (420, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.155', NULL, '136ccf6f-4a97-4e2b-8ee3-ffa028bf0d02', 'DENIED', 'E-404', '2026-09-21 11:06:50', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (421, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.155', NULL, '0c59c4c9-f9d1-4109-b528-f6f16b95a9ae', 'DENIED', 'E-404', '2026-09-21 11:06:50', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (422, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.155', NULL, '8904486f-0572-4310-af60-1185c2b29b38', 'DENIED', 'E-404', '2026-09-21 11:06:50', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (423, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.155', NULL, 'e6d6e8de-4f5b-4e58-9892-f32a4f8a5a9f', 'DENIED', 'E-404', '2026-09-21 11:06:50', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (424, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.155', NULL, 'b78ad744-5223-4175-a936-911528e561f5', 'DENIED', 'E-404', '2026-09-21 11:06:50', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (425, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.155', NULL, 'a2dbc6ab-de38-4d25-9bb7-f35eb38a105a', 'DENIED', 'E-404', '2026-09-21 11:06:50', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (426, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.155', NULL, 'acb7c1e1-152a-4073-b59e-ba84a48a4565', 'DENIED', 'E-404', '2026-09-21 11:06:50', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (427, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.155', NULL, '0abcacd3-2cb6-4caa-94df-2cce5f07e181', 'DENIED', 'E-404', '2026-09-21 11:06:50', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (428, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.155', NULL, 'c3e0929b-feea-4320-9f20-b3fc5d6fa87d', 'DENIED', 'E-404', '2026-09-21 11:06:50', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (429, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.155', NULL, '8b6b3d4f-adc0-466a-9169-f76969c1e81a', 'DENIED', 'E-404', '2026-09-21 11:06:50', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (430, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.155', NULL, '6215d1c4-5fca-417b-bf2d-aba156921427', 'DENIED', 'E-404', '2026-09-21 11:06:50', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (431, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.155', NULL, 'a98ba67d-9a4b-4377-bdbc-6ace5534b8d9', 'DENIED', 'E-404', '2026-09-21 11:06:50', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (432, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.155', NULL, '184c0c82-fa71-4af1-8eb7-64aa982b47f9', 'DENIED', 'E-404', '2026-09-21 11:06:51', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (433, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.155', NULL, '86b8613a-8cfe-4dc6-9384-4cbea196d038', 'DENIED', 'E-404', '2026-09-21 11:06:51', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (434, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.155', NULL, 'f16bab64-e0a2-47e2-ae0d-2a666121951a', 'DENIED', 'E-404', '2026-09-21 11:06:51', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (435, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.155', NULL, 'd4af80d0-6b85-4f58-a064-7a9d1bc17360', 'DENIED', 'E-404', '2026-09-21 11:06:51', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (436, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.155', NULL, 'a762cd39-b53e-4cd3-9999-f82785bb5960', 'DENIED', 'E-404', '2026-09-21 11:06:51', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (437, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.155', NULL, '24ced6a4-8bdc-40dd-9bdb-106d655ee066', 'DENIED', 'E-404', '2026-09-21 11:06:51', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (438, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.155', NULL, '6fe43718-d166-4ee6-9ff3-9e64439e3173', 'DENIED', 'E-404', '2026-09-21 11:06:51', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (439, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.155', NULL, '0fe5d704-5bee-400a-b390-5294ba9ff1a8', 'DENIED', 'E-404', '2026-09-21 11:06:51', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (440, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.155', NULL, '72da0396-ca1f-467e-946a-51c58f34ba6f', 'DENIED', 'E-404', '2026-09-21 11:06:51', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (441, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.155', NULL, '3a7bbf55-e0e7-49fc-bb4d-fff48b842969', 'DENIED', 'E-404', '2026-09-21 11:06:51', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (442, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.155', NULL, '83261fd6-0ac8-4105-993d-58ab22a485ac', 'DENIED', 'E-404', '2026-09-21 11:06:51', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (443, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.155', NULL, 'c85ce4e6-704e-4c39-8197-74952d651402', 'DENIED', 'E-404', '2026-09-21 11:06:51', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (444, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.155', NULL, 'c6f10b68-be99-46d0-877e-fa5898fce081', 'DENIED', 'E-404', '2026-09-21 11:06:51', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (445, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.155', NULL, 'f2352ed9-79a9-4c58-aaaa-3c8a54a88d1d', 'DENIED', 'E-404', '2026-09-21 11:06:51', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (446, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.155', NULL, '8625474a-3e8e-4d1e-9145-803cafd59af9', 'DENIED', 'E-404', '2026-09-21 11:06:52', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (447, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.155', NULL, 'b149f278-1243-4354-aec8-00213b1b7aed', 'DENIED', 'E-404', '2026-09-21 11:06:52', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (448, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.155', NULL, '4fdbf84e-454c-4577-a11e-42b35e476870', 'DENIED', 'E-404', '2026-09-21 11:06:52', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (449, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.155', NULL, 'a851079c-6bed-4a2f-91e3-0d6f8eb509e8', 'DENIED', 'E-404', '2026-09-21 11:06:52', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (450, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.155', NULL, 'bab73640-3e60-4d4c-80ec-1b6a0d96a61c', 'DENIED', 'E-404', '2026-09-21 11:06:52', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (451, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.155', NULL, '18c6464d-a5a5-480d-909d-98514a1eb519', 'DENIED', 'E-404', '2026-09-21 11:06:52', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (452, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.155', NULL, '2711df01-ccec-47b7-82d1-76006d8bc758', 'DENIED', 'E-404', '2026-09-21 11:06:52', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (453, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.155', NULL, 'a54a4541-3ed8-4f1b-b310-43526f763504', 'DENIED', 'E-404', '2026-09-21 11:06:52', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (454, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.155', NULL, '58d4c592-5587-4e05-8350-5e863c922f98', 'DENIED', 'E-404', '2026-09-21 11:06:52', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (455, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.155', NULL, '1308dd37-7010-4508-88aa-8aa2f7a1ec1d', 'DENIED', 'E-404', '2026-09-21 11:06:52', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (456, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.155', NULL, 'e1932eb0-f270-40e3-b023-4375dcae16da', 'DENIED', 'E-404', '2026-09-21 11:06:52', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (457, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.155', NULL, 'b36859ff-b766-4a96-b33a-3afaf4edc9cb', 'DENIED', 'E-404', '2026-09-21 11:06:52', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (458, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.155', NULL, '68cb327b-e181-4f20-a854-73433e2b44ac', 'DENIED', 'E-404', '2026-09-21 11:06:52', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (459, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.155', NULL, '3caeeee3-48ac-4279-8a91-611eba0ff452', 'DENIED', 'E-404', '2026-09-21 11:06:52', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (460, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.155', NULL, '16082995-2e5e-4282-bf03-2be8f6f5aff9', 'DENIED', 'E-404', '2026-09-21 11:06:52', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (461, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.155', NULL, '26cbd351-b672-4c9c-8217-9f6d21fcefff', 'DENIED', 'E-404', '2026-09-21 11:06:52', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (462, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.155', NULL, '7d7a5221-ccae-4cc8-96cf-3d8ed4062c44', 'DENIED', 'E-404', '2026-09-21 11:06:52', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (463, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.155', NULL, 'e6fcbe01-6adb-451d-83ea-4c10fa072567', 'DENIED', 'E-404', '2026-09-21 11:06:52', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (464, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.155', NULL, 'fdd53e41-0f7a-4405-8456-fe7c051dc128', 'DENIED', 'E-404', '2026-09-21 11:06:52', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (465, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.155', NULL, '31b6664d-5ba3-4789-afc6-bfaecbb278f3', 'DENIED', 'E-404', '2026-09-21 11:06:52', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (466, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.155', NULL, '118200f4-b043-4619-beee-be1f51714a7b', 'DENIED', 'E-404', '2026-09-21 11:06:53', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (467, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.155', NULL, '2d7a9940-f340-422c-8cfe-108b1fc891bf', 'DENIED', 'E-404', '2026-09-21 11:06:53', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (468, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.155', NULL, 'f13a0938-6870-421b-99ed-2dd2070f0365', 'DENIED', 'E-404', '2026-09-21 11:06:53', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (469, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.155', NULL, '2d15bb42-3e2f-49bc-b662-bde4c4e8d8cd', 'DENIED', 'E-404', '2026-09-21 11:06:53', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (470, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.155', NULL, 'cdaff019-158c-4ef3-a630-4efc7135f06d', 'DENIED', 'E-404', '2026-09-21 11:06:53', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (471, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.155', NULL, 'dc1e557f-5a74-49aa-aa30-6e5851ca56a9', 'DENIED', 'E-404', '2026-09-21 11:06:53', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (472, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.155', NULL, '0169adff-5644-41f5-beb4-65ff9f869709', 'DENIED', 'E-404', '2026-09-21 11:06:53', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (473, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.155', NULL, '0672d86f-5032-489f-a08b-6e10b24b2206', 'DENIED', 'E-404', '2026-09-21 11:06:53', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (474, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.155', NULL, 'ec009419-fa09-4636-8140-d27a34094adf', 'DENIED', 'E-404', '2026-09-21 11:06:53', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (475, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.155', NULL, 'eacdb6ec-f6ab-4bb6-b7a4-a403bc46e440', 'DENIED', 'E-404', '2026-09-21 11:06:53', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (476, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.155', NULL, '3abfde0e-bd16-4ec3-8332-0e53233c5eff', 'DENIED', 'E-404', '2026-09-21 11:06:53', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (477, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.155', NULL, '7cf42f04-5d05-46c0-99b3-b77db083494c', 'DENIED', 'E-404', '2026-09-21 11:06:53', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (478, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.155', NULL, 'ea200958-1050-4efa-b944-63130f8e1df2', 'DENIED', 'E-404', '2026-09-21 11:06:53', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (479, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.155', NULL, '10ac975c-e3f8-40e2-ad2c-089fffaf99de', 'DENIED', 'E-404', '2026-09-21 11:06:53', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (480, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.155', NULL, '04bf270f-4791-4b3d-81cd-7382aca8cfe1', 'DENIED', 'E-404', '2026-09-21 11:06:53', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (481, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.155', NULL, '95e44802-380b-448d-ac11-17bb5e8d7dd9', 'DENIED', 'E-404', '2026-09-21 11:06:53', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (482, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.155', NULL, 'a5b3fe87-4336-42d8-8bbe-f75671cc4696', 'DENIED', 'E-404', '2026-09-21 11:06:53', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (483, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.155', NULL, 'd888725c-fc19-4def-afa4-bf6134a38854', 'DENIED', 'E-404', '2026-09-21 11:06:53', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (484, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.155', NULL, 'afbf7082-481d-4369-9594-e1ed745a63a3', 'DENIED', 'E-404', '2026-09-21 11:06:54', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (485, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.155', NULL, 'f3f12d04-04c2-4127-87dc-650ab2debb4a', 'DENIED', 'E-404', '2026-09-21 11:06:54', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (486, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.155', NULL, '5e804b37-4237-4182-9fda-603b3bcf2883', 'DENIED', 'E-404', '2026-09-21 11:06:54', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (487, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.155', NULL, '8ee6a07a-592a-4477-b2fe-c1be806b2230', 'DENIED', 'E-404', '2026-09-21 11:06:54', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (488, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.155', NULL, '6b73c077-6d2e-437a-b83e-0788801502d6', 'DENIED', 'E-404', '2026-09-21 11:06:54', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (489, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.155', NULL, 'e4e3a641-f396-42aa-a837-40c7de513516', 'DENIED', 'E-404', '2026-09-21 11:06:54', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (490, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.155', NULL, 'a02bf8a0-dd37-4a45-bc33-60911de44a5a', 'DENIED', 'E-404', '2026-09-21 11:06:54', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (491, 2, NULL, 'LOGIN', 'USER', 2, '0:0:0:0:0:0:0:1', 'wx-login', '10c1d312-1650-497e-85e3-ece3fc9638c8', 'SUCCESS', NULL, '2026-09-21 11:17:17', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (492, 2, 1, 'FAMILY_QUERY', 'FAMILY', 1, '0:0:0:0:0:0:0:1', 'page=1;pageSize=100', '4d430ced-2e6f-4fe1-8fda-6c1b053e2345', 'SUCCESS', NULL, '2026-09-21 11:17:22', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (493, 2, 1, 'FAMILY_QUERY', 'FAMILY', 1, '0:0:0:0:0:0:0:1', 'page=1;pageSize=100', 'd388fa07-bd8f-45da-9d9e-ad25d48490f2', 'SUCCESS', NULL, '2026-09-21 11:17:36', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (494, 2, 1, 'REQUEST_FAILED', NULL, NULL, '0:0:0:0:0:0:0:1', NULL, 'ecdf3eda-3653-45bb-becd-2dd3d80a6d5e', 'FAILED', 'E-500', '2026-09-21 11:17:45', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (495, 2, 1, 'FAMILY_QUERY', 'FAMILY', 1, '0:0:0:0:0:0:0:1', 'page=1;pageSize=100', '3018dc04-e2be-4dce-a49e-7b461c5eb6c0', 'SUCCESS', NULL, '2026-09-21 11:20:27', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (496, 2, 1, 'REQUEST_FAILED', NULL, NULL, '0:0:0:0:0:0:0:1', NULL, 'e31d53d8-274c-47b1-8405-74f25a908e1e', 'FAILED', 'E-500', '2026-09-21 11:20:34', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (497, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '444c346e-2f30-4d7e-b119-d4932b875e11', 'DENIED', 'E-404', '2026-09-21 11:36:23', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (498, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '9f8e0ee0-d73f-4d78-bbc5-ccdf892bc54c', 'DENIED', 'E-404', '2026-09-21 11:36:24', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (499, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '5e8afaeb-d3c2-4783-a035-d02b1c6e25d1', 'DENIED', 'E-404', '2026-09-21 11:36:24', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (500, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '3bfb1dce-ffda-490c-8fcf-c4d192089e79', 'DENIED', 'E-404', '2026-09-21 11:36:24', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (501, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '619e7a0d-5314-411f-b435-1c5eb6f6fec6', 'DENIED', 'E-404', '2026-09-21 11:36:24', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (502, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '409797da-edae-4821-835d-756e3c4634af', 'DENIED', 'E-404', '2026-09-21 11:36:24', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (503, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, 'c42ffe5e-b896-4c20-abd9-953b5d7dcdf7', 'DENIED', 'E-404', '2026-09-21 11:36:24', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (504, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '0131e4a0-ff23-4c0a-a21e-7be203ebdac4', 'DENIED', 'E-404', '2026-09-21 11:36:24', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (505, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, 'd1575e7e-ff02-463f-862b-1ca8b3613297', 'DENIED', 'E-404', '2026-09-21 11:36:24', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (506, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '5419d7be-39f6-45c0-81ca-e7e6adf848ab', 'DENIED', 'E-404', '2026-09-21 11:36:24', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (507, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '56f237da-bcdf-41ee-85f2-c36ba0df3e22', 'DENIED', 'E-404', '2026-09-21 11:36:24', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (508, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, 'c3b0f9b3-e2e7-4b8a-8647-9471885589f4', 'DENIED', 'E-404', '2026-09-21 11:36:24', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (509, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '22494667-8297-4dcc-b20b-5c2569b3d4a8', 'DENIED', 'E-404', '2026-09-21 11:36:24', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (510, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, 'b91e6c4d-4e92-44d3-a6ee-4c2fa213832a', 'DENIED', 'E-404', '2026-09-21 11:36:24', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (511, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '7326e128-9939-44c9-9f84-87eb272d78fe', 'DENIED', 'E-404', '2026-09-21 11:36:25', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (512, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, 'b69f04ff-c24a-4578-81d0-9bbccdecb865', 'DENIED', 'E-404', '2026-09-21 11:36:25', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (513, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '6d4c62e1-efac-48f5-8e14-30817a393052', 'DENIED', 'E-404', '2026-09-21 11:36:25', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (514, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '723e52d1-8889-457e-b982-4934d668dd57', 'DENIED', 'E-404', '2026-09-21 11:36:25', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (515, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '59bd1314-42ce-4eb8-a168-76d374e77021', 'DENIED', 'E-404', '2026-09-21 11:36:25', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (516, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, 'ca00005d-5990-46ca-8456-ea8c10155c4b', 'DENIED', 'E-404', '2026-09-21 11:36:25', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (517, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, 'bb11cc28-d075-4732-a4c2-17bc3cfc1f71', 'DENIED', 'E-404', '2026-09-21 11:36:25', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (518, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '8c190ff7-4f5d-47ff-a121-38f4efa2d305', 'DENIED', 'E-404', '2026-09-21 11:36:25', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (519, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, 'c89f2d54-827b-4f32-b7d7-2dcda0220968', 'DENIED', 'E-404', '2026-09-21 11:36:25', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (520, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, 'd66f65c9-0a1d-4ca2-936a-78aa4d6d9347', 'DENIED', 'E-404', '2026-09-21 11:36:25', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (521, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, 'e0d364eb-2176-4007-9dc2-2fe1821c4c0b', 'DENIED', 'E-404', '2026-09-21 11:36:25', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (522, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '80a028f1-e02f-4816-b8c4-dcb835e53fa6', 'DENIED', 'E-404', '2026-09-21 11:36:25', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (523, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '651d0ede-35d4-42b8-a144-ab8c734a0fb4', 'DENIED', 'E-404', '2026-09-21 11:36:25', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (524, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '53a5274b-3a76-47fe-b535-8427d62a532e', 'DENIED', 'E-404', '2026-09-21 11:36:25', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (525, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '41a124e3-079a-4cc5-a285-31b8671b65c5', 'DENIED', 'E-404', '2026-09-21 11:36:25', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (526, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '457813b8-5bd9-4760-a4c9-5e3c46575e0d', 'DENIED', 'E-404', '2026-09-21 11:36:26', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (527, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, 'ee906ac0-c998-4e3a-a8a4-ad28e9279cb1', 'DENIED', 'E-404', '2026-09-21 11:36:26', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (528, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '3e297cc9-0c7b-4493-ad31-cd8fa3885b2b', 'DENIED', 'E-404', '2026-09-21 11:36:26', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (529, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '07d32eba-4d89-405b-aff6-3d1e91d4a15a', 'DENIED', 'E-404', '2026-09-21 11:36:26', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (530, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, 'd7f171af-749f-4e79-bd78-bbb5975102b2', 'DENIED', 'E-404', '2026-09-21 11:36:26', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (531, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '31c1f9b6-694a-4008-ac0e-a6a9553aa00c', 'DENIED', 'E-404', '2026-09-21 11:36:26', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (532, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '85e69fed-a9e3-4562-9b82-f7887c5ef540', 'DENIED', 'E-404', '2026-09-21 11:36:26', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (533, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '4fa21bc4-f07d-460c-aead-3e44711fe214', 'DENIED', 'E-404', '2026-09-21 11:36:26', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (534, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '717e7370-a9a3-4ee4-8b36-2600dedd9cdd', 'DENIED', 'E-404', '2026-09-21 11:36:26', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (535, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '0e041c2b-bad2-4bb1-bf98-689ed1d77613', 'DENIED', 'E-404', '2026-09-21 11:36:26', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (536, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '4892e671-8e92-453e-896d-db3615662493', 'DENIED', 'E-404', '2026-09-21 11:36:26', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (537, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '29c3e396-cff9-47f9-9bdb-5501cf0a12a5', 'DENIED', 'E-404', '2026-09-21 11:36:26', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (538, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '833277a3-8c44-41d0-95df-7c3c4c1f6a50', 'DENIED', 'E-404', '2026-09-21 11:36:26', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (539, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '1d31727a-12a8-4aa3-b3ce-2c88b8ac0efe', 'DENIED', 'E-404', '2026-09-21 11:36:26', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (540, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, 'b156896d-df59-43ee-9a07-895884e339a4', 'DENIED', 'E-404', '2026-09-21 11:36:26', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (541, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, 'abc93f4c-f560-45db-b092-3b08c18db080', 'DENIED', 'E-404', '2026-09-21 11:36:26', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (542, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '28195a05-0da3-4b32-a434-a9d107b4da83', 'DENIED', 'E-404', '2026-09-21 11:36:26', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (543, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, 'fdf96b2e-8e95-4444-8ee3-99df64ba38e5', 'DENIED', 'E-404', '2026-09-21 11:36:26', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (544, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '38f07c7b-89a8-4f50-9707-644e73e92a45', 'DENIED', 'E-404', '2026-09-21 11:36:26', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (545, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '052b6640-ee0e-40a4-8554-71c9d04d43b2', 'DENIED', 'E-404', '2026-09-21 11:36:27', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (546, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, 'bba8be2c-0e21-46cb-af59-533db7b3290a', 'DENIED', 'E-404', '2026-09-21 11:36:27', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (547, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '2257d3f6-b64b-40bc-a6dd-366156dd2f85', 'DENIED', 'E-404', '2026-09-21 11:36:27', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (548, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '68e7e686-0839-411a-b138-608373e0209f', 'DENIED', 'E-404', '2026-09-21 11:36:27', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (549, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '89af4e29-1f68-46ca-a25f-c0b10fe42bcd', 'DENIED', 'E-404', '2026-09-21 11:36:27', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (550, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '295edcf8-448e-48a1-87d4-a0227317ea1c', 'DENIED', 'E-404', '2026-09-21 11:36:27', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (551, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '0aa236c6-5fdb-4c5b-b083-0d1740588870', 'DENIED', 'E-404', '2026-09-21 11:36:27', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (552, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '9f5d5c64-f5bc-472f-a5e3-79e810e8d38c', 'DENIED', 'E-404', '2026-09-21 11:36:27', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (553, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '9570e490-f683-4523-98d4-e8b033850c70', 'DENIED', 'E-404', '2026-09-21 11:36:27', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (554, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '6c00b2e7-6c80-4d96-ad38-9c1d985962e0', 'DENIED', 'E-404', '2026-09-21 11:36:27', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (555, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '34b34ffd-0a36-4e83-a113-843c0aebcffb', 'DENIED', 'E-404', '2026-09-21 11:36:27', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (556, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '64730fb1-e0af-43d7-978e-af83e2713112', 'DENIED', 'E-404', '2026-09-21 11:36:27', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (557, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '8a343ec9-eb7d-4fb5-b9a1-4600cd8c8728', 'DENIED', 'E-404', '2026-09-21 11:36:27', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (558, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '9f9ea63e-a251-4982-8b9f-281e8a1df19c', 'DENIED', 'E-404', '2026-09-21 11:36:27', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (559, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '5ab1a9e2-6e68-4724-a25e-325313da409c', 'DENIED', 'E-404', '2026-09-21 11:36:27', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (560, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '435b427f-1d5d-438f-8ea4-4963bfe91397', 'DENIED', 'E-404', '2026-09-21 11:36:27', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (561, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '48c99bec-8088-470e-b093-1ee74b6e5c90', 'DENIED', 'E-404', '2026-09-21 11:36:27', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (562, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, 'd9272e18-65b2-40c4-acef-08001250cd1a', 'DENIED', 'E-404', '2026-09-21 11:36:27', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (563, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '56f18e1d-1ad7-436d-9bf7-46b4e4389a7d', 'DENIED', 'E-404', '2026-09-21 11:36:27', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (564, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '40d27489-cd3e-4a1e-a71e-ae6110b0b4b4', 'DENIED', 'E-404', '2026-09-21 11:36:28', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (565, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, 'b1cab019-420b-4859-9edc-bd283667ff81', 'DENIED', 'E-404', '2026-09-21 11:36:28', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (566, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '6de06225-7b8a-4acd-8981-07c8530d7a29', 'DENIED', 'E-404', '2026-09-21 11:36:28', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (567, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '325d0076-dd3c-4066-8505-f983f62cd7a8', 'DENIED', 'E-404', '2026-09-21 11:36:28', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (568, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, 'b17c22cf-6b0d-4f8f-bb6c-35100094a8a9', 'DENIED', 'E-404', '2026-09-21 11:36:28', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (569, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, 'ef4d9a37-31f3-4138-81b8-2052ccb32667', 'DENIED', 'E-404', '2026-09-21 11:36:28', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (570, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, 'ed9e1173-9b70-4aeb-9acc-3aac684fa1ec', 'DENIED', 'E-404', '2026-09-21 11:36:28', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (571, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, 'c3854556-3220-49a9-b712-3ede2afa5ee2', 'DENIED', 'E-404', '2026-09-21 11:36:28', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (572, 2, NULL, 'LOGIN', 'USER', 2, '0:0:0:0:0:0:0:1', 'wx-login', '54185c28-25ab-4c29-a50d-2b12790e5d7a', 'SUCCESS', NULL, '2026-09-21 13:44:14', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (573, 2, 1, 'FAMILY_QUERY', 'FAMILY', 1, '0:0:0:0:0:0:0:1', 'page=1;pageSize=100', '9918f218-21ef-4967-a164-0ebd5522bc7d', 'SUCCESS', NULL, '2026-09-21 13:44:26', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (574, 2, 1, 'FAMILY_QUERY', 'FAMILY', 1, '0:0:0:0:0:0:0:1', 'page=1;pageSize=100', '9c4388d4-623d-4fab-9ccf-a2cbc85fd1a8', 'SUCCESS', NULL, '2026-09-21 13:44:34', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (575, 2, 1, 'FAMILY_QUERY', 'FAMILY', 1, '0:0:0:0:0:0:0:1', 'page=1;pageSize=100', '4c6b3c8d-0a88-4809-bf54-7e48f915114e', 'SUCCESS', NULL, '2026-09-21 13:57:16', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (576, 2, 1, 'FAMILY_QUERY', 'FAMILY', 1, '0:0:0:0:0:0:0:1', 'page=1;pageSize=100', '1cb28f9a-6cd3-467a-8489-9340dd110068', 'SUCCESS', NULL, '2026-09-21 13:58:53', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (577, 2, 1, 'FAMILY_QUERY', 'FAMILY', 1, '0:0:0:0:0:0:0:1', 'page=1;pageSize=100', '629875e8-693c-414e-99e4-4463f53bdf96', 'SUCCESS', NULL, '2026-09-21 13:59:43', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (578, 2, 1, 'FAMILY_QUERY', 'FAMILY', 1, '0:0:0:0:0:0:0:1', 'page=1;pageSize=100', '20020411-7695-4e39-849a-ee36f2bc6d47', 'SUCCESS', NULL, '2026-09-21 14:01:39', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (579, 2, 1, 'FAMILY_QUERY', 'FAMILY', 1, '0:0:0:0:0:0:0:1', 'page=1;pageSize=100', 'eb34773d-1ed3-43f5-8208-a11109e6b31e', 'SUCCESS', NULL, '2026-09-21 14:03:13', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (580, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '0:0:0:0:0:0:0:1', NULL, '643dc670-0008-43ab-9ae7-7f42ec5b77d8', 'DENIED', 'E-001', '2026-09-21 14:14:14', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (581, 2, NULL, 'LOGIN', 'USER', 2, '0:0:0:0:0:0:0:1', 'wx-login', 'be1293d3-1f4b-41d6-b4d8-06205039fc1f', 'SUCCESS', NULL, '2026-09-21 14:30:35', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (582, 2, 1, 'FAMILY_QUERY', 'FAMILY', 1, '0:0:0:0:0:0:0:1', 'page=1;pageSize=100', '2467d19a-baac-417b-a021-4b26847abb43', 'SUCCESS', NULL, '2026-09-21 14:30:40', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (583, 2, 1, 'FAMILY_QUERY', 'FAMILY', 1, '0:0:0:0:0:0:0:1', 'page=1;pageSize=100', '5d7894dd-075c-46fc-9764-4aee866fe6c2', 'SUCCESS', NULL, '2026-09-21 14:35:44', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (584, 2, 1, 'FAMILY_QUERY', 'FAMILY', 1, '0:0:0:0:0:0:0:1', 'page=1;pageSize=100', 'd33ae63a-be30-4fb5-8af9-caf11eff164c', 'SUCCESS', NULL, '2026-09-21 14:36:07', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (585, 2, 1, 'FAMILY_QUERY', 'FAMILY', 1, '0:0:0:0:0:0:0:1', 'page=1;pageSize=100', '89636e9d-94cc-4987-8888-a0bf089350e4', 'SUCCESS', NULL, '2026-09-21 14:36:11', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (586, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '127.0.0.1', NULL, '394fa78f-2528-4347-8bcc-0074378b6db9', 'DENIED', 'E-404', '2026-09-21 14:52:14', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (587, 2, NULL, 'LOGIN', 'USER', 2, '0:0:0:0:0:0:0:1', 'wx-login', 'd9b3e5fc-680a-49ae-aaa0-fd08c82aae81', 'SUCCESS', NULL, '2026-09-22 09:56:12', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (588, 2, 1, 'FAMILY_QUERY', 'FAMILY', 1, '0:0:0:0:0:0:0:1', 'page=1;pageSize=100', 'd3426ad3-0732-4c1b-b803-09f7f0b11ce9', 'SUCCESS', NULL, '2026-09-22 09:58:31', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (589, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, 'd712111d-89e8-429f-9f02-873ebfb28878', 'DENIED', 'E-404', '2026-09-22 10:07:08', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (590, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '61f9457e-b14d-4ec2-9828-1ecd85866d3a', 'DENIED', 'E-404', '2026-09-22 10:07:08', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (591, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, 'dbc1fe41-4b99-4839-8b55-da6adde86e73', 'DENIED', 'E-404', '2026-09-22 10:07:09', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (592, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, 'cb36ebdc-fb49-403b-a066-d558ff412931', 'DENIED', 'E-404', '2026-09-22 10:07:09', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (593, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '15846862-1119-4a36-b923-5e7182a652e0', 'DENIED', 'E-404', '2026-09-22 10:07:09', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (594, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '8492aa68-1122-47de-8004-3843b3e6024d', 'DENIED', 'E-404', '2026-09-22 10:07:09', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (595, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '914c3832-2c74-44f7-8add-ba95b701ca9b', 'DENIED', 'E-404', '2026-09-22 10:07:09', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (596, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '7512395e-22d8-4a2f-bcf6-dd1dfb7ed652', 'DENIED', 'E-404', '2026-09-22 10:07:09', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (597, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '5714dacc-8a59-44e5-ad6e-bc66c6b7cb80', 'DENIED', 'E-404', '2026-09-22 10:07:09', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (598, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '60917b1f-6dee-4507-bd68-5625a63ed494', 'DENIED', 'E-404', '2026-09-22 10:07:09', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (599, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, 'ea9f7446-cb9e-409f-aa7a-83667daf2524', 'DENIED', 'E-404', '2026-09-22 10:07:09', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (600, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, 'cbba0fc8-80cc-4149-90cb-49bbb6b6bdee', 'DENIED', 'E-404', '2026-09-22 10:07:09', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (601, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '1749109d-df5d-4ef2-b12b-0c413115206c', 'DENIED', 'E-404', '2026-09-22 10:07:09', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (602, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, 'dad7968b-4389-44e3-a021-8019ae6423b6', 'DENIED', 'E-404', '2026-09-22 10:07:10', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (603, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '37f2d618-5bca-43bb-9f11-1b635106939e', 'DENIED', 'E-404', '2026-09-22 10:07:10', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (604, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '641b4917-6817-462b-9e5e-d7065b21f776', 'DENIED', 'E-404', '2026-09-22 10:07:10', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (605, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '030e3164-9f61-4427-a2af-d45e4fd28974', 'DENIED', 'E-404', '2026-09-22 10:07:10', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (606, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '9207725c-c79b-45b7-9f4f-5845ee66188f', 'DENIED', 'E-404', '2026-09-22 10:07:10', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (607, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, 'f914493d-fe1a-4dc3-9869-e08d1aec8a3d', 'DENIED', 'E-404', '2026-09-22 10:07:10', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (608, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '92284777-062a-4fd8-9f9e-98e8f0065462', 'DENIED', 'E-404', '2026-09-22 10:07:10', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (609, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '21cff298-fc28-4821-b33a-e8686d33be5e', 'DENIED', 'E-404', '2026-09-22 10:07:10', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (610, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '9d34a8c7-5c92-4e01-8dbc-4338485854a6', 'DENIED', 'E-404', '2026-09-22 10:07:10', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (611, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '6a089b75-519b-450f-86ef-9d35b08f4974', 'DENIED', 'E-404', '2026-09-22 10:07:11', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (612, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '0bfe133a-5a00-4af5-a35e-d5b1049ed6a9', 'DENIED', 'E-404', '2026-09-22 10:07:11', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (613, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '34a487d1-1587-4da8-828f-195bfcbffa40', 'DENIED', 'E-404', '2026-09-22 10:07:11', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (614, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '2db95869-783d-4164-aff7-cc10ede29932', 'DENIED', 'E-404', '2026-09-22 10:07:11', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (615, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '526ddf58-1761-4b55-a97a-369a5a318f5f', 'DENIED', 'E-404', '2026-09-22 10:07:11', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (616, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '25a87150-c0a2-49c4-906a-9ac6c25dc68b', 'DENIED', 'E-404', '2026-09-22 10:07:11', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (617, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '373411a2-5a0e-4363-9055-43f877b562e0', 'DENIED', 'E-404', '2026-09-22 10:07:11', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (618, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '79f4b055-454f-4f64-9c76-d6e86aa21af3', 'DENIED', 'E-404', '2026-09-22 10:07:11', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (619, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '0db8d152-98b0-49db-881a-ba97640feb41', 'DENIED', 'E-404', '2026-09-22 10:07:11', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (620, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '4cbf7b78-adbf-40f5-9d38-1b8a899133d8', 'DENIED', 'E-404', '2026-09-22 10:07:12', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (621, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '2d1aa799-db52-4361-969d-c9a53a4533e5', 'DENIED', 'E-404', '2026-09-22 10:07:12', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (622, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '27e1c409-7375-4256-ba22-eae326a9ff77', 'DENIED', 'E-404', '2026-09-22 10:07:12', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (623, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, 'e8555651-8e25-490d-a867-a5e7565ccb9a', 'DENIED', 'E-404', '2026-09-22 10:07:12', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (624, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '217e006b-6604-4600-94f1-74178dfd0d40', 'DENIED', 'E-404', '2026-09-22 10:07:12', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (625, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '26b020cc-4808-4fb0-b976-e93073c2d8c7', 'DENIED', 'E-404', '2026-09-22 10:07:12', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (626, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, 'eb4a444b-37d1-4f6e-ae49-9b5eab3fbf91', 'DENIED', 'E-404', '2026-09-22 10:07:12', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (627, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '22a4be57-ef44-4b5e-959b-bc30e7ca907b', 'DENIED', 'E-404', '2026-09-22 10:07:12', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (628, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, 'a20334f7-a676-45ff-9e8e-c355fbaafbe2', 'DENIED', 'E-404', '2026-09-22 10:07:12', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (629, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '9943a91e-2a52-4154-9665-812470582d2c', 'DENIED', 'E-404', '2026-09-22 10:07:12', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (630, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, 'b8097349-eeaa-4f0b-ac2e-4d00b48657bb', 'DENIED', 'E-404', '2026-09-22 10:07:12', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (631, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '50cab4fe-76cb-4050-9644-b247924ac1cf', 'DENIED', 'E-404', '2026-09-22 10:07:12', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (632, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, 'e5f90d16-80d4-4725-972f-6c3c47953358', 'DENIED', 'E-404', '2026-09-22 10:07:12', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (633, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, 'f948f1af-b534-4f4c-90f3-cfef7696a773', 'DENIED', 'E-404', '2026-09-22 10:07:13', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (634, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '97925124-bc7f-4880-af54-5423c981295e', 'DENIED', 'E-404', '2026-09-22 10:07:13', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (635, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, 'df0ca943-d09d-42df-bc96-1288c983a961', 'DENIED', 'E-404', '2026-09-22 10:07:13', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (636, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '53cbaa04-d03b-4143-b313-63131b944a21', 'DENIED', 'E-404', '2026-09-22 10:07:13', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (637, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '9c95cd20-f068-444f-b084-c0beb677af4e', 'DENIED', 'E-404', '2026-09-22 10:07:13', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (638, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '4fe5d515-b181-444d-ae4e-c043a015b62a', 'DENIED', 'E-404', '2026-09-22 10:07:13', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (639, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '2ccc87ef-35a9-417f-8ab6-bef01adb3401', 'DENIED', 'E-404', '2026-09-22 10:07:13', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (640, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, 'e379531e-9431-42ed-80f9-63621c477514', 'DENIED', 'E-404', '2026-09-22 10:07:13', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (641, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '90c5a9b9-b088-46bc-97d9-dfeecceae208', 'DENIED', 'E-404', '2026-09-22 10:07:13', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (642, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '8a80aabc-6731-4660-abf0-0084cb65dc89', 'DENIED', 'E-404', '2026-09-22 10:07:13', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (643, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '2e595ea0-babe-42e3-9347-382a903b0066', 'DENIED', 'E-404', '2026-09-22 10:07:13', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (644, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, 'a22df0dd-82f3-4b43-beb1-33662d62692d', 'DENIED', 'E-404', '2026-09-22 10:07:13', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (645, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '27c81bf9-316d-44b3-bbc8-9a1067ca59f0', 'DENIED', 'E-404', '2026-09-22 10:07:13', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (646, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, 'b479a89a-e307-4fe4-b748-6ba01936233d', 'DENIED', 'E-404', '2026-09-22 10:07:14', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (647, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '78febe9a-b6d3-419a-9e77-aebf2b185581', 'DENIED', 'E-404', '2026-09-22 10:07:14', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (648, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, 'e32b975d-495a-49d4-a422-78c2321cad4a', 'DENIED', 'E-404', '2026-09-22 10:07:14', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (649, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, 'c8adb6a0-06a6-45a7-b51b-50e82f62ee4e', 'DENIED', 'E-404', '2026-09-22 10:07:14', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (650, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '54d20425-8553-4e2a-a796-8a30c75e8cfc', 'DENIED', 'E-404', '2026-09-22 10:07:14', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (651, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, 'daeb7118-6c7a-424f-81fc-f332c6d1b15a', 'DENIED', 'E-404', '2026-09-22 10:07:14', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (652, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '84aca90e-d23f-4e4f-bed9-c9081c0c4955', 'DENIED', 'E-404', '2026-09-22 10:07:14', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (653, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '14489eee-fefe-4a86-b9e7-95ac31f6b524', 'DENIED', 'E-404', '2026-09-22 10:07:14', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (654, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, 'ad482edc-160f-4354-9aa9-5f517681bf1c', 'DENIED', 'E-404', '2026-09-22 10:07:14', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (655, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, 'ca043386-bc72-4573-a592-fa2f80d13b9f', 'DENIED', 'E-404', '2026-09-22 10:07:14', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (656, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '0e0a34b6-a120-4863-a646-25508d11f5ee', 'DENIED', 'E-404', '2026-09-22 10:07:14', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (657, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '2d27f559-6499-4703-8d40-f34e8a41fc56', 'DENIED', 'E-404', '2026-09-22 10:07:14', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (658, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '3e854304-b0f7-4317-9627-ace9c35f616d', 'DENIED', 'E-404', '2026-09-22 10:07:14', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (659, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '7758ec92-9c30-4f75-99f5-cbdb7d087e22', 'DENIED', 'E-404', '2026-09-22 10:07:14', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (660, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '748f9a24-8d32-4d69-94ff-90d858af043d', 'DENIED', 'E-404', '2026-09-22 10:07:15', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (661, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '979d9fc2-ca5b-47c9-8e8f-b6db2379d0c8', 'DENIED', 'E-404', '2026-09-22 10:07:15', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (662, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '49eb2861-3169-4af3-8c3f-e1c95a3dc81d', 'DENIED', 'E-404', '2026-09-22 10:07:15', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (663, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.146', NULL, '625ba438-3c6a-455c-b62f-6c36d187c5a9', 'DENIED', 'E-404', '2026-09-22 10:07:15', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (664, 2, NULL, 'LOGOUT', 'USER', 2, '0:0:0:0:0:0:0:1', 'all sessions', '75e2e156-c027-4a36-9797-3463d5751293', 'SUCCESS', NULL, '2026-09-22 10:08:11', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (665, 3, NULL, 'LOGIN', 'USER', 3, '0:0:0:0:0:0:0:1', 'wx-login', '711cef02-4e86-4347-965d-941719422a3b', 'SUCCESS', NULL, '2026-09-22 10:08:19', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (666, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '0:0:0:0:0:0:0:1', NULL, '5591d66f-964f-4704-a785-bae94810645c', 'DENIED', 'E-001', '2026-09-22 10:08:19', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (667, 3, NULL, 'LOGIN', 'USER', 3, '0:0:0:0:0:0:0:1', 'wx-login', '61cf3d78-afee-4691-8c62-da578e5d1740', 'SUCCESS', NULL, '2026-09-22 10:08:36', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (668, 3, NULL, 'ROLE', 'USER', 3, '0:0:0:0:0:0:0:1', 'select-role=CHILD', '222b0a71-d404-4c85-969c-eca6a18098e9', 'SUCCESS', NULL, '2026-09-22 10:08:39', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (669, 3, NULL, 'BIND_QUERY', 'CHILD', 3, '0:0:0:0:0:0:0:1', 'query own binding', '93f5161d-9a6e-40aa-bc07-3f585391e16f', 'SUCCESS', NULL, '2026-09-22 10:08:39', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (670, 3, NULL, 'BIND_QUERY', 'CHILD', 3, '0:0:0:0:0:0:0:1', 'query own binding', '49d43038-463a-41c3-b7dd-90159a9532e9', 'SUCCESS', NULL, '2026-09-22 10:09:05', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (671, 3, NULL, 'LOGOUT', 'USER', 3, '0:0:0:0:0:0:0:1', 'all sessions', 'fbbb5adf-f057-42af-bb8b-b38aaa9eb372', 'SUCCESS', NULL, '2026-09-22 10:09:17', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (672, 2, NULL, 'LOGIN', 'USER', 2, '0:0:0:0:0:0:0:1', 'wx-login', 'becf0131-4278-40f8-a415-761817591b62', 'SUCCESS', NULL, '2026-09-22 10:09:19', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (673, 2, 1, 'FAMILY_QUERY', 'FAMILY', 1, '0:0:0:0:0:0:0:1', 'page=1;pageSize=20', '73adda1b-8bbc-45a4-a0ca-f7ba19eab40f', 'SUCCESS', NULL, '2026-09-22 10:09:23', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (674, 2, NULL, 'LOGOUT', 'USER', 2, '0:0:0:0:0:0:0:1', 'all sessions', 'cab53984-a7dc-42c1-a166-81e49e8f15ed', 'SUCCESS', NULL, '2026-09-22 10:09:55', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (675, 3, NULL, 'LOGIN', 'USER', 3, '0:0:0:0:0:0:0:1', 'wx-login', 'e500fd23-11e5-46ae-a268-05b56fc97c32', 'SUCCESS', NULL, '2026-09-22 10:10:05', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (676, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '0:0:0:0:0:0:0:1', NULL, '74409d2e-369c-4b44-809a-f0e0ce4a4bd6', 'DENIED', 'E-001', '2026-09-22 10:10:05', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (677, 3, NULL, 'LOGIN', 'USER', 3, '0:0:0:0:0:0:0:1', 'wx-login', 'b5ebdb58-a3d2-4f3f-91f2-4af08e775b56', 'SUCCESS', NULL, '2026-09-22 10:10:14', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (678, 3, NULL, 'BIND_QUERY', 'CHILD', 3, '0:0:0:0:0:0:0:1', 'query own binding', '63636ca3-6a75-4b78-aa91-7ea4b89092e6', 'SUCCESS', NULL, '2026-09-22 10:10:17', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (679, 3, 1, 'JOIN', 'FAMILY_MEMBER', 2, '0:0:0:0:0:0:0:1', 'join family', 'cc59bf5d-12fa-4a5c-884b-cb2e3cfa3519', 'SUCCESS', NULL, '2026-09-22 10:10:30', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (680, 3, 1, 'BIND_QUERY', 'CHILD', 3, '0:0:0:0:0:0:0:1', 'query own binding', '48e1b662-1106-4942-9449-d1e307f504a9', 'SUCCESS', NULL, '2026-09-22 10:10:30', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (681, 3, NULL, 'LOGOUT', 'USER', 3, '0:0:0:0:0:0:0:1', 'all sessions', 'e6ea25f0-bc14-40cf-97de-52842012f084', 'SUCCESS', NULL, '2026-09-22 10:10:38', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (682, 2, NULL, 'LOGIN', 'USER', 2, '0:0:0:0:0:0:0:1', 'wx-login', 'f863ecd9-fd81-4073-85ae-b0903ff4ef43', 'SUCCESS', NULL, '2026-09-22 10:10:41', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (683, 2, 1, 'FAMILY_QUERY', 'FAMILY', 1, '0:0:0:0:0:0:0:1', 'page=1;pageSize=20', '2b21f5c3-e2ba-45fb-a740-8389a4d96530', 'SUCCESS', NULL, '2026-09-22 10:10:43', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (684, 2, 1, 'CONSENT_QUERY', 'CHILD', 3, '0:0:0:0:0:0:0:1', 'status=NONE', 'c16b912f-b0ed-4bff-ae2f-2e67441da773', 'SUCCESS', NULL, '2026-09-22 10:10:46', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (685, 2, 1, 'GRANT', 'CONSENT', 1, '0:0:0:0:0:0:0:1', 'version=v1;applicationVersion=1', 'ce304487-068b-44fd-90a1-681b0e6f815f', 'SUCCESS', NULL, '2026-09-22 10:11:01', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (686, 2, 1, 'CONSENT_QUERY', 'CHILD', 3, '0:0:0:0:0:0:0:1', 'status=GRANTED', 'f0b4c824-da3d-420b-9b42-813a97b6a736', 'SUCCESS', NULL, '2026-09-22 10:11:01', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (687, 2, 1, 'BIND', 'FAMILY_MEMBER', 2, '0:0:0:0:0:0:0:1', 'bind approve=true;applicationVersion=1', '5344ac88-24fb-4b71-b792-f6402ff1160f', 'SUCCESS', NULL, '2026-09-22 10:11:13', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (688, 2, 1, 'FAMILY_QUERY', 'FAMILY', 1, '0:0:0:0:0:0:0:1', 'page=1;pageSize=20', 'f2ae5fb1-2f40-4a94-a0f2-bc727d06e781', 'SUCCESS', NULL, '2026-09-22 10:11:13', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (689, 2, 1, 'FAMILY_QUERY', 'FAMILY', 1, '0:0:0:0:0:0:0:1', 'page=1;pageSize=20', 'c7e440b6-bed8-4956-9b5e-37a28b6273ba', 'SUCCESS', NULL, '2026-09-22 10:11:17', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (690, 2, 1, 'FAMILY_QUERY', 'FAMILY', 1, '0:0:0:0:0:0:0:1', 'page=1;pageSize=100', '10d4ddfe-e675-41f7-8813-9d8a6cc48b4e', 'SUCCESS', NULL, '2026-09-22 10:11:22', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (691, 2, 1, 'CONSENT_QUERY', 'CHILD', 3, '0:0:0:0:0:0:0:1', 'status=GRANTED', '1e71a6f4-6e22-4fc1-be7a-addb75ae06da', 'SUCCESS', NULL, '2026-09-22 10:11:22', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (692, 2, 1, 'REQUEST_FAILED', NULL, NULL, '0:0:0:0:0:0:0:1', NULL, 'a147afa3-8466-46f3-9835-a96f5bbef438', 'DENIED', 'E-404', '2026-09-22 10:11:22', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (693, 2, 1, 'CONSENT_QUERY', 'CHILD', 3, '0:0:0:0:0:0:0:1', 'status=GRANTED', '41f4adfe-aa58-4589-99e5-578da45bb1e9', 'SUCCESS', NULL, '2026-09-22 10:11:28', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (694, 2, 1, 'REQUEST_FAILED', NULL, NULL, '0:0:0:0:0:0:0:1', NULL, '90b6874e-fab7-467e-9053-e5a800345ce0', 'DENIED', 'E-404', '2026-09-22 10:11:29', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (695, 2, 1, 'PROFILE', 'CHILD', 3, '0:0:0:0:0:0:0:1', 'consentId=1;version=v1', '8541a48d-ec6c-4ccd-bd6b-f60d64fc152a', 'SUCCESS', NULL, '2026-09-22 10:11:54', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (696, 2, 1, 'CONSENT_QUERY', 'CHILD', 3, '0:0:0:0:0:0:0:1', 'status=GRANTED', '83d23ea2-98de-4d97-a034-b918181a4291', 'SUCCESS', NULL, '2026-09-22 10:11:54', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (697, 2, 1, 'PROFILE_QUERY', 'CHILD', 3, '0:0:0:0:0:0:0:1', 'consentId=1', 'a7873cf0-9162-407a-a7d7-fe2308a34ed7', 'SUCCESS', NULL, '2026-09-22 10:11:54', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (698, 2, 1, 'FAMILY_QUERY', 'FAMILY', 1, '0:0:0:0:0:0:0:1', 'page=1;pageSize=100', '55cc4289-ccb9-452f-994c-10ef75f33a19', 'SUCCESS', NULL, '2026-09-22 10:12:03', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (699, 2, NULL, 'LOGOUT', 'USER', 2, '0:0:0:0:0:0:0:1', 'all sessions', 'fab9ab3a-0c44-4d6e-b6af-d8beb3dc99e2', 'SUCCESS', NULL, '2026-09-22 10:12:14', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (700, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.188', NULL, 'f6201efa-e4cc-4474-98c7-9b7f2023a8b6', 'DENIED', 'E-404', '2026-09-22 10:12:25', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (701, 3, NULL, 'LOGIN', 'USER', 3, '0:0:0:0:0:0:0:1', 'wx-login', '83d2a4c3-be0c-42c7-bc07-5f4b93c341b5', 'SUCCESS', NULL, '2026-09-22 10:12:28', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (702, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.188', NULL, '9c20316f-767c-4e5b-95da-705457224663', 'DENIED', 'E-404', '2026-09-22 10:12:30', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (703, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.188', NULL, 'fe54f521-3d6a-4af0-a8c7-13bc354afa8a', 'DENIED', 'E-404', '2026-09-22 10:12:30', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (704, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.188', NULL, '2d8a47b6-c0e1-471b-9117-fe1232761c3a', 'DENIED', 'E-404', '2026-09-22 10:12:30', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (705, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.190', NULL, '8f97e936-1ebb-499f-9358-8230596a455a', 'DENIED', 'E-404', '2026-09-22 10:12:30', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (706, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.190', NULL, '6b164a73-b24d-4304-8222-4b73f4c96beb', 'DENIED', 'E-404', '2026-09-22 10:12:30', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (707, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.190', NULL, 'fd62dfa2-d048-4db6-9327-ee97b74ba2c1', 'DENIED', 'E-404', '2026-09-22 10:12:30', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (708, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.190', NULL, '4131a385-6457-453b-a290-973b344799c8', 'DENIED', 'E-404', '2026-09-22 10:12:30', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (709, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.190', NULL, '3af6e6a3-30fa-4625-978a-613d648b6abb', 'DENIED', 'E-404', '2026-09-22 10:12:30', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (710, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.190', NULL, '872775fc-52f5-4eb9-a994-7c3328992cb5', 'DENIED', 'E-404', '2026-09-22 10:12:30', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (711, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.123', NULL, '6fbb5925-9a55-432a-b9c3-bb895d9d68b0', 'DENIED', 'E-404', '2026-09-22 10:12:30', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (712, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.188', NULL, '9fd3fea9-e574-4d57-b7e2-c6181063f3d4', 'DENIED', 'E-404', '2026-09-22 10:12:30', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (713, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.190', NULL, '6cc01979-c487-4995-acb1-674a52a11772', 'DENIED', 'E-404', '2026-09-22 10:12:30', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (714, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.190', NULL, '4c9bc87b-b97b-4932-93f8-192ebe2fa82a', 'DENIED', 'E-404', '2026-09-22 10:12:30', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (715, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.190', NULL, '9f9d6ef5-3e3a-43b3-9241-de6c0f30651e', 'DENIED', 'E-404', '2026-09-22 10:12:30', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (716, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.123', NULL, '04d24821-c52b-40a3-9fe1-c66953804653', 'DENIED', 'E-404', '2026-09-22 10:12:30', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (717, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.190', NULL, 'e65d9b17-5ed9-4e06-b876-bffd137671e8', 'DENIED', 'E-404', '2026-09-22 10:12:30', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (718, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.190', NULL, 'a5e3705d-91c2-44ed-9c3b-c7d2fe13ffd8', 'DENIED', 'E-404', '2026-09-22 10:12:30', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (719, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.190', NULL, '4f03c2bd-a578-4d90-aff3-249fcf0832af', 'DENIED', 'E-404', '2026-09-22 10:12:30', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (720, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.188', NULL, '64cb1b93-8a76-4d2b-9ede-dda34f645e67', 'DENIED', 'E-404', '2026-09-22 10:12:30', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (721, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.190', NULL, 'fbfdd3fb-59d9-452e-a271-5a43b37a5383', 'DENIED', 'E-404', '2026-09-22 10:12:30', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (722, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.190', NULL, '38c4b76e-076e-49f5-ad85-1bfe838aac13', 'DENIED', 'E-404', '2026-09-22 10:12:30', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (723, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.190', NULL, '47c3717c-0bf9-44de-87f0-709ab7cdabf6', 'DENIED', 'E-404', '2026-09-22 10:12:30', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (724, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.123', NULL, '4c75aa25-fa10-4561-b4b4-776695bda6a7', 'DENIED', 'E-404', '2026-09-22 10:12:30', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (725, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.190', NULL, 'f35ccddd-1072-4d9a-af1c-e4c6b2910560', 'DENIED', 'E-404', '2026-09-22 10:12:30', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (726, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.190', NULL, '28476d10-c7f3-4baf-8618-ef3d1dda309b', 'DENIED', 'E-404', '2026-09-22 10:12:30', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (727, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.190', NULL, '46f31147-7a49-4a74-bec4-08358d685b7d', 'DENIED', 'E-404', '2026-09-22 10:12:30', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (728, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.190', NULL, '7823c021-0989-4cf6-b12b-36c10fa12e98', 'DENIED', 'E-404', '2026-09-22 10:12:30', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (729, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.190', NULL, 'c7ee2892-b008-4c3d-a9b4-f69a09c858e5', 'DENIED', 'E-404', '2026-09-22 10:12:30', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (730, 3, 1, 'BIND_QUERY', 'CHILD', 3, '0:0:0:0:0:0:0:1', 'query own binding', '4dd58bce-d6fe-450f-872b-ce2b9110489a', 'SUCCESS', NULL, '2026-09-22 10:12:30', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (731, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.190', NULL, '720e3a0d-4a88-405b-b3d6-6075434d90f1', 'DENIED', 'E-404', '2026-09-22 10:12:30', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (732, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.190', NULL, '29f71342-8619-4085-87ba-66940dc1f0a3', 'DENIED', 'E-404', '2026-09-22 10:12:31', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (733, 3, 1, 'REQUEST_FAILED', NULL, NULL, '0:0:0:0:0:0:0:1', NULL, '6e7d2791-9c03-4594-a4cd-4511d31a276a', 'DENIED', 'E-404', '2026-09-22 10:12:31', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (734, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.190', NULL, '99f0fdaa-c635-4e09-a259-f9e3ebd2fd27', 'DENIED', 'E-404', '2026-09-22 10:12:31', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (735, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.190', NULL, 'd4027755-e10a-4ed5-93b9-2e0000630353', 'DENIED', 'E-404', '2026-09-22 10:12:31', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (736, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.190', NULL, '32bdff1a-1fcc-48b1-b7a0-02bac27aed5b', 'DENIED', 'E-404', '2026-09-22 10:12:31', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (737, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.190', NULL, '2122cdc1-3f81-4da1-9a94-e4b06f44e674', 'DENIED', 'E-404', '2026-09-22 10:12:31', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (738, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.190', NULL, '5b7ae619-3a2c-44ef-9f2f-9a0a74e0346d', 'DENIED', 'E-404', '2026-09-22 10:12:31', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (739, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.190', NULL, '756ba9c5-7e29-48e9-bb83-2e737b496e74', 'DENIED', 'E-404', '2026-09-22 10:12:31', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (740, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.190', NULL, 'e04149c8-5893-41cf-9f1f-1d776809dc95', 'DENIED', 'E-404', '2026-09-22 10:12:31', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (741, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.190', NULL, 'ba46e2df-88c0-4d97-aa96-1dd775e68001', 'DENIED', 'E-404', '2026-09-22 10:12:31', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (742, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.190', NULL, 'c1ce48d7-529f-4164-b292-1a1098601baa', 'DENIED', 'E-404', '2026-09-22 10:12:31', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (743, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.190', NULL, 'e0bbdcea-dc19-46c0-baee-25c154f15885', 'DENIED', 'E-404', '2026-09-22 10:12:31', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (744, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.190', NULL, '21208d9e-59f8-4166-bac0-71b8fd445567', 'DENIED', 'E-404', '2026-09-22 10:12:31', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (745, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.190', NULL, 'e283d60f-c05f-447a-aaf8-8a886c96ff27', 'DENIED', 'E-404', '2026-09-22 10:12:31', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (746, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.190', NULL, '809d879e-4311-4baa-ae5c-84957a40b7b3', 'DENIED', 'E-404', '2026-09-22 10:12:31', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (747, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.190', NULL, 'bd5daf22-421c-4330-a017-c927b4d1c4d6', 'DENIED', 'E-404', '2026-09-22 10:12:31', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (748, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.190', NULL, 'a8804a78-4c59-4ba8-b74e-ef51d21891fe', 'DENIED', 'E-404', '2026-09-22 10:12:31', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (749, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.190', NULL, 'b7cd442c-5c42-443b-bbb2-17b8447dd860', 'DENIED', 'E-404', '2026-09-22 10:12:31', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (750, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.190', NULL, 'ee8c4167-2aa5-42d0-9aa4-567b7b860620', 'DENIED', 'E-404', '2026-09-22 10:12:31', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (751, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.190', NULL, 'b26d8e8c-8966-44a7-8aac-c1b5e12c56de', 'DENIED', 'E-404', '2026-09-22 10:12:31', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (752, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.190', NULL, 'e1a9dfd2-068e-4e4b-8d15-858c6ff2ceea', 'DENIED', 'E-404', '2026-09-22 10:12:32', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (753, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.190', NULL, '3058082e-a9cc-4914-85d5-2851deffe6c0', 'DENIED', 'E-404', '2026-09-22 10:12:32', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (754, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.190', NULL, 'adc3415e-6a1b-4354-b776-6543db8fc845', 'DENIED', 'E-404', '2026-09-22 10:12:32', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (755, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.190', NULL, 'fbbb3110-692f-4433-8e87-f4aeb7feecc3', 'DENIED', 'E-404', '2026-09-22 10:12:32', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (756, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.190', NULL, '3b2b1c8c-3204-420f-b8dd-b479661abe06', 'DENIED', 'E-404', '2026-09-22 10:12:32', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (757, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.190', NULL, '87d37081-5316-4ad6-a6b5-df047c1b0c8c', 'DENIED', 'E-404', '2026-09-22 10:12:32', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (758, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.190', NULL, '42ed7e4c-e307-4589-b9cc-71327dac5bb1', 'DENIED', 'E-404', '2026-09-22 10:12:32', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (759, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.190', NULL, '5336f69d-cfb0-44a0-8592-a18edcf1c477', 'DENIED', 'E-404', '2026-09-22 10:12:32', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (760, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.190', NULL, '6f59366b-3662-4373-89e0-81f7cb76017d', 'DENIED', 'E-404', '2026-09-22 10:12:33', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (761, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.190', NULL, '747ce4c4-1a5e-47e4-8dd0-c053bce137a8', 'DENIED', 'E-404', '2026-09-22 10:12:33', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (762, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.190', NULL, '8aa197f7-4f14-4265-81cd-33eb68ad83db', 'DENIED', 'E-404', '2026-09-22 10:12:33', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (763, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.190', NULL, '3b6f89b9-7935-439a-ab45-6692c208d2e9', 'DENIED', 'E-404', '2026-09-22 10:12:33', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (764, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.190', NULL, 'a314697c-a185-4fe7-af8f-ea8b9d4ffb0d', 'DENIED', 'E-404', '2026-09-22 10:12:33', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (765, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.190', NULL, '07f4d701-96b7-4410-b859-a5d1ae59e010', 'DENIED', 'E-404', '2026-09-22 10:12:33', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (766, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.190', NULL, '06544ace-94c4-466c-84a2-bfceda7696c7', 'DENIED', 'E-404', '2026-09-22 10:12:33', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (767, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.190', NULL, 'ad83ee28-297c-4c94-8545-5ee4e237b217', 'DENIED', 'E-404', '2026-09-22 10:12:33', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (768, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.190', NULL, 'fe03181d-1de5-412d-b582-917d5a39eec8', 'DENIED', 'E-404', '2026-09-22 10:12:33', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (769, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.190', NULL, '6a63a263-c5b0-4fe5-b710-df525f0cb7e3', 'DENIED', 'E-404', '2026-09-22 10:12:33', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (770, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.190', NULL, '1be187ca-9908-4e10-b1eb-260470a1ad26', 'DENIED', 'E-404', '2026-09-22 10:12:34', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (771, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.190', NULL, '4f6f43f1-28f6-4626-be5e-003fbb8f9ce9', 'DENIED', 'E-404', '2026-09-22 10:12:34', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (772, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.190', NULL, 'cf74cb51-0ce5-4368-834e-12c619338769', 'DENIED', 'E-404', '2026-09-22 10:12:34', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (773, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.190', NULL, 'fbc8ad15-e340-42e1-8053-8d2ee23b526a', 'DENIED', 'E-404', '2026-09-22 10:12:34', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (774, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.190', NULL, 'be02d262-b722-415b-8d7e-a8946f3c4c15', 'DENIED', 'E-404', '2026-09-22 10:12:34', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (775, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.190', NULL, '1f83b0e9-dcf5-4821-922a-d0e76f167ee3', 'DENIED', 'E-404', '2026-09-22 10:12:34', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (776, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.190', NULL, 'dad4f9f2-c0cb-460b-9ca1-5fa32800dca2', 'DENIED', 'E-404', '2026-09-22 10:12:34', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (777, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.190', NULL, 'c436bb76-f35d-4406-b7a2-6b773cfb2ae7', 'DENIED', 'E-404', '2026-09-22 10:12:34', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (778, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.190', NULL, 'c68e2e89-efd6-4f24-9e56-db18a07cfc29', 'DENIED', 'E-404', '2026-09-22 10:12:34', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (779, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.190', NULL, '4e3170fb-db1f-41b4-b6df-55b0246528a5', 'DENIED', 'E-404', '2026-09-22 10:12:35', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (780, 3, 1, 'REQUEST_FAILED', NULL, NULL, '0:0:0:0:0:0:0:1', NULL, '88e458ac-ffe9-462f-9eb8-f3c65b5fe843', 'DENIED', 'E-404', '2026-09-22 10:12:35', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (781, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.190', NULL, '9369beeb-b4ff-4bc6-86be-363435c8f8c6', 'DENIED', 'E-404', '2026-09-22 10:12:35', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (782, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.190', NULL, '8aa48cb2-9fcd-4e88-b31c-65561fbf4b6a', 'DENIED', 'E-404', '2026-09-22 10:12:35', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (783, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.190', NULL, 'd2402a0b-6c5d-43e2-b974-6dec6057c45a', 'DENIED', 'E-404', '2026-09-22 10:12:35', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (784, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.190', NULL, '8b5f29c6-d9b4-4f0d-bbb3-dec188153ead', 'DENIED', 'E-404', '2026-09-22 10:12:35', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (785, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.190', NULL, 'd0e599f7-dbe7-4231-8415-69c166afdc3d', 'DENIED', 'E-404', '2026-09-22 10:12:35', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (786, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.190', NULL, 'aac48525-8146-4047-8b3a-2c9158854d7a', 'DENIED', 'E-404', '2026-09-22 10:12:35', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (787, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.190', NULL, 'aa390400-4280-46e0-9741-82683da1d3ed', 'DENIED', 'E-404', '2026-09-22 10:12:35', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (788, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.190', NULL, '1d06b743-1175-4176-96ba-8108f17856d0', 'DENIED', 'E-404', '2026-09-22 10:12:36', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (789, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.162', NULL, 'e008cd79-28bd-4b21-8c42-900d01528434', 'DENIED', 'E-404', '2026-09-22 10:12:36', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (790, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.190', NULL, '28bc59af-4790-4689-8ef8-0b30729ed82b', 'DENIED', 'E-404', '2026-09-22 10:12:36', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (791, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.162', NULL, '58ed17fe-f999-4ff0-add7-ebb4152c40d3', 'DENIED', 'E-404', '2026-09-22 10:12:36', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (792, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.162', NULL, '8ba6d3c4-ab9f-4b67-bc8b-a97ca4e969bd', 'DENIED', 'E-404', '2026-09-22 10:12:36', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (793, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.190', NULL, '8930531f-94a2-4d45-a468-7c3b4e881332', 'DENIED', 'E-404', '2026-09-22 10:12:36', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (794, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.190', NULL, 'df79c20d-ec44-422a-822c-215ab87c42a9', 'DENIED', 'E-404', '2026-09-22 10:12:36', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (795, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.190', NULL, '9ae92dc9-61bb-4ca1-b242-31e07d4c6cbb', 'DENIED', 'E-404', '2026-09-22 10:12:36', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (796, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.190', NULL, 'f1591eb1-e53b-433a-a4ff-e73b2b770aeb', 'DENIED', 'E-404', '2026-09-22 10:12:36', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (797, 3, 1, 'REQUEST_FAILED', NULL, NULL, '0:0:0:0:0:0:0:1', NULL, '77afd540-ac65-46c7-b993-72cc68b3d956', 'DENIED', 'E-404', '2026-09-22 10:12:36', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (798, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.190', NULL, 'cf3ad9d3-b949-4e20-a6a4-3255fc95f54b', 'DENIED', 'E-404', '2026-09-22 10:12:36', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (799, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.162', NULL, '521b98be-7e67-47c2-9d66-da9b822f1461', 'DENIED', 'E-404', '2026-09-22 10:12:36', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (800, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.162', NULL, 'a316a990-aca4-4e6f-ac1b-3a14659b658e', 'DENIED', 'E-404', '2026-09-22 10:12:37', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (801, 3, 1, 'REQUEST_FAILED', NULL, NULL, '0:0:0:0:0:0:0:1', NULL, '9ff5c1f0-7011-4250-925a-18eb3a581127', 'DENIED', 'E-404', '2026-09-22 10:12:37', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (802, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.162', NULL, 'a9a89379-3a64-4658-b61c-e5301e51088c', 'DENIED', 'E-404', '2026-09-22 10:12:37', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (803, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.162', NULL, '93133f1e-5585-4ed9-813b-c7963f3283ee', 'DENIED', 'E-404', '2026-09-22 10:12:37', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (804, 3, 1, 'REQUEST_FAILED', NULL, NULL, '0:0:0:0:0:0:0:1', NULL, '6a8826ef-f66e-4084-a92f-d16e354c34fd', 'DENIED', 'E-404', '2026-09-22 10:12:38', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (805, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.162', NULL, 'c3849dbc-de01-4441-8eb9-109dad4aa71c', 'DENIED', 'E-404', '2026-09-22 10:12:38', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (806, 3, 1, 'REQUEST_FAILED', NULL, NULL, '0:0:0:0:0:0:0:1', NULL, '9e37c0f1-1b22-47a4-857d-92505cd0ebe1', 'DENIED', 'E-404', '2026-09-22 10:12:38', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (807, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.162', NULL, '9ddaa536-4cb3-4b88-9716-26a028e58e22', 'DENIED', 'E-404', '2026-09-22 10:12:38', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (808, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.162', NULL, '8a58ecbf-1474-4506-aa27-ab2bac4cd1f3', 'DENIED', 'E-404', '2026-09-22 10:12:39', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (809, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.162', NULL, '6c619a04-09f7-4b7d-8e1d-5f81157ca5a0', 'DENIED', 'E-404', '2026-09-22 10:12:39', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (810, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.162', NULL, '32777efc-8567-4629-af3e-9a772f60de3a', 'DENIED', 'E-404', '2026-09-22 10:12:40', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (811, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.162', NULL, '570fd2de-0aca-4ab4-80ad-bc321be0eaa4', 'DENIED', 'E-404', '2026-09-22 10:12:40', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (812, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.162', NULL, '15d799a3-f726-4c8b-940d-d3cd41b733c1', 'DENIED', 'E-404', '2026-09-22 10:12:41', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (813, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.162', NULL, '02f841ac-cb07-4f38-8390-95c85ae522be', 'DENIED', 'E-404', '2026-09-22 10:12:41', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (814, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.162', NULL, '8ff9d13f-fdaf-4662-b0ea-11960d97e9f1', 'DENIED', 'E-404', '2026-09-22 10:12:42', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (815, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.162', NULL, '4d4c8630-b9ea-4e32-8481-7627668a84ea', 'DENIED', 'E-404', '2026-09-22 10:12:42', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (816, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.162', NULL, '273120f7-bbe5-4380-ace2-6f34f6bf0745', 'DENIED', 'E-404', '2026-09-22 10:12:43', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (817, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.162', NULL, '9a00af7a-da59-4a64-bf43-42e1aca5fbc5', 'DENIED', 'E-404', '2026-09-22 10:12:43', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (818, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.162', NULL, '9e13879f-1d8f-47ff-9d48-83492b68fb61', 'DENIED', 'E-404', '2026-09-22 10:12:43', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (819, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.162', NULL, '6c2ed693-4496-4224-901d-652ac9e570cc', 'DENIED', 'E-404', '2026-09-22 10:12:44', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (820, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.162', NULL, '676b19ad-4d8b-46af-a78d-10eef8251e96', 'DENIED', 'E-404', '2026-09-22 10:12:44', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (821, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.162', NULL, 'f5b4f587-c8e4-474a-a616-c5b4e9cb49c6', 'DENIED', 'E-404', '2026-09-22 10:12:45', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (822, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.162', NULL, 'ee594241-cdd4-4ef4-80b8-ade6127e27cf', 'DENIED', 'E-404', '2026-09-22 10:12:45', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (823, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.162', NULL, 'a36f331c-1510-400b-8e19-21ff493ab9fd', 'DENIED', 'E-404', '2026-09-22 10:12:45', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (824, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.162', NULL, '27392c02-99f8-4e1e-8092-205a731bc950', 'DENIED', 'E-404', '2026-09-22 10:12:45', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (825, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.162', NULL, '31caa74d-5e7e-4b2f-bc0e-2ad2b43a71c4', 'DENIED', 'E-404', '2026-09-22 10:12:45', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (826, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.162', NULL, 'bf6a16f1-1b4f-47f6-a3dd-2e397a789b5b', 'DENIED', 'E-404', '2026-09-22 10:12:48', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (827, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.162', NULL, 'e303bb5c-cb13-4cea-acc8-024c0aff5af3', 'DENIED', 'E-404', '2026-09-22 10:12:48', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (828, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.162', NULL, 'a7b7bf07-06be-44cd-a88c-a29816822788', 'DENIED', 'E-404', '2026-09-22 10:12:48', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (829, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.162', NULL, '708c9047-fb48-4a36-af3f-f1def4c6314f', 'DENIED', 'E-404', '2026-09-22 10:12:48', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (830, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.162', NULL, 'a99dc473-fe2d-4cf9-8732-20c61575fbb7', 'DENIED', 'E-404', '2026-09-22 10:12:48', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (831, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.162', NULL, '1bf54020-3b80-4fff-9288-c76c14415d80', 'DENIED', 'E-404', '2026-09-22 10:12:48', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (832, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.162', NULL, '5bf65a2a-b407-4173-a3c8-6fb5d91fc602', 'DENIED', 'E-404', '2026-09-22 10:12:48', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (833, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.162', NULL, 'fedd3499-f146-4c2d-804d-95be2ec38d74', 'DENIED', 'E-404', '2026-09-22 10:12:49', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (834, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.162', NULL, 'fb7d842b-c983-4e55-871f-6c23a6db1a36', 'DENIED', 'E-404', '2026-09-22 10:12:49', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (835, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.162', NULL, '93422e99-a652-47a9-88ab-dae1f9d27577', 'DENIED', 'E-404', '2026-09-22 10:12:49', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (836, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.162', NULL, '0f10e640-e2dd-48b4-be85-a863b6c0e628', 'DENIED', 'E-404', '2026-09-22 10:12:49', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (837, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.162', NULL, 'c95e3bbb-5a5d-49eb-8618-b6e8c4f23801', 'DENIED', 'E-404', '2026-09-22 10:12:49', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (838, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.162', NULL, '9c57e424-6c13-48ab-86b6-5aaa0d8cee21', 'DENIED', 'E-404', '2026-09-22 10:12:49', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (839, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.162', NULL, '20b0a41f-d7ad-4d6b-a766-afb4229512f4', 'DENIED', 'E-404', '2026-09-22 10:12:49', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (840, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.162', NULL, '073f5e47-d99b-4867-951e-a085c4255b32', 'DENIED', 'E-404', '2026-09-22 10:12:49', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (841, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.162', NULL, '89196ac2-7928-46a8-847a-738e46981939', 'DENIED', 'E-404', '2026-09-22 10:12:49', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (842, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.162', NULL, '164add23-d669-4226-b25a-646cbf560393', 'DENIED', 'E-404', '2026-09-22 10:12:50', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (843, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.162', NULL, '19dad57d-19df-449c-b798-84853ca6b2cb', 'DENIED', 'E-404', '2026-09-22 10:12:50', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (844, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.162', NULL, '4dd7d290-5f90-406a-9793-23dc0beed215', 'DENIED', 'E-404', '2026-09-22 10:12:50', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (845, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.162', NULL, '654cea26-d1ae-44c9-be3b-6435b800ee32', 'DENIED', 'E-404', '2026-09-22 10:12:50', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (846, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.162', NULL, '49a6d959-abf8-4586-b4a7-3e91843fe58f', 'DENIED', 'E-404', '2026-09-22 10:12:50', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (847, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.162', NULL, 'd2d2ae68-d54c-45d3-aed1-a93afbe314b4', 'DENIED', 'E-404', '2026-09-22 10:12:50', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (848, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.162', NULL, '0694cd8f-d5db-4cb5-b380-2377332434f0', 'DENIED', 'E-404', '2026-09-22 10:12:50', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (849, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.162', NULL, 'f4f9373e-e7b7-470b-81cd-8b6b05276b95', 'DENIED', 'E-404', '2026-09-22 10:12:50', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (850, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.162', NULL, 'd0dd3877-155a-4bbf-b4cb-84d0cac67a2b', 'DENIED', 'E-404', '2026-09-22 10:12:50', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (851, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.162', NULL, '4d472e2c-7d35-4297-ba24-48d582c1b87b', 'DENIED', 'E-404', '2026-09-22 10:12:51', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (852, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '10.78.43.162', NULL, '20579fb8-b5cc-4637-b658-ea076cdc29da', 'DENIED', 'E-404', '2026-09-22 10:12:51', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (853, 3, NULL, 'LOGIN', 'USER', 3, '0:0:0:0:0:0:0:1', 'wx-login', '24c1a317-a234-4d6e-b48a-5a1809639f58', 'SUCCESS', NULL, '2026-09-22 13:43:27', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (854, 3, 1, 'BIND_QUERY', 'CHILD', 3, '0:0:0:0:0:0:0:1', 'query own binding', '3634a84b-818f-45ef-91fb-56fcf678392d', 'SUCCESS', NULL, '2026-09-22 13:43:29', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (855, 3, 1, 'REQUEST_FAILED', NULL, NULL, '0:0:0:0:0:0:0:1', NULL, '0d3f249d-326f-4d6f-8b10-37cfbe5dad69', 'DENIED', 'E-404', '2026-09-22 13:43:29', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (856, 3, 1, 'REQUEST_FAILED', NULL, NULL, '0:0:0:0:0:0:0:1', NULL, 'e4a6179b-c8e5-4f4b-b548-09e92cf50523', 'DENIED', 'E-404', '2026-09-22 13:43:29', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (857, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '0:0:0:0:0:0:0:1', NULL, 'd8941afe-c014-49a3-b024-c853ab9633dd', 'DENIED', 'E-404', '2026-09-22 13:43:29', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (858, 3, 1, 'REQUEST_FAILED', NULL, NULL, '0:0:0:0:0:0:0:1', NULL, '8d18643d-75cd-43cf-a7cb-ab6fbfbcd699', 'FAILED', 'E-500', '2026-09-22 13:43:30', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (859, 3, 1, 'BIND_QUERY', 'CHILD', 3, '0:0:0:0:0:0:0:1', 'query own binding', '1ffcc135-e9a5-48c4-8bb1-2f3a4404858c', 'SUCCESS', NULL, '2026-09-22 13:43:39', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (860, 3, 1, 'REQUEST_FAILED', NULL, NULL, '0:0:0:0:0:0:0:1', NULL, '69fdadcc-acc9-40ec-99a9-3a9c0648579a', 'FAILED', 'E-500', '2026-09-22 13:43:39', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (861, 3, 1, 'BIND_QUERY', 'CHILD', 3, '0:0:0:0:0:0:0:1', 'query own binding', 'c99000a5-bbea-445f-ba8d-555fa1c65e3d', 'SUCCESS', NULL, '2026-09-22 13:43:44', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (862, 3, 1, 'REQUEST_FAILED', NULL, NULL, '0:0:0:0:0:0:0:1', NULL, '79e95450-c4c4-43d0-8cda-0bd5f68e5b54', 'FAILED', 'E-500', '2026-09-22 13:43:44', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (863, 3, 1, 'REQUEST_FAILED', NULL, NULL, '0:0:0:0:0:0:0:1', NULL, '3d9faa54-9f7a-4191-9926-d1e564ec65d6', 'FAILED', 'E-500', '2026-09-22 13:43:44', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (864, 3, 1, 'BIND_QUERY', 'CHILD', 3, '0:0:0:0:0:0:0:1', 'query own binding', 'd18a150c-915a-4e31-944c-66a180ca9959', 'SUCCESS', NULL, '2026-09-22 13:48:33', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (865, 3, 1, 'BIND_QUERY', 'CHILD', 3, '0:0:0:0:0:0:0:1', 'query own binding', '10cde156-6014-4c38-aaf8-4a95f3312e99', 'SUCCESS', NULL, '2026-09-22 13:48:39', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (866, 3, 1, 'BIND_QUERY', 'CHILD', 3, '0:0:0:0:0:0:0:1', 'query own binding', '4e3c574d-e939-4c75-b148-44bf4174c2eb', 'SUCCESS', NULL, '2026-09-22 13:48:44', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (867, 3, 1, 'BIND_QUERY', 'CHILD', 3, '0:0:0:0:0:0:0:1', 'query own binding', '5183eb12-ddb3-458f-b331-f63a2ff4c6fb', 'SUCCESS', NULL, '2026-09-22 13:49:20', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (868, 3, 1, 'REQUEST_FAILED', NULL, NULL, '0:0:0:0:0:0:0:1', NULL, '56a5ff77-4a77-4fe1-bd51-87891f8a767a', 'DENIED', 'E-404', '2026-09-22 13:49:20', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (869, 3, 1, 'REQUEST_FAILED', NULL, NULL, '0:0:0:0:0:0:0:1', NULL, 'd76bf2a1-0aac-4302-835e-0be0063c50a9', 'DENIED', 'E-404', '2026-09-22 13:49:20', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (870, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '0:0:0:0:0:0:0:1', NULL, '714ea56b-d552-4652-8510-94dbdb4bb2cf', 'DENIED', 'E-404', '2026-09-22 13:49:20', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (871, 3, 1, 'BIND_QUERY', 'CHILD', 3, '0:0:0:0:0:0:0:1', 'query own binding', '50bc649c-57d9-4faf-a995-3b75b4880560', 'SUCCESS', NULL, '2026-09-22 13:49:23', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (872, 3, 1, 'REQUEST_FAILED', NULL, NULL, '0:0:0:0:0:0:0:1', NULL, 'ab9b6b34-d569-4729-bb6c-4b0124f1cdb9', 'DENIED', 'E-404', '2026-09-22 13:49:23', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (873, 3, 1, 'REQUEST_FAILED', NULL, NULL, '0:0:0:0:0:0:0:1', NULL, '00d77fce-518c-4705-9bf6-ce7fa77977f7', 'DENIED', 'E-404', '2026-09-22 13:49:27', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (874, 3, 1, 'REQUEST_FAILED', NULL, NULL, '0:0:0:0:0:0:0:1', NULL, 'd684a5fe-91db-4e9b-9b0a-5cb6d9d016f9', 'DENIED', 'E-404', '2026-09-22 13:49:28', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (875, 3, 1, 'BIND_QUERY', 'CHILD', 3, '0:0:0:0:0:0:0:1', 'query own binding', 'b6f7ffc4-362a-4a7b-90d1-f63d6ec85c06', 'SUCCESS', NULL, '2026-09-22 13:49:31', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (876, 3, 1, 'REQUEST_FAILED', NULL, NULL, '0:0:0:0:0:0:0:1', NULL, '65885d76-8103-4259-bf7a-e708d7c4758c', 'DENIED', 'E-404', '2026-09-22 13:49:31', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (877, 3, 1, 'REQUEST_FAILED', NULL, NULL, '0:0:0:0:0:0:0:1', NULL, 'aefb9af1-f9a1-4cac-8691-cb32adaef315', 'DENIED', 'E-404', '2026-09-22 13:49:31', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (878, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '0:0:0:0:0:0:0:1', NULL, '502d7f97-860e-4663-bd75-eafadedf7746', 'DENIED', 'E-404', '2026-09-22 13:49:31', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (879, 3, 1, 'BIND_QUERY', 'CHILD', 3, '0:0:0:0:0:0:0:1', 'query own binding', '4ca48916-ea87-45d5-9dce-42c2af91d919', 'SUCCESS', NULL, '2026-09-22 13:49:33', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (880, 3, 1, 'BIND_QUERY', 'CHILD', 3, '0:0:0:0:0:0:0:1', 'query own binding', 'f5e3e031-4202-4240-a763-84385d95e0c7', 'SUCCESS', NULL, '2026-09-22 13:49:39', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (881, 3, 1, 'BIND_QUERY', 'CHILD', 3, '0:0:0:0:0:0:0:1', 'query own binding', '7e2868cc-65ed-4800-a5e4-f14837f7f476', 'SUCCESS', NULL, '2026-09-22 13:49:48', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (882, 3, 1, 'REQUEST_FAILED', NULL, NULL, '0:0:0:0:0:0:0:1', NULL, '64883d15-d076-4448-9f0a-016029ec972e', 'DENIED', 'E-404', '2026-09-22 13:49:48', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (883, 3, 1, 'REQUEST_FAILED', NULL, NULL, '0:0:0:0:0:0:0:1', NULL, '2ea2224f-1830-4da8-bb4a-1c90de0d43ec', 'DENIED', 'E-404', '2026-09-22 13:49:48', 0);
INSERT INTO `sys_audit_log` (`id`, `actor_user_id`, `family_id`, `action`, `target_type`, `target_id`, `ip`, `detail`, `request_id`, `result`, `error_code`, `create_time`, `delete_at`) VALUES (884, NULL, NULL, 'REQUEST_FAILED', NULL, NULL, '0:0:0:0:0:0:0:1', NULL, '341a7604-eed1-4320-beab-564e9a37a40a', 'DENIED', 'E-404', '2026-09-22 13:49:48', 0);
COMMIT;

-- ----------------------------
-- Table structure for sys_notice
-- ----------------------------
DROP TABLE IF EXISTS `sys_notice`;
CREATE TABLE `sys_notice` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `event_key` varchar(128) NOT NULL,
  `receiver_id` bigint NOT NULL,
  `family_id` bigint NOT NULL,
  `child_id` bigint NOT NULL,
  `channel` varchar(16) NOT NULL,
  `status` varchar(16) NOT NULL,
  `event_type` varchar(32) NOT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `delete_at` bigint NOT NULL DEFAULT '0',
  `read_at` bigint DEFAULT NULL,
  `attempt_count` int NOT NULL DEFAULT '0',
  `last_attempt_at` bigint DEFAULT NULL,
  `next_retry_at` bigint DEFAULT NULL,
  `last_error` varchar(32) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_event_receiver_channel` (`event_key`,`receiver_id`,`channel`),
  KEY `idx_delivery` (`channel`,`status`,`id`),
  KEY `idx_child_scope` (`family_id`,`child_id`),
  KEY `idx_notice_inbox` (`receiver_id`,`channel`,`read_at`,`id`),
  KEY `idx_notice_retry` (`channel`,`status`,`next_retry_at`,`id`),
  CONSTRAINT `chk_notice_attempts` CHECK ((`attempt_count` between 0 and 4))
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Records of sys_notice
-- ----------------------------
BEGIN;
INSERT INTO `sys_notice` (`id`, `event_key`, `receiver_id`, `family_id`, `child_id`, `channel`, `status`, `event_type`, `create_time`, `delete_at`, `read_at`, `attempt_count`, `last_attempt_at`, `next_retry_at`, `last_error`) VALUES (1, 'binding:2:1:PENDING', 2, 1, 3, 'IN_APP', 'PENDING', 'BIND_PENDING', '2026-09-22 10:10:30', 0, NULL, 0, NULL, NULL, NULL);
INSERT INTO `sys_notice` (`id`, `event_key`, `receiver_id`, `family_id`, `child_id`, `channel`, `status`, `event_type`, `create_time`, `delete_at`, `read_at`, `attempt_count`, `last_attempt_at`, `next_retry_at`, `last_error`) VALUES (2, 'binding:2:1:PENDING', 2, 1, 3, 'SUBSCRIBE', 'UNAUTHORIZED', 'BIND_PENDING', '2026-09-22 10:10:30', 0, NULL, 0, NULL, NULL, NULL);
INSERT INTO `sys_notice` (`id`, `event_key`, `receiver_id`, `family_id`, `child_id`, `channel`, `status`, `event_type`, `create_time`, `delete_at`, `read_at`, `attempt_count`, `last_attempt_at`, `next_retry_at`, `last_error`) VALUES (3, 'binding:2:1:BOUND', 3, 1, 3, 'IN_APP', 'PENDING', 'BIND_BOUND', '2026-09-22 10:11:13', 0, 1790056185497, 0, NULL, NULL, NULL);
INSERT INTO `sys_notice` (`id`, `event_key`, `receiver_id`, `family_id`, `child_id`, `channel`, `status`, `event_type`, `create_time`, `delete_at`, `read_at`, `attempt_count`, `last_attempt_at`, `next_retry_at`, `last_error`) VALUES (4, 'binding:2:1:BOUND', 3, 1, 3, 'SUBSCRIBE', 'UNAUTHORIZED', 'BIND_BOUND', '2026-09-22 10:11:13', 0, NULL, 0, NULL, NULL, NULL);
COMMIT;

-- ----------------------------
-- Table structure for sys_notice_subscription
-- ----------------------------
DROP TABLE IF EXISTS `sys_notice_subscription`;
CREATE TABLE `sys_notice_subscription` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `notice_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `consent_id` bigint NOT NULL,
  `status` varchar(16) NOT NULL,
  `authorized_at` bigint NOT NULL,
  `expires_at` bigint NOT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `delete_at` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_subscription_notice` (`notice_id`),
  KEY `idx_subscription_user` (`user_id`,`status`),
  CONSTRAINT `chk_subscription_status` CHECK ((`status` in (_utf8mb4'AUTHORIZED',_utf8mb4'REVOKED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Records of sys_notice_subscription
-- ----------------------------
BEGIN;
COMMIT;

-- ----------------------------
-- Table structure for sys_privacy_request
-- ----------------------------
DROP TABLE IF EXISTS `sys_privacy_request`;
CREATE TABLE `sys_privacy_request` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `requester_id` bigint NOT NULL,
  `child_id` bigint NOT NULL,
  `family_id` bigint NOT NULL,
  `request_type` varchar(16) NOT NULL,
  `idempotency_key` varchar(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `request_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `status` varchar(32) NOT NULL,
  `due_at` bigint DEFAULT NULL,
  `verified_at` bigint DEFAULT NULL,
  `result_ref` varchar(512) DEFAULT NULL,
  `expires_at` bigint DEFAULT NULL,
  `error_code` varchar(32) DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `delete_at` bigint NOT NULL DEFAULT '0',
  `operator_id` bigint DEFAULT NULL,
  `evidence_ref` varchar(128) DEFAULT NULL,
  `version` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_request` (`requester_id`,`request_type`,`idempotency_key`),
  UNIQUE KEY `uk_privacy_request_key` (`requester_id`,`idempotency_key`),
  KEY `idx_processing` (`status`,`due_at`),
  KEY `idx_child_scope` (`family_id`,`child_id`),
  KEY `idx_privacy_requester` (`requester_id`,`child_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Records of sys_privacy_request
-- ----------------------------
BEGIN;
COMMIT;

-- ----------------------------
-- Table structure for sys_privacy_verification
-- ----------------------------
DROP TABLE IF EXISTS `sys_privacy_verification`;
CREATE TABLE `sys_privacy_verification` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `requester_id` bigint NOT NULL,
  `request_id` bigint NOT NULL,
  `verified_at` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_privacy_verification` (`code_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Records of sys_privacy_verification
-- ----------------------------
BEGIN;
COMMIT;

-- ----------------------------
-- Table structure for usr_child_profile
-- ----------------------------
DROP TABLE IF EXISTS `usr_child_profile`;
CREATE TABLE `usr_child_profile` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `family_id` bigint NOT NULL,
  `nickname` varchar(64) DEFAULT NULL,
  `grade` varchar(32) DEFAULT NULL,
  `school` varchar(128) DEFAULT NULL,
  `allergies` json DEFAULT NULL,
  `dislikes` json DEFAULT NULL,
  `tastes` json DEFAULT NULL,
  `profile_status` varchar(16) NOT NULL DEFAULT 'INCOMPLETE',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `delete_at` bigint NOT NULL DEFAULT '0',
  `favorite_dish_ids` json NOT NULL DEFAULT (json_array()),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user` (`user_id`),
  KEY `idx_family` (`family_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Records of usr_child_profile
-- ----------------------------
BEGIN;
INSERT INTO `usr_child_profile` (`id`, `user_id`, `family_id`, `nickname`, `grade`, `school`, `allergies`, `dislikes`, `tastes`, `profile_status`, `create_time`, `update_time`, `delete_at`, `favorite_dish_ids`) VALUES (1, 3, 1, '昊昊', '六年级', '张江竹园中学', '[]', '[]', '[]', 'COMPLETE', '2026-09-22 10:11:54', '2026-09-22 10:11:54', 0, '[]');
COMMIT;

-- ----------------------------
-- Table structure for usr_child_want_eat
-- ----------------------------
DROP TABLE IF EXISTS `usr_child_want_eat`;
CREATE TABLE `usr_child_want_eat` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `child_id` bigint NOT NULL,
  `family_id` bigint NOT NULL,
  `menu_date` date NOT NULL,
  `meal_type` varchar(16) NOT NULL COMMENT 'BREAKFAST/LUNCH/DINNER',
  `source_type` varchar(16) NOT NULL COMMENT '标记时所在菜单来源 SCHOOL/FAMILY（上下文，不进唯一键）',
  `dish_type` varchar(16) NOT NULL COMMENT '菜品类型 PRESET/FAMILY（与 DishRef 一致）',
  `dish_id` bigint NOT NULL,
  `status` varchar(16) NOT NULL DEFAULT 'MARKED' COMMENT 'MARKED/ADOPTED/COOKED（P2 状态流转用，P0 仅写 MARKED）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `delete_at` bigint NOT NULL DEFAULT '0',
  `version` int NOT NULL DEFAULT '0',
  `wish_id` bigint NOT NULL DEFAULT '0' COMMENT '被收编的心愿单 id，0=未收编',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_child_date_meal_dish` (`child_id`,`menu_date`,`meal_type`,`dish_type`,`dish_id`,`delete_at`),
  KEY `idx_family_date` (`family_id`,`menu_date`,`meal_type`),
  KEY `idx_child_date` (`child_id`,`menu_date`),
  KEY `idx_want_eat_wish` (`wish_id`),
  CONSTRAINT `fk_want_eat_family` FOREIGN KEY (`family_id`) REFERENCES `usr_family` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='儿童每日想吃标记';

-- ----------------------------
-- Records of usr_child_want_eat
-- ----------------------------
BEGIN;
COMMIT;

-- ----------------------------
-- Table structure for usr_consent_log
-- ----------------------------
DROP TABLE IF EXISTS `usr_consent_log`;
CREATE TABLE `usr_consent_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `child_id` bigint NOT NULL,
  `family_id` bigint NOT NULL,
  `apply_id` bigint DEFAULT NULL,
  `application_version` int DEFAULT NULL,
  `consent_type` varchar(32) NOT NULL,
  `action` varchar(16) NOT NULL,
  `version` varchar(16) NOT NULL,
  `self_reported_age` int DEFAULT NULL,
  `guardian_status` varchar(16) NOT NULL,
  `signed_at` bigint DEFAULT NULL,
  `expire_at` bigint DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `delete_at` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_child` (`child_id`),
  KEY `idx_consent_scope` (`family_id`,`child_id`,`user_id`,`apply_id`,`application_version`,`consent_type`,`id`),
  KEY `idx_family` (`family_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Records of usr_consent_log
-- ----------------------------
BEGIN;
INSERT INTO `usr_consent_log` (`id`, `user_id`, `child_id`, `family_id`, `apply_id`, `application_version`, `consent_type`, `action`, `version`, `self_reported_age`, `guardian_status`, `signed_at`, `expire_at`, `create_time`, `update_time`, `delete_at`) VALUES (1, 2, 3, 1, 2, 1, 'PROFILE', 'GRANT', 'v1', 40, 'SELF_ATTESTED', 1790043060976, 1821579060976, '2026-09-22 10:11:01', '2026-09-22 10:11:01', 0);
COMMIT;

-- ----------------------------
-- Table structure for usr_family
-- ----------------------------
DROP TABLE IF EXISTS `usr_family`;
CREATE TABLE `usr_family` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `family_name` varchar(64) NOT NULL,
  `owner_user_id` bigint NOT NULL,
  `invite_code` varchar(6) DEFAULT NULL,
  `invite_code_expire` bigint DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `delete_at` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_invite_code` (`invite_code`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Records of usr_family
-- ----------------------------
BEGIN;
INSERT INTO `usr_family` (`id`, `family_name`, `owner_user_id`, `invite_code`, `invite_code_expire`, `create_time`, `update_time`, `delete_at`) VALUES (1, '111', 2, '73CPMC', 1790129365446, '2026-09-20 13:35:31', '2026-09-20 13:35:31', 0);
COMMIT;

-- ----------------------------
-- Table structure for usr_family_member
-- ----------------------------
DROP TABLE IF EXISTS `usr_family_member`;
CREATE TABLE `usr_family_member` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `family_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `relation_label` varchar(32) DEFAULT NULL,
  `role` varchar(16) NOT NULL,
  `bind_status` varchar(16) NOT NULL DEFAULT 'PENDING',
  `guardian_status` varchar(16) NOT NULL DEFAULT 'UNVERIFIED',
  `application_version` int NOT NULL DEFAULT '1',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `delete_at` bigint NOT NULL DEFAULT '0',
  `effective_child_id` bigint GENERATED ALWAYS AS ((case when ((`role` = _utf8mb4'CHILD') and (`bind_status` in (_utf8mb4'PENDING',_utf8mb4'BOUND')) and (`delete_at` = 0)) then `user_id` else NULL end)) STORED,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_family_user` (`family_id`,`user_id`),
  UNIQUE KEY `uk_effective_child` (`effective_child_id`),
  KEY `idx_family` (`family_id`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Records of usr_family_member
-- ----------------------------
BEGIN;
INSERT INTO `usr_family_member` (`id`, `family_id`, `user_id`, `relation_label`, `role`, `bind_status`, `guardian_status`, `application_version`, `create_time`, `update_time`, `delete_at`) VALUES (1, 1, 2, '家长', 'PARENT', 'BOUND', 'UNVERIFIED', 1, '2026-09-20 13:35:31', '2026-09-20 13:35:31', 0);
INSERT INTO `usr_family_member` (`id`, `family_id`, `user_id`, `relation_label`, `role`, `bind_status`, `guardian_status`, `application_version`, `create_time`, `update_time`, `delete_at`) VALUES (2, 1, 3, NULL, 'CHILD', 'BOUND', 'SELF_ATTESTED', 1, '2026-09-22 10:10:30', '2026-09-22 10:10:30', 0);
COMMIT;

-- ----------------------------
-- Table structure for usr_family_setting
-- ----------------------------
DROP TABLE IF EXISTS `usr_family_setting`;
CREATE TABLE `usr_family_setting` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `family_id` bigint NOT NULL,
  `wish_menu_max_dishes` tinyint NOT NULL DEFAULT '5' COMMENT '心愿菜单可选菜品上限 1~10',
  `wish_menu_enabled` tinyint NOT NULL DEFAULT '1' COMMENT '心愿菜单功能开关 1=开启 0=关闭',
  `version` int NOT NULL DEFAULT '0' COMMENT '手写乐观锁（项目未装配乐观锁插件）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `delete_at` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_family_setting` (`family_id`,`delete_at`),
  CONSTRAINT `fk_setting_family` FOREIGN KEY (`family_id`) REFERENCES `usr_family` (`id`),
  CONSTRAINT `chk_setting_enabled` CHECK ((`wish_menu_enabled` in (0,1))),
  CONSTRAINT `chk_setting_max` CHECK ((`wish_menu_max_dishes` between 1 and 10))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='家庭级设置（心愿菜单上限与开关）';

-- ----------------------------
-- Records of usr_family_setting
-- ----------------------------
BEGIN;
COMMIT;

-- ----------------------------
-- Table structure for usr_user
-- ----------------------------
DROP TABLE IF EXISTS `usr_user`;
CREATE TABLE `usr_user` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `openid` varchar(64) NOT NULL,
  `unionid` varchar(64) DEFAULT NULL,
  `role` varchar(16) NOT NULL DEFAULT 'UNSELECTED',
  `nickname` varchar(64) DEFAULT NULL,
  `avatar_url` varchar(512) DEFAULT NULL,
  `phone` varchar(20) DEFAULT NULL,
  `status` varchar(16) NOT NULL DEFAULT 'NORMAL',
  `token_version` bigint NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `delete_at` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_openid` (`openid`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Records of usr_user
-- ----------------------------
BEGIN;
INSERT INTO `usr_user` (`id`, `openid`, `unionid`, `role`, `nickname`, `avatar_url`, `phone`, `status`, `token_version`, `create_time`, `update_time`, `delete_at`) VALUES (1, 'mock_openid_synthetic_parent_1', 'mock_unionid_synthetic_parent_1', 'UNSELECTED', NULL, NULL, NULL, 'NORMAL', 0, '2026-09-20 13:32:04', '2026-09-20 13:32:04', 0);
INSERT INTO `usr_user` (`id`, `openid`, `unionid`, `role`, `nickname`, `avatar_url`, `phone`, `status`, `token_version`, `create_time`, `update_time`, `delete_at`) VALUES (2, 'mock_openid_parent_demo', 'mock_unionid_parent_demo', 'PARENT', NULL, NULL, NULL, 'NORMAL', 4, '2026-09-20 13:35:20', '2026-09-20 13:35:20', 0);
INSERT INTO `usr_user` (`id`, `openid`, `unionid`, `role`, `nickname`, `avatar_url`, `phone`, `status`, `token_version`, `create_time`, `update_time`, `delete_at`) VALUES (3, 'mock_openid_child_demo', 'mock_unionid_child_demo', 'CHILD', NULL, NULL, NULL, 'NORMAL', 3, '2026-09-22 10:08:19', '2026-09-22 10:08:19', 0);
COMMIT;

SET FOREIGN_KEY_CHECKS = 1;

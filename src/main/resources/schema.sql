-- ==========================================================================
-- Agent Server Database Schema
-- ==========================================================================

CREATE DATABASE IF NOT EXISTS agent_server DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE agent_server;

-- --------------------------------------------------------------------------
-- Session table
-- One session per user conversation, maps 1:1 to an agent instance
-- --------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_session` (
    `session_id`     VARCHAR(64)  NOT NULL COMMENT 'Unique session ID',
    `user_id`        VARCHAR(64)  NOT NULL COMMENT 'User ID',
    `status`         VARCHAR(20)  NOT NULL DEFAULT 'INIT' COMMENT 'Session status: INIT, RUNNING, SLEEP, CLOSED',
    `title`          VARCHAR(256) NULL COMMENT 'Session title',
    `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation time',
    `last_active_at` DATETIME     NULL COMMENT 'Last activity time',
    PRIMARY KEY (`session_id`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_status` (`status`),
    INDEX `idx_last_active_at` (`last_active_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Session management table';

-- --------------------------------------------------------------------------
-- Task table
-- Each intent recognition plan is mapped to a task
-- --------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_task` (
    `task_id`       VARCHAR(64)  NOT NULL COMMENT 'Unique task ID',
    `session_id`    VARCHAR(64)  NOT NULL COMMENT 'Session ID',
    `name`          VARCHAR(256) NULL COMMENT 'Task name/description',
    `status`        VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'Task status: PENDING, RUNNING, COMPLETED, FAILED, CANCELLED',
    `intent`        VARCHAR(64)  NULL COMMENT 'Recognized intent',
    `steps_json`    TEXT         NULL COMMENT 'Execution steps (JSON)',
    `result_json`   TEXT         NULL COMMENT 'Task result (JSON)',
    `error_message` TEXT         NULL COMMENT 'Error message if failed',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation time',
    `completed_at`  DATETIME     NULL COMMENT 'Completion time',
    PRIMARY KEY (`task_id`),
    INDEX `idx_session_id` (`session_id`),
    INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Task management table';

-- --------------------------------------------------------------------------
-- Agent context table
-- Stores serialized agent runtime context for dump/load (persistence)
-- --------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_agent_context` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'Auto-increment ID',
    `session_id`   VARCHAR(64)  NOT NULL COMMENT 'Session ID',
    `context_data` LONGTEXT     NULL COMMENT 'Serialized agent context (JSON)',
    `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation time',
    `updated_at`   DATETIME     NULL COMMENT 'Last update time',
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_session_id` (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent context persistence table';

-- --------------------------------------------------------------------------
-- Conversation history table
-- Archives all conversation events for history retrieval (separate from streaming)
-- --------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_conversation_history` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'Auto-increment ID',
    `session_id`       VARCHAR(64)  NOT NULL COMMENT 'Session ID',
    `event_id`         VARCHAR(64)  NOT NULL COMMENT 'Event ID',
    `event_type`       VARCHAR(32)  NOT NULL COMMENT 'Event type',
    `event_source`     VARCHAR(64)  NULL COMMENT 'Event source',
    `event_source_type` VARCHAR(32) NULL COMMENT 'Event source type',
    `task_id`          VARCHAR(64)  NULL COMMENT 'Associated task ID',
    `content`          TEXT         NULL COMMENT 'Message content (JSON)',
    `created_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation time',
    PRIMARY KEY (`id`),
    INDEX `idx_session_id` (`session_id`),
    INDEX `idx_event_type` (`event_type`),
    INDEX `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Conversation history archive table';

-- ==========================================================================
-- Memory & Segment System Tables
-- ==========================================================================

-- --------------------------------------------------------------------------
-- Segment table
-- Stores inference segments for context reconstruction
-- --------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_segment` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'Auto-increment ID',
    `session_id`   VARCHAR(64)  NOT NULL COMMENT 'Session ID',
    `segment_type` VARCHAR(32)  NOT NULL COMMENT 'Segment type: USER_INPUT, THOUGHT, CODE, CODE_RESULT, EXPRESS, ROUND, TOOL_CALL, TOOL_RESULT, ERROR, SYSTEM, ENVIRONMENT',
    `content`      TEXT         NULL COMMENT 'Segment content',
    `round_index`  INT          NULL COMMENT 'Round index within the session',
    `metadata`     JSON         NULL COMMENT 'Additional metadata (JSON)',
    `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation time',
    PRIMARY KEY (`id`),
    INDEX `idx_session_id` (`session_id`),
    INDEX `idx_segment_type` (`segment_type`),
    INDEX `idx_round_index` (`round_index`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Inference segment table for context reconstruction';

-- --------------------------------------------------------------------------
-- Sensory memory table
-- Environment sensing snapshots captured during user interaction
-- --------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_sensory_memory` (
    `id`           BIGINT        NOT NULL AUTO_INCREMENT COMMENT 'Auto-increment ID',
    `session_id`   VARCHAR(64)   NOT NULL COMMENT 'Session ID',
    `page_url`     VARCHAR(1024) NULL COMMENT 'Current page URL',
    `page_title`   VARCHAR(256)  NULL COMMENT 'Current page title',
    `page_context` JSON          NULL COMMENT 'Page DOM/context data (JSON)',
    `user_actions` JSON          NULL COMMENT 'List of user actions (JSON)',
    `captured_at`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Capture time',
    `valid`        TINYINT       NOT NULL DEFAULT 1 COMMENT 'Whether the snapshot is valid: 1=valid, 0=invalid',
    PRIMARY KEY (`id`),
    INDEX `idx_session_id` (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Sensory memory table for environment sensing snapshots';

-- --------------------------------------------------------------------------
-- Short-term memory table
-- Session-level working memory for segments and entities
-- --------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_short_term_memory` (
    `id`           BIGINT      NOT NULL AUTO_INCREMENT COMMENT 'Auto-increment ID',
    `session_id`   VARCHAR(64) NOT NULL COMMENT 'Session ID',
    `user_id`      VARCHAR(64) NULL COMMENT 'User ID',
    `memory_type`  VARCHAR(32) NULL COMMENT 'Memory type: SEGMENT, ENTITY',
    `content`      TEXT        NULL COMMENT 'Memory content',
    `segment_type` VARCHAR(32) NULL COMMENT 'Segment type (when memory_type=SEGMENT)',
    `round_index`  INT         NULL COMMENT 'Round index within the session',
    `role`         VARCHAR(16) NULL COMMENT 'Role associated with the memory',
    `entity_type`  VARCHAR(64) NULL COMMENT 'Entity type (when memory_type=ENTITY)',
    `entity_data`  JSON        NULL COMMENT 'Entity data (JSON)',
    `tags`         JSON        NULL COMMENT 'Tags for categorization (JSON)',
    `created_at`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation time',
    `expire_at`    DATETIME    NULL COMMENT 'Expiration time',
    PRIMARY KEY (`id`),
    INDEX `idx_session_id` (`session_id`),
    INDEX `idx_memory_type` (`memory_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Short-term memory table for session-level working memory';

-- --------------------------------------------------------------------------
-- Knowledge point table
-- Long-term knowledge memory for domain-specific knowledge
-- --------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_knowledge_point` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'Auto-increment ID',
    `title`      VARCHAR(256) NOT NULL COMMENT 'Knowledge point title',
    `content`    TEXT         NOT NULL COMMENT 'Knowledge point content',
    `domain`     VARCHAR(64)  NULL COMMENT 'Knowledge domain',
    `tags`       JSON         NULL COMMENT 'Tags for categorization (JSON)',
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation time',
    PRIMARY KEY (`id`),
    INDEX `idx_domain` (`domain`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Long-term knowledge memory table';

-- --------------------------------------------------------------------------
-- Execution experience table
-- Code execution experience records for learning and improvement
-- --------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_execution_experience` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'Auto-increment ID',
    `tool_name`      VARCHAR(128) NULL COMMENT 'Tool name used in execution',
    `input_summary`  TEXT         NULL COMMENT 'Summary of execution input',
    `output_summary` TEXT         NULL COMMENT 'Summary of execution output',
    `success`        TINYINT      NOT NULL DEFAULT 0 COMMENT 'Whether execution succeeded: 1=success, 0=failure',
    `code`           TEXT         NULL COMMENT 'Executed code',
    `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation time',
    PRIMARY KEY (`id`),
    INDEX `idx_tool_name` (`tool_name`),
    INDEX `idx_success` (`success`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Execution experience table for learning from code execution';

-- --------------------------------------------------------------------------
-- User preference table
-- Stores user preference memory for personalization
-- --------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_user_preference` (
    `id`            BIGINT      NOT NULL AUTO_INCREMENT COMMENT 'Auto-increment ID',
    `user_id`       VARCHAR(64) NOT NULL COMMENT 'User ID',
    `entity_schema` JSON        NULL COMMENT 'Entity schema definition (JSON)',
    `entity_data`   JSON        NULL COMMENT 'Entity data (JSON)',
    `operation`     VARCHAR(64) NULL COMMENT 'Operation type',
    `tags`          JSON        NULL COMMENT 'Tags for categorization (JSON)',
    `window_start`  DATETIME    NULL COMMENT 'Time window start',
    `window_end`    DATETIME    NULL COMMENT 'Time window end',
    `created_at`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation time',
    PRIMARY KEY (`id`),
    INDEX `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='User preference memory table';

-- --------------------------------------------------------------------------
-- History chat summary table
-- Historical chat summaries for long-term memory and vector search
-- --------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_history_chat_summary` (
    `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT 'Auto-increment ID',
    `session_id`  VARCHAR(64) NOT NULL COMMENT 'Session ID',
    `user_id`     VARCHAR(64) NOT NULL COMMENT 'User ID',
    `summary`     TEXT        NULL COMMENT 'Chat summary',
    `search_keys` JSON        NULL COMMENT 'Keys for vector search (JSON)',
    `created_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation time',
    `expire_at`   DATETIME    NULL COMMENT 'Expiration time',
    PRIMARY KEY (`id`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_session_id` (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Historical chat summary table for long-term memory';

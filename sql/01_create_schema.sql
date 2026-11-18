-- MySQL 8.x。先执行本文件，再执行 02_init_data.sql。
-- 精简版使用独立数据库。历史 campus_secondhand 数据库无需删除。
-- 日期时间按 UTC 写入。商品分类与 RBAC 权限映射由后端代码统一定义。
CREATE DATABASE IF NOT EXISTS campus_market
    CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

USE campus_market;

CREATE TABLE IF NOT EXISTS users (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    phone VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
        COMMENT '规范化的国际手机号，例如 +86 后跟国内手机号',
    password_hash VARCHAR(60) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
        COMMENT 'Spring Security BCryptPasswordEncoder 生成的 BCrypt 哈希',
    nickname VARCHAR(40) NOT NULL,
    campus VARCHAR(64) NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_phone (phone),
    CONSTRAINT ck_users_phone CHECK (REGEXP_LIKE(phone, '^[+][1-9][0-9]{1,14}$', 'c')),
    CONSTRAINT ck_users_password_hash CHECK (
        REGEXP_LIKE(password_hash, '^[$]2[aby][$](0[4-9]|[12][0-9]|3[01])[$][./A-Za-z0-9]{53}$', 'c')
    ),
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'DISABLED'))
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS roles (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code VARCHAR(32) NOT NULL,
    name VARCHAR(64) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_roles_code (code)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS user_roles (
    user_id BIGINT UNSIGNED NOT NULL,
    role_id BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (id)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS items (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    seller_id BIGINT UNSIGNED NOT NULL,
    category VARCHAR(16) NOT NULL,
    campus VARCHAR(64) NOT NULL,
    image_keys JSON NULL COMMENT '商品图片存储键数组，由后端校验与排序',
    title VARCHAR(100) NOT NULL,
    description TEXT NOT NULL,
    condition_code VARCHAR(16) NOT NULL,
    sale_type VARCHAR(16) NOT NULL,
    price DECIMAL(10, 2) NULL COMMENT '一口价标价，拍卖规则见 auctions',
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    published_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY ix_items_market (status, sale_type, campus, published_at, id),
    KEY ix_items_seller (seller_id, status, updated_at, id),
    CONSTRAINT fk_items_seller FOREIGN KEY (seller_id) REFERENCES users (id),
    CONSTRAINT ck_items_category CHECK (category IN ('BOOKS', 'DORM', 'ELECTRONICS', 'SPORTS', 'OTHER')),
    CONSTRAINT ck_items_images CHECK (image_keys IS NULL OR JSON_TYPE(image_keys) = 'ARRAY'),
    CONSTRAINT ck_items_condition CHECK (condition_code IN ('NEW', 'LIKE_NEW', 'GOOD', 'FAIR')),
    CONSTRAINT ck_items_status CHECK (
        status IN ('DRAFT', 'PENDING_REVIEW', 'REJECTED', 'ON_SALE', 'AUCTIONING', 'RESERVED', 'SOLD', 'OFF_SHELF', 'UNSOLD', 'BLOCKED', 'DELETED')
    ),
    CONSTRAINT ck_items_sale_price CHECK (
        (sale_type = 'FIXED_PRICE' AND price IS NOT NULL AND price > 0)
        OR (sale_type = 'AUCTION' AND price IS NULL)
    )
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS item_reviews (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    item_id BIGINT UNSIGNED NOT NULL,
    item_version BIGINT UNSIGNED NOT NULL,
    reviewer_id BIGINT UNSIGNED NOT NULL,
    decision VARCHAR(16) NOT NULL,
    reason VARCHAR(500) NULL,
    reviewed_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_item_reviews_revision (item_id, item_version),
    KEY ix_item_reviews_reviewer (reviewer_id, reviewed_at, id),
    CONSTRAINT fk_item_reviews_item FOREIGN KEY (item_id) REFERENCES items (id),
    CONSTRAINT fk_item_reviews_reviewer FOREIGN KEY (reviewer_id) REFERENCES users (id),
    CONSTRAINT ck_item_reviews_decision CHECK (decision IN ('APPROVED', 'REJECTED')),
    CONSTRAINT ck_item_reviews_reason CHECK (
        decision = 'APPROVED' OR (reason IS NOT NULL AND CHAR_LENGTH(TRIM(reason)) > 0)
    )
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS conversations (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    item_id BIGINT UNSIGNED NOT NULL,
    buyer_id BIGINT UNSIGNED NOT NULL,
    buyer_last_read_message_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    seller_last_read_message_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    last_message_at DATETIME(3) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_conversations_item_buyer (item_id, buyer_id),
    KEY ix_conversations_buyer_time (buyer_id, last_message_at, id),
    CONSTRAINT fk_conversations_item FOREIGN KEY (item_id) REFERENCES items (id),
    CONSTRAINT fk_conversations_buyer FOREIGN KEY (buyer_id) REFERENCES users (id)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS chat_messages (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    conversation_id BIGINT UNSIGNED NOT NULL,
    sender_id BIGINT UNSIGNED NOT NULL,
    client_message_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    body VARCHAR(2000) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_chat_messages_client (conversation_id, sender_id, client_message_id),
    KEY ix_chat_messages_history (conversation_id, id),
    CONSTRAINT fk_chat_messages_conversation FOREIGN KEY (conversation_id) REFERENCES conversations (id),
    CONSTRAINT fk_chat_messages_sender FOREIGN KEY (sender_id) REFERENCES users (id),
    CONSTRAINT ck_chat_messages_body CHECK (CHAR_LENGTH(TRIM(body)) > 0)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS auctions (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    item_id BIGINT UNSIGNED NOT NULL,
    start_price DECIMAL(10, 2) NOT NULL,
    bid_increment DECIMAL(10, 2) NOT NULL,
    highest_price DECIMAL(10, 2) NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    duration_minutes INT UNSIGNED NOT NULL,
    started_at DATETIME(3) NULL,
    ends_at DATETIME(3) NULL,
    settled_at DATETIME(3) NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    open_item_id BIGINT UNSIGNED GENERATED ALWAYS AS (
        CASE WHEN status IN ('DRAFT', 'OPEN') THEN item_id ELSE NULL END
    ) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_auctions_open_item (open_item_id),
    KEY ix_auctions_due (status, ends_at, id),
    CONSTRAINT fk_auctions_item FOREIGN KEY (item_id) REFERENCES items (id),
    CONSTRAINT ck_auctions_price CHECK (
        start_price > 0 AND bid_increment > 0 AND (highest_price IS NULL OR highest_price >= start_price)
    ),
    CONSTRAINT ck_auctions_status CHECK (status IN ('DRAFT', 'OPEN', 'SETTLED', 'UNSOLD', 'CANCELLED')),
    CONSTRAINT ck_auctions_duration CHECK (duration_minutes > 0),
    CONSTRAINT ck_auctions_times CHECK (
        (started_at IS NULL AND ends_at IS NULL AND status IN ('DRAFT', 'CANCELLED'))
        OR (started_at IS NOT NULL AND ends_at IS NOT NULL AND ends_at > started_at AND status <> 'DRAFT')
    )
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS auction_bids (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    auction_id BIGINT UNSIGNED NOT NULL,
    bidder_id BIGINT UNSIGNED NOT NULL,
    client_request_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    amount DECIMAL(10, 2) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_auction_bids_request (auction_id, bidder_id, client_request_id),
    KEY ix_auction_bids_highest (auction_id, amount DESC, id),
    CONSTRAINT fk_auction_bids_auction FOREIGN KEY (auction_id) REFERENCES auctions (id),
    CONSTRAINT fk_auction_bids_bidder FOREIGN KEY (bidder_id) REFERENCES users (id),
    CONSTRAINT ck_auction_bids_amount CHECK (amount > 0)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS reservations (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    item_id BIGINT UNSIGNED NOT NULL,
    buyer_id BIGINT UNSIGNED NOT NULL,
    auction_id BIGINT UNSIGNED NULL,
    client_request_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    agreed_price DECIMAL(10, 2) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    buyer_confirmed_at DATETIME(3) NULL,
    seller_confirmed_at DATETIME(3) NULL,
    closed_reason VARCHAR(255) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    active_item_id BIGINT UNSIGNED GENERATED ALWAYS AS (
        CASE WHEN status = 'ACTIVE' THEN item_id ELSE NULL END
    ) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_reservations_active_item (active_item_id),
    UNIQUE KEY uk_reservations_auction (auction_id),
    UNIQUE KEY uk_reservations_buyer_request (buyer_id, client_request_id),
    KEY ix_reservations_item_date (item_id, created_at, id),
    KEY ix_reservations_buyer_date (buyer_id, created_at, id),
    CONSTRAINT fk_reservations_item FOREIGN KEY (item_id) REFERENCES items (id),
    CONSTRAINT fk_reservations_buyer FOREIGN KEY (buyer_id) REFERENCES users (id),
    CONSTRAINT fk_reservations_auction FOREIGN KEY (auction_id) REFERENCES auctions (id),
    CONSTRAINT ck_reservations_price CHECK (agreed_price > 0),
    CONSTRAINT ck_reservations_status CHECK (status IN ('ACTIVE', 'COMPLETED', 'CANCELLED', 'CLOSED')),
    CONSTRAINT ck_reservations_source CHECK (
        (auction_id IS NULL AND client_request_id IS NOT NULL)
        OR (auction_id IS NOT NULL AND client_request_id IS NULL)
    ),
    CONSTRAINT ck_reservations_confirmed CHECK (
        status <> 'COMPLETED' OR (buyer_confirmed_at IS NOT NULL AND seller_confirmed_at IS NOT NULL)
    )
) ENGINE = InnoDB;

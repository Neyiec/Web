-- 仅导入 campus_market_demo。先在该库执行建表和角色初始化，再运行本脚本。
-- 演示账号共用密码 CampusDemo2026!，数据库仅保存各账号的 BCrypt 哈希。
-- 卖家 +12025550101，买家 +12025550102，管理员 +12025550103，第二买家 +12025550104。
USE campus_market_demo;

DROP PROCEDURE IF EXISTS seed_campus_market_demo;
DELIMITER $$

CREATE PROCEDURE seed_campus_market_demo()
seed: BEGIN
    DECLARE v_student_role BIGINT UNSIGNED;
    DECLARE v_admin_role BIGINT UNSIGNED;
    DECLARE v_seller BIGINT UNSIGNED;
    DECLARE v_buyer BIGINT UNSIGNED;
    DECLARE v_admin BIGINT UNSIGNED;
    DECLARE v_other_buyer BIGINT UNSIGNED;
    DECLARE v_book BIGINT UNSIGNED;
    DECLARE v_mouse BIGINT UNSIGNED;
    DECLARE v_racket BIGINT UNSIGNED;
    DECLARE v_racket_auction BIGINT UNSIGNED;
    DECLARE v_pump BIGINT UNSIGNED;
    DECLARE v_conversation BIGINT UNSIGNED;
    DECLARE v_first_message BIGINT UNSIGNED;
    DECLARE v_seed_count INT;
    DECLARE v_hash_count INT;
    DECLARE v_now DATETIME(3);
    DECLARE v_seller_hash CHAR(60) DEFAULT '$2a$10$X7i.wuS32msHutpuTcXMMOyLAIAWbRuSJEazd6L2O.Rk7z.sKqwkG';
    DECLARE v_buyer_hash CHAR(60) DEFAULT '$2a$10$FXynMM.bs/BQDDdvMQmGR.QEvbBf1VgJA0/HRbGMjPq58WZ4ed7JW';
    DECLARE v_admin_hash CHAR(60) DEFAULT '$2a$10$Ef3wEoF2FVE7StrvGqIMPuW8ruEVjQGOe7WvcYD2iMCgHP4BUDzhS';
    DECLARE v_other_hash CHAR(60) DEFAULT '$2a$10$AeLv10E5JrP/Ic5ltYUpe.PxHfdSkDJACCu6pjrM6WwWdR64Gx/RC';
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    START TRANSACTION;
    SELECT COUNT(*), COALESCE(SUM(
        (phone = '+12025550101' AND password_hash = v_seller_hash)
        OR (phone = '+12025550102' AND password_hash = v_buyer_hash)
        OR (phone = '+12025550103' AND password_hash = v_admin_hash)
        OR (phone = '+12025550104' AND password_hash = v_other_hash)
    ), 0)
    INTO v_seed_count, v_hash_count
    FROM users
    WHERE phone IN ('+12025550101', '+12025550102', '+12025550103', '+12025550104');

    IF v_seed_count = 4 AND v_hash_count = 4 THEN
        COMMIT;
        SELECT 'Demo seed already imported' AS result;
        LEAVE seed;
    END IF;
    IF v_seed_count <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Demo account collision. Import into an empty demo database.';
    END IF;

    SELECT id INTO v_student_role FROM roles WHERE code = 'STUDENT';
    SELECT id INTO v_admin_role FROM roles WHERE code = 'ADMIN';
    IF v_student_role IS NULL OR v_admin_role IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Initialize STUDENT and ADMIN roles before importing demo data.';
    END IF;
    SET v_now = UTC_TIMESTAMP(3);

    INSERT INTO users (phone, password_hash, nickname, campus) VALUES
        ('+12025550101', v_seller_hash, '演示卖家', '东校区'),
        ('+12025550102', v_buyer_hash, '演示买家', '东校区'),
        ('+12025550103', v_admin_hash, '演示管理员', '东校区'),
        ('+12025550104', v_other_hash, '演示第二买家', '西校区');

    SELECT id INTO v_seller FROM users WHERE phone = '+12025550101';
    SELECT id INTO v_buyer FROM users WHERE phone = '+12025550102';
    SELECT id INTO v_admin FROM users WHERE phone = '+12025550103';
    SELECT id INTO v_other_buyer FROM users WHERE phone = '+12025550104';
    INSERT INTO user_roles (user_id, role_id) VALUES
        (v_seller, v_student_role),
        (v_buyer, v_student_role),
        (v_admin, v_admin_role),
        (v_other_buyer, v_student_role);

    INSERT INTO items (seller_id, category, campus, title, description, condition_code, sale_type, price,
                       status, version, published_at)
    VALUES (v_seller, 'BOOKS', '东校区', '【演示】高等数学教材', '上下册一套，附少量课堂笔记，可在东校区图书馆门口面交。',
            'GOOD', 'FIXED_PRICE', 28.00, 'ON_SALE', 1, v_now);
    SET v_book = LAST_INSERT_ID();

    INSERT INTO items (seller_id, category, campus, title, description, condition_code, sale_type, price,
                       status, version)
    VALUES (v_seller, 'DORM', '东校区', '【演示】宿舍台灯', '三档亮度，含充电线。可用于演示管理员审核上架。',
            'LIKE_NEW', 'FIXED_PRICE', 35.00, 'PENDING_REVIEW', 1);

    INSERT INTO items (seller_id, category, campus, title, description, condition_code, sale_type, price,
                       status, version)
    VALUES (v_seller, 'ELECTRONICS', '东校区', '【演示】无线鼠标', '闲置无线鼠标，可用于演示补充信息后重新提交审核。',
            'GOOD', 'FIXED_PRICE', 22.00, 'REJECTED', 1);
    SET v_mouse = LAST_INSERT_ID();

    INSERT INTO items (seller_id, category, campus, title, description, condition_code, sale_type, price,
                       status, version, published_at)
    VALUES (v_seller, 'SPORTS', '东校区', '【演示】羽毛球拍', '附拍套，已预置一笔 40 元出价，继续出价至少 45 元。',
            'GOOD', 'AUCTION', NULL, 'AUCTIONING', 1, v_now);
    SET v_racket = LAST_INSERT_ID();

    INSERT INTO items (seller_id, category, campus, title, description, condition_code, sale_type, price,
                       status, version, published_at)
    VALUES (v_seller, 'SPORTS', '东校区', '【演示】自行车打气筒', '适用英嘴和美嘴，可在体育馆门口面交。',
            'GOOD', 'FIXED_PRICE', 18.00, 'RESERVED', 1, v_now);
    SET v_pump = LAST_INSERT_ID();

    INSERT INTO item_reviews (item_id, item_version, reviewer_id, decision, reason, reviewed_at)
    VALUES (v_book, 1, v_admin, 'APPROVED', NULL, v_now),
           (v_mouse, 1, v_admin, 'REJECTED', '请补充接收器和电池的使用情况。', v_now),
           (v_racket, 1, v_admin, 'APPROVED', NULL, v_now),
           (v_pump, 1, v_admin, 'APPROVED', NULL, v_now);

    INSERT INTO auctions (item_id, start_price, bid_increment, highest_price, status, duration_minutes,
                          started_at, ends_at, version)
    VALUES (v_racket, 35.00, 5.00, 40.00, 'OPEN', 1440, v_now, DATE_ADD(v_now, INTERVAL 24 HOUR), 2);
    SET v_racket_auction = LAST_INSERT_ID();
    INSERT INTO auction_bids (auction_id, bidder_id, client_request_id, amount, created_at)
    VALUES (v_racket_auction, v_buyer, '2be2dbbb-65b7-475d-825e-c7cddde8079f', 40.00, v_now);

    INSERT INTO reservations (item_id, buyer_id, client_request_id, agreed_price)
    VALUES (v_pump, v_buyer, '07fd7fa5-cde4-4963-ba3a-bf6c729cd7bb', 18.00);

    INSERT INTO conversations (item_id, buyer_id, last_message_at)
    VALUES (v_pump, v_buyer, v_now);
    SET v_conversation = LAST_INSERT_ID();
    INSERT INTO chat_messages (conversation_id, sender_id, client_message_id, body, created_at)
    VALUES (v_conversation, v_buyer, '729358c3-540c-4c75-ae96-d3b2d1829a37', '你好，打气筒可以在东校区体育馆门口交接吗？', v_now);
    SET v_first_message = LAST_INSERT_ID();
    INSERT INTO chat_messages (conversation_id, sender_id, client_message_id, body, created_at)
    VALUES (v_conversation, v_seller, 'd10cade6-70c0-4e64-8d53-9c2b93e4f2a7', '可以，今天下午五点见。', v_now);
    UPDATE conversations SET seller_last_read_message_id = v_first_message WHERE id = v_conversation;

    COMMIT;
    SELECT 'Demo seed imported' AS result;
END$$

DELIMITER ;
CALL seed_campus_market_demo();
DROP PROCEDURE seed_campus_market_demo;

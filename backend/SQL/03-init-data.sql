-- ============================================
-- CampusHub 初始化数据
-- ============================================

USE campushub;

-- 初始化需求分类数据
INSERT INTO categories (name, has_review, sort_order) VALUES
    ('快递代取', 1, 1),
    ('学习辅导', 1, 2),
    ('二手交易', 0, 3),
    ('组队匹配', 0, 4),
    ('信息咨询', 1, 5)
ON DUPLICATE KEY UPDATE
    has_review = VALUES(has_review),
    sort_order = VALUES(sort_order);

-- 测试用户。
-- 密码 Test123456，管理员密码 nju123456
INSERT INTO users (
    name, email, phone, password_hash, password, role, credit_score, status, nickname, student_id
) VALUES
    ('测试账号1', 'Test1@smail.nju.edu.cn', 'encrypted_phone_1', '$2b$10$2DMBDXChU2GLMx3jQoD5fOTpT8zW4RmGOrxJfbH7y9vSFwu.ydmiS', '$2b$10$2DMBDXChU2GLMx3jQoD5fOTpT8zW4RmGOrxJfbH7y9vSFwu.ydmiS', 'requester', 100, 1, '测试账号1', '241880997'),
    ('测试账号2', 'Test2@smail.nju.edu.cn', 'encrypted_phone_2', '$2b$10$2DMBDXChU2GLMx3jQoD5fOTpT8zW4RmGOrxJfbH7y9vSFwu.ydmiS', '$2b$10$2DMBDXChU2GLMx3jQoD5fOTpT8zW4RmGOrxJfbH7y9vSFwu.ydmiS', 'requester', 100, 1, '测试账号2', '241880998'),
    ('管理员', 'admin@smail.nju.edu.cn', 'LX13rUmUALY9k4Ioj0moag==', '$2b$10$GKp4Iafz0/1ZTzEQAxdZ.e4K6JjOix8vPsVraNGI6eMGfQbwKEAve', '$2b$10$GKp4Iafz0/1ZTzEQAxdZ.e4K6JjOix8vPsVraNGI6eMGfQbwKEAve', 'admin', 100, 1, '管理员', '241880999')
ON DUPLICATE KEY UPDATE
    name = VALUES(name), role = VALUES(role),
    credit_score = VALUES(credit_score), status = VALUES(status),
    nickname = VALUES(nickname), student_id = VALUES(student_id);

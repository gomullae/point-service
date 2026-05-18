INSERT INTO point_config (config_key, config_value, value_type, description, created_at, updated_at) VALUES
    ('MAX_EARN_AMOUNT_ONCE', '100000', 'INT', '1회 최대 적립 포인트',       NOW(), NOW()),
    ('MAX_HOLD_AMOUNT',      '1000000', 'INT', '개인별 최대 보유 포인트',   NOW(), NOW()),
    ('DEFAULT_EXPIRY_DAYS',  '365',    'INT', '기본 만료일 (일 단위)',      NOW(), NOW());

-- =============================================
-- 설정
-- =============================================
CREATE TABLE IF NOT EXISTS point_config (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    config_key  VARCHAR(100) NOT NULL UNIQUE,
    config_value VARCHAR(255) NOT NULL,
    value_type  VARCHAR(20)  NOT NULL,
    description VARCHAR(500),
    created_at  DATETIME     NOT NULL,
    updated_at  DATETIME     NOT NULL
);

CREATE TABLE IF NOT EXISTS point_config_history (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    point_config_id  BIGINT       NOT NULL,
    config_key       VARCHAR(100) NOT NULL,
    old_value        VARCHAR(255),
    new_value        VARCHAR(255) NOT NULL,
    changed_by       VARCHAR(100) NOT NULL,
    created_at       DATETIME     NOT NULL,
    updated_at       DATETIME     NOT NULL,
    FOREIGN KEY (point_config_id) REFERENCES point_config(id)
);

-- =============================================
-- 계정
-- =============================================
CREATE TABLE IF NOT EXISTS point_account (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id    VARCHAR(100) NOT NULL UNIQUE,
    balance    BIGINT       NOT NULL DEFAULT 0,
    version    BIGINT       NOT NULL DEFAULT 0,
    created_at DATETIME     NOT NULL,
    updated_at DATETIME     NOT NULL
);

-- =============================================
-- 사용 (point_grant보다 먼저 생성 - point_grant가 FK 참조)
-- =============================================
CREATE TABLE IF NOT EXISTS point_usage (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    point_key        VARCHAR(100) NOT NULL UNIQUE,
    point_account_id BIGINT       NOT NULL,
    user_id          VARCHAR(100) NOT NULL,
    order_id         VARCHAR(100) NOT NULL,
    used_amount      BIGINT       NOT NULL,
    cancelled_amount BIGINT       NOT NULL DEFAULT 0,
    status           VARCHAR(20)  NOT NULL,
    created_at       DATETIME     NOT NULL,
    updated_at       DATETIME     NOT NULL,
    FOREIGN KEY (point_account_id) REFERENCES point_account(id)
);

CREATE TABLE IF NOT EXISTS point_usage_cancel (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    point_key        VARCHAR(100) NOT NULL UNIQUE,
    point_usage_id   BIGINT       NOT NULL,
    cancel_amount    BIGINT       NOT NULL,
    created_at       DATETIME     NOT NULL,
    updated_at       DATETIME     NOT NULL,
    FOREIGN KEY (point_usage_id) REFERENCES point_usage(id)
);

-- =============================================
-- 적립 (point_usage_cancel을 nullable FK로 참조)
-- =============================================
CREATE TABLE IF NOT EXISTS point_grant (
    id                     BIGINT AUTO_INCREMENT PRIMARY KEY,
    point_key              VARCHAR(100) NOT NULL UNIQUE,
    point_account_id       BIGINT       NOT NULL,
    user_id                VARCHAR(100) NOT NULL,
    original_amount        BIGINT       NOT NULL,
    remaining_amount       BIGINT       NOT NULL,
    grant_type             VARCHAR(20)  NOT NULL,
    status                 VARCHAR(20)  NOT NULL,
    expiry_date            DATE         NOT NULL,
    source_usage_cancel_id BIGINT,
    created_at             DATETIME     NOT NULL,
    updated_at             DATETIME     NOT NULL,
    FOREIGN KEY (point_account_id)       REFERENCES point_account(id),
    FOREIGN KEY (source_usage_cancel_id) REFERENCES point_usage_cancel(id)
);

-- =============================================
-- 사용 상세 (point_usage + point_grant 연결)
-- =============================================
CREATE TABLE IF NOT EXISTS point_usage_detail (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    point_usage_id   BIGINT   NOT NULL,
    point_grant_id   BIGINT   NOT NULL,
    use_sequence     INT      NOT NULL,
    used_amount      BIGINT   NOT NULL,
    created_at       DATETIME NOT NULL,
    updated_at       DATETIME NOT NULL,
    FOREIGN KEY (point_usage_id) REFERENCES point_usage(id),
    FOREIGN KEY (point_grant_id) REFERENCES point_grant(id)
);

-- =============================================
-- 취소 상세 (point_usage_cancel + point_usage_detail 연결)
-- =============================================
CREATE TABLE IF NOT EXISTS point_usage_cancel_detail (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    point_usage_cancel_id   BIGINT      NOT NULL,
    point_usage_detail_id   BIGINT      NOT NULL,
    cancel_amount           BIGINT      NOT NULL,
    restore_type            VARCHAR(30) NOT NULL,
    restored_point_grant_id BIGINT,
    created_at              DATETIME    NOT NULL,
    updated_at              DATETIME    NOT NULL,
    FOREIGN KEY (point_usage_cancel_id)   REFERENCES point_usage_cancel(id),
    FOREIGN KEY (point_usage_detail_id)   REFERENCES point_usage_detail(id),
    FOREIGN KEY (restored_point_grant_id) REFERENCES point_grant(id)
);

-- =============================================
-- 인덱스 (Section 5.5 기준)
-- =============================================
CREATE INDEX IF NOT EXISTS idx_grant_account_status_expiry ON point_grant(point_account_id, status, expiry_date);
CREATE INDEX IF NOT EXISTS idx_usage_detail_usage_id       ON point_usage_detail(point_usage_id);
CREATE INDEX IF NOT EXISTS idx_usage_detail_grant_id       ON point_usage_detail(point_grant_id);
CREATE INDEX IF NOT EXISTS idx_cancel_detail_cancel_id     ON point_usage_cancel_detail(point_usage_cancel_id);
CREATE INDEX IF NOT EXISTS idx_cancel_detail_detail_id     ON point_usage_cancel_detail(point_usage_detail_id);

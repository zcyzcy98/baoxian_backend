-- 修复已存在的 customer_sessions 缺少 user_id
ALTER TABLE customer_sessions ADD COLUMN IF NOT EXISTS user_id BIGINT NOT NULL DEFAULT 1;
CREATE INDEX IF NOT EXISTS idx_customer_sessions_user_id ON customer_sessions(user_id);

-- customer_messages（已存在跳过）
CREATE TABLE IF NOT EXISTS customer_messages (
                                                 id               BIGSERIAL PRIMARY KEY,
                                                 session_id       BIGINT NOT NULL REFERENCES customer_sessions(id) ON DELETE CASCADE,
    customer_info    TEXT,
    question         TEXT NOT NULL,
    channel          VARCHAR(50),
    surface_question TEXT,
    true_intent      TEXT,
    emotion_state    VARCHAR(100),
    anxiety_level    INT,
    response_stable  TEXT,
    response_deep    TEXT,
    response_close   TEXT,
    next_steps       TEXT,
    created_at       TIMESTAMPTZ DEFAULT NOW()
    );
CREATE INDEX IF NOT EXISTS idx_customer_messages_session_id ON customer_messages(session_id);

-- style_sources（已存在跳过）
CREATE TABLE IF NOT EXISTS style_sources (
                                             id           BIGSERIAL PRIMARY KEY,
                                             user_id      BIGINT NOT NULL DEFAULT 1,
                                             title        VARCHAR(200),
    content_type VARCHAR(20) NOT NULL DEFAULT 'text',
    url          VARCHAR(1000),
    raw_text     TEXT,
    word_count   INTEGER DEFAULT 0,
    created_at   TIMESTAMPTZ DEFAULT NOW()
    );

-- style_profiles（已存在跳过）
CREATE TABLE IF NOT EXISTS style_profiles (
                                              id           BIGSERIAL PRIMARY KEY,
                                              user_id      BIGINT NOT NULL DEFAULT 1 UNIQUE,
                                              version      INTEGER NOT NULL DEFAULT 1,
                                              source_count INTEGER DEFAULT 0,
                                              total_words  INTEGER DEFAULT 0,
                                              signature    TEXT,
                                              radar        JSONB,
                                              traits       JSONB,
                                              trained_at   TIMESTAMPTZ DEFAULT NOW()
    );

-- 生成内容历史（新）
CREATE TABLE IF NOT EXISTS generated_contents (
                                                  id              BIGSERIAL PRIMARY KEY,
                                                  user_id         BIGINT NOT NULL DEFAULT 1,
                                                  type            VARCHAR(30) NOT NULL,
    title           TEXT,
    content         TEXT,
    image_urls      TEXT[],
    video_url       TEXT,
    cover_url       TEXT,
    source_url      TEXT,
    source_platform VARCHAR(20),
    model           VARCHAR(50),
    tags            TEXT[],
    created_at      TIMESTAMPTZ DEFAULT NOW()
    );
CREATE INDEX IF NOT EXISTS idx_generated_contents_user_id ON generated_contents(user_id);
CREATE INDEX IF NOT EXISTS idx_generated_contents_type ON generated_contents(type);
CREATE INDEX IF NOT EXISTS idx_generated_contents_created_at ON generated_contents(created_at DESC);

-- 话题收藏（新）
CREATE TABLE IF NOT EXISTS saved_topics (
                                            id         BIGSERIAL PRIMARY KEY,
                                            user_id    BIGINT NOT NULL DEFAULT 1,
                                            title      TEXT NOT NULL,
                                            platform   VARCHAR(20),
    category   VARCHAR(50),
    source     VARCHAR(50),
    score      INTEGER,
    reason     TEXT,
    raw_data   JSONB,
    created_at TIMESTAMPTZ DEFAULT NOW()
    );
CREATE INDEX IF NOT EXISTS idx_saved_topics_user_id ON saved_topics(user_id);

-- Bitable 配置（新，替换 JSON 文件）
CREATE TABLE IF NOT EXISTS bitable_configs (
                                               id          VARCHAR(50) PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    description TEXT,
    app_token   VARCHAR(200),
    table_id    VARCHAR(200),
    kind        VARCHAR(30),
    category    VARCHAR(50),
    field_map   JSONB,
    active      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ DEFAULT NOW(),
    updated_at  TIMESTAMPTZ DEFAULT NOW()
    );

-- 用量统计（新）
CREATE TABLE IF NOT EXISTS usage_records (
                                             id          BIGSERIAL PRIMARY KEY,
                                             user_id     BIGINT NOT NULL DEFAULT 1,
                                             action      VARCHAR(50) NOT NULL,
    tokens_used INTEGER DEFAULT 0,
    model       VARCHAR(50),
    metadata    JSONB,
    created_at  TIMESTAMPTZ DEFAULT NOW()
    );
CREATE INDEX IF NOT EXISTS idx_usage_records_user_id ON usage_records(user_id);
CREATE INDEX IF NOT EXISTS idx_usage_records_created_at ON usage_records(created_at DESC);

CREATE TABLE IF NOT EXISTS user_profile (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT UNIQUE REFERENCES users(id),
    tags       TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 用户认证
CREATE TABLE IF NOT EXISTS users (
    id         BIGSERIAL PRIMARY KEY,
    phone      VARCHAR(20) UNIQUE NOT NULL,
    has_access BOOLEAN NOT NULL DEFAULT FALSE,
    credits    INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS sms_codes (
    phone      VARCHAR(20) PRIMARY KEY,
    code       VARCHAR(10) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS auth_tokens (
    token      VARCHAR(64) PRIMARY KEY,
    user_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    expires_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_auth_tokens_user_id ON auth_tokens(user_id);

-- 积分套餐
CREATE TABLE IF NOT EXISTS credit_packages (
    id          VARCHAR(50) PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    credits     INTEGER NOT NULL,
    price_fen   INTEGER NOT NULL,
    save_fen    INTEGER NOT NULL DEFAULT 0,
    sort_order  INTEGER DEFAULT 0,
    enabled     BOOLEAN DEFAULT TRUE
);

INSERT INTO credit_packages (id, name, credits, price_fen, save_fen, sort_order) VALUES
    ('credits_1000',  '基础包',   1000,  9900,     0,    1),
    ('credits_5000',  '进阶包',   5000,  45900,  3600,  2),
    ('credits_10000', '专业包',  10000,  85900, 13100,  3)
ON CONFLICT (id) DO NOTHING;
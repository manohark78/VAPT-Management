CREATE TABLE user_activation_token (
    id UUID PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_user_activation_token_user FOREIGN KEY (user_id) REFERENCES app_user(id)
);

CREATE INDEX idx_user_activation_token_user ON user_activation_token(user_id);

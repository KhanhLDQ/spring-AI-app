CREATE TABLE usage_log (
    id         BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMPTZ    NOT NULL,
    tokens_in  BIGINT         NOT NULL,
    tokens_out BIGINT         NOT NULL,
    cost_usd   NUMERIC(12, 6) NOT NULL
);

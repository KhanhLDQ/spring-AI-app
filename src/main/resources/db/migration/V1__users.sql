CREATE TABLE users (
    id       BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    role     VARCHAR(20) NOT NULL CHECK (role IN ('USER', 'ADMIN'))
);

INSERT INTO users (username, role) VALUES ('an', 'USER'), ('admin', 'ADMIN');

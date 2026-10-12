CREATE TABLE products (
    id              BIGSERIAL PRIMARY KEY,
    sku             VARCHAR(40)  NOT NULL UNIQUE,
    flavor          VARCHAR(60)  NOT NULL,
    size            VARCHAR(20)  NOT NULL,
    price_vnd       INTEGER      NOT NULL CHECK (price_vnd > 0),
    stock           INTEGER      NOT NULL CHECK (stock >= 0),
    initial_stock   INTEGER      NOT NULL CHECK (initial_stock >= 0),
    shelf_life_days INTEGER      NOT NULL CHECK (shelf_life_days > 0),
    description     VARCHAR(300) NOT NULL,
    image           VARCHAR(120) NOT NULL
);

CREATE SEQUENCE order_id_seq START WITH 1043;

CREATE TABLE orders (
    id           BIGINT PRIMARY KEY DEFAULT nextval('order_id_seq'),
    username     VARCHAR(50) NOT NULL REFERENCES users (username),
    status       VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')),
    created_at   TIMESTAMPTZ NOT NULL,
    total_amount BIGINT      NOT NULL CHECK (total_amount >= 0)
);
CREATE INDEX orders_username_idx ON orders (username);
CREATE INDEX orders_status_idx ON orders (status);

CREATE TABLE order_items (
    id         BIGSERIAL PRIMARY KEY,
    order_id   BIGINT  NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    product_id BIGINT  NOT NULL REFERENCES products (id),
    quantity   INTEGER NOT NULL CHECK (quantity > 0),
    unit_price BIGINT  NOT NULL CHECK (unit_price > 0)
);
CREATE INDEX order_items_order_idx ON order_items (order_id);

-- Prices and shelf life come from mayiukefir.vn; stock is invented because the shop does not publish it.
INSERT INTO products (sku, flavor, size, price_vnd, stock, initial_stock, shelf_life_days, description, image) VALUES
 ('HY-LAP-250',     'Hy Lạp Kefir',     '250g',   90000, 40, 40, 7, 'Thick Greek-style kefir, 7 days in the fridge.',   '/images/huong-vi/hy-lap.webp'),
 ('HY-LAP-330',     'Hy Lạp Kefir',     '330g',  115000, 30, 30, 7, 'Thick Greek-style kefir, 7 days in the fridge.',   '/images/huong-vi/hy-lap.webp'),
 ('HY-LAP-500',     'Hy Lạp Kefir',     '500g',  160000, 25, 25, 7, 'Thick Greek-style kefir, 7 days in the fridge.',   '/images/huong-vi/hy-lap.webp'),
 ('NGUYEN-BAN-330', 'Kefir Nguyên Bản', '330ml',  35000, 80, 80, 7, 'Original drinking kefir, 7 days in the fridge.',   '/images/huong-vi/nguyen-ban.webp'),
 ('NGUYEN-BAN-500', 'Kefir Nguyên Bản', '500ml',  50000, 60, 60, 7, 'Original drinking kefir, 7 days in the fridge.',   '/images/huong-vi/nguyen-ban.webp'),
 ('NGUYEN-BAN-1000','Kefir Nguyên Bản', '1000ml', 90000, 35, 35, 7, 'Original drinking kefir, 7 days in the fridge.',   '/images/huong-vi/nguyen-ban.webp'),
 ('MAT-ONG-330',    'Kefir Mật Ong',    '330ml',  38000, 24, 24, 3, 'Honey kefir, best within 3 days in the fridge.',   '/images/huong-vi/mat-ong.webp'),
 ('MAT-ONG-500',    'Kefir Mật Ong',    '500ml',  55000, 18, 18, 3, 'Honey kefir, best within 3 days in the fridge.',   '/images/huong-vi/mat-ong.webp'),
 ('ME-DEN-330',     'Kefir Mè Đen',     '330ml',  40000, 50, 50, 5, 'Black sesame kefir, 5 days in the fridge.',        '/images/huong-vi/me-den.png'),
 ('ME-DEN-500',     'Kefir Mè Đen',     '500ml',  58000, 40, 40, 5, 'Black sesame kefir, 5 days in the fridge.',        '/images/huong-vi/me-den.png');

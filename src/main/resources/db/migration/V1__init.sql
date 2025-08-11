-- Estensioni
CREATE EXTENSION IF NOT EXISTS pgcrypto; -- per gen_random_uuid()

-- users (se non già creato nello step precedente)
CREATE TABLE IF NOT EXISTS users (
  id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  telegram_user_id TEXT NOT NULL,
  current_step     TEXT,
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uk_users_telegram_user_id UNIQUE (telegram_user_id)
);
CREATE INDEX IF NOT EXISTS idx_users_telegram_user_id ON users(telegram_user_id);

-- products
CREATE TABLE IF NOT EXISTS products (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name        TEXT NOT NULL,
  description TEXT,
  price_eur   NUMERIC(10,2) NOT NULL DEFAULT 0,
  user_id     UUID REFERENCES users(id),
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_products_price   ON products(price_eur);
CREATE INDEX IF NOT EXISTS ix_products_name    ON products(name);
CREATE INDEX IF NOT EXISTS ix_products_user_id ON products(user_id);

-- customers
CREATE TABLE IF NOT EXISTS customers (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name        TEXT NOT NULL,
  phone       TEXT,
  address     TEXT,
  user_id     UUID REFERENCES users(id),
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_customers_name    ON customers(name);
CREATE INDEX IF NOT EXISTS ix_customers_user_id ON customers(user_id);

-- orders
CREATE TABLE IF NOT EXISTS orders (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  customer_id UUID NOT NULL REFERENCES customers(id),
  order_date  TIMESTAMPTZ NOT NULL DEFAULT now(),
  user_id     UUID REFERENCES users(id),
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_orders_order_date  ON orders(order_date);
CREATE INDEX IF NOT EXISTS ix_orders_customer_id ON orders(customer_id);
CREATE INDEX IF NOT EXISTS ix_orders_user_id     ON orders(user_id);

-- relazione molti-a-molti ordine<->prodotti
CREATE TABLE IF NOT EXISTS order_products (
  order_id   UUID NOT NULL REFERENCES orders(id)   ON DELETE CASCADE,
  product_id UUID NOT NULL REFERENCES products(id) ON DELETE RESTRICT,
  PRIMARY KEY (order_id, product_id)
);
-- opzionale: indice per ricerche per prodotto
CREATE INDEX IF NOT EXISTS ix_order_products_product_id ON order_products(product_id);

ALTER TABLE products
    ADD COLUMN IF NOT EXISTS on_offer    BOOLEAN DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS prepared    BOOLEAN DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS quantity_kg NUMERIC(10,3);

CREATE INDEX IF NOT EXISTS ix_products_on_offer  ON products(on_offer);
CREATE INDEX IF NOT EXISTS ix_products_catch_date ON products(catch_date);
CREATE INDEX IF NOT EXISTS ix_products_prepared  ON products(prepared);

-- V3__extend_products_freshness_origin.sql

-- nuovi campi su products
ALTER TABLE products
    ADD COLUMN IF NOT EXISTS freshness      TEXT,
    ADD COLUMN IF NOT EXISTS source         TEXT,
    ADD COLUMN IF NOT EXISTS origin_country VARCHAR(2),
    ADD COLUMN IF NOT EXISTS origin_area    VARCHAR(100),
    ADD COLUMN IF NOT EXISTS fao_area       VARCHAR(10),
    ADD COLUMN IF NOT EXISTS landing_port   VARCHAR(100),
    ADD COLUMN IF NOT EXISTS catch_date     DATE,
    ADD COLUMN IF NOT EXISTS best_before    DATE,
    ADD COLUMN IF NOT EXISTS processing     VARCHAR(50);

-- indici utili per filtrare/ricercare
CREATE INDEX IF NOT EXISTS ix_products_freshness   ON products(freshness);
CREATE INDEX IF NOT EXISTS ix_products_source      ON products(source);
CREATE INDEX IF NOT EXISTS ix_products_fao_area    ON products(fao_area);

-- (facoltativo) vincoli "di coerenza" base:
-- CHECK su enum testuali (evita valori diversi da quelli attesi)
-- NB: se preferisci libertà totale, NON aggiungere queste CHECK
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'ck_products_freshness_values'
    ) THEN
        ALTER TABLE products
            ADD CONSTRAINT ck_products_freshness_values
            CHECK (freshness IN ('FRESH','FROZEN') OR freshness IS NULL);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'ck_products_source_values'
    ) THEN
        ALTER TABLE products
            ADD CONSTRAINT ck_products_source_values
            CHECK (source IN ('WILD_CAUGHT','FARMED') OR source IS NULL);
    END IF;
END$$;

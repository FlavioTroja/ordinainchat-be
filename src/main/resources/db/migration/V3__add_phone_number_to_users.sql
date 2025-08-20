-- Aggiunge la colonna phone_number (nullable)
ALTER TABLE users
ADD COLUMN IF NOT EXISTS phone_number VARCHAR(20);

-- (Opzionale) Indice se prevedi ricerche per numero di telefono
CREATE INDEX IF NOT EXISTS idx_users_phone_number ON users (phone_number);

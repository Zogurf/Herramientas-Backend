-- Aplicar sobre la base existente que contiene package_schema.packages.
-- Puede ejecutarse nuevamente sin reiniciar la secuencia ni eliminar datos.
BEGIN;

ALTER TABLE package_schema.packages
    ADD COLUMN IF NOT EXISTS sender_name VARCHAR(150),
    ADD COLUMN IF NOT EXISTS sender_phone VARCHAR(20),
    ADD COLUMN IF NOT EXISTS description TEXT;

CREATE SEQUENCE IF NOT EXISTS package_schema.tracking_code_seq
    START WITH 100000;

COMMIT;

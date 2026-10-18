-- =============================================================================
-- DATABASE SCHEMA: NEXORA STORE & LOGISTICS PORTAL
-- PostgreSQL Database Script with Auth, RBAC, Account Recovery & Social Logins
-- Supported Social Providers: Google, Facebook, Instagram
-- =============================================================================

-- 1. SCHEMAS & EXTENSIONS
CREATE SCHEMA IF NOT EXISTS auth_schema;
CREATE SCHEMA IF NOT EXISTS store_schema;
CREATE SCHEMA IF NOT EXISTS package_schema;

GRANT USAGE ON SCHEMA auth_schema, store_schema, package_schema TO PUBLIC;

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Function to handle automatic updated_at timestamp updates
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- =============================================================================
-- 2. AUTHENTICATION & CORE USERS (auth_schema)
-- =============================================================================

-- Enum for Social Identity Providers
CREATE TYPE auth_schema.social_provider AS ENUM ('LOCAL', 'GOOGLE', 'FACEBOOK', 'INSTAGRAM', 'OUTLOOK');

-- Core Users Table
CREATE TABLE IF NOT EXISTS auth_schema.users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    full_name VARCHAR(150) NOT NULL,
    email VARCHAR(150) UNIQUE NOT NULL,
    password_hash VARCHAR(255), -- Nullable for pure OAuth users
    phone VARCHAR(20),
    avatar_url TEXT,
    is_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    is_account_non_locked BOOLEAN NOT NULL DEFAULT TRUE,
    is_account_non_expired BOOLEAN NOT NULL DEFAULT TRUE,
    is_credentials_non_expired BOOLEAN NOT NULL DEFAULT TRUE,
    is_email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    mfa_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    mfa_secret VARCHAR(100),
    provider VARCHAR(20) NOT NULL DEFAULT 'LOCAL',
    provider_id VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT users_provider_check CHECK (provider IN ('LOCAL', 'GOOGLE', 'FACEBOOK', 'INSTAGRAM', 'OUTLOOK'))
);

CREATE TRIGGER trg_users_updated_at
BEFORE UPDATE ON auth_schema.users
FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- -----------------------------------------------------------------------------
-- Reconciliación idempotente de auth_schema.users
-- Cubre bases creadas por versiones previas de la entidad (columnas enabled/role,
-- sin provider_id) y las alinea con el modelo actual.
-- -----------------------------------------------------------------------------
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = 'auth_schema' AND table_name = 'users' AND column_name = 'enabled')
       AND NOT EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = 'auth_schema' AND table_name = 'users' AND column_name = 'is_enabled') THEN
        ALTER TABLE auth_schema.users RENAME COLUMN enabled TO is_enabled;
    END IF;
END $$;

ALTER TABLE auth_schema.users
    ADD COLUMN IF NOT EXISTS provider VARCHAR(20) NOT NULL DEFAULT 'LOCAL',
    ADD COLUMN IF NOT EXISTS provider_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS is_enabled BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE auth_schema.users ALTER COLUMN provider SET DEFAULT 'LOCAL';
ALTER TABLE auth_schema.users ALTER COLUMN is_enabled SET DEFAULT TRUE;

-- El rol se resuelve vía RBAC (user_roles/roles): eliminar la columna heredada si existe.
ALTER TABLE auth_schema.users DROP CONSTRAINT IF EXISTS users_role_check;
ALTER TABLE auth_schema.users DROP COLUMN IF EXISTS role;

-- Normalizar el CHECK de provider.
ALTER TABLE auth_schema.users DROP CONSTRAINT IF EXISTS chk_users_provider;
ALTER TABLE auth_schema.users DROP CONSTRAINT IF EXISTS users_provider_check;
ALTER TABLE auth_schema.users ADD CONSTRAINT users_provider_check
    CHECK (provider IN ('LOCAL', 'GOOGLE', 'FACEBOOK', 'INSTAGRAM', 'OUTLOOK'));

CREATE INDEX IF NOT EXISTS idx_users_provider_id ON auth_schema.users(provider_id);

CREATE INDEX IF NOT EXISTS idx_users_email ON auth_schema.users(email);
CREATE INDEX IF NOT EXISTS idx_users_enabled ON auth_schema.users(is_enabled);

-- =============================================================================
-- 3. ROLE-BASED ACCESS CONTROL (RBAC)
-- =============================================================================

CREATE TABLE IF NOT EXISTS auth_schema.roles (
    id SERIAL PRIMARY KEY,
    name VARCHAR(50) UNIQUE NOT NULL, -- e.g. 'ADMIN', 'CLIENTE', 'REPARTIDOR' (sin prefijo ROLE_)
    description VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS auth_schema.permissions (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100) UNIQUE NOT NULL, -- e.g. 'user:read', 'product:write', 'package:update'
    description VARCHAR(255)
);

-- Join table: Users <-> Roles
CREATE TABLE IF NOT EXISTS auth_schema.user_roles (
    user_id UUID NOT NULL REFERENCES auth_schema.users(id) ON DELETE CASCADE,
    role_id INT NOT NULL REFERENCES auth_schema.roles(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

-- Join table: Roles <-> Permissions
CREATE TABLE IF NOT EXISTS auth_schema.role_permissions (
    role_id INT NOT NULL REFERENCES auth_schema.roles(id) ON DELETE CASCADE,
    permission_id INT NOT NULL REFERENCES auth_schema.permissions(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

-- =============================================================================
-- 4. OAUTH & SOCIAL LOGIN SESSIONS (Google, Facebook, Instagram)
-- =============================================================================

-- Table to link user accounts with Social Login Providers
CREATE TABLE IF NOT EXISTS auth_schema.user_social_accounts (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES auth_schema.users(id) ON DELETE CASCADE,
    provider auth_schema.social_provider NOT NULL,
    provider_user_id VARCHAR(255) NOT NULL, -- Unique ID given by Google, Facebook, or Instagram
    provider_email VARCHAR(150),
    access_token TEXT,
    refresh_token TEXT,
    token_expires_at TIMESTAMP WITH TIME ZONE,
    raw_profile_data JSONB, -- Stores raw OAuth profile payload
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_provider_user UNIQUE (provider, provider_user_id)
);

CREATE INDEX idx_social_provider_user ON auth_schema.user_social_accounts(provider, provider_user_id);
CREATE INDEX idx_social_user_id ON auth_schema.user_social_accounts(user_id);

CREATE TRIGGER trg_user_social_updated_at
BEFORE UPDATE ON auth_schema.user_social_accounts
FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Active User Sessions & Refresh Tokens
CREATE TABLE IF NOT EXISTS auth_schema.user_sessions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES auth_schema.users(id) ON DELETE CASCADE,
    refresh_token_hash VARCHAR(255) NOT NULL UNIQUE,
    device_info VARCHAR(255),
    user_agent TEXT,
    ip_address VARCHAR(45),
    is_revoked BOOLEAN NOT NULL DEFAULT FALSE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    last_accessed_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_sessions_user_id ON auth_schema.user_sessions(user_id);
CREATE INDEX idx_sessions_token ON auth_schema.user_sessions(refresh_token_hash);

-- =============================================================================
-- 5. ACCOUNT RECOVERY & VERIFICATION TOKENS
-- =============================================================================

-- Password Reset Tokens
CREATE TABLE IF NOT EXISTS auth_schema.password_reset_tokens (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES auth_schema.users(id) ON DELETE CASCADE,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at TIMESTAMP WITH TIME ZONE,
    ip_address VARCHAR(45),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_pwd_reset_token ON auth_schema.password_reset_tokens(token_hash);
CREATE INDEX idx_pwd_reset_user ON auth_schema.password_reset_tokens(user_id);

-- Email Verification Tokens
CREATE TABLE IF NOT EXISTS auth_schema.email_verification_tokens (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES auth_schema.users(id) ON DELETE CASCADE,
    token VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    verified_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_email_verify_token ON auth_schema.email_verification_tokens(token);

-- =============================================================================
-- 6. E-COMMERCE CATALOG (store_schema)
-- =============================================================================

CREATE TABLE IF NOT EXISTS store_schema.categories (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    slug VARCHAR(120) NOT NULL UNIQUE,
    description TEXT,
    parent_id INT REFERENCES store_schema.categories(id) ON DELETE SET NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS store_schema.products (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    sku VARCHAR(50) UNIQUE NOT NULL,
    name VARCHAR(200) NOT NULL,
    slug VARCHAR(220) UNIQUE NOT NULL,
    description TEXT,
    price NUMERIC(10, 2) NOT NULL,
    stock INT NOT NULL DEFAULT 0,
    category_id INT REFERENCES store_schema.categories(id) ON DELETE SET NULL,
    image_url TEXT NOT NULL,
    image_hover_url TEXT,
    rating NUMERIC(3, 2) DEFAULT 5.0,
    reviews_count INT DEFAULT 0,
    is_new BOOLEAN DEFAULT TRUE,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_products_sku ON store_schema.products(sku);
CREATE INDEX idx_products_category ON store_schema.products(category_id);

-- -----------------------------------------------------------------------------
-- Reconciliación idempotente del catálogo (store_schema)
-- Alinea bases creadas por Hibernate con el DDL de arriba.
-- -----------------------------------------------------------------------------
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'store_schema' AND table_name = 'categories'
          AND column_name = 'id' AND is_identity = 'NO' AND column_default IS NULL
    ) THEN
        ALTER TABLE store_schema.categories ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY;
    END IF;
END $$;

ALTER TABLE store_schema.categories ALTER COLUMN is_active SET DEFAULT TRUE;
ALTER TABLE store_schema.categories ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE store_schema.products ALTER COLUMN id SET DEFAULT uuid_generate_v4();
ALTER TABLE store_schema.products ALTER COLUMN stock SET DEFAULT 0;
ALTER TABLE store_schema.products ALTER COLUMN is_active SET DEFAULT TRUE;
ALTER TABLE store_schema.products ALTER COLUMN is_new SET DEFAULT TRUE;
ALTER TABLE store_schema.products ALTER COLUMN rating SET DEFAULT 5.0;
ALTER TABLE store_schema.products ALTER COLUMN reviews_count SET DEFAULT 0;
ALTER TABLE store_schema.products ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE store_schema.products ALTER COLUMN updated_at SET DEFAULT CURRENT_TIMESTAMP;

CREATE TRIGGER trg_products_updated_at
BEFORE UPDATE ON store_schema.products
FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- =============================================================================
-- 7. LOGISTICS & PARCEL TRACKING (package_schema)
-- =============================================================================

CREATE TYPE package_schema.package_status AS ENUM (
    'REGISTRADO',
    'EN_ALMACEN',
    'EN_TRANSITO',
    'EN_RUTA',
    'ENTREGADO',
    'INCIDENCIA'
);

CREATE TABLE IF NOT EXISTS package_schema.packages (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tracking_code VARCHAR(30) UNIQUE NOT NULL,
    sender_id UUID REFERENCES auth_schema.users(id) ON DELETE SET NULL,
    recipient_name VARCHAR(150) NOT NULL,
    recipient_phone VARCHAR(20) NOT NULL,
    destination_address TEXT NOT NULL,
    origin_city VARCHAR(100) NOT NULL,
    destination_city VARCHAR(100) NOT NULL,
    weight_kg NUMERIC(6,2) NOT NULL,
    current_status package_schema.package_status NOT NULL DEFAULT 'REGISTRADO',
    courier_id UUID REFERENCES auth_schema.users(id) ON DELETE SET NULL,
    estimated_delivery DATE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_packages_tracking ON package_schema.packages(tracking_code);
CREATE INDEX idx_packages_sender ON package_schema.packages(sender_id);
CREATE INDEX idx_packages_courier ON package_schema.packages(courier_id);

CREATE TRIGGER trg_packages_updated_at
BEFORE UPDATE ON package_schema.packages
FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TABLE IF NOT EXISTS package_schema.tracking_events (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    package_id UUID NOT NULL REFERENCES package_schema.packages(id) ON DELETE CASCADE,
    status package_schema.package_status NOT NULL,
    location VARCHAR(150) NOT NULL,
    description TEXT,
    registered_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_tracking_events_package ON package_schema.tracking_events(package_id);

-- =============================================================================
-- 8. INITIAL SEED DATA (ROLES, PERMISSIONS & DEFAULT ADMIN)
-- =============================================================================

-- Seed Roles
INSERT INTO auth_schema.roles (id, name, description) VALUES
(1, 'ADMIN', 'Administrador con acceso total al sistema y módulos'),
(2, 'CLIENTE', 'Cliente comprador y usuario de rastreo de paquetería'),
(3, 'REPARTIDOR', 'Repartidor / Courier operativo para actualización de guías')
ON CONFLICT DO NOTHING;

-- Seed Permissions
INSERT INTO auth_schema.permissions (id, name, description) VALUES
(1, 'user:read', 'Leer información de usuarios'),
(2, 'user:write', 'Crear y actualizar usuarios'),
(3, 'product:read', 'Ver productos en catálogo'),
(4, 'product:write', 'Gestionar inventario y productos'),
(5, 'package:read', 'Consultar guías de envío'),
(6, 'package:write', 'Crear y actualizar estados de guías de envío')
ON CONFLICT (name) DO NOTHING;

-- Seed Role-Permissions
INSERT INTO auth_schema.role_permissions (role_id, permission_id) VALUES
(1, 1), (1, 2), (1, 3), (1, 4), (1, 5), (1, 6), -- ADMIN has all permissions
(2, 3), (2, 5),                                 -- CLIENTE can read products and packages
(3, 5), (3, 6)                                  -- REPARTIDOR can read and update package status
ON CONFLICT DO NOTHING;

-- Seed Default Administrator User (Password BCrypt hash for 'admin123')
INSERT INTO auth_schema.users (id, full_name, email, password_hash, is_enabled, is_email_verified)
VALUES (
    'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
    'Administrador General',
    'admin@expressdelivery.com',
    '$2a$10$e8R6.V/W/wWnQ/Gk9O8x/uO9241wFf/34G6q8Y0d.z41gJ28zK622', -- 'admin123'
    TRUE,
    TRUE
) ON CONFLICT (email) DO NOTHING;

-- Assign ROLE_ADMIN to default administrator
INSERT INTO auth_schema.user_roles (user_id, role_id)
VALUES ('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 1)
ON CONFLICT DO NOTHING;

-- -----------------------------------------------------------------------------
-- Reconciliación idempotente del RBAC
-- 1) Los nombres de rol se almacenan SIN el prefijo 'ROLE_'; ese prefijo es una
--    convención de authorities de Spring y ya lo aplica User#getAuthorities().
-- 2) Se eliminan asignaciones huérfanas y se restaura la FK de user_id.
-- 3) Backfill: todo usuario sin rol recibe CLIENTE, coherente con el alta de
--    cuentas (que persiste el rol explícitamente).
-- -----------------------------------------------------------------------------
UPDATE auth_schema.roles AS rol
   SET name = substring(rol.name from 6)
 WHERE substring(rol.name from 1 for 5) = 'ROLE_'
   AND NOT EXISTS (
        SELECT 1 FROM auth_schema.roles r2
         WHERE r2.id <> rol.id
           AND r2.name = substring(rol.name from 6));

DELETE FROM auth_schema.user_roles ur
 WHERE NOT EXISTS (SELECT 1 FROM auth_schema.users u WHERE u.id = ur.user_id);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint
                    WHERE conrelid = 'auth_schema.user_roles'::regclass
                      AND conname = 'user_roles_user_id_fkey') THEN
        ALTER TABLE auth_schema.user_roles
            ADD CONSTRAINT user_roles_user_id_fkey
            FOREIGN KEY (user_id) REFERENCES auth_schema.users(id) ON DELETE CASCADE;
    END IF;
END $$;

INSERT INTO auth_schema.user_roles (user_id, role_id)
SELECT u.id, r.id
  FROM auth_schema.users u
  JOIN auth_schema.roles r ON r.name IN ('CLIENTE', 'ROLE_CLIENTE')
 WHERE NOT EXISTS (SELECT 1 FROM auth_schema.user_roles ur WHERE ur.user_id = u.id)
ON CONFLICT DO NOTHING;

-- Seed Default Store Categories
INSERT INTO store_schema.categories (id, name, slug, description) VALUES
(1, 'Teclados Gamer', 'teclados-gamer', 'Teclados mecánicos y magnéticos de alto rendimiento'),
(2, 'Mouse Gamer', 'mouse-gamer', 'MICE inalámbricos y ultraligeros con sensor óptico'),
(3, 'Audífonos & Audio', 'audifonos-audio', 'Headsets con sonido envolvente y micrófonos de estudio'),
(4, 'Monitores', 'monitores', 'Pantallas gaming con alta tasa de refresco y panel IPS/OLED')
ON CONFLICT (slug) DO NOTHING;

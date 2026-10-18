-- =============================================================================
-- DATABASE SCHEMA: NEXORA STORE & LOGISTICS PORTAL
-- PostgreSQL Database Script with Auth, RBAC, Account Recovery & Social Logins
-- Supported Social Providers: Google, Facebook, Instagram
-- =============================================================================

-- 1. SCHEMAS & EXTENSIONS
CREATE SCHEMA IF NOT EXISTS auth_schema;
CREATE SCHEMA IF NOT EXISTS store_schema;
CREATE SCHEMA IF NOT EXISTS package_schema;

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

-- Enum de proveedores sociales (idempotente: schema.sql debe poder re-ejecutarse).
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type
                    WHERE typname = 'social_provider'
                      AND typnamespace = 'auth_schema'::regnamespace) THEN
        CREATE TYPE auth_schema.social_provider AS ENUM ('LOCAL', 'GOOGLE', 'FACEBOOK', 'INSTAGRAM', 'OUTLOOK');
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS auth_schema.users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    full_name VARCHAR(150) NOT NULL,
    email VARCHAR(150) UNIQUE NOT NULL,
    password_hash VARCHAR(255),
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
    name VARCHAR(50) UNIQUE NOT NULL,
    description VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS auth_schema.permissions (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100) UNIQUE NOT NULL,
    description VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS auth_schema.user_roles (
    user_id UUID NOT NULL REFERENCES auth_schema.users(id) ON DELETE CASCADE,
    role_id INT NOT NULL REFERENCES auth_schema.roles(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE IF NOT EXISTS auth_schema.role_permissions (
    role_id INT NOT NULL REFERENCES auth_schema.roles(id) ON DELETE CASCADE,
    permission_id INT NOT NULL REFERENCES auth_schema.permissions(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

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

-- =============================================================================
-- 4. OAUTH & SOCIAL LOGIN SESSIONS (Google, Facebook, Instagram)
-- =============================================================================

CREATE TABLE IF NOT EXISTS auth_schema.user_social_accounts (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES auth_schema.users(id) ON DELETE CASCADE,
    provider auth_schema.social_provider NOT NULL,
    provider_user_id VARCHAR(255) NOT NULL,
    provider_email VARCHAR(150),
    access_token TEXT,
    refresh_token TEXT,
    token_expires_at TIMESTAMP WITH TIME ZONE,
    raw_profile_data JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_provider_user UNIQUE (provider, provider_user_id)
);

CREATE INDEX IF NOT EXISTS idx_social_provider_user ON auth_schema.user_social_accounts(provider, provider_user_id);
CREATE INDEX IF NOT EXISTS idx_social_user_id ON auth_schema.user_social_accounts(user_id);

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

CREATE INDEX IF NOT EXISTS idx_sessions_user_id ON auth_schema.user_sessions(user_id);
CREATE INDEX IF NOT EXISTS idx_sessions_token ON auth_schema.user_sessions(refresh_token_hash);

-- =============================================================================
-- 5. ACCOUNT RECOVERY & VERIFICATION TOKENS
-- =============================================================================

CREATE TABLE IF NOT EXISTS auth_schema.password_reset_tokens (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES auth_schema.users(id) ON DELETE CASCADE,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at TIMESTAMP WITH TIME ZONE,
    ip_address VARCHAR(45),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_pwd_reset_token ON auth_schema.password_reset_tokens(token_hash);
CREATE INDEX IF NOT EXISTS idx_pwd_reset_user ON auth_schema.password_reset_tokens(user_id);

CREATE TABLE IF NOT EXISTS auth_schema.email_verification_tokens (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES auth_schema.users(id) ON DELETE CASCADE,
    token VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    verified_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_email_verify_token ON auth_schema.email_verification_tokens(token);

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

CREATE INDEX IF NOT EXISTS idx_products_sku ON store_schema.products(sku);
CREATE INDEX IF NOT EXISTS idx_products_category ON store_schema.products(category_id);

-- -----------------------------------------------------------------------------
-- Reconciliación idempotente del catálogo (store_schema)
-- Cubre bases creadas por Hibernate, que no definían identidad en categories.id
-- ni los DEFAULT declarados aquí; las alinea con schema.sql (fuente de verdad).
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

-- =============================================================================
-- 7. LOGISTICS & PARCEL TRACKING (package_schema)
-- =============================================================================

-- Enum de estados de paquete (idempotente).
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type
                    WHERE typname = 'package_status'
                      AND typnamespace = 'package_schema'::regnamespace) THEN
        CREATE TYPE package_schema.package_status AS ENUM (
            'REGISTRADO',
            'EN_ALMACEN',
            'EN_TRANSITO',
            'EN_RUTA',
            'ENTREGADO',
            'INCIDENCIA'
        );
    END IF;
END $$;

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

CREATE INDEX IF NOT EXISTS idx_packages_tracking ON package_schema.packages(tracking_code);

CREATE TABLE IF NOT EXISTS package_schema.tracking_events (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    package_id UUID NOT NULL REFERENCES package_schema.packages(id) ON DELETE CASCADE,
    status package_schema.package_status NOT NULL,
    location VARCHAR(150) NOT NULL,
    description TEXT,
    registered_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_tracking_events_package ON package_schema.tracking_events(package_id);

-- Order tracking (store_schema). Independent from parcel tracking in package_schema.
-- Idempotent migration: also safe to apply to an existing database.
CREATE TABLE IF NOT EXISTS store_schema.orders (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS store_schema.tracking_events (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    order_id UUID NOT NULL REFERENCES store_schema.orders(id),
    status VARCHAR(50) NOT NULL,
    location VARCHAR(200),
    description TEXT,
    event_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_order_tracking_timeline
    ON store_schema.tracking_events(order_id, event_date DESC);
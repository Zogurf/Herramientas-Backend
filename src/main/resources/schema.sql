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

CREATE TYPE auth_schema.social_provider AS ENUM ('LOCAL', 'GOOGLE', 'FACEBOOK', 'INSTAGRAM', 'OUTLOOK');

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
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

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

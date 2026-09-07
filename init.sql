-- Crear esquemas separados para modularidad de microservicios
CREATE SCHEMA IF NOT EXISTS auth_schema;
CREATE SCHEMA IF NOT EXISTS package_schema;

-- Extensiones útiles para UUIDs
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- =========================================================
-- 1. TABLAS DEL MICROSERVICIO DE AUTENTICACIÓN (HU5 / HU2)
-- =========================================================

CREATE TYPE auth_schema.user_role AS ENUM ('CLIENTE', 'REPARTIDOR', 'ADMIN');
CREATE TYPE auth_schema.auth_provider AS ENUM ('LOCAL', 'GOOGLE', 'OUTLOOK');

CREATE TABLE auth_schema.users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    full_name VARCHAR(150) NOT NULL,
    email VARCHAR(150) UNIQUE NOT NULL,
    password_hash VARCHAR(255),
    phone VARCHAR(20),
    avatar_url TEXT,
    role auth_schema.user_role NOT NULL DEFAULT 'CLIENTE',
    provider auth_schema.auth_provider NOT NULL DEFAULT 'LOCAL',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Index para búsquedas rápidas por correo en login
CREATE INDEX idx_users_email ON auth_schema.users(email);

-- =========================================================
-- 2. TABLAS DEL MICROSERVICIO DE PAQUETES Y RASTREO (HU3 / HU4 / HU6 / HU7)
-- =========================================================

CREATE TYPE package_schema.package_status AS ENUM (
    'REGISTRADO',
    'EN_ALMACEN',
    'EN_TRANSITO',
    'EN_RUTA',
    'ENTREGADO',
    'INCIDENCIA'
);

CREATE TABLE package_schema.packages (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tracking_code VARCHAR(30) UNIQUE NOT NULL,
    sender_id UUID REFERENCES auth_schema.users(id),
    recipient_name VARCHAR(150) NOT NULL,
    recipient_phone VARCHAR(20) NOT NULL,
    destination_address TEXT NOT NULL,
    origin_city VARCHAR(100) NOT NULL,
    destination_city VARCHAR(100) NOT NULL,
    weight_kg NUMERIC(6,2) NOT NULL,
    current_status package_schema.package_status NOT NULL DEFAULT 'REGISTRADO',
    courier_id UUID REFERENCES auth_schema.users(id),
    estimated_delivery DATE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_packages_tracking ON package_schema.packages(tracking_code);

CREATE TABLE package_schema.tracking_events (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    package_id UUID NOT NULL REFERENCES package_schema.packages(id) ON DELETE CASCADE,
    status package_schema.package_status NOT NULL,
    location VARCHAR(150) NOT NULL,
    description TEXT,
    registered_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_tracking_events_package ON package_schema.tracking_events(package_id);

-- =========================================================
-- 3. DATOS DE PRUEBA (MOCK DATA)
-- =========================================================

-- Usuario Administrador por defecto (Contraseña hash simulada)
INSERT INTO auth_schema.users (id, full_name, email, password_hash, role, provider)
VALUES (
    'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
    'Admin General',
    'admin@expressdelivery.com',
    '$2a$10$e8R6.V/W/wWnQ/Gk9O8x/uO9241wFf/34G6q8Y0d.z41gJ28zK622', -- hash BCrypt de 'admin123'
    'ADMIN',
    'LOCAL'
);

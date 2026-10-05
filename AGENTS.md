# Structural Architecture & Context Guide — Backend (Herramientas-Backend)

Welcome AI Agent. This document provides the authoritative structural context, package topology, graph analysis, and architectural guidelines for **Herramientas-Backend** (Spring Boot 3 / Java 21 REST API).

---

## 1. System Overview & Technology Stack

**Herramientas-Backend** provides the backend API services for **NEXORA STORE & Logistics**, including authentication, user management, e-commerce product catalog persistence, and media storage integration.

- **Language & Runtime:** Java 21
- **Framework:** Spring Boot (Spring Web MVC, Spring Data JPA, Spring Security)
- **Authentication & Tokens:** JWT (`io.jsonwebtoken:jjwt:0.12.5`), BCrypt Password Encoding
- **Database:** Insforge Cloud PostgreSQL (`jdbc:postgresql://db.insforge.cloud:5432/delivery_db`)
- **Media Storage:** Cloudinary Java SDK (`com.cloudinary:cloudinary-http44:1.38.0`)
- **Build System:** Apache Maven (`pom.xml`)
- **Root Package:** `com.logistics.proyect.group5`

---

## 2. Directory & Package Structure

```
Herramientas-Backend/
├── pom.xml                                   # Maven Dependencies & Compiler Specs
├── src/main/resources/
│   ├── application.properties               # Insforge DB, Cloudinary & Multipart Properties
│   └── application.yml                      # YML Profile Configuration
└── src/main/java/com/logistics/proyect/group5/
    ├── Application.java                      # Spring Boot Main Entry Point
    ├── config/                               # Infrastructure & Security Beans
    │   ├── CloudinaryConfig.java             # Cloudinary SDK Bean Initialization
    │   ├── CorsConfig.java                   # Global CORS Policy (http://localhost:3000)
    │   ├── CustomUserDetailsService.java     # Spring Security UserDetails Adapter
    │   ├── JwtAuthenticationFilter.java      # Request Token Interceptor Filter
    │   ├── JwtTokenProvider.java             # JWT Generation & Validation Utility
    │   ├── PasswordEncoderConfig.java        # BCryptPasswordEncoder Bean
    │   └── SecurityConfig.java               # Spring Security FilterChain Rules
    ├── controller/                           # REST API Endpoints
    │   ├── AuthController.java               # Login, Registration & User Session Endpoints
    │   └── ProductController.java            # Product Catalog & Multipart Upload Endpoints
    ├── dto/                                  # Data Transfer Objects
    │   ├── AuthResponse.java
    │   ├── LoginRequest.java
    │   ├── RegisterRequest.java
    │   └── UserSummaryDto.java
    ├── exception/                            # Cross-cutting Error Handling
    │   └── GlobalExceptionHandler.java       # Centralized @ControllerAdvice Error Responses
    ├── model/                                # JPA Entity Models
    │   ├── AuthProvider.java                 # Auth Provider Enum (LOCAL, GOOGLE, etc.)
    │   ├── Product.java                      # Product Entity (id, name, price, imageUrl)
    │   ├── User.java                         # User Entity (implements UserDetails)
    │   └── UserRole.java                     # User Roles Enum (ADMIN, CLIENTE, REPARTIDOR)
    ├── repository/                           # Spring Data JPA Repositories
    │   ├── ProductRepository.java            # JpaRepository<Product, Long>
    │   └── UserRepository.java               # JpaRepository<User, Long>
    └── service/                              # Business Logic Services
        ├── AuthService.java                  # User Registration & Token Issue Logic
        └── CloudinaryService.java            # Multipart Image Upload to Cloudinary
```

---

## 3. Structural Graph Topology (Graphify Summary)

Based on topological graph analysis (`graphify-out/GRAPH_REPORT.md`):

### Core God Nodes & Bridges
- **`User` (`model/User.java`):** Central domain entity (10 connected edges), implementing Spring Security's `UserDetails`.
- **`JwtTokenProvider` & `JwtAuthenticationFilter` (`config/`):** Security bridges intercepting incoming HTTP requests.
- **`AuthController` & `ProductController` (`controller/`):** Primary REST API controllers handling HTTP payloads.
- **`GlobalExceptionHandler` (`exception/`):** Cross-community bridge intercepting exceptions across all controllers.

### Key Functional Communities
1. **Product & Media Upload Community (`Community 0`):** `Product`, `ProductRepository`, `ProductController`, `CloudinaryService`, `CloudinaryConfig`.
2. **Security & Authentication Community (`Community 1 & 4`):** `User`, `UserRepository`, `AuthService`, `JwtTokenProvider`, `JwtAuthenticationFilter`, `SecurityConfig`.
3. **CORS & Global Configuration (`Community 12`):** `CorsConfig`, `WebMvcConfigurer`.

---

## 4. API & Environment Contracts

### Ports & Endpoints
- **Server Port:** `8080`
- **Context Path:** `/api`
- **Base API URL:** `http://localhost:8080/api`

### Core API Endpoints
- `POST /api/products/upload`: Accepts `multipart/form-data` (`file`, `name`, `price`), uploads image to Cloudinary, and saves `Product` entity in Insforge PostgreSQL.
- `GET /api/products`: Returns list of all persisted products.
- `POST /api/auth/register`: Creates new user account.
- `POST /api/auth/login`: Authenticates user and returns JWT token.

---

## 5. Developer & AI Agent Guidelines

1. **Lombok Annotations:** Use Lombok `@Getter`, `@Setter`, `@NoArgsConstructor`, `@AllArgsConstructor`, `@Builder` to maintain clean POJOs.
2. **Security Scoping:** Ensure public endpoints (`/products/**`, `/auth/**`) are correctly permitted in `SecurityConfig.java`.
3. **Response Entity Standard:** Always wrap REST responses in `ResponseEntity<T>` with appropriate HTTP status codes (`200 OK`, `201 CREATED`, `400 BAD REQUEST`).
4. **Environment Property Injection:** Use `@Value("${property.name}")` for dynamic property injection from `application.properties`.

<!-- INSFORGE:START -->
## InsForge backend

This project uses [InsForge](https://insforge.dev): an all-in-one, open-source Postgres-based backend (BaaS) that gives this app a database, authentication, file storage, edge functions, realtime, an AI model gateway, and payments through one platform.

- **Project:** **app_nexostorage** (API base `https://26kgknrj.us-east.insforge.app`)
- **Skills:** these InsForge skills are installed for supported coding agents. Reach for them before implementing any InsForge feature instead of guessing the API:
  - `insforge`: app code with the `@insforge/sdk` client (database CRUD, auth, storage, edge functions, realtime, AI, email, and Stripe payments).
  - `insforge-cli`: backend and infrastructure via the `insforge` CLI (projects, SQL, migrations, RLS policies, storage buckets, functions, secrets, payment setup, schedules, deploys).
  - `insforge-debug`: diagnosing failures (SDK/HTTP errors, RLS denials, auth and OAuth issues) and running security or performance audits.
  - `insforge-integrations`: wiring external auth providers (Clerk, Auth0, WorkOS, Better Auth, etc.) for JWT-based RLS, or the OKX x402 payment facilitator.
  - `find-skills`: discovering additional skills on demand.
- **Credentials:** app code reads keys from `.env.local`; the CLI reads `.insforge/project.json`. Never hardcode or commit keys.

Key patterns:

- Database inserts take an array: `insert([{ ... }])`.
- Reference users with `auth.users(id)`; use `auth.uid()` in RLS policies.
- For storage uploads, persist both the returned `url` and `key`.
<!-- INSFORGE:END -->

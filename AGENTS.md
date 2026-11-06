# Guía de arquitectura y contexto — Herramientas-Backend

Este documento describe la estructura vigente del backend para NEXORA STORE & Logistics. La referencia interactiva de Graphify está en `graphify-out/graph.html`; el análisis fechado más reciente está en `graphify-out/2026-10-17/`.

## 1. Resumen del sistema

API REST construida con Java 21, Spring Boot 4.1.1 y Maven. Incluye autenticación JWT, inicio de sesión con Google y con Facebook, gestión de usuarios y perfiles, catálogo de productos, subida de imágenes a Cloudinary, recuperación de contraseñas por correo y persistencia PostgreSQL. La configuración permite conectar PostgreSQL administrado por InsForge mediante JDBC; el código backend no usa el SDK `@insforge/sdk`.

- **Persistencia:** PostgreSQL con Spring Data JPA; entidades de autenticación en `auth_schema`.
- **Autenticación:** Spring Security, JWT (`jjwt` 0.12.5) y BCrypt; login con Google verificando el `id_token` contra `google.client-id` (`AuthService.loginWithGoogle`) y login con Facebook verificando el access token contra la Graph API con `debug_token` y las credenciales `facebook.client-id` / `facebook.client-secret` (`AuthService.loginWithFacebook`).
- **Archivos:** Cloudinary.
- **Correo:** Spring Mail / SMTP para recuperación de contraseña.
- **Despliegue local/contenedor:** Dockerfile multi-stage y Docker Compose, que incluye PostgreSQL local opcional y pgAdmin. El despliegue en la nube (Render) queda descrito por `render.yaml` (Blueprint del servicio Docker y de sus variables) y `DEPLOY-RENDER.md` (checklist de puesta en marcha).

## 2. Estructura del código

```text
Herramientas-Backend/
├── pom.xml
├── Dockerfile
├── docker-compose.yml
├── render.yaml                       # Blueprint de Render: servicio Docker y variables de entorno
├── DEPLOY-RENDER.md                  # Checklist de despliegue en Render
├── .env.example                      # Plantilla de variables de entorno (solo placeholders)
├── init.sql                          # Inicialización PostgreSQL local y datos iniciales
├── src/main/resources/
│   ├── application.properties        # Puerto, contexto /api, única fuente de ddl-auto y variables de entorno
│   ├── application.yml               # Nombre de app, datasource JPA, default_schema y JWT (sin ddl-auto ni contexto)
│   ├── application-local.properties  # Perfil `local` (ignorado por git); no activarlo contra la base administrada
│   └── schema.sql                    # Esquemas de autenticación, catálogo y paquetería (+ migración idempotente)
└── src/main/java/com/logistics/proyect/group5/
    ├── Application.java
    ├── config/                       # JWT, Spring Security, CORS, Cloudinary y BCrypt
    ├── controller/
    │   ├── AuthController.java       # Sesión, cuenta, perfil, recuperación de contraseña y login con Google/Facebook
    │   ├── ProductController.java    # CRUD del catálogo (lectura pública; escritura solo ADMIN)
    │   └── CategoryController.java   # CRUD de categorías (lectura pública; escritura solo ADMIN)
    ├── dto/                          # Peticiones y respuestas de auth, perfil, recuperación, Google, Facebook y catálogo
    ├── exception/                    # Respuestas globales de errores, validación y ResourceNotFoundException
    ├── model/                        # User, Product (store_schema), Category, PasswordResetToken y enums
    ├── repository/                   # Repositorios JPA de usuarios, productos, categorías y tokens
    └── service/                      # AuthService, ProductService, CategoryService, CloudinaryService, EmailService, SlugUtils
```

## 3. API HTTP

`application.properties` establece `server.servlet.context-path=/api` y el puerto predeterminado `8080` (configurable mediante `PORT`). Por tanto, las rutas externas incluyen el prefijo `/api`.

### Autenticación y cuenta

Controlador con base `/v1/auth`, por lo que las rutas externas son:

| Método | Ruta | Función |
|---|---|---|
| `POST` | `/api/v1/auth/login` | Autenticar y emitir JWT |
| `POST` | `/api/v1/auth/register` | Crear cuenta y emitir JWT |
| `POST` | `/api/v1/auth/google` | Iniciar sesión con Google (`credential` = ID token de Google) |
| `POST` | `/api/v1/auth/facebook` | Iniciar sesión con Facebook (`accessToken` emitido por el SDK de Meta) |
| `POST` | `/api/v1/auth/logout` | Cierre de sesión sin estado en servidor |
| `GET` | `/api/v1/auth/me` | Consultar el usuario autenticado |
| `POST` | `/api/v1/auth/forgot-password` | Solicitar enlace de recuperación |
| `POST` | `/api/v1/auth/reset-password` | Restablecer contraseña con token |
| `PUT` | `/api/v1/auth/profile` | Actualizar datos del perfil |
| `POST` | `/api/v1/auth/profile/avatar` | Subir avatar como `multipart/form-data` (campo `file`) |

### Catálogo (productos y categorías)

Las entidades `Product` y `Category` mapean **`store_schema`** (`ddl-auto=none`; el esquema manda). La lectura es pública; toda escritura exige rol `ADMIN` mediante `@PreAuthorize("hasRole('ADMIN')")`.

| Método | Ruta | Acceso | Función |
|---|---|---|---|
| `GET` | `/api/products` | público | Listar productos activos |
| `GET` | `/api/products/manage` | ADMIN | Listar todos (incluye inactivos) |
| `GET` | `/api/products/{id}` | público | Consultar un producto (UUID) |
| `POST` | `/api/products` | ADMIN | Crear producto (`multipart/form-data`) |
| `POST` | `/api/products/upload` | ADMIN | Alias histórico de `POST /api/products` |
| `PUT` | `/api/products/{id}` | ADMIN | Actualizar producto (`multipart/form-data`) |
| `DELETE` | `/api/products/{id}` | ADMIN | Eliminar producto (`204`) |
| `GET` | `/api/categories` | público | Listar categorías |
| `GET` | `/api/categories/{id}` | público | Consultar una categoría |
| `POST` | `/api/categories` | ADMIN | Crear categoría (JSON) |
| `PUT` | `/api/categories/{id}` | ADMIN | Actualizar categoría (JSON) |
| `DELETE` | `/api/categories/{id}` | ADMIN | Eliminar categoría (`400` si hay productos que la usan) |

Campos aceptados al crear/actualizar un producto: `name` (obligatorio), `price` (obligatorio, ≥ 0), `stock`, `categoryId`, `sku` y `slug` (se autogeneran si se omiten y se garantiza su unicidad), `description`, `imageUrl` e `imageHoverUrl`. Si se envía el archivo `file` en la petición, se sube a Cloudinary y tiene prioridad sobre `imageUrl`; si no hay archivo ni `imageUrl`, la creación responde `400` porque la columna es `NOT NULL`.

Los controladores devuelven `ResponseEntity`. El registro responde `201 CREATED`; las consultas y acciones satisfactorias responden `200 OK`. Los errores usan `400` (validación o regla de negocio), `403` (sin permisos o sin token), `404` (recurso inexistente) y `401` (credenciales inválidas).

## 4. Dominio y flujo de autenticación

- `User` implementa `UserDetails`, tiene identificador UUID y se almacena en `auth_schema.users`. El rol se resuelve vía RBAC (`auth_schema.user_roles` → `auth_schema.roles`): los nombres de rol se almacenan **sin** el prefijo `ROLE_` (ese prefijo es una convención de authorities de Spring y lo añade `User#getAuthorities()`), y toda cuenta nueva **persiste explícitamente su fila `CLIENTE`** al crearse (alta local y primer login social) para que `user_roles` sea la única fuente de verdad; el auto-registro no acepta rol. Si un usuario no tuviera ninguna fila de rol, `CLIENTE` se usa como red de seguridad. Las columnas `provider` (`LOCAL`/`GOOGLE`/`FACEBOOK`) y `provider_id` registran la identidad social usada. El rol se lee con una `@Formula` que, si el usuario tiene varias filas, resuelve de forma **determinista** al de mayor privilegio (`ADMIN` > `REPARTIDOR` > resto); como `JwtAuthenticationFilter` vuelve a cargar el usuario desde la base en cada petición, un cambio de rol surte efecto sin volver a iniciar sesión.
- `PasswordResetToken` se almacena en `auth_schema.password_reset_tokens`, asociado a un usuario, con vencimiento, marca de uso y dirección IP.
- `AuthService` gestiona inicio/registro, JWT, actualización del perfil, carga del avatar, recuperación de contraseña y los logins sociales con Google (`loginWithGoogle`, verifica el `id_token`) y con Facebook (`loginWithFacebook`, verifica el access token con `debug_token`). Ambos comparten `loginWithSocialUser`, que crea o vincula la cuenta reutilizando el correo existente. `EmailService` construye el enlace usando `app.frontend.url` y envía el correo por SMTP cuando está configurado.
- `Product` mapea `store_schema.products` (clave UUID) y `Category` mapea `store_schema.categories`; ambos forman el catálogo real que consume la tienda. `ProductService` sube la imagen a Cloudinary, autogenera `sku`/`slug` si faltan y resuelve el nombre de la categoría para la respuesta. `CategoryService` impide borrar una categoría con productos asociados.
- `SecurityConfig` permite sin token `POST` de auth y los `GET` del catálogo; el resto exige autenticación. La autorización fina de escrituras se hace con `@PreAuthorize("hasRole('ADMIN')")` (method security habilitada). El **CORS tiene una sola fuente**: `CorsConfig`, que expone el bean `CorsConfigurationSource` que consume `SecurityConfig` (`cors(Customizer.withDefaults())`); los orígenes salen de `app.cors.allowed-origins` / `CORS_ALLOWED_ORIGINS` (lista separada por comas, admite patrones como `https://*.onrender.com`) y por defecto permiten `localhost:3000`, `localhost:3001` y `127.0.0.1:3000`. La autorización de paquetes/tracking está preparada en la configuración, pero no se encontraron controladores de esos recursos en el código actual.

## 5. Configuración y operación

Las credenciales y valores de entorno deben permanecer fuera del código y de los commits. `application.properties` espera variables como `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET`, `JWT_SECRET`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `GOOGLE_CLIENT_ID`, `FACEBOOK_CLIENT_ID` y `FACEBOOK_CLIENT_SECRET`. La URL de frontend se configura con `FRONTEND_URL` (predeterminada a `http://localhost:3000`), los orígenes permitidos por CORS con `CORS_ALLOWED_ORIGINS` (por defecto, los de desarrollo local) y `FACEBOOK_API_VERSION` fija la versión de la Graph API (por defecto `v26.0`). Docker Compose carga un archivo `.env` local.

**Configuración de Spring repartida en dos archivos (ya sin contradicciones):** `application.properties` define el contexto `/api` y es la **única fuente** de `ddl-auto` (predeterminado `none`, sobrescribible con `SPRING_JPA_HIBERNATE_DDL_AUTO`); `application.yml` define el nombre de la app, el datasource, `hibernate.default_schema: auth_schema`, `format_sql` y JWT, y **ya no declara** `ddl-auto`. No hay dos valores distintos de `ddl-auto`, por lo que se eliminó la ambigüedad previa. El esquema SQL (`schema.sql` / `init.sql`) es la fuente de verdad: incluye `provider` / `provider_id` en `auth_schema.users`, bloques de reconciliación idempotentes para bases existentes (columnas de `users`, RBAC y catálogo `store_schema`) y `schema.sql` puede re-ejecutarse sin error (los tipos `ENUM` se crean de forma idempotente); `init.sql` es un script de arranque único para la base local. Al cambiar configuración, verifica el perfil y las propiedades efectivas.

**Archivo de perfil local (no versionado):** `src/main/resources/application-local.properties` replica los valores del `.env` para el perfil `local` y está correctamente ignorado por git (`.gitignore`), sin historial ni copia en el repositorio remoto. **Ojo:** trae `SPRING_JPA_HIBERNATE_DDL_AUTO=update`, de modo que activarlo contra la base administrada haría que Hibernate alterase el esquema de producción. Hoy no hay ninguna referencia a `spring.profiles.active=local` en el código, así que solo se aplica si se activa a mano.

**Ojo con `store_schema` en la base administrada:** sus tablas se crearon en algún momento desde entidades Hibernate, por lo que `categories.id` no tenía identidad y faltaban los `DEFAULT` declarados en `schema.sql`. El bloque de reconciliación del catálogo (en ambos scripts) los restaura de forma idempotente; si añades columnas nuevas al catálogo, aplícalas también ahí.

El proyecto utiliza InsForge como PostgreSQL administrado cuando `SPRING_DATASOURCE_URL` apunta a ese servicio. La CLI de InsForge se configura por separado en `.insforge/project.json`; nunca copies credenciales de aplicación o CLI a esta guía.

## 6. Mapa de dependencias (Graphify)

El grafo interactivo está en `graphify-out/graph.html`; el análisis fechado más reciente está en `graphify-out/2026-10-17/` (métricas: 233 nodos, 360 aristas, 30 comunidades, 93% EXTRACTED; nodos centrales: `User`, `AuthService`, `AuthController`, `ResponseEntity`). Se generó desde el árbol de trabajo en el commit `d508dd18`, por lo que incluye el trabajo de Google OAuth aún sin commitear; ejecuta `git rev-parse HEAD` y compáralo para detectar desactualización. Sus agrupaciones reflejan, entre otras, estas áreas:

1. **Auth y usuarios:** `AuthController`, `AuthService` (incluye `loginWithGoogle`), `GoogleAuthRequest`, `UserRepository`, `User` y DTO de cuenta.
2. **Recuperación de contraseña y correo:** `PasswordResetToken`, `PasswordResetTokenRepository`, `AuthService` y `EmailService`.
3. **Catálogo e imágenes:** `ProductController`/`ProductService`, `CategoryController`/`CategoryService`, `Product`, `Category`, `ProductRepository`, `CategoryRepository`, `CloudinaryService` y `CloudinaryConfig`.
4. **Seguridad:** `SecurityConfig`, `JwtAuthenticationFilter`, `JwtTokenProvider`, `CustomUserDetailsService` y `PasswordEncoderConfig`.

El grafo **aún no incluye** el CRUD de catálogo, la autorización por rol ni el CORS configurable de `CorsConfig`, añadidos después de su generación (commit `d508dd18`); el HEAD está ya muy por delante, así que vuelve a generarlo si necesitas el mapa actualizado.

Usa el HTML para inspeccionar nodos, archivos de origen y conexiones al modificar o ampliar una de estas áreas.

## 7. Pautas para cambios

1. Mantén Java 21 y revisa las dependencias y convenciones vigentes en `pom.xml` (Spring Boot 4.1.1).
2. Devuelve respuestas HTTP desde controladores usando `ResponseEntity` y códigos apropiados.
3. Inyecta configuración mediante propiedades de Spring y variables de entorno; nunca hardcodees ni publiques credenciales.
4. Conserva la coherencia entre entidades JPA y los esquemas PostgreSQL definidos en `schema.sql` / `init.sql`. El esquema es la fuente de verdad (`ddl-auto=none`): al añadir o renombrar columnas, actualiza ambos scripts **y** el bloque de migración idempotente para bases ya existentes.
5. Antes de cambiar rutas o reglas de acceso, revisa `server.servlet.context-path` y `SecurityConfig` para considerar el prefijo externo `/api`.

<!-- INSFORGE:START -->
## InsForge

Este proyecto usa [InsForge](https://insforge.dev) como servicio PostgreSQL administrado para la aplicación.

- **Proyecto:** `app_nexostorage` (API base `https://26kgknrj.us-east.insforge.app`).
- **Conexión backend:** PostgreSQL por JDBC configurado mediante variables `SPRING_DATASOURCE_*`.
- **Credenciales:** app en variables de entorno / `.env` local; CLI en `.insforge/project.json`. No hardcodear ni commitear claves.
- **Skills disponibles:** `insforge` para integración en código con `@insforge/sdk`; `insforge-cli` para infraestructura y CLI; `insforge-debug` para diagnóstico; `insforge-integrations` para proveedores externos y pagos; `find-skills` para descubrir otras capacidades.

El backend Java actual usa JDBC/JPA; aplica las pautas del SDK solo si se incorpora código que realmente use `@insforge/sdk`.
<!-- INSFORGE:END -->

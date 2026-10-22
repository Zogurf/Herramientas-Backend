# NEXORA Store & Logistics — Backend

API REST para autenticación, catálogo de productos, inventario administrativo y seguimiento de pedidos. Desarrollada como parte del proyecto Herramientas.

- **API desplegada:** <https://herramientas-backend-fjmk.onrender.com/api>

- **Frontend:** <https://herramientas-front-d9oe.vercel.app>

> ⚠️ **Nota de seguridad / Credenciales de prueba:**
> * **Correo:** admin.demo@nexora.test
> * **Contraseña:** NxAdmin-sH0Yk8lA5yJGAv
> 
> 

## Tecnologías

Java 21, Spring Boot 4.1.1, Spring Security, JWT, Spring Data JPA y PostgreSQL. Maven Wrapper para compilación, Cloudinary para imágenes, Spring Mail para correo, Apache POI y OpenPDF para exportaciones. El Dockerfile utiliza Eclipse Temurin 21.

## Funcionalidades

- Registro e inicio de sesión con correo y contraseña; autenticación con Google.
- Consulta y actualización del perfil, avatar y recuperación de contraseña.
- Catálogo público de productos y categorías; escrituras autorizadas para ADMIN.
- Inventario con búsqueda, filtros, paginación, estadísticas y exportación Excel/PDF.
- Administración de categorías, jerarquías y desactivación.
- Seguimiento de pedidos mediante eventos y consulta de su historial.

El contexto HTTP es `/api`. Los controladores declaran rutas relativas a ese contexto: `/products` se publica como `/api/products`.

## Ejecutar localmente

### Requisitos

- JDK 21 y `JAVA_HOME` configurado.
- Acceso a una base PostgreSQL con el esquema del proyecto.
- Credenciales de Cloudinary y un secreto JWT.
- Conexión a Internet para la primera descarga de Maven y dependencias.

### Configuración

1. Clona el repositorio y entra a su carpeta.
2. Copia `.env.example` a `.env` y completa los valores. No publiques ese archivo.
3. Establece `SPRING_JPA_HIBERNATE_DDL_AUTO=none`: la plantilla puede incluir `update`, pero los scripts SQL son la fuente de verdad.
4. Inicializa una base nueva con `src/main/resources/schema.sql`, o aplica las migraciones idempotentes necesarias a una base existente. La presencia del archivo no garantiza su ejecución automática contra PostgreSQL.
5. Ejecuta la aplicación:

```powershell
# Windows / PowerShell
.\mvnw.cmd spring-boot:run
```

```bash
# Linux / macOS
chmod +x mvnw
./mvnw spring-boot:run
```

La API local estará disponible en `http://localhost:8080/api`, salvo que configures otro `PORT`.

### Variables de entorno

| Variable | Uso |
|---|---|
| `PORT` | Puerto HTTP; predeterminado `8080`. |
| `SPRING_DATASOURCE_URL` | URL JDBC, por ejemplo `jdbc:postgresql://<host>:5432/<base>?sslmode=require` para la base administrada. |
| `SPRING_DATASOURCE_USERNAME` | Usuario PostgreSQL. |
| `SPRING_DATASOURCE_PASSWORD` | Contraseña vigente de PostgreSQL. |
| `SPRING_JPA_HIBERNATE_DDL_AUTO` | Usar `none` para mantener el esquema mediante SQL. |
| `JWT_SECRET` | Secreto aleatorio de al menos 32 bytes para firmar tokens. |
| `JWT_EXPIRATION` | Duración del token en milisegundos; predeterminado `86400000`. |
| `CLOUDINARY_CLOUD_NAME` | Nombre de la nube de Cloudinary. |
| `CLOUDINARY_API_KEY` | Clave de API de Cloudinary. |
| `CLOUDINARY_API_SECRET` | Secreto de API de Cloudinary. |
| `GOOGLE_CLIENT_ID` | Client ID de Google usado para verificar el ID token. |
| `MAIL_USERNAME`, `MAIL_PASSWORD` | Cuenta SMTP Gmail y contraseña de aplicación para recuperar contraseñas. |
| `FRONTEND_URL` | URL del frontend para los enlaces de recuperación. |
| `CORS_ALLOWED_ORIGINS` | Orígenes autorizados, separados por comas y sin barra final. |

El login con Google actual **no utiliza `GOOGLE_CLIENT_SECRET`**. El frontend debe tener el mismo Client ID en `NEXT_PUBLIC_GOOGLE_CLIENT_ID`.

Para el frontend publicado:

```env
FRONTEND_URL=https://herramientas-front-d9oe.vercel.app
CORS_ALLOWED_ORIGINS=https://herramientas-front-d9oe.vercel.app
```

Si también necesitas trabajar localmente, añade `http://localhost:3000` a la lista de CORS, separado por coma. El frontend configura `NEXT_PUBLIC_API_URL` con la URL del backend **incluyendo `/api`**.

## Endpoints principales

Todas las rutas siguientes incluyen el contexto externo `/api`. Las operaciones administrativas requieren `Authorization: Bearer <token>` y rol `ADMIN`.

| Método | Ruta | Función |
|---|---|---|
| POST | `/api/v1/auth/register` | Registro de cuenta. |
| POST | `/api/v1/auth/login` | Inicio de sesión y emisión de JWT. |
| POST | `/api/v1/auth/google` | Inicio de sesión con `{ "credential": "<id_token>" }`. |
| GET | `/api/v1/auth/me` | Consultar la cuenta autenticada. |
| POST | `/api/v1/auth/logout` | Cierre de sesión; el cliente debe descartar el JWT. |
| PUT | `/api/v1/auth/profile` | Actualizar el perfil. |
| POST | `/api/v1/auth/profile/avatar` | Subir avatar como multipart, campo `file`. |
| POST | `/api/v1/auth/forgot-password` | Solicitar recuperación por correo. |
| POST | `/api/v1/auth/reset-password` | Restablecer contraseña con un token. |
| GET | `/api/products` | Listar productos activos del catálogo público. |
| GET | `/api/products/{id}` | Consultar producto por UUID. |
| GET | `/api/categories` | Listar categorías del catálogo. |
| GET | `/api/categories/{id}` | Consultar categoría. |
| GET | `/api/admin/products/inventory` | Inventario paginado. |
| GET | `/api/admin/products/inventory/stats` | Totales y estadísticas del inventario. |
| GET | `/api/admin/products/inventory/export.xlsx` | Exportar inventario a Excel. |
| GET | `/api/admin/products/inventory/export.pdf` | Exportar inventario a PDF. |
| POST / PUT | `/api/admin/products` / `/api/admin/products/{id}` | Crear o editar producto con JSON. |
| PATCH | `/api/admin/products/{id}/deactivate` | Desactivar producto. |
| DELETE | `/api/admin/products/{id}` | Eliminar producto. |
| POST | `/api/admin/products/{id}/images` | Subir imagen principal `image` y opcional `hoverImage` como multipart. |
| DELETE | `/api/admin/products/{id}/images/hover` | Retirar imagen secundaria. |
| GET / POST | `/api/admin/categories` | Buscar categorías o crear una. |
| PUT / DELETE | `/api/admin/categories/{id}` | Editar o eliminar categoría. |
| PATCH | `/api/admin/categories/{id}/deactivate` | Desactivar categoría. |
| GET | `/api/tracking/{orderId}` | Consultar historial de un pedido. |
| POST | `/api/tracking` | Registrar evento de seguimiento; requiere ADMIN. |

También existen `GET /api/products/manage`, escrituras multipart en `/api/products` y `/api/products/{id}`, el alias `POST /api/products/upload` y escrituras JSON en `/api/categories`. Estas operaciones de gestión exigen ADMIN.

El inventario admite `search`, `categoryId`, `active` y `stockStatus` (`OUT`, `LOW`, `OPTIMAL`). El listado añade `page`, `size`, `sortBy` y `direction`; `page` comienza en cero y `size` admite de 1 a 100. Los mismos filtros se pueden usar en estadísticas y exportaciones.

## Base de datos

- `auth_schema`: usuarios, roles y recuperación de contraseña.
- `store_schema`: catálogo, pedidos y eventos de seguimiento de pedidos.
- `package_schema`: tablas SQL de paquetería; no equivalen a los endpoints de seguimiento de pedidos.

`schema.sql` contiene inicialización y reconciliación idempotente. `init.sql` se utiliza al inicializar el volumen PostgreSQL de Docker Compose. Al modificar el esquema, mantén ambos scripts consistentes. No actives el perfil local contra la base administrada sin revisar sus propiedades.

## Compilación y pruebas

```powershell
# Compilar desde cero
.\mvnw.cmd clean compile

# Pruebas de servicios sin iniciar el contexto completo de la aplicación
.\mvnw.cmd '-Dtest=*ServiceTest' test

# Generar el JAR sin ejecutar las pruebas
.\mvnw.cmd clean package -DskipTests
```

En Linux/macOS, sustituye `.\mvnw.cmd` por `./mvnw`. `ApplicationTests.contextLoads` requiere configuración externa y PostgreSQL disponible; por eso no es equivalente a las pruebas unitarias de servicios. En JDK recientes, Mockito puede requerir configurar su agente Java; usa preferentemente el JDK 21 del proyecto.

## Docker y Render

```bash
# Construir la imagen
docker build -t nexora-backend .

# Ejecutarla con las variables locales
docker run --rm --env-file .env -e PORT=8080 -p 8080:8080 nexora-backend
```

`docker-compose.yml` también incluye PostgreSQL y pgAdmin. Para conectar el contenedor backend a esa base local, usa el host `postgres`, puerto `5432` y nombre `delivery_db` en la URL JDBC; `localhost` dentro del contenedor no apunta al servicio PostgreSQL.

Render utiliza el Dockerfile y las variables definidas en su panel o Blueprint. **El `render.yaml` versionado apunta a `dev`**, aunque este README se publique en `main`; comprueba la rama configurada en el servicio antes de desplegar. Cambiar variables del backend requiere aplicar el cambio y desplegar/reiniciar según corresponda; las variables `NEXT_PUBLIC_*` de Vercel requieren reconstruir el frontend.

En el plan gratuito, Render puede suspender el backend tras inactividad, por lo que la primera petición puede tardar. Gmail SMTP no puede enviar correo desde Render Free por el bloqueo de los puertos SMTP; para recuperación de contraseña se necesita otra integración por HTTP o un entorno que permita SMTP. Consulta [la guía del proyecto](DEPLOY-RENDER.md) para los pasos y limitaciones.

## Estructura

```text
src/main/java/com/logistics/proyect/group5/
├── config/       # Seguridad, JWT, CORS y proveedores
├── controller/   # Rutas HTTP
├── dto/          # Peticiones y respuestas
├── exception/    # Manejo de errores
├── model/        # Entidades JPA
├── repository/   # Consultas y proyecciones
└── service/      # Reglas de negocio e integraciones
src/main/resources/  # Configuración y SQL
src/test/            # Pruebas
```

No subas contraseñas, tokens JWT, credenciales de prueba ni archivos `.env`. Configura los secretos mediante variables de entorno y usa las cuentas correspondientes a cada entorno.

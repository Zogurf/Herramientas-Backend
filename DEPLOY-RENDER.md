# Despliegue en Render — NEXORA STORAGE & LOGISTICS

Guía de traspaso para desplegar **backend** (`Herramientas-Backend`) y **frontend**
(`HerramientasFront`) en una cuenta de Render. Ambos repos son públicos y ambas
ramas de trabajo son `dev`.

Los dos servicios se crean con un **Blueprint**: cada repo incluye ya un
`render.yaml` que declara el servicio y sus variables. No hay que configurar nada
a mano en el dashboard salvo **rellenar los valores marcados como secretos**.

---

## 0. Antes de empezar — bloqueantes

Estas tres cosas impiden que el despliegue funcione. Hazlas primero.

- [ ] **Rotar la contraseña de PostgreSQL en InsForge.**
      La contraseña actual está **publicada en internet** (en el `package.json`
      de `HerramientasFront`, rama `dev`, de un repo público). Mientras no se
      rote, cualquiera puede leerla. Al rotarla hay que actualizar
      `SPRING_DATASOURCE_PASSWORD` en el `.env` local del backend, en Render y
      en el script `db:users` del frontend.

- [ ] **Aplicar el cambio de CORS en el backend** (pendiente en el código).
      Hoy `config/CorsConfig.java` y `config/SecurityConfig.java` tienen los
      orígenes fijos a `http://localhost:3000`. Con eso, el navegador bloquea
      **todas** las llamadas del frontend desplegado con **HTTP 403** en el
      preflight `OPTIONS`. Hasta aplicarlo, el frontend desplegado cargará pero
      no podrá autenticar ni listar productos. Los orígenes deben salir de la
      variable `CORS_ALLOWED_ORIGINS` (ya declarada en `render.yaml`).

- [ ] **Fijar `SPRING_JPA_HIBERNATE_DDL_AUTO=none`.**
      Va ya así en `render.yaml`. No lo cambies a `update`: el esquema es la
      fuente de verdad (`src/main/resources/schema.sql`) y `update` haría que
      Hibernate altere `auth_schema` y `store_schema` en cada arranque.

> Nota: el login con Google y la recuperación de contraseña dependen de
> variables que debe aportar el dueño de esos proyectos (Cloudinary, Google
> Cloud, Gmail). Ver la sección 3.

---

## 1. Backend (servicio Docker)

- [ ] Render Dashboard → **New + → Blueprint**.
- [ ] Conectar la cuenta de GitHub (ver sección 5) y seleccionar
      `Zogurf/Herramientas-Backend`, rama **`dev`**.
- [ ] Render detecta `render.yaml` y propone crear el servicio `nexora-backend`.
- [ ] Rellenar los valores `sync: false` que pedirá el dashboard (sección 3).
- [ ] Aplicar. El primer build tarda **varios minutos** (Maven descarga
      dependencias; en local fueron ~8 min).
- [ ] Verificar cuando termine (sección 4).

El blueprint define:

| Ajuste | Valor | Motivo |
|---|---|---|
| `runtime` | `docker` | Usa el `Dockerfile` multi-stage (Temurin 21). |
| `plan` | `free` | 0.1 CPU / 512 MB. Ver troubleshooting de memoria. |
| `region` | `virginia` | US East, la más cercana a la base InsForge (`us-east`). |
| `PORT` | *(no se define)* | Render lo inyecta (default 10000) y la app lo respeta. |
| `healthCheckPath` | *(omitido)* | **A propósito**: `/api/actuator/health` da 403 hoy. |
| `JWT_SECRET` | `generateValue` | Render genera un secreto aleatorio de 256 bits. |

---

## 2. Frontend (servicio Node)

- [ ] Repetir el alta de Blueprint con `JosiasVilca/HerramientasFront`, rama **`dev`**.
- [ ] Rellenar `NEXT_PUBLIC_API_URL` y `NEXT_PUBLIC_GOOGLE_CLIENT_ID` (sección 3).
- [ ] Verificar (sección 4).

El blueprint define build `pnpm install --frozen-lockfile && pnpm run build`,
arranque `pnpm start`, `NODE_VERSION=22.22.0` y health check en `/login`.

> `NEXT_PUBLIC_*` se incrusta en el bundle **en tiempo de build**. Si cambias la
> URL del backend, hay que **redeployar el frontend**, no basta con reiniciarlo.
> Y como el backend se nombra primero, el orden natural es: backend → copiar su
> URL → desplegar el frontend.

---

## 3. Variables de entorno

No las escribas en el repo: el dashboard las pedirá una sola vez.

### Backend

| Variable | De dónde sale |
|---|---|
| `SPRING_DATASOURCE_URL` | InsForge → `jdbc:postgresql://<host>:5432/insforge?sslmode=require` |
| `SPRING_DATASOURCE_USERNAME` | InsForge (`postgres`) |
| `SPRING_DATASOURCE_PASSWORD` | InsForge — **usar la contraseña ya rotada** |
| `CLOUDINARY_CLOUD_NAME` / `_API_KEY` / `_API_SECRET` | Cloudinary → Dashboard |
| `GOOGLE_CLIENT_ID` | Google Cloud → Credenciales → OAuth 2.0 Client ID |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | Cuenta de Gmail + contraseña de aplicación |
| `FRONTEND_URL` | URL del frontend en Render, **sin barra final** |
| `CORS_ALLOWED_ORIGINS` | La misma URL del frontend, **sin barra final** |
| `JWT_SECRET`, `JWT_EXPIRATION`, `SPRING_JPA_HIBERNATE_DDL_AUTO` | Ya generados/fijados por el blueprint |

### Frontend

| Variable | Valor |
|---|---|
| `NEXT_PUBLIC_API_URL` | URL del backend **incluyendo `/api`**: `https://nexora-backend.onrender.com/api` |
| `NEXT_PUBLIC_GOOGLE_CLIENT_ID` | El **mismo** Client ID que el backend |

> Si el backend y el frontend no se llaman `nexora-backend` / `nexora-frontend`,
> ajusta las URLs. Si prefieres otro nombre de servicio, cámbialo en `render.yaml`
> **antes** de crearlo: renombrar después cambia la URL y obliga a actualizarla en
> tres sitios (frontend, CORS y Google Cloud).

---

## 4. Verificación posterior

Con la URL del backend (`$API = https://<backend>.onrender.com`):

```bash
curl -s -o /dev/null -w '%{http_code}\n' "$API/api/products"      # 200
curl -s -o /dev/null -w '%{http_code}\n' "$API/api/categories"    # 200
curl -s -o /dev/null -w '%{http_code}\n' -X POST "$API/api/products"   # 403 (protegido)

# CORS: debe responder 200 y traer la cabecera access-control-allow-origin
curl -s -i -X OPTIONS "$API/api/v1/auth/login" \
  -H "Origin: https://<frontend>.onrender.com" \
  -H 'Access-Control-Request-Method: POST' | head -20
```

Si el preflight devuelve **403** y sin cabecera `access-control-allow-origin`, el
cambio de CORS de la sección 0 no está aplicado o `CORS_ALLOWED_ORIGINS` no
coincide **exactamente** con el origen del frontend (ni barra final, ni `www`,
esquema `https` correcto).

En el frontend: abrir `/login`, iniciar sesión y comprobar que el panel `/admin`
carga con datos reales (requiere rol `ADMIN`).

---

## 5. Cuenta de GitHub y auto-deploy

Los repos son **públicos**, así que se pueden desplegar sin ser colaborador. Pero
Render **solo ofrece auto-deploy** (redeploy con cada push) si el servicio está
conectado por la app de GitHub. Al crear un Blueprint, Render pide conectar
GitHub: con eso se obtienen auto-deploy y previews. Si se conecta el repo por URL
pública, cada actualización exige un deploy manual.

- [ ] Al conectar GitHub, dar a Render acceso a **ambos** repositorios.

---

## 6. Limitaciones conocidas del plan Free

| Limitación | Consecuencia |
|---|---|
| Puertos de salida 25/465/**587** bloqueados | **Gmail no puede enviar correo.** La recuperación de contraseña falla en silencio (el endpoint responde 200 igual). Usar un proveedor por API HTTP. |
| Suspensión tras 15 min sin tráfico | El primer acceso tras la pausa tarda ~1 min en responder. |
| 512 MB de RAM | El heap por defecto (75 % = 384 MB) deja muy poco margen para Spring Boot. Ver troubleshooting. |
| Sin discos persistentes ni SSH | No se puede depurar dentro del contenedor. |
| 750 h/mes de instancia | Suficiente para un servicio 24/7. |

---

## 7. Troubleshooting

**El contenedor muere por memoria (OOM) o arranca lento.**
`JAVA_OPTS` usa `-XX:MaxRAMPercentage=75.0`, que en 512 MB reserva 384 MB de heap
y deja ~128 MB para el resto de la JVM. Si aparece, bajarlo a `60.0`–`65.0` en el
`Dockerfile`, o subir el plan a `1c-2g` (2 GB).

**El deploy falla el health check.**
No añadir `healthCheckPath` al backend hasta que exista Actuator. La sonda TCP
por defecto funciona (comprobado: la app arranca y sirve en ~7 s).

**El correo de recuperación no llega.**
Ver la tabla del plan Free. `EmailService` no propaga el error, así que revisa los
logs del backend en busca de excepciones SMTP.

**El login con Google no funciona.**
Además del `CLIENT_ID`, hay que autorizar el dominio del frontend en Google Cloud
→ *Credenciales* → *Orígenes autorizados de JavaScript*. Ese cambio requiere
acceso al proyecto de Google (el `CLIENT_SECRET` no lo usa el backend).

**El frontend carga pero no muestra datos.**
Comprobar `NEXT_PUBLIC_API_URL` (debe incluir `/api`) y que el backend responda.
Un `NEXT_PUBLIC_*` mal puesto obliga a **redeployar**, no solo reiniciar.

---

## 8. Propiedad y continuidad

- El despliegue pertenece a la cuenta de Render de quien lo crea: esa persona
  administra logs, variables y borrado. Para compartir el control hay que
  invitar al **workspace** (Render soporta equipos).
- Las credenciales (Cloudinary, Google, InsForge, Gmail) quedarán visibles para
  quien tenga acceso a ese workspace. Si no se quieren compartir las personales,
  conviene crear proyectos dedicados: Cloudinary y Google Cloud son gratuitos.
- Con las mismas variables, este despliegue apunta a la **misma base de datos**
  de producción: los datos son compartidos con cualquier otro entorno que use
  las mismas credenciales.

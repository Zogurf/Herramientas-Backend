# Observabilidad — NEXORA Backend

Dos modos conviven en el mismo backend:

```
LOCAL (este directorio)
  backend :8080 ──scrape──> Prometheus :9090 ──query──> Grafana :3001

PRODUCCIÓN (Render)
  backend ──OTLP/HTTPS push──> Grafana Cloud (Prometheus + Grafana gestionados)
```

En local se usa **scrape** (Prometheus consulta `/api/actuator/prometheus`).
En producción se usa **push OTLP** porque el plan Free de Render no tiene discos
persistentes ni mantiene el servicio despierto. Ambos registries de Micrometer
están en el classpath, pero solo uno está activo en cada entorno.

---

## 1. Arranque rápido (local)

```bash
# 1) Backend en el host (como siempre)
./mvnw spring-boot:run

# 2) Stack de observabilidad
docker compose up -d prometheus grafana
```

| Servicio | URL | Credenciales |
|---|---|---|
| Grafana | http://localhost:3001 | `admin` / `GRAFANA_ADMIN_PASSWORD` |
| Prometheus | http://localhost:9090 | sin auth (solo escucha en local) |
| Métricas del backend | http://localhost:8080/api/actuator/prometheus | Basic auth (`MANAGEMENT_USER`) |

El dashboard **NEXORA Backend** y el datasource de Prometheus se aprovisionan
solos: al abrir Grafana ya están en la carpeta `NEXORA`. Las alertas también
(Alerting → Alert rules).

### Requisito en Linux con UFW

UFW aplica `policy drop` en la cadena `INPUT`, así que **los contenedores no
pueden llegar a los puertos del host** aunque el backend escuche en `0.0.0.0`.
Prometheus vería el target como `DOWN` con `context deadline exceeded`. Se
resuelve una sola vez:

```bash
sudo ./observability/scripts/allow-docker-to-backend.sh
```

Abre el puerto 8080 a `172.16.0.0/12` (todos los rangos de bridge de Docker).
Es reversible:

```bash
sudo ufw delete allow from 172.16.0.0/12 to any port 8080 proto tcp
```

Sin UFW, o en Docker Desktop, no hace falta nada: `host.docker.internal` ya
resuelve al host.

---

## 2. Variables de entorno

Se definen en `.env` (ignorado por git). Plantilla en `.env.example`.

| Variable | Para qué |
|---|---|
| `MANAGEMENT_USER` / `MANAGEMENT_PASSWORD` | HTTP Basic de `/api/actuator/prometheus`. Si faltan, Actuator responde 401 (falla cerrado). |
| `PROM_USER` / `PROM_PASS` | Credenciales que usa Prometheus en el scrape. Deben coincidir con `MANAGEMENT_*`. |
| `PROM_TARGET` | Destino del scrape: `host.docker.internal:8080` (backend con `./mvnw`) o `backend:8080` (backend en compose). |
| `PROM_RETENTION` | Retención de la serie temporal (por defecto `15d`). |
| `GRAFANA_ADMIN_PASSWORD` | Contraseña del usuario `admin` de Grafana local. |
| `OTLP_ENABLED` | `false` en local; `true` en Render. |
| `OTLP_URL` | Endpoint OTLP de Grafana Cloud (`.../otlp/v1/metrics`). |
| `OTLP_AUTH` | `Basic <base64(instanceID:token)>` de Grafana Cloud. |

Cambiar de modo (backend en host ↔ backend en Docker) es solo editar
`PROM_TARGET` y recrear Prometheus:

```bash
docker compose up -d --force-recreate prometheus
```

---

## 3. Seguridad de Actuator

`SecurityConfig` define una cadena propia (`@Order(1)`) para `/actuator/**`,
separada de la cadena JWT y sin su filtro:

| Ruta | Acceso |
|---|---|
| `/api/actuator/health`, `/health/**`, `/info` | público (sonda de Render) |
| `/api/actuator/prometheus` y el resto | HTTP Basic, rol `MONITOR` |

El usuario se crea en memoria a partir de `MANAGEMENT_USER` /
`MANAGEMENT_PASSWORD`. Si falta cualquiera de las dos, el proveedor queda sin
usuarios: los endpoints protegidos devuelven 401 y nunca se usan credenciales
por defecto. El proveedor se registra solo en el `AuthenticationManager` de esa
cadena, así que no interfiere con los usuarios de la base.

Ojo con el prefijo: `server.servlet.context-path=/api`, así que las rutas
externas son `/api/actuator/...`.

---

## 4. Contenido del directorio

```
observability/
├── prometheus/
│   └── render-config.sh              # genera la config con las credenciales de .env
├── grafana/
│   ├── provisioning/
│   │   ├── datasources/prometheus.yml
│   │   ├── dashboards/dashboards.yml
│   │   └── alerting/nexora-rules.yml
│   └── dashboards/nexora-backend.json
└── scripts/
    └── allow-docker-to-backend.sh    # regla de UFW (solo Linux)
```

Prometheus **no expande variables de entorno** en su YAML y las credenciales no
pueden vivir en el repo, así que el contenedor arranca con
`render-config.sh`: genera `/tmp/prometheus.yml` con `PROM_USER`, `PROM_PASS` y
`PROM_TARGET` y luego hace `exec` de Prometheus.

---

## 5. Qué se mide

| Panel | Métrica |
|---|---|
| JVM | `jvm_memory_used_bytes`, `jvm_gc_pause_seconds_sum`, `jvm_threads_live_threads`, `process_uptime_seconds` |
| HTTP | `http_server_requests_seconds_count` por status; p95/p99 desde `_bucket`; ratio 5xx |
| Pool | `hikaricp_connections_{active,idle,pending,max}`, `hikaricp_connections_acquire_seconds_*`, `hikaricp_connections_timeout_total` |

El p95 usa `histogram_quantile` sobre los buckets que habilita
`management.metrics.distribution.percentiles-histogram.http.server.requests=true`.

### Alertas provisionadas

| Alerta | Condición | Para |
|---|---|---|
| Errores 5xx por encima del 5 % | ratio 5xx > 0.05 | 5 min |
| Latencia p95 por encima de 2 s | p95 > 2 s | 10 min |
| Conexiones pendientes en el pool | `hikaricp_connections_pending > 0` | 5 min |

Se evalúan en Grafana (no en Prometheus, para no añadir Alertmanager). Las
notificaciones usan el *contact point* por defecto, que en una instalación limpia
no envía correo: configura uno en **Alerting → Contact points** si quieres avisos
fuera de la interfaz.

---

## 6. Problemas frecuentes

**El target sale DOWN con `context deadline exceeded`.**
Es UFW: ver la sección 1. Comprueba desde el contenedor con
`docker exec nexora_prometheus wget -qO- http://host.docker.internal:8080/api/actuator/health`.

**Los paneles están vacíos pero el target está UP.**
El backend arrancó antes de añadir Actuator, o el filtro `application="nexora-backend"`
no coincide: comprueba `curl -u user:pass localhost:8080/api/actuator/prometheus | grep application`.

**Los paneles de HikariCP no muestran nada.**
El pool no llegó a crearse: si la base de datos no responde al arrancar, HikariCP
falla el *fail-fast* y no publica métricas. El resto de paneles funciona igual.

**Grafana no carga el dashboard tras editarlo.**
El aprovisionamiento refresca cada 30 s (`updateIntervalSeconds`). Si el JSON es
inválido, el error aparece en `docker compose logs grafana`.

**`version` en docker-compose.yml es obsoleto.**
Es un aviso de Docker Compose v2; no afecta al funcionamiento.

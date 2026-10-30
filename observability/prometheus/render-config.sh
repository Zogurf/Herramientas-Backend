#!/bin/sh
# =============================================================================
# Renderiza la configuración de Prometheus desde variables de entorno.
#
# Prometheus NO expande variables de entorno en su YAML, y las credenciales del
# scrape no pueden vivir en el repositorio. Este script (que corre como
# entrypoint del contenedor) genera /tmp/prometheus.yml en cada arranque.
#
# Variables obligatorias (definidas en .env):
#   PROM_USER     usuario HTTP Basic de /api/actuator/prometheus
#   PROM_PASS     contraseña de ese usuario
#   PROM_TARGET   destino del scrape:
#                   host.docker.internal:8080 → backend con ./mvnw en el host
#                   backend:8080             → backend levantado con compose
# Variables opcionales:
#   PROM_RETENTION  retención de la serie temporal (por defecto 15d)
# =============================================================================
set -eu

: "${PROM_USER:?Falta PROM_USER (añádelo a .env)}"
: "${PROM_PASS:?Falta PROM_PASS (añádelo a .env)}"
: "${PROM_TARGET:?Falta PROM_TARGET (añádelo a .env)}"

config=/tmp/prometheus.yml

# Las comillas simples de YAML evitan que caracteres especiales rompan el parseo.
# No uses comillas simples en PROM_PASS.
cat > "$config" <<EOF
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  - job_name: nexora-backend
    metrics_path: /api/actuator/prometheus
    basic_auth:
      username: '${PROM_USER}'
      password: '${PROM_PASS}'
    static_configs:
      - targets: ['${PROM_TARGET}']
EOF

echo "Prometheus: scrape de ${PROM_TARGET} (usuario ${PROM_USER})"

exec /bin/prometheus \
  --config.file="$config" \
  --storage.tsdb.path=/prometheus \
  --storage.tsdb.retention.time="${PROM_RETENTION:-15d}" \
  --web.enable-lifecycle

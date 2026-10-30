#!/usr/bin/env bash
# =============================================================================
# Permite que los contenedores de Docker alcancen el backend que corre en el
# host (./mvnw spring-boot:run).
#
# Por qué hace falta: UFW aplica `policy drop` en la cadena INPUT, así que el
# tráfico contenedor → host se descarta aunque el backend escuche en 0.0.0.0.
# Prometheus (en Docker) necesita llegar a http://host.docker.internal:8080.
#
# Uso:
#   sudo ./observability/scripts/allow-docker-to-backend.sh [puerto]
#
# Para revertirlo:
#   sudo ufw delete allow from 172.16.0.0/12 to any port 8080 proto tcp
# =============================================================================
set -euo pipefail

PORT="${1:-8080}"
COMMENT="docker-to-backend-observability"

if ! command -v ufw >/dev/null 2>&1; then
  echo "ufw no está instalado: no hace falta hacer nada."
  exit 0
fi

if [ "$(id -u)" -ne 0 ]; then
  echo "Ejecútalo con sudo: sudo $0 $PORT" >&2
  exit 1
fi

# 172.16.0.0/12 cubre todos los rangos que Docker asigna a sus bridges
# (172.17.x a 172.31.x), así que la regla sigue valiendo aunque cambie de red.
if ufw status | grep -q "$COMMENT"; then
  echo "La regla ya existe."
else
  ufw allow from 172.16.0.0/12 to any port "$PORT" proto tcp comment "$COMMENT"
fi

ufw status | grep "$COMMENT"

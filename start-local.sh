#!/usr/bin/env bash
# =============================================================================
# start-local.sh — Start the SAGA with Liberty stack locally
#
# Prerequisites:
#   - Java 17+, Maven 3.9+, curl
#   - Docker or Podman (for PostgreSQL only)
#   - curl (for health checks)
#   The LRA coordinator runs as a local JVM process by default.
#   Pass --use-container to run it in a container instead.
#
# Services started:
#   postgres           localhost:5432
#   lra-coordinator    localhost:8070
#   deposit-service    localhost:9081  /deposit
#   withdrawal-service localhost:9082  /withdrawal
#   transfer-service   localhost:9083  /transfer
#
# Usage:
#   chmod +x start-local.sh
#   ./start-local.sh                 # start everything (LRA runs as JVM process)
#   ./start-local.sh --skip-build    # skip mvn package, use existing WARs
#   ./start-local.sh --use-container # run LRA coordinator in a container
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SKIP_BUILD=false
NO_CONTAINER=true
LOG_DIR="${SCRIPT_DIR}/.logs"
# lra-coordinator-quarkus runner JARs are published up to 5.13.0.Final on Maven Central.
# The REST API is stable and fully compatible with lra-client 7.x.
LRA_VERSION="5.13.0.Final"
LRA_JAR="lra-coordinator-quarkus-${LRA_VERSION}-runner.jar"
LRA_JAR_URL="https://repo1.maven.org/maven2/org/jboss/narayana/rts/lra-coordinator-quarkus/${LRA_VERSION}/${LRA_JAR}"
LRA_JAR_PATH="${SCRIPT_DIR}/.lra/${LRA_JAR}"
LRA_PID=""
PG_DRIVER_VERSION="42.7.13"
PG_DRIVER_JAR="postgresql-${PG_DRIVER_VERSION}.jar"
PG_DRIVER_URL="https://jdbc.postgresql.org/download/${PG_DRIVER_JAR}"

# Colours
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; NC='\033[0m'

info()    { echo -e "${CYAN}[INFO]${NC}  $*"; }
success() { echo -e "${GREEN}[OK]${NC}    $*"; }
warn()    { echo -e "${YELLOW}[WARN]${NC}  $*"; }
error()   { echo -e "${RED}[ERROR]${NC} $*" >&2; }

# -----------------------------------------------------------------------------
# Parse arguments
# -----------------------------------------------------------------------------
for arg in "$@"; do
  case $arg in
    --skip-build)     SKIP_BUILD=true ;;
    --use-container)  NO_CONTAINER=false ;;
    --help|-h)
      echo "Usage: $0 [--skip-build] [--use-container]"
      echo "  --skip-build      Skip 'mvn package' and use existing target/ WARs"
      echo "  --use-container   Run LRA coordinator in a container instead of a local JVM process"
      exit 0 ;;
    *) error "Unknown argument: $arg"; exit 1 ;;
  esac
done

mkdir -p "${LOG_DIR}"

# -----------------------------------------------------------------------------
# Trap — kill background Liberty processes on exit
# -----------------------------------------------------------------------------
PIDS=()
cleanup() {
  echo ""
  info "Shutting down Liberty services..."
  for pid in "${PIDS[@]+"${PIDS[@]}"}"; do
    kill "${pid}" 2>/dev/null && info "Stopped PID ${pid}" || true
  done
  if [[ "${NO_CONTAINER}" == true ]]; then
    if [[ -n "${LRA_PID}" ]]; then
      kill "${LRA_PID}" 2>/dev/null && info "Stopped LRA coordinator (PID ${LRA_PID})" || true
    fi
    info "Stopping PostgreSQL container..."
    ${CONTAINER_CLI} stop saga-postgres 2>/dev/null || true
  else
    info "Stopping containers..."
    ${CONTAINER_CLI} stop saga-postgres saga-lra-coordinator 2>/dev/null || true
  fi
  info "Done."
}
trap cleanup EXIT INT TERM

# -----------------------------------------------------------------------------
# 1. Check prerequisites
# -----------------------------------------------------------------------------
info "Checking prerequisites..."
for cmd in java mvn curl; do
  if ! command -v "${cmd}" &>/dev/null; then
    error "'${cmd}' not found. Please install it before running this script."
    exit 1
  fi
done

# Detect container runtime (docker preferred, podman as fallback)
if command -v docker &>/dev/null; then
  CONTAINER_CLI=docker
elif command -v podman &>/dev/null; then
  CONTAINER_CLI=podman
else
  error "Neither 'docker' nor 'podman' found. Please install one before running this script."
  exit 1
fi
info "Using container runtime: ${CONTAINER_CLI}"

JAVA_VER=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1)
if [[ "${JAVA_VER}" -lt 17 ]]; then
  error "Java 17+ required. Found: $(java -version 2>&1 | head -1)"
  exit 1
fi
success "Prerequisites OK (Java ${JAVA_VER}, $(mvn -v | head -1))"

# -----------------------------------------------------------------------------
# 2. Start PostgreSQL
# -----------------------------------------------------------------------------
info "Starting PostgreSQL..."
${CONTAINER_CLI} rm -f saga-postgres 2>/dev/null || true
${CONTAINER_CLI} run -d \
  --name saga-postgres \
  -e POSTGRES_DB=sagadb \
  -e POSTGRES_USER=saga \
  -e POSTGRES_PASSWORD=saga \
  -p 5432:5432 \
  -v "${SCRIPT_DIR}/db/init.sql:/docker-entrypoint-initdb.d/init.sql:ro" \
  postgres:16 \
  > "${LOG_DIR}/postgres.log" 2>&1

info "Waiting for PostgreSQL to be ready..."
for i in $(seq 1 30); do
  if ${CONTAINER_CLI} exec saga-postgres pg_isready -U saga -d sagadb &>/dev/null; then
    success "PostgreSQL ready."
    break
  fi
  if [[ $i -eq 30 ]]; then
    error "PostgreSQL did not become ready in time. Check ${LOG_DIR}/postgres.log"
    exit 1
  fi
  sleep 2
done

# -----------------------------------------------------------------------------
# 3. Start Narayana LRA coordinator
# -----------------------------------------------------------------------------
if [[ "${NO_CONTAINER}" == true ]]; then
  info "Starting Narayana LRA coordinator natively (JVM) on port 8070..."
  mkdir -p "${SCRIPT_DIR}/.lra"
  if [[ ! -f "${LRA_JAR_PATH}" ]]; then
    info "Downloading ${LRA_JAR}..."
    curl -fSL "${LRA_JAR_URL}" -o "${LRA_JAR_PATH}"
    success "LRA coordinator JAR cached at ${LRA_JAR_PATH}"
  else
    success "LRA coordinator JAR already cached."
  fi
  java -Dquarkus.http.port=8070 \
       -Dquarkus.log.level=DEBUG \
       -jar "${LRA_JAR_PATH}" \
       > "${LOG_DIR}/lra-coordinator.log" 2>&1 &
  LRA_PID=$!
else
  info "Starting Narayana LRA coordinator on port 8070 (container)..."
  ${CONTAINER_CLI} rm -f saga-lra-coordinator 2>/dev/null || true
  ${CONTAINER_CLI} run -d \
    --name saga-lra-coordinator \
    -e QUARKUS_HTTP_PORT=8070 \
    -e QUARKUS_LOG_LEVEL=DEBUG \
    -p 8070:8070 \
    quay.io/jbosstm/lra-coordinator:latest \
    > "${LOG_DIR}/lra-coordinator.log" 2>&1
fi

info "Waiting for LRA coordinator to be ready..."
for i in $(seq 1 30); do
  if curl -sf http://localhost:8070/lra-coordinator &>/dev/null; then
    success "LRA coordinator ready."
    break
  fi
  if [[ $i -eq 30 ]]; then
    error "LRA coordinator did not become ready in time. Check ${LOG_DIR}/lra-coordinator.log"
    exit 1
  fi
  sleep 2
done

# -----------------------------------------------------------------------------
# 4. Download PostgreSQL JDBC driver if needed and place in each service
# -----------------------------------------------------------------------------
info "Checking PostgreSQL JDBC driver..."
for svc in deposit-service withdrawal-service; do
  # liberty:run creates the server dir lazily; pre-create the shared resources dir
  JDBC_DIR="${SCRIPT_DIR}/${svc}/target/liberty/wlp/usr/shared/resources/jdbc"
  mkdir -p "${JDBC_DIR}"
  if [[ ! -f "${JDBC_DIR}/${PG_DRIVER_JAR}" ]]; then
    info "Downloading ${PG_DRIVER_JAR} for ${svc}..."
    curl -fSL "${PG_DRIVER_URL}" -o "${JDBC_DIR}/${PG_DRIVER_JAR}"
    success "Driver placed in ${JDBC_DIR}"
  else
    success "Driver already present for ${svc}."
  fi
done

# -----------------------------------------------------------------------------
# 5. Build all services (unless --skip-build)
# -----------------------------------------------------------------------------
if [[ "${SKIP_BUILD}" == false ]]; then
  for svc in deposit-service withdrawal-service transfer-service; do
    info "Building ${svc}..."
    mvn -f "${SCRIPT_DIR}/${svc}/pom.xml" package -DskipTests -q
    success "${svc} built."
  done
else
  warn "--skip-build set: skipping mvn package."
fi

# -----------------------------------------------------------------------------
# Helper: wait for a Liberty server to log "server is ready"
# -----------------------------------------------------------------------------
wait_for_liberty() {
  local log="$1"
  local label="$2"
  for i in $(seq 1 60); do
    if grep -q "server is ready to run a smarter planet" "${log}" 2>/dev/null; then
      success "${label} ready."
      return 0
    fi
    sleep 2
  done
  error "${label} did not start in time. Check ${log}"
  return 1
}

# -----------------------------------------------------------------------------
# 6. Start deposit-service
# -----------------------------------------------------------------------------
info "Starting deposit-service on port 9081..."
mvn -f "${SCRIPT_DIR}/deposit-service/pom.xml" liberty:run \
  > "${LOG_DIR}/deposit-service.log" 2>&1 &
PIDS+=($!)
wait_for_liberty "${LOG_DIR}/deposit-service.log" "deposit-service"

# -----------------------------------------------------------------------------
# 7. Start withdrawal-service
# -----------------------------------------------------------------------------
info "Starting withdrawal-service on port 9082..."
mvn -f "${SCRIPT_DIR}/withdrawal-service/pom.xml" liberty:run \
  > "${LOG_DIR}/withdrawal-service.log" 2>&1 &
PIDS+=($!)
wait_for_liberty "${LOG_DIR}/withdrawal-service.log" "withdrawal-service"

# -----------------------------------------------------------------------------
# 8. Start transfer-service
# -----------------------------------------------------------------------------
info "Starting transfer-service on port 9083..."
mvn -f "${SCRIPT_DIR}/transfer-service/pom.xml" liberty:run \
  > "${LOG_DIR}/transfer-service.log" 2>&1 &
PIDS+=($!)
wait_for_liberty "${LOG_DIR}/transfer-service.log" "transfer-service"

# -----------------------------------------------------------------------------
# 9. Smoke test
# -----------------------------------------------------------------------------
echo ""
info "Running smoke test (transfer ACC-001 → ACC-002, amount 50)..."
HTTP_STATUS=$(curl -s -o /tmp/saga-smoke.json -w "%{http_code}" \
  -X POST http://localhost:9083/transfer/transfer \
  -H "Content-Type: application/json" \
  -d '{"fromAccount":"ACC-001","toAccount":"ACC-002","amount":50.00}')

if [[ "${HTTP_STATUS}" == "200" ]]; then
  success "Smoke test passed: $(cat /tmp/saga-smoke.json)"
else
  warn "Smoke test returned HTTP ${HTTP_STATUS}: $(cat /tmp/saga-smoke.json)"
fi

# -----------------------------------------------------------------------------
# 10. Summary
# -----------------------------------------------------------------------------
echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN} All services are running${NC}"
echo -e "${GREEN}========================================${NC}"
echo -e "  PostgreSQL         localhost:5432"
echo -e "  LRA Coordinator    http://localhost:8070/lra-coordinator"
echo -e "  deposit-service    http://localhost:9081/deposit"
echo -e "  withdrawal-service http://localhost:9082/withdrawal"
echo -e "  transfer-service   http://localhost:9083/transfer"
echo ""
echo -e "  Logs: ${LOG_DIR}/"
echo ""
echo -e "  Example transfer:"
echo -e "  ${CYAN}curl -X POST http://localhost:9083/transfer/transfer \\"
echo -e "    -H 'Content-Type: application/json' \\"
echo -e "    -d '{\"fromAccount\":\"ACC-001\",\"toAccount\":\"ACC-002\",\"amount\":100}'${NC}"
echo ""
echo -e "Press ${RED}Ctrl+C${NC} to stop all services."
echo ""

# Keep script alive until Ctrl+C
wait

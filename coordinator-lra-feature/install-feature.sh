#!/usr/bin/env bash
# install-feature.sh
# Builds the usr:coordinatorLRA-2.0 Liberty user feature and installs it into
# the Liberty server whose user directory is passed as the first argument.
#
# Usage:
#   ./install-feature.sh /path/to/wlp/usr
#
# If no argument is given, the script prints the manual copy commands instead.
#
# IMPORTANT — DEDICATED COORDINATOR SERVER
# ─────────────────────────────────────────
# The server that loads usr:coordinatorLRA-2.0 must be a dedicated coordinator
# server.  Do NOT deploy application WARs, EARs, or any other deployables to
# it (no dropins/, no <application> elements).  Business microservices belong
# on their own Liberty servers and should use usr:clientLRA-2.0 to reach this
# coordinator.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ZIP="${SCRIPT_DIR}/feature/target/coordinatorLRA-2.0-feature.zip"

# Build the feature if the zip does not exist yet
if [[ ! -f "${ZIP}" ]]; then
  echo "→ Building coordinatorLRA-2.0 feature…"
  mvn -f "${SCRIPT_DIR}/pom.xml" -q clean package
fi

WLP_USR="${1:-}"

if [[ -z "${WLP_USR}" ]]; then
  echo ""
  echo "No target directory supplied. To install manually, run:"
  echo "  unzip -o '${ZIP}' -d <wlp.user.dir>"
  echo ""
  echo "Then add to your server.xml featureManager:"
  echo "  <feature>usr:coordinatorLRA-2.0</feature>"
  echo ""
  echo "Configure the transaction-log store:"
  echo "  File store (default):"
  echo "    <lraCoordinatorStore storeType=\"file\" storeDir=\"\${server.output.dir}/lra-logs\"/>"
  echo ""
  echo "  Database store (PostgreSQL):"
  echo "    <lraCoordinatorStore storeType=\"db\""
  echo "                         dbUrl=\"jdbc:postgresql://localhost:5432/sagadb\""
  echo "                         dbUser=\"saga\""
  echo "                         dbPassword=\"secret\""
  echo "                         tablePrefix=\"lra_\"/>"
  echo ""
  echo "The coordinator REST API will be served at: http://<host>:<port>/lra-coordinator"
  echo ""
  echo "⚠  IMPORTANT: this must be a DEDICATED coordinator server."
  echo "   Do not deploy application WARs/EARs or add <application> elements."
  echo "   Business services belong on separate Liberty servers."
  exit 0
fi

echo "→ Installing coordinatorLRA-2.0 into ${WLP_USR}…"
unzip -o "${ZIP}" -d "${WLP_USR}"
echo "✓ Feature installed."
echo ""
echo "Add to your server.xml featureManager:"
echo "  <feature>usr:coordinatorLRA-2.0</feature>"
echo ""
echo "Configure the store (file or db):"
echo "  <lraCoordinatorStore storeType=\"file\" storeDir=\"\${server.output.dir}/lra-logs\"/>"
echo ""
echo "The LRA coordinator REST API will be available at:"
echo "  http://<server-host>:<httpPort>/lra-coordinator"
echo ""
echo "⚠  IMPORTANT: keep this a DEDICATED coordinator server."
echo "   Do not deploy application WARs/EARs or add <application> elements"
echo "   to this server.xml.  Business microservices belong on separate"
echo "   Liberty servers using usr:clientLRA-2.0 to reach this coordinator."

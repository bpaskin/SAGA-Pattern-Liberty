#!/usr/bin/env bash
# install-feature.sh
# Builds the usr:clientLRA-2.0 Liberty user feature and installs it into the
# Liberty server whose user directory is passed as the first argument.
#
# Usage:
#   ./install-feature.sh /path/to/wlp/usr
#
# If no argument is given, the script prints the manual copy commands instead.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ZIP="${SCRIPT_DIR}/feature/target/clientLRA-2.0-feature.zip"

# Build the feature if the zip does not exist yet
if [[ ! -f "${ZIP}" ]]; then
  echo "→ Building clientLRA-2.0 feature…"
  mvn -f "${SCRIPT_DIR}/pom.xml" -q clean package
fi

WLP_USR="${1:-}"

if [[ -z "${WLP_USR}" ]]; then
  echo ""
  echo "No target directory supplied. To install manually, run:"
  echo "  unzip -o '${ZIP}' -d <wlp.user.dir>"
  echo ""
  echo "Then add to your server.xml:"
  echo "  <feature>usr:clientLRA-2.0</feature>"
  echo ""
  echo "And configure the coordinator:"
  echo "  <lraCoordinator host=\"lra-coordinator\" port=\"8070\"/>"
  exit 0
fi

echo "→ Installing clientLRA-2.0 into ${WLP_USR}…"
unzip -o "${ZIP}" -d "${WLP_USR}"
echo "✓ Feature installed."
echo ""
echo "Add to your server.xml featureManager:"
echo "  <feature>usr:clientLRA-2.0</feature>"
echo ""
echo "And configure the coordinator host and port:"
echo "  <lraCoordinator host=\"lra-coordinator\" port=\"8070\"/>"

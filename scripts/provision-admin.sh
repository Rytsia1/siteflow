#!/usr/bin/env bash
# =============================================================================
# SiteFlow — Administrator Provisioning Script
#
# Usage:
#   ./scripts/provision-admin.sh
#
# Interactively prompts for username, display name, and securely reads the
# password (hidden input) to prevent exposure in process lists or shell history.
# =============================================================================

set -euo pipefail

echo "========================================================"
echo " SiteFlow — Explicit Administrator Provisioning"
echo "========================================================"
echo

read -r -p "Enter admin username: " ADMIN_USERNAME
if [[ -z "$ADMIN_USERNAME" ]]; then
    echo "Error: Username cannot be empty." >&2
    exit 1
fi

read -r -p "Enter full name [System Administrator]: " ADMIN_FULL_NAME
ADMIN_FULL_NAME=${ADMIN_FULL_NAME:-"System Administrator"}

read -r -p "Enter job position [Site Administrator]: " ADMIN_JOB_POSITION
ADMIN_JOB_POSITION=${ADMIN_JOB_POSITION:-"Site Administrator"}

# Read password without echoing to terminal
read -r -s -p "Enter admin password (min 8 characters): " ADMIN_PASSWORD
echo
if [[ ${#ADMIN_PASSWORD} -lt 8 ]]; then
    echo "Error: Password must be at least 8 characters long." >&2
    exit 1
fi

read -r -s -p "Confirm admin password: " ADMIN_PASSWORD_CONFIRM
echo
if [[ "$ADMIN_PASSWORD" != "$ADMIN_PASSWORD_CONFIRM" ]]; then
    echo "Error: Passwords do not match." >&2
    exit 1
fi

echo
echo "Provisioning administrator '$ADMIN_USERNAME'..."

export SITEFLOW_PROVISION_ADMIN=true
export SITEFLOW_ADMIN_USERNAME="$ADMIN_USERNAME"
export SITEFLOW_ADMIN_PASSWORD="$ADMIN_PASSWORD"
export SITEFLOW_ADMIN_FULL_NAME="$ADMIN_FULL_NAME"
export SITEFLOW_ADMIN_JOB_POSITION="$ADMIN_JOB_POSITION"
export SITEFLOW_PROVISION_ADMIN_EXIT_AFTER=true

# Locate JAR or run via maven
if [[ -f "target/siteflow-0.0.1-SNAPSHOT.jar" ]]; then
    java -jar target/siteflow-0.0.1-SNAPSHOT.jar
else
    mvn spring-boot:run -Dspring-boot.run.arguments="--siteflow.provision-admin=true --siteflow.provision-admin.exit-after=true"
fi

# Clear secrets from environment
unset SITEFLOW_ADMIN_PASSWORD
unset SITEFLOW_ADMIN_PASSWORD_CONFIRM

echo "Administrator '$ADMIN_USERNAME' provisioning finished successfully."

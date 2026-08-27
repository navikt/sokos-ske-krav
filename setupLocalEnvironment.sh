#!/bin/bash
set -euo pipefail

readonly APP_NAME="sokos-ske-krav"
readonly VAULT_PATH_POSTGRES="postgresql/preprod-fss/creds/sokos-ske-krav-user"
export VAULT_ADDR=https://vault.adeo.no

log() { echo "[$(date +%H:%M:%S)] $*"; }
error() { echo "[$(date +%H:%M:%S)] ERROR: $*" >&2; exit 1; }

# ── Authentication ────────────────────────────────────────────────────────────

log "Checking gcloud authentication..."
if ! gcloud auth print-identity-token &>/dev/null; then
    gcloud auth login
fi

log "Getting active gcloud user"
DB_USER=$(gcloud auth list --format="value(account)" | grep nav)

log "Switching kubectl context to dev-gcp / okonomi..."
kubectl config use-context dev-gcp
kubectl config set-context --current --namespace=okonomi

log "Checking Vault authentication..."
if ! vault token lookup -format=json 2>/dev/null | jq -e '.data.display_name' 2>/dev/null ; then
    vault login -method=oidc -no-print
fi

# ── Fetch env vars from pod ───────────────────────────────────────────────────

ENV_VARS=$(cat <<'EOF'
MASKINPORTEN_CLIENT_JWK
MASKINPORTEN_CLIENT_ID
MASKINPORTEN_WELL_KNOWN_URL
MASKINPORTEN_SCOPES
SKE_SFTP_USERNAME
SKE_SFTP_PASSWORD
SOKOS_SKE_KRAV_SLACK_WEBHOOK_URL
BASIC_AUTH_USERNAME
BASIC_AUTH_PASSWORD
SOKOS_SKE_KRAV_SLACKID_PRODUCTLEADER
SOKOS_SKE_KRAV_SLACKID_DOMAINSPECIALISTS
SOKOS_SKE_KRAV_SLACKID_TECHNICALSPECIALIST
AZURE_OPENID_CONFIG_JWKS_URI
AZURE_OPENID_CONFIG_ISSUER
AZURE_APP_CLIENT_ID
EOF
)

log "Looking up pod for $APP_NAME..."
POD_NAME=$(kubectl get pods --no-headers | grep "$APP_NAME" | awk 'NR==1{print $1}')
[ -z "$POD_NAME" ] && error "No running pod found for $APP_NAME"
log "Using pod: $POD_NAME"

log "Fetching environment variables from pod..."
envValue=$(kubectl exec "$POD_NAME" -c "$APP_NAME" -- env \
    | awk -F= 'NR==FNR{allow[$1]=1; next} allow[$1]' <(printf '%s\n' "$ENV_VARS") - \
    | sort)
[ -z "$envValue" ] && error "No matching environment variables found in pod"

# ── Fetch secrets from Vault ──────────────────────────────────────────────────

log "Fetching SFTP private key from Vault..."
PRIVATE_KEY=$(kubectl exec -n okonomi "$POD_NAME" -- cat /var/run/secrets/sokos-ske-krav-sftp-private-key/private-key)
[ -z "$PRIVATE_KEY" ] && error "Failed to fetch SFTP private key"

# ── Write output files ────────────────────────────────────────────────────────

log "Writing defaults.properties..."
{
    echo "$envValue"
    echo "DB_USER=$DB_USER"
} > defaults.properties

log "Writing privateKey..."
echo "$PRIVATE_KEY" > privateKey
chmod 600 privateKey

log "Done! defaults.properties and privateKey created successfully."

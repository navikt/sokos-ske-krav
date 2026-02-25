#!/bin/bash
export VAULT_ADDR=https://vault.adeo.no

# Ensure user is authenticated, and run login if not.
if ! gcloud auth print-identity-token &>/dev/null; then
    gcloud auth login
fi

kubectl config use-context dev-fss
kubectl config set-context --current --namespace=okonomi

# Authenticate Vault if needed
if ! vault token lookup -format=json | jq -e '.data.display_name' | grep -q "nav.no"; then
    vault login -method=oidc -no-print
fi

# Fetch secrets from Kubernetes
get_secret() {
    local secret_name=$1
    local key=$2
    kubectl get secret "$secret_name" -o "jsonpath={.data.$key}" | base64 --decode
}

get_secretName(){
  local prefix=$1
  kubectl get secrets --no-headers -o custom-columns=":metadata.name" | grep "^$prefix-" | head -n1
}


MASKINPORTEN_CLIENT_JWK=$(get_secret "$(get_secretName maskinporten-sokos-ske-krav)" MASKINPORTEN_CLIENT_JWK)
MASKINPORTEN_CLIENT_ID=$(get_secret "$(get_secretName maskinporten-sokos-ske-krav)" MASKINPORTEN_CLIENT_ID)
MASKINPORTEN_WELL_KNOWN_URL=$(get_secret "$(get_secretName maskinporten-sokos-ske-krav)" MASKINPORTEN_WELL_KNOWN_URL)
MASKINPORTEN_SCOPES=$(get_secret "$(get_secretName maskinporten-sokos-ske-krav)" MASKINPORTEN_SCOPES)

AZURE_APP_CLIENT_ID=$(get_secret "$(get_secretName azure-sokos-ske-krav)" AZURE_APP_CLIENT_ID)
AZURE_APP_WELL_KNOWN_URL=$(get_secret "$(get_secretName azure-sokos-ske-krav)" AZURE_APP_WELL_KNOWN_URL)
AZURE_APP_TENANT_ID=$(get_secret "$(get_secretName azure-sokos-ske-krav)" AZURE_APP_TENANT_ID)
AZURE_APP_CLIENT_SECRET=$(get_secret "$(get_secretName azure-sokos-ske-krav)" AZURE_APP_CLIENT_SECRET)

SKE_SFTP_USERNAME=$(get_secret ske-sftp-creds SKE_SFTP_USERNAME)
SKE_SFTP_PASSWORD=$(get_secret ske-sftp-creds SKE_SFTP_PASSWORD)
TEAM_BEST_SLACK_WEBHOOK_URL=$(get_secret team-best-slackbot-webhook TEAM_BEST_SLACK_WEBHOOK_URL)

# Get database username and password secret from Vault
POSTGRES_USER=$(vault kv get -field=data postgresql/preprod-fss/creds/sokos-ske-krav-user)


username=$(echo "$POSTGRES_USER" | awk -F 'username:' '{print $2}' | awk '{print $1}' | sed 's/]$//')
password=$(echo "$POSTGRES_USER" | awk -F 'password:' '{print $2}' | awk '{print $1}' | sed 's/]$//')

rm -f defaults.properties
{
    echo "MASKINPORTEN_CLIENT_JWK=$MASKINPORTEN_CLIENT_JWK"
    echo "MASKINPORTEN_CLIENT_ID=$MASKINPORTEN_CLIENT_ID"
    echo "MASKINPORTEN_WELL_KNOWN_URL=$MASKINPORTEN_WELL_KNOWN_URL"
    echo "MASKINPORTEN_SCOPES=$MASKINPORTEN_SCOPES"
    echo "AZURE_APP_CLIENT_ID=$AZURE_APP_CLIENT_ID"
    echo "AZURE_APP_WELL_KNOWN_URL=$AZURE_APP_WELL_KNOWN_URL"
    echo "AZURE_APP_TENANT_ID=$AZURE_APP_TENANT_ID"
    echo "AZURE_APP_CLIENT_SECRET=$AZURE_APP_CLIENT_SECRET"
    echo "SKE_SFTP_USERNAME=$SKE_SFTP_USERNAME"
    echo "SKE_SFTP_PASSWORD=$SKE_SFTP_PASSWORD"
    echo "POSTGRES_USERNAME=$username"
    echo "POSTGRES_PASSWORD=$password"
    echo "TEAM_BEST_SLACK_WEBHOOK_URL=$TEAM_BEST_SLACK_WEBHOOK_URL"
} > defaults.properties

echo "defaults.properties created successfully."
#!/bin/bash
export VAULT_ADDR=https://vault.adeo.no
# Ensure user is authenicated, and run login if not.
gcloud auth print-identity-token &> /dev/null
if [ $? -gt 0 ]; then
    gcloud auth login
fi
kubectl config use-context dev-fss
kubectl config set-context --current --namespace=okonomi

# Get database username and password secret from Vault
[[ "$(vault token lookup -format=json | jq '.data.display_name' -r; exit ${PIPESTATUS[0]})" =~ "nav.no" ]] &>/dev/null || vault login -method=oidc -no-print

# Get AZURE system variables
envValue=$(kubectl exec -it $(kubectl get pods | grep sokos-ske-krav | cut -f1 -d' ') -c sokos-ske-krav -- env | egrep "^AZURE|^MASKINPORTEN|^SKE_REST_URL|^SKE_SFTP_USERNAME|SKE_SFTP_PASSWORD|SFTP_PORT|POSTGRES_PORT|POSTGRES_NAME|TEAM_BEST_SLACK_WEBHOOK_URL|USE_TIMER|TIMER_INITIAL_DELAY|TIMER_INTERVAL_PERIOD" | sort)
PRIVATE_KEY=$(vault read -field=privateKey kv/preprod/fss/sokos-ske-krav/okonomi/sftp)

POSTGRES_USER=$(vault kv get -field=data postgresql/preprod-fss/creds/sokos-ske-krav-user)
#POSTGRES_ADMIN=$(vault kv get -field=data postgresql/preprod-fss/creds/sokos-ske-krav-admin)

username=$(echo "$POSTGRES_USER" | awk -F 'username:' '{print $2}' | awk '{print $1}' | sed 's/]$//')
password=$(echo "$POSTGRES_USER" | awk -F 'password:' '{print $2}' | awk '{print $1}' | sed 's/]$//')
rm -f defaults.properties
echo "$envValue" > defaults.properties

echo "POSTGRES_USERNAME=$username" >> defaults.properties
echo "POSTGRES_PASSWORD=$password" >> defaults.properties



rm -f privKey
echo "$PRIVATE_KEY" > privKey

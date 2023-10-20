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
PRIVATE_KEY=$(vault read -field=privateKey kv/preprod/fss/sokos-ske-krav/okonomi/sftp)
HOST_KEY=$(vault read -field=localDevHostKey kv/preprod/fss/sokos-ske-krav/okonomi/sftp)
# Get AZURE system variables
envValue=$(kubectl exec -it $(kubectl get pods | grep sokos-ske-krav | cut -f1 -d' ') -c sokos-ske-krav -- env | egrep "^MASKINPORTEN|^SKE_REST_URL|^SKE_SFTP_USERNAME|SKE_SFTP_PASSWORD" )

# Set AZURE as local environment variables
rm -f defaults.properties
echo "$envValue" > defaults.properties
echo "POSTGRES_USERNAME=" >> defaults.properties
echo "POSTGRES_PASSWORD=" >> defaults.properties
echo "MASKINPORTEN, SKE_REST_URL, FTP stored as defaults.properties"
rm -f privKey
echo "$PRIVATE_KEY" > privKey
rm -f hostKey
echo "$HOST_KEY" > hostKey
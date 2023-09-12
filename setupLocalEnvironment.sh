#!/bin/bash

# Ensure user is authenicated, and run login if not.
gcloud auth print-identity-token &> /dev/null
if [ $? -gt 0 ]; then
    gcloud auth login
fi
kubectl config use-context dev-fss
kubectl config set-context --current --namespace=okonomi

# Get AZURE system variables
envValue=$(kubectl exec -it $(kubectl get pods | grep sokos-ske-krav | cut -f1 -d' ') -c sokos-ske-krav -- env | egrep "^MASKINPORTEN|^ske_REST_URL|^FTP")

# Set AZURE as local environment variables
rm -f defaults.properties
echo "$envValue" > defaults.properties
echo "MASKINPORTEN, ske_REST_URL, FTP stored as defaults.properties"
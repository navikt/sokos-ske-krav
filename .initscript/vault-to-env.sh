#!/bin/sh

if test -f '/var/run/secrets/nais.io/database-user/username'; then
    export POSTGRES_USERNAME=$(cat /var/run/secrets/nais.io/database-user/username)
    echo '- exporting POSTGRES_USERNAME'
fi

if test -f '/var/run/secrets/nais.io/database-user/password'; then
    export POSTGRES_PASSWORD=$(cat /var/run/secrets/nais.io/database-user/password)
    echo '- exporting POSTGRES_PASSWORD'
fi
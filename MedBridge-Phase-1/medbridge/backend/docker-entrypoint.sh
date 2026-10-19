#!/bin/sh
set -eu
umask 077

CA_FILE=/etc/secrets/ca.pem
TRUST_STORE=/tmp/medbridge-cacerts

if [ ! -s "$CA_FILE" ]; then
    echo "ERROR: Missing or empty Aiven CA certificate at $CA_FILE" >&2
    exit 1
fi

cp "$JAVA_HOME/lib/security/cacerts" "$TRUST_STORE"
chmod 600 "$TRUST_STORE"

"$JAVA_HOME/bin/keytool" -importcert -noprompt -trustcacerts \
    -alias medbridge-aiven-ca \
    -file "$CA_FILE" \
    -keystore "$TRUST_STORE" \
    -storepass changeit

exec java \
    -Djavax.net.ssl.trustStore="$TRUST_STORE" \
    -Djavax.net.ssl.trustStorePassword=changeit \
    -jar /app/app.jar

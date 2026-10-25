#!/bin/sh
set -eu
umask 077

CA_FILE=/etc/secrets/ca.pem
TRUST_STORE=/tmp/medbridge-cacerts

if [ "${SPRING_PROFILES_ACTIVE:-}" = review ] && [ "${REVIEW_TRANSPORT:-hosted-tls}" = hosted-tls ]; then
    if [ "${REVIEW_TLS_TRUST:-}" != system ]; then
        echo 'ERROR: TiDB review requires explicitly approved system TLS trust.' >&2
        exit 1
    fi
    if [ -e "$CA_FILE" ]; then
        echo 'ERROR: Remove conflicting custom CA configuration only after operator approval.' >&2
        exit 1
    fi
    # Publicly trusted TiDB certificates use the Java 17 image trust roots.
    TRUST_STORE="$JAVA_HOME/lib/security/cacerts"
    if [ ! -s "$TRUST_STORE" ] || ! "$JAVA_HOME/bin/keytool" -list -keystore "$TRUST_STORE" -storepass changeit >/dev/null 2>&1; then
        echo 'ERROR: Missing or invalid JVM system trust store.' >&2
        exit 1
    fi
else
    # Preserve legacy Aiven startup for non-review deployments; no cloud change.
    if [ ! -s "$CA_FILE" ]; then
        echo 'ERROR: Missing or empty custom CA certificate.' >&2
        exit 1
    fi
    cp "$JAVA_HOME/lib/security/cacerts" "$TRUST_STORE"
    chmod 600 "$TRUST_STORE"
    if ! "$JAVA_HOME/bin/keytool" -importcert -noprompt -trustcacerts -alias medbridge-aiven-ca -file "$CA_FILE" -keystore "$TRUST_STORE" -storepass changeit >/dev/null 2>&1; then
        echo 'ERROR: Invalid custom CA certificate.' >&2
        exit 1
    fi
fi

exec java -Djavax.net.ssl.trustStore="$TRUST_STORE" -Djavax.net.ssl.trustStorePassword=changeit -jar /app/app.jar

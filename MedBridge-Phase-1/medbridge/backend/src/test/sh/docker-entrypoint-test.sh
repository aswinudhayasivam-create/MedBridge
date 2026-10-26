#!/bin/sh
# Run only in an isolated disposable Java 17 container; never on a host filesystem.
set -eu
test -f /.dockerenv || { echo 'Disposable container required' >&2; exit 1; }
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
entry=/work/docker-entrypoint.sh
mkdir -p "$work/bin" "$work/missing/lib/security" /etc/secrets
# The launch sentinel isolates startup trust checks from Spring and any DB connection.
printf '#!/bin/sh\necho JVM_LAUNCH_REACHED\n' > "$work/bin/java"
chmod +x "$work/bin/java"
export PATH="$work/bin:$PATH"
export SPRING_PROFILES_ACTIVE=review REVIEW_TRANSPORT=hosted-tls
export REVIEW_TLS_TRUST=system
expect_fail() {
 label=$1; expected=$2
 if sh "$entry" > "$work/output" 2>&1; then echo "FAIL: $label unexpectedly started"; exit 1; fi
 grep -F "$expected" "$work/output" >/dev/null
 if grep -F JVM_LAUNCH_REACHED "$work/output" >/dev/null; then echo 'Guard allowed JVM startup'; exit 1; fi
 echo "PASS: $label"
}
saved_home=$JAVA_HOME
REVIEW_TLS_TRUST= expect_fail 'missing explicit trust selection' 'requires explicitly approved'
JAVA_HOME="$work/missing" expect_fail 'missing trust roots' 'Missing or invalid JVM'
printf 'invalid truststore' > "$work/missing/lib/security/cacerts"
mkdir -p "$work/missing/bin"
ln -s "$saved_home/bin/keytool" "$work/missing/bin/keytool"
JAVA_HOME="$work/missing" expect_fail 'invalid truststore' 'Missing or invalid JVM'
printf 'invalid certificate' > /etc/secrets/ca.pem
expect_fail 'conflicting custom CA in TiDB mode' 'conflicting custom CA'
SPRING_PROFILES_ACTIVE= REVIEW_TRANSPORT= expect_fail 'invalid legacy custom certificate' 'Invalid custom CA'
rm /etc/secrets/ca.pem
SPRING_PROFILES_ACTIVE= REVIEW_TRANSPORT= expect_fail 'missing legacy custom certificate' 'Missing or empty custom CA'
sh "$entry" > "$work/output" 2>&1
grep -F JVM_LAUNCH_REACHED "$work/output" >/dev/null
echo 'PASS: explicit TiDB system trust reaches JVM launch'
echo '7 startup checks passed; no network or database connection attempted.'

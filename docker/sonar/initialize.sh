#!/bin/sh

set -eu

SONAR_URL="${SONAR_URL:-http://sonarqube:9000}"
OUTPUT_FILE="${OUTPUT_FILE:-/output/sonar.env}"

if [ -s "$OUTPUT_FILE" ]; then
    echo "SonarQube credentials already initialized in $OUTPUT_FILE"
    exit 0
fi

echo "Waiting for SonarQube at $SONAR_URL..."
while ! curl --fail --silent "$SONAR_URL/api/system/status" | grep -q '"status":"UP"'; do
    sleep 3
done

admin_auth="${SONAR_ADMIN_LOGIN}:${SONAR_ADMIN_PASSWORD}"
analysis_login="$SONAR_ANALYSIS_LOGIN"

users_response="$(curl --fail --silent --user "$admin_auth" --get \
    --data-urlencode "q=$analysis_login" \
    "$SONAR_URL/api/users/search")"

if ! printf '%s' "$users_response" | grep -Fq "\"login\":\"$analysis_login\""; then
    echo "Creating SonarQube analysis user $analysis_login..."
    curl --fail --silent --show-error --user "$admin_auth" --request POST \
        --data-urlencode "login=$analysis_login" \
        --data-urlencode "name=$SONAR_ANALYSIS_NAME" \
        --data-urlencode "password=$SONAR_ANALYSIS_PASSWORD" \
        "$SONAR_URL/api/users/create" >/dev/null
fi

echo "Granting analysis permissions to $analysis_login..."
for permission in provisioning scan; do
    curl --fail --silent --show-error --user "$admin_auth" --request POST \
        --data-urlencode "login=$analysis_login" \
        --data-urlencode "permission=$permission" \
        "$SONAR_URL/api/permissions/add_user" >/dev/null
done

echo "Generating SonarQube token..."
token_response="$(curl --fail --silent --show-error --user "$admin_auth" --request POST \
    --data-urlencode "login=$analysis_login" \
    --data-urlencode "name=$SONAR_TOKEN_NAME" \
    --data-urlencode "type=USER_TOKEN" \
    "$SONAR_URL/api/user_tokens/generate")"
sonar_token="$(printf '%s' "$token_response" | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')"

if [ -z "$sonar_token" ]; then
    echo "SonarQube did not return a token" >&2
    exit 1
fi

temporary_output="${OUTPUT_FILE}.tmp"
umask 077
{
    printf 'SONAR_URL=%s\n' "http://localhost:9000"
    printf 'SONAR_LOGIN=%s\n' "$analysis_login"
    printf 'SONAR_PASSWORD=%s\n' "$SONAR_ANALYSIS_PASSWORD"
    printf 'SONAR_TOKEN=%s\n' "$sonar_token"
} >"$temporary_output"
mv "$temporary_output" "$OUTPUT_FILE"

echo "SonarQube initialized. Credentials and token written to $OUTPUT_FILE"

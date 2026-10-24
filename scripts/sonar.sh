#!/bin/sh
set -eu

repo_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)

usage() {
    cat <<EOF
Usage: $0 {analyze|mcp}
  analyze  Run the Maven JaCoCo and SonarQube analysis
  mcp      Start the SonarQube MCP Docker server
  -h, --help
           Show this help message
EOF
}

load_credentials() {
    credentials_spec=${SONAR_CREDENTIALS_FILE:-.sonar/sonar.env}
    case "$credentials_spec" in
        /*) credentials_path="$credentials_spec" ;;
        *) credentials_path="$repo_root/$credentials_spec" ;;
    esac

    if [ ! -r "$credentials_path" ]; then
        echo "SonarQube credentials not found: $credentials_path" >&2
        exit 1
    fi

    set -a
    . "$credentials_path"
    set +a

    : "${SONAR_TOKEN:?SONAR_TOKEN is missing from $credentials_path}"
}

analyze() {
    compose_file="$repo_root/docker-compose.sonar.yml"
    if [ ! -r "$compose_file" ]; then
        echo "SonarQube compose file not found: $compose_file" >&2
        exit 1
    fi

    echo "Starting local SonarQube..."
    docker compose -f "$compose_file" up -d sonarqube

    echo "Waiting for SonarQube to become ready..."
    for attempt in $(seq 1 60); do
        sq_state=$(curl -fsS http://localhost:9000/api/system/status 2>/dev/null || true)
        case "$sq_state" in
            *'"status":"UP"'*) break ;;
            *)
                if [ "$attempt" -eq 60 ]; then
                    echo "SonarQube did not become ready within 5 minutes." >&2
                    exit 1
                fi
                sleep 5
                ;;
        esac
    done

    docker compose -f "$compose_file" run --rm sonar-init
    load_credentials
    cd "$repo_root"
    exec ./mvnw --batch-mode --no-transfer-progress \
        -Pjacoco,sonar clean verify sonar:sonar
}

start_mcp() {
    load_credentials
    export SONARQUBE_TOKEN="$SONAR_TOKEN"
    export SONARQUBE_URL=${SONARQUBE_URL:-http://host.docker.internal:9000}

    exec docker run --init --pull=always -i --rm \
        -e SONARQUBE_TOKEN \
        -e SONARQUBE_URL \
        sonarsource/sonarqube-mcp
}

case "${1:-}" in
    analyze) analyze ;;
    mcp) start_mcp ;;
    -h|--help|help) usage; exit 0 ;;
    *) usage >&2; exit 2 ;;
esac

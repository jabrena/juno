#!/bin/bash
# Provisions the JDK this build requires (maven.compiler.release=25) in Claude Code on the web
# sessions, whose base image only ships JDK 21. Installs the exact build .sdkmanrc and CI pin:
# GraalVM CE 25.2.4 ("25 Innovation 2", JDK 25.0.4), then warms the Maven dependency cache.
set -euo pipefail

if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

JDK_DIR=/opt/graalvm-ce-25.2.4
JDK_URL=https://github.com/graalvm/graalvm-ce-builds/releases/download/graal-25.2.4/graalvm-community-jdk-25i2-25.0.4_linux-x64_bin.tar.gz

if [ ! -x "$JDK_DIR/bin/java" ]; then
  tmp=$(mktemp -d)
  curl -fsSL --retry 4 -o "$tmp/graalvm.tar.gz" "$JDK_URL"
  mkdir -p "$JDK_DIR"
  tar -xzf "$tmp/graalvm.tar.gz" -C "$JDK_DIR" --strip-components=1
  rm -rf "$tmp"
fi

export JAVA_HOME="$JDK_DIR"
export PATH="$JAVA_HOME/bin:$PATH"
if [ -n "${CLAUDE_ENV_FILE:-}" ]; then
  echo "export JAVA_HOME=\"$JDK_DIR\"" >> "$CLAUDE_ENV_FILE"
  echo "export PATH=\"$JDK_DIR/bin:\$PATH\"" >> "$CLAUDE_ENV_FILE"
fi

# Resolve plugins/dependencies for the core modules so offline-ish test runs are fast.
# juno-site (Quarkus/Roq) is left out: it is only needed for -Psite docs regeneration.
cd "$CLAUDE_PROJECT_DIR"
./mvnw --batch-mode --no-transfer-progress -q -pl juno,juno-maven-plugin,juno-examples -am \
  -DskipTests install >/dev/null

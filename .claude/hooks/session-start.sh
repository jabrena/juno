#!/bin/bash
# Provisions Claude Code cloud sessions: the JDK 27 this build requires (maven.compiler.release=27)
# plus the ARM, QEMU and media tools (see install-toolchain.sh), then warms the Maven cache.
set -euo pipefail

if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

"$CLAUDE_PROJECT_DIR/.claude/hooks/install-toolchain.sh"

JDK_DIR=/opt/jdk-27
export JAVA_HOME="$JDK_DIR"
export PATH="$JAVA_HOME/bin:$PATH"
if [ -n "${CLAUDE_ENV_FILE:-}" ]; then
  echo "export JAVA_HOME=\"$JDK_DIR\"" >> "$CLAUDE_ENV_FILE"
  echo "export PATH=\"$JDK_DIR/bin:\$PATH\"" >> "$CLAUDE_ENV_FILE"
fi

# Resolve plugins/dependencies for the core modules so offline-ish test runs are fast.
# juno-site (Quarkus/Roq) is left out: it is only needed for -Psite docs regeneration.
cd "$CLAUDE_PROJECT_DIR"
./mvnw --batch-mode --no-transfer-progress -q -pl juno-api,juno-compiler,juno-maven-plugin,juno-examples -am \
  -DskipTests install >/dev/null

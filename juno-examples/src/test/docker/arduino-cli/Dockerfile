# arduino-cli with the UNO R4 core, for ArduinoCliCompileTest (./mvnw -f juno-examples/pom.xml -Parduino-cli test).
# The test builds this image on first use and Docker caches it afterwards.
FROM debian:bookworm-slim

RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl \
    && rm -rf /var/lib/apt/lists/*

ARG ARDUINO_CLI_VERSION=1.3.1
RUN case "$(dpkg --print-architecture)" in \
        amd64) asset=Linux_64bit ;; \
        arm64) asset=Linux_ARM64 ;; \
        *) echo "unsupported architecture" >&2; exit 1 ;; \
    esac \
    && curl -fsSL "https://github.com/arduino/arduino-cli/releases/download/v${ARDUINO_CLI_VERSION}/arduino-cli_${ARDUINO_CLI_VERSION}_${asset}.tar.gz" \
        | tar -xz -C /usr/local/bin arduino-cli

# The same platform juno:install-deps installs; pin it with --build-arg CORE=arduino:renesas_uno@<version>.
ARG CORE=arduino:renesas_uno
RUN arduino-cli core update-index && arduino-cli core install "${CORE}"

WORKDIR /sketches
CMD ["sleep", "infinity"]

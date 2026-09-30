# arduino-cli with the supported cores, for ArduinoCliCompileIT
# (./mvnw -f juno-examples/pom.xml -Parduino-cli verify).
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

# The same platforms juno:install-deps installs, covering both @Board targets (UNO R4 WiFi and UNO Q);
# pin either with --build-arg CORE=arduino:renesas_uno@<version> / ZEPHYR_CORE=arduino:zephyr@<version>.
ARG CORE=arduino:renesas_uno
ARG ZEPHYR_CORE=arduino:zephyr
RUN arduino-cli core update-index && arduino-cli core install "${CORE}" "${ZEPHYR_CORE}"

# Optional library some TFT programs (e.g. RatonLocoTFT) need; not bundled with either core above,
# see juno-site/src/main/resources/content/hid.md.
RUN arduino-cli lib install Mouse

# Required for every UNO Q sketch, not just ones using Wifi/Udp: the zephyr core's Arduino.h
# #errors out at compile time without it ("install the Arduino_RouterBridge library ... for
# proper Serial support on this board"). Not bundled with the arduino:zephyr core above.
RUN arduino-cli lib install Arduino_RouterBridge

WORKDIR /sketches
CMD ["sleep", "infinity"]

# arduino-cli with the supported cores, for ArduinoCliCompileIT (API and shield fixtures)
# (./mvnw -pl juno-compiler -am -Parduino-cli verify).
# The test builds this image on first use and Docker caches it afterwards.
FROM debian:bookworm-slim

# python3 and pyserial: the arduino:esp32 core's build recipes run Python scripts (partition table,
# pre-build hooks) and esptool.py, which imports serial.
RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl python3 python3-serial \
    && rm -rf /var/lib/apt/lists/*

ARG ARDUINO_CLI_VERSION=1.3.1
RUN case "$(dpkg --print-architecture)" in \
        amd64) asset=Linux_64bit ;; \
        arm64) asset=Linux_ARM64 ;; \
        *) echo "unsupported architecture" >&2; exit 1 ;; \
    esac \
    && curl -fsSL "https://github.com/arduino/arduino-cli/releases/download/v${ARDUINO_CLI_VERSION}/arduino-cli_${ARDUINO_CLI_VERSION}_${asset}.tar.gz" \
        | tar -xz -C /usr/local/bin arduino-cli

# The platforms of every @Board target (UNO R3, UNO R4 WiFi, UNO Q, UNO WiFi Rev2 and Nano ESP32); pin any with
# --build-arg CORE=arduino:renesas_uno@<version> / ZEPHYR_CORE=arduino:zephyr@<version> /
# MEGAAVR_CORE=arduino:megaavr@<version> / AVR_CORE=arduino:avr@<version> / ESP32_CORE=arduino:esp32@<version>.
ARG CORE=arduino:renesas_uno
ARG ZEPHYR_CORE=arduino:zephyr
ARG MEGAAVR_CORE=arduino:megaavr
ARG AVR_CORE=arduino:avr
ARG ESP32_CORE=arduino:esp32
RUN arduino-cli core update-index \
    && arduino-cli core install "${CORE}" "${ZEPHYR_CORE}" "${MEGAAVR_CORE}" "${AVR_CORE}" "${ESP32_CORE}"

# Optional library some TFT programs (e.g. RatonLocoTFT) need; not bundled with either core above,
# see juno-site/src/main/resources/content/hid.md.
RUN arduino-cli lib install Mouse

# Required for every UNO Q sketch, not just ones using Wifi/Udp: the zephyr core's Arduino.h
# #errors out at compile time without it ("install the Arduino_RouterBridge library ... for
# proper Serial support on this board"). Not bundled with the arduino:zephyr core above.
RUN arduino-cli lib install Arduino_RouterBridge

# Libraries the generated shims include for the Servo and SdCard APIs.
RUN arduino-cli lib install Servo SdFat

# The UNO WiFi Rev2 reaches its NINA-W102 Wi-Fi module (Wi-Fi, UDP, HTTP, HTTPS, TLS mail) through WiFiNINA,
# which the megaavr core does not bundle.
RUN arduino-cli lib install WiFiNINA

# The UNO R3 reaches the retired shield's HDG104 module through the legacy WiFi library.
RUN arduino-cli lib install WiFi@1.2.7

WORKDIR /sketches
CMD ["sleep", "infinity"]

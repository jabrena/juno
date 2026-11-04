# Runs Juno-generated Xtensa code (Nano ESP32, ESP32-S3) under Espressif's QEMU for XtensaRunIT
# (./mvnw -pl juno-compiler -am -Pxtensa verify). The image carries the pinned arduino:esp32 core, which builds the
# sketch, and the pinned Espressif QEMU build, which boots the merged flash image: see run.sh, merge_flash.py and
# variant.cpp.
# XtensaRunIT retains the named image so Docker can reuse this toolchain layer across Maven JVMs.
FROM debian:bookworm-slim

# python3 and pyserial: the arduino:esp32 core's build recipes run esptool.py, which imports serial.
RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl python3 python3-serial xz-utils libglib2.0-0 \
        libpixman-1-0 libgcrypt20 libslirp0 libsdl2-2.0-0 \
    && rm -rf /var/lib/apt/lists/*

# Espressif's esp-develop QEMU (ESP32-S3 machine), pinned by release tag and the SHA-256 of the Linux asset.
ARG QEMU_RELEASE=esp-develop-9.2.2-20260417
ARG QEMU_VERSION=9.2.2_20260417
RUN case "$(dpkg --print-architecture)" in \
        amd64) asset=x86_64-linux-gnu; sha256=0eecb2a34a5586c0e59110f77b9343b7b336e82fdb0e1a30e1dc1bab8a547e35 ;; \
        arm64) asset=aarch64-linux-gnu; sha256=00de5985094c14e47d1b38464a006b8ed64fd0fa7a289c56da24f3a329f65339 ;; \
        *) echo "unsupported architecture" >&2; exit 1 ;; \
    esac \
    && curl -fsSL -o /tmp/qemu.tar.xz \
        "https://github.com/espressif/qemu/releases/download/${QEMU_RELEASE}/qemu-xtensa-softmmu-esp_develop_${QEMU_VERSION}-${asset}.tar.xz" \
    && echo "${sha256}  /tmp/qemu.tar.xz" | sha256sum -c - \
    && tar -xJf /tmp/qemu.tar.xz -C /opt \
    && rm /tmp/qemu.tar.xz
ENV PATH=/opt/qemu/bin:$PATH

# The arduino:esp32 core that builds the Nano ESP32 sketch, pinned to the version the Board enum targets.
ARG ARDUINO_CLI_VERSION=1.3.1
ARG ESP32_CORE=arduino:esp32@2.0.18-arduino.5
RUN case "$(dpkg --print-architecture)" in \
        amd64) asset=Linux_64bit ;; \
        arm64) asset=Linux_ARM64 ;; \
        *) echo "unsupported architecture" >&2; exit 1 ;; \
    esac \
    && curl -fsSL "https://github.com/arduino/arduino-cli/releases/download/v${ARDUINO_CLI_VERSION}/arduino-cli_${ARDUINO_CLI_VERSION}_${asset}.tar.gz" \
        | tar -xz -C /usr/local/bin arduino-cli \
    && arduino-cli core update-index \
    && arduino-cli core install "${ESP32_CORE}"

# The emulator variant: the core's Nano ESP32 variant with variant.cpp replaced (see that file).
COPY variant.cpp /harness/
RUN cp -r /root/.arduino15/packages/arduino/hardware/esp32/*/variants/arduino_nano_nora /harness/variant \
    && cp /harness/variant.cpp /harness/variant/variant.cpp

COPY run.sh merge_flash.py /harness/
RUN chmod +x /harness/run.sh

WORKDIR /work
CMD ["sleep", "infinity"]

# Runs Juno-generated Thumb-2 under QEMU (Cortex-M4, mps2-an386), for QemuRunIT
# (./mvnw -f juno-examples/pom.xml -Pqemu verify). The image carries a tiny bare-metal harness instead of
# the Arduino core: see run.sh, startup.cpp and Arduino.h. Docker caches the image after the first build.
FROM debian:bookworm-slim

RUN apt-get update \
    && apt-get install -y --no-install-recommends qemu-system-arm gcc-arm-none-eabi \
        libnewlib-arm-none-eabi libstdc++-arm-none-eabi-newlib \
    && rm -rf /var/lib/apt/lists/*

COPY Arduino.h Arduino_LED_Matrix.h startup.cpp link.ld run.sh /harness/
RUN chmod +x /harness/run.sh

WORKDIR /work
CMD ["sleep", "infinity"]

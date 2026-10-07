# Runs Juno-generated Thumb-2 under QEMU (Cortex-M4, mps2-an386), for QemuRunIT
# (./mvnw -f juno-examples/pom.xml -Pqemu verify). The image carries a tiny bare-metal harness instead of
# the Arduino core: see run.sh, startup.cpp and Arduino.h. QemuRunIT retains the named image so Docker can
# reuse this toolchain layer across Maven JVMs; harness-only changes rebuild only the small COPY layer below.
FROM debian:bookworm-slim

RUN apt-get update \
    && apt-get install -y --no-install-recommends qemu-system-arm gcc-arm-none-eabi \
        libnewlib-arm-none-eabi \
    && rm -rf /var/lib/apt/lists/*

COPY Arduino.h Arduino_LED_Matrix.h startup.cpp link.ld run.sh /harness/
COPY zephyr/kernel.h /harness/zephyr/kernel.h
RUN chmod +x /harness/run.sh

WORKDIR /work
CMD ["sleep", "infinity"]

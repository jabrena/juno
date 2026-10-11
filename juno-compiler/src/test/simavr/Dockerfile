# Runs Juno-generated AVR code under simavr (ATmega2560, the same instruction set as the UNO WiFi Rev2's ATmega4809)
# for SimavrRunIT (./mvnw -pl juno-compiler -am -Psimavr verify). The image carries a tiny bare-metal harness instead
# of the megaavr core: see run.sh, startup.cpp and the stand-in headers. SimavrRunIT retains the named image so Docker
# can reuse the toolchain layer across Maven JVMs; harness-only changes rebuild only the small layers below it.
FROM debian:bookworm-slim

RUN apt-get update \
    && apt-get install -y --no-install-recommends gcc gcc-avr avr-libc binutils-avr libc6-dev libsimavr-dev \
        libelf-dev \
    && rm -rf /var/lib/apt/lists/*

COPY runner.c /harness/
RUN gcc -O2 /harness/runner.c -o /harness/simavr-runner -I/usr/include/simavr -lsimavr -lelf
COPY Arduino.h WiFiNINA.h WiFiUdp.h SPI.h SdFat.h startup.cpp run.sh /harness/
RUN chmod +x /harness/run.sh

WORKDIR /work
CMD ["sleep", "infinity"]

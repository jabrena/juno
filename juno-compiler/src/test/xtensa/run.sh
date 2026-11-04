#!/bin/sh
# Usage: run.sh <sketch directory>
# The directory is an Arduino sketch named after its program: <Name>.ino, program.S and shim.cpp. arduino-cli builds
# it for the Nano ESP32 with USB CDC disabled, so Serial is UART0; the merged flash image then boots under Espressif's
# QEMU (ESP32-S3). Stdout is the UART capture: boot log, [juno-begin], the program's output and [juno-exit], at
# which QEMU stops.
set -eu
sketch="$1"
name="$(basename "$sketch")"
# build.variant.path swaps in the emulator variant (/harness/variant), which skips the board's double-tap recovery.
arduino-cli compile --fqbn arduino:esp32:nano_nora --build-property build.cdc_on_boot=0 \
    --build-property build.variant.path=/harness/variant --build-path "$sketch-build" "$sketch" >&2
python3 /harness/merge_flash.py "$sketch-build" "$name" "$sketch/flash.bin"
# The Nano ESP32's core is built for its 8 MiB octal PSRAM, which the boot code probes, so QEMU emulates one (its
# default PSRAM is quad SPI). The timer-group watchdog is disabled: the harness never feeds it.
# The guest never powers off, so the awk filter ends the run at the completion marker and QEMU stops on SIGPIPE.
timeout 60 qemu-system-xtensa -nographic -machine esp32s3 -m 8M \
    -global driver=ssi_psram,property=is_octal,value=true \
    -global driver=timer.esp32s3.timg,property=wdt_disable,value=true \
    -drive file="$sketch/flash.bin",if=mtd,format=raw 2>&1 \
    | awk '{ print; fflush() } /\[juno-exit\]/ { exit }'

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
/harness/merge_flash.sh "$sketch-build" "$name" "$sketch/flash.bin"
# The Nano ESP32's core is built for its 8 MiB octal PSRAM, which the boot code probes, so QEMU emulates one (its
# default PSRAM is quad SPI). The timer-group watchdog is disabled: the harness never feeds it.
# The guest never powers off. Keep the output stream live, but terminate QEMU explicitly as soon as the completion
# marker arrives; waiting for a later write to trigger SIGPIPE can otherwise leave QEMU alive until the watchdog fires.
tmpdir="$(mktemp -d)"
fifo="$tmpdir/qemu-output"
mkfifo "$fifo"
qemu_pid=
watchdog_pid=

cleanup() {
    if [ -n "${watchdog_pid:-}" ]; then
        kill "$watchdog_pid" 2>/dev/null || true
        wait "$watchdog_pid" 2>/dev/null || true
    fi
    if [ -n "${qemu_pid:-}" ]; then
        kill "$qemu_pid" 2>/dev/null || true
        wait "$qemu_pid" 2>/dev/null || true
    fi
    rm -rf "$tmpdir"
}
trap cleanup EXIT INT TERM

qemu-system-xtensa -nographic -machine esp32s3 -m 8M \
    -global driver=ssi_psram,property=is_octal,value=true \
    -global driver=timer.esp32s3.timg,property=wdt_disable,value=true \
    -drive file="$sketch/flash.bin",if=mtd,format=raw >"$fifo" 2>&1 &
qemu_pid=$!

# Keep a real hang guard without making normal marker-terminated runs wait for it.
(
    sleep 60
    kill "$qemu_pid" 2>/dev/null || true
) &
watchdog_pid=$!

while IFS= read -r line; do
    printf '%s\n' "$line"
    case "$line" in
        *'[juno-exit]'*)
            kill "$qemu_pid" 2>/dev/null || true
            wait "$qemu_pid" 2>/dev/null || true
            qemu_pid=
            kill "$watchdog_pid" 2>/dev/null || true
            wait "$watchdog_pid" 2>/dev/null || true
            watchdog_pid=
            exit 0
            ;;
    esac
done <"$fifo"

if wait "$qemu_pid"; then
    qemu_status=0
else
    qemu_status=$?
fi
qemu_pid=
kill "$watchdog_pid" 2>/dev/null || true
wait "$watchdog_pid" 2>/dev/null || true
watchdog_pid=
exit "$qemu_status"

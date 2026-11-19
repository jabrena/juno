#!/bin/sh
# Usage: run.sh <sketch directory> [board id]
# The directory is an Arduino sketch named after its program: <Name>.ino, program.S and shim.cpp. arduino-cli builds
# it for the board with USB CDC disabled, so Serial is UART0; the merged flash image then boots under Espressif's QEMU.
# The board id selects the core, the QEMU machine and its memory settings: arduino-nano-esp32 (default), m5stack-cardputer
# (both ESP32-S3) or ai-thinker-esp32-cam (classic ESP32, LX6). Stdout is the UART capture: boot log, [juno-begin], the
# program's output and [juno-exit], at which QEMU stops.
set -eu
# Milliseconds since the epoch, for the "run.sh timing:" lines XtensaRunIT logs.
now_ms() { date +%s%3N; }
started_ms=$(now_ms)
sketch="$1"
board="${2:-arduino-nano-esp32}"
name="$(basename "$sketch")"
case "$board" in
    arduino-nano-esp32)
        fqbn="arduino:esp32:nano_nora"
        # build.variant.path swaps in the emulator variant (/harness/variant), which skips the board's double-tap recovery.
        variant_option="--build-property build.variant.path=/harness/variant"
        machine=esp32s3
        memory="-m 8M"
        # The Nano ESP32's core is built for its 8 MiB octal PSRAM, which the boot code probes.
        psram_options="-global driver=ssi_psram,property=is_octal,value=true"
        timer_driver=timer.esp32s3.timg
        flash_mib=16
        bootloader_offset=0x0
        emulator_shim=""
        ;;
    m5stack-cardputer)
        # DIO flash: the core's default QIO boot fails to set the flash's QIE bit under QEMU.
        # USBMode=hwcdc: the default TinyUSB stack drives the USB-OTG peripheral, which QEMU does not model; with it the
        # app hung silently right after the bootloader handed over.
        fqbn="m5stack:esp32:m5stack_cardputer:USBMode=hwcdc,CDCOnBoot=cdc,UploadMode=cdc,FlashMode=dio"
        variant_option=""
        machine=esp32s3
        memory="-m 8M"
        # The Cardputer has no PSRAM, so the PSRAM model stays off.
        psram_options=""
        timer_driver=timer.esp32s3.timg
        flash_mib=8
        bootloader_offset=0x0
        # ESP-IDF 5 calibrates the ADC at start-up and waits for an event QEMU never raises: see emulator_adc.cpp.
        emulator_shim=emulator_adc.cpp
        emulator_link_flags="-Wl,--wrap=adc_hal_self_calibration"
        ;;
    ai-thinker-esp32-cam)
        # DIO flash: the core's default QIO boot fails to set the flash's QIE bit under QEMU, which the ESP-IDF 5 app
        # then treats as a fatal flash-init error (startup_funcs.c:118).
        fqbn="esp32:esp32:esp32cam:FlashMode=dio"
        variant_option=""
        machine=esp32
        memory=""
        psram_options=""
        timer_driver=timer.esp32.timg
        flash_mib=4
        # The classic ESP32's boot ROM loads the second-stage bootloader from 0x1000, not 0x0.
        bootloader_offset=0x1000
        emulator_shim=""
        ;;
    *)
        echo "run.sh: unsupported board '$board'" >&2
        exit 2
        ;;
esac
# build.variant.path swaps in the emulator variant (/harness/variant), which skips the board's double-tap recovery.
if [ -n "$emulator_shim" ]; then
    cp "/harness/$emulator_shim" "$sketch/"
fi
# variant_option is unquoted on purpose: empty, it adds no argument; set, it splits into the property and its value.
if [ -n "$emulator_shim" ]; then
    # shellcheck disable=SC2086
    arduino-cli compile --fqbn "$fqbn" --build-property build.cdc_on_boot=0 $variant_option \
        --build-property "compiler.c.elf.extra_flags=$emulator_link_flags" \
        --build-path "$sketch-build" "$sketch" >&2
else
    # shellcheck disable=SC2086
    arduino-cli compile --fqbn "$fqbn" --build-property build.cdc_on_boot=0 $variant_option \
        --build-path "$sketch-build" "$sketch" >&2
fi
echo "run.sh timing: build $(( $(now_ms) - started_ms )) ms" >&2
/harness/merge_flash.sh "$sketch-build" "$name" "$sketch/flash.bin" "$flash_mib" "$bootloader_offset"
# The Nano ESP32's core is built for its 8 MiB octal PSRAM, which the boot code probes, so QEMU emulates one (its
# default PSRAM is quad SPI). The timer-group watchdog is disabled: the harness never feeds it.
# The guest never powers off. Keep the output stream live, but terminate QEMU explicitly as soon as the completion
# marker arrives; waiting for a later write to trigger SIGPIPE can otherwise leave QEMU alive until the watchdog fires.
tmpdir="$(mktemp -d)"
fifo="$tmpdir/qemu-output"
mkfifo "$fifo"
qemu_pid=
watchdog_pid=

# QEMU exposes the MTD drive as writable and the guest updates flash while booting. Keep the freshly merged image as
# the immutable baseline and give every emulator process its own copy, so a host-side crash cannot poison a retry.
base_flash="$sketch/flash.bin"
# A QEMU core can be hundreds of MiB and provides no useful conformance evidence; stdout/stderr and the exit status do.
ulimit -c 0

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

# Decodes a panic backtrace ("Backtrace: 0xPC:0xSP ...") against the build's ELF, so a boot failure names its source.
decode_backtrace() {
    elf="$sketch-build/$name.ino.elf"
    addr2line="$(find /root/.arduino15/packages -name "xtensa-${machine}-elf-addr2line" -type f 2>/dev/null | head -1)"
    [ -n "$addr2line" ] || addr2line="$(find /root/.arduino15/packages -name 'xtensa-esp-elf-addr2line' -type f 2>/dev/null | head -1)"
    if [ -z "$addr2line" ] || [ ! -f "$elf" ]; then
        echo "run.sh: cannot decode the backtrace (addr2line or $elf missing)" >&2
        return
    fi
    addresses="$(printf '%s\n' "$1" | tr ' ' '\n' | grep -o '^0x[0-9a-fA-F]*' || true)"
    echo "run.sh: decoded backtrace:" >&2
    # shellcheck disable=SC2086
    "$addr2line" -pfiaC -e "$elf" $addresses >&2 || true
}


# Espressif's QEMU occasionally crashes while the guest boots (seen as exit 139 with the boot log and no [juno-begin]).
# Such a run never reached the program, so it is retried, at most twice; a crash after [juno-begin], a crash on every
# attempt, or a hang stopped by the watchdog still fails. The crash repeats on a retry with the same settings on the
# same runner, while other runners boot the same image: it follows the host, not the program. So a retry changes how
# QEMU runs the guest: the first attempt keeps QEMU's default, which on a multi-core host runs the two ESP32-S3 cores on
# their own host threads (multi-threaded TCG); a retry runs them in turn on one thread, which the program cannot tell
# apart, only slower.
max_attempts=3
attempt=1
while :; do
    begun=0
    attempt_flash="$tmpdir/flash-attempt-$attempt.bin"
    cp "$base_flash" "$attempt_flash"
    if [ "$attempt" -eq 1 ]; then
        tcg_threads=default
        accel_option=
    else
        tcg_threads=single
        accel_option="-accel tcg,thread=single"
    fi
    # accel_option is unquoted on purpose: empty, it adds no argument; set, it splits into the option and its value.
    # shellcheck disable=SC2086
    # memory, psram_options and accel_option are unquoted on purpose: each splits into its own arguments, or none.
    # shellcheck disable=SC2086
    qemu-system-xtensa -nographic -machine "$machine" $memory $accel_option \
        $psram_options \
        -global driver=$timer_driver,property=wdt_disable,value=true \
        -drive file="$attempt_flash",if=mtd,format=raw >"$fifo" 2>&1 &
    qemu_pid=$!
    qemu_started_ms=$(now_ms)

    # Keep a real hang guard without making normal marker-terminated runs wait for it. Its sleep outlives the kill of
    # the guard, so it must not hold the script's output open, or a caller reading to the end would wait for it.
    # A good boot reaches [juno-begin] in well under a second, so a boot that has not got there in 20 s is a silent
    # hang: the guard stops it then, and gives a running program the rest of the 60 s.
    rm -f "$tmpdir/begun"
    (
        sleep 20
        if [ ! -f "$tmpdir/begun" ]; then
            kill "$qemu_pid" 2>/dev/null || true
            exit 0
        fi
        sleep 40
        kill "$qemu_pid" 2>/dev/null || true
    ) </dev/null >/dev/null 2>&1 &
    watchdog_pid=$!

    while IFS= read -r line; do
        printf '%s\n' "$line"
        # A boot that fails before [juno-begin] never recovers: stop at once instead of waiting for the hang guard.
        if [ "$begun" -eq 0 ]; then
            case "$line" in
                *'invalid header'*|*'Backtrace:'*|*'Rebooting...'*|*'Guru Meditation'*)
                    kill "$qemu_pid" 2>/dev/null || true
                    wait "$qemu_pid" 2>/dev/null || true
                    qemu_pid=
                    kill "$watchdog_pid" 2>/dev/null || true
                    wait "$watchdog_pid" 2>/dev/null || true
                    watchdog_pid=
                    echo "run.sh: boot failed before [juno-begin] on $board: $line" >&2
                    case "$line" in
                        *'Backtrace:'*) decode_backtrace "$line" ;;
                    esac
                    exit 3
                    ;;
            esac
        fi
        case "$line" in
            *'[juno-begin]'*)
                begun=1
                : >"$tmpdir/begun"
                begun_ms=$(now_ms)
                echo "run.sh timing: boot to [juno-begin] $(( begun_ms - qemu_started_ms )) ms (attempt $attempt)" >&2
                ;;
            *'[juno-exit]'*)
                echo "run.sh timing: program to [juno-exit] $(( $(now_ms) - ${begun_ms:-$qemu_started_ms} )) ms" >&2
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

    # 134 SIGABRT, 135 SIGBUS, 136 SIGFPE, 139 SIGSEGV: QEMU itself crashed.
    case "$qemu_status" in
        134|135|136|139)
            if [ "$begun" -eq 0 ] && [ "$attempt" -lt "$max_attempts" ]; then
                echo "run.sh: QEMU crashed (exit $qemu_status, thread=$tcg_threads) before [juno-begin];" \
                    "retrying with single-threaded TCG" >&2
                attempt=$((attempt + 1))
                continue
            fi
            ;;
    esac
    exit "$qemu_status"
done

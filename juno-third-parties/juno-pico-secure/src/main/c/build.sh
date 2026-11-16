#!/usr/bin/env bash
# Builds juno-pico-secure.bin with the Arm toolchain the arduino-pico core installs, and refreshes the copy the module
# ships (src/main/resources). Run it after changing anything in this directory and commit both.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
module="$(cd "$here/../../.." && pwd)"
out="$module/target/pico-secure"
gcc_dir="$(ls -d "${ARDUINO15:-$HOME/Library/Arduino15}"/packages/rp2040/tools/pqt-gcc/*/bin 2>/dev/null | tail -1)"
if [ -z "$gcc_dir" ]; then
    echo "arduino-pico's pqt-gcc toolchain not found; install the rp2040:rp2040 core first" >&2
    exit 1
fi
cc="$gcc_dir/arm-none-eabi-gcc"
flags=(${JUNO_SECURE_FLAGS:-} -mcpu=cortex-m33 -mthumb -mfloat-abi=soft -mcmse -Os -ffreestanding -fno-builtin -nostdlib -Wall -Wextra -Werror)
mkdir -p "$out"
"$cc" "${flags[@]}" -c "$here/start.S" -o "$out/start.o"
"$cc" "${flags[@]}" -c "$here/secure.c" -o "$out/secure.o"
"$cc" "${flags[@]}" -T "$here/secure.ld" -Wl,--gc-sections -Wl,--no-warn-rwx-segments -Wl,-Map,"$out/juno-pico-secure.map" \
    "$out/start.o" "$out/secure.o" -lgcc -o "$out/juno-pico-secure.elf"
"$gcc_dir/arm-none-eabi-objcopy" -O binary "$out/juno-pico-secure.elf" "$out/juno-pico-secure.bin"
size=$(wc -c < "$out/juno-pico-secure.bin")
if [ "$size" -gt 12288 ]; then
    echo "juno-pico-secure.bin is $size bytes; it must fit the 12 KB slot below 0x10003000" >&2
    exit 1
fi
if [ -n "${JUNO_SECURE_FLAGS:-}" ]; then
    echo "juno-pico-secure.bin: $size bytes, built with $JUNO_SECURE_FLAGS, left in $out only"
    exit 0
fi
mkdir -p "$module/src/main/resources/pico-secure"
cp "$out/juno-pico-secure.bin" "$module/src/main/resources/pico-secure/juno-pico-secure.bin"
echo "juno-pico-secure.bin: $size bytes"

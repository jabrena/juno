#!/usr/bin/env bash
# Usage: merge_flash.sh <arduino-cli build directory> <sketch name> <output image>
# Merges an arduino-cli ESP32 build into one flash image, at the offsets the ESP32 boot ROM expects: the bootloader at
# 0x0, the partition table at 0x8000, and the application in the factory slot at 0x10000. The rest of the image is
# erased flash (0xFF), up to the 16 MiB the core's default partition table (partitions.csv) ends at, the Nano ESP32's
# flash size.
set -euo pipefail

build="$1"
name="$2"
output="$3"
flash_size=$((16 * 1024 * 1024))

head -c "$flash_size" /dev/zero | tr '\000' '\377' > "$output"

place() {
    local offset="$1" segment="$2"
    dd if="$segment" of="$output" bs=64K seek="$offset" oflag=seek_bytes conv=notrunc status=none
}

place $((0x0)) "$build/$name.ino.bootloader.bin"
place $((0x8000)) "$build/$name.ino.partitions.bin"
place $((0x10000)) "$build/$name.ino.bin"

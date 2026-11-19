#!/usr/bin/env bash
# Usage: merge_flash.sh <arduino-cli build directory> <sketch name> <output image> [flash size in MiB] [bootloader offset]
# Merges an arduino-cli ESP32 build into one flash image, at the offsets the boot ROM expects: the bootloader at
# 0x0 on the ESP32-S3 or 0x1000 on the classic ESP32, the partition table at 0x8000, and the application in the factory slot at 0x10000. The rest of the image is
# erased flash (0xFF), up to the board's flash size: 16 MiB for the Nano ESP32 (the default), 8 MiB for the Cardputer and
# 4 MiB for the ESP32-CAM.
set -euo pipefail

build="$1"
name="$2"
output="$3"
flash_size=$((${4:-16} * 1024 * 1024))
# The bootloader offset: 0x0 on the ESP32-S3 (the default), 0x1000 on the classic ESP32.
bootloader_offset=$((${5:-0x0}))

head -c "$flash_size" /dev/zero | tr '\000' '\377' > "$output"

place() {
    local offset="$1" segment="$2"
    dd if="$segment" of="$output" bs=64K seek="$offset" oflag=seek_bytes conv=notrunc status=none
}

place "$bootloader_offset" "$build/$name.ino.bootloader.bin"
place $((0x8000)) "$build/$name.ino.partitions.bin"
place $((0x10000)) "$build/$name.ino.bin"

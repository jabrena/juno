"""Merges an arduino-cli ESP32 build into one flash image, at the offsets the ESP32 boot ROM expects.

Usage: merge_flash.py <arduino-cli build directory> <sketch name> <output image>
"""

import pathlib
import sys

# The core's default partition table (partitions.csv) ends at 16 MiB, the Nano ESP32's flash.
FLASH_SIZE = 16 * 1024 * 1024
ERASED = 0xFF

# Bootloader at 0x0, partition table at 0x8000, and the application in the factory slot at 0x10000.
SEGMENTS = [
    (0x0, "{name}.ino.bootloader.bin"),
    (0x8000, "{name}.ino.partitions.bin"),
    (0x10000, "{name}.ino.bin"),
]


def main() -> None:
    build, name, output = pathlib.Path(sys.argv[1]), sys.argv[2], pathlib.Path(sys.argv[3])
    image = bytearray([ERASED]) * FLASH_SIZE
    for offset, pattern in SEGMENTS:
        data = (build / pattern.format(name=name)).read_bytes()
        image[offset:offset + len(data)] = data
    output.write_bytes(bytes(image))


if __name__ == "__main__":
    main()

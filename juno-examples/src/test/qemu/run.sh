#!/bin/sh
# Usage: run.sh <directory holding program.S and shim.cpp> <entry symbol>
# Builds the generated program the way the UNO R4 core does (Cortex-M4, thumb, hard-float shim) against the
# harness, runs it under QEMU, and leaves its semihosting output on stdout.
set -eu
cd "$1"
CPU="-mcpu=cortex-m4 -mthumb"
FLAGS="$CPU -mfloat-abi=hard -mfpu=fpv4-sp-d16 -Os -ffunction-sections -fdata-sections -fno-exceptions -fno-rtti -I/harness"
arm-none-eabi-gcc $CPU -c program.S -o program.o
arm-none-eabi-g++ $FLAGS -include Arduino.h -c shim.cpp -o shim.o
arm-none-eabi-g++ $FLAGS -DJUNO_ENTRY="$2" -c /harness/startup.cpp -o startup.o
arm-none-eabi-g++ $FLAGS -nostartfiles --specs=nosys.specs -Wl,--gc-sections -T /harness/link.ld \
    startup.o shim.o program.o -o program.elf -lm -lc -lgcc
timeout 30 qemu-system-arm -M mps2-an386 -cpu cortex-m4 -nographic \
    -semihosting-config enable=on,target=native -kernel program.elf 2>&1

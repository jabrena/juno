#!/bin/sh
# Usage: run.sh <directory holding program.S and shim.cpp> <entry symbol>
# Builds the generated program with the flags the megaavr core compiles a sketch with against the harness, runs it
# under simavr, and leaves its serial output on stdout.
set -eu
HARNESS=$(dirname "$0")
cd "$1"
MCU="-mmcu=atmega2560"
FLAGS="$MCU -Os -std=gnu++11 -fpermissive -fno-exceptions -fno-rtti -ffunction-sections -fdata-sections -fno-threadsafe-statics -I$HARNESS"
avr-gcc $MCU -c program.S -o program.o
avr-g++ $FLAGS -c shim.cpp -o shim.o
avr-g++ $FLAGS -DJUNO_ENTRY="$2" -c "$HARNESS/startup.cpp" -o startup.o
avr-gcc $MCU -Wl,--gc-sections startup.o shim.o program.o -o program.elf
"$HARNESS/simavr-runner" program.elf

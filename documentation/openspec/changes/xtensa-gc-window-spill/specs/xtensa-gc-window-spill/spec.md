## ADDED Requirements

### Requirement: The collector spills register windows before scanning on Xtensa

The generated runtime SHALL write every live register window of the collecting thread to memory before the
conservative stack scan begins, on every core runtime that targets the Xtensa windowed ABI.

#### Scenario: Nano ESP32 shim contains the spill

- **WHEN** a program that allocates is compiled for the Nano ESP32 or the Cardputer
- **THEN** `juno_gc_mark` calls `xthal_window_spill()` before it scans the stack

#### Scenario: Other boards unchanged

- **WHEN** the examples are compiled for UNO R4 WiFi, UNO Q and the AVR boards
- **THEN** their generated `.S`, shim and report are byte-identical to the output before this change

### Requirement: References held in caller frames survive a collection

A collection SHALL NOT free an object that is reachable only through a reference held in a caller's frame of the
collecting thread.

#### Scenario: Deep call chain survives a forced collection

- **WHEN** a program holds references in locals across a call chain at least eight frames deep and allocates until
  a collection runs
- **THEN** every referenced object keeps its contents and the program prints the expected transcript on Xtensa QEMU
  and on hardware

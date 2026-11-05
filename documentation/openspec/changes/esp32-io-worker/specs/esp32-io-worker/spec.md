## ADDED Requirements

### Requirement: Waiting device calls run on an ESP32 I/O worker

The generated shim SHALL include an ESP32 I/O worker that provides the `juno_io_offload` port contract when a
program for the Nano ESP32 (`arduino:esp32`) or the M5Stack Cardputer (`m5stack:esp32`) uses threads together with
HTTP, HTTPS, the HTTP server, SD or LEGO Powered Up, and `juno_io_offload` SHALL run its job on that worker while the
calling Juno thread is parked in the scheduler's waiting-I/O state.

#### Scenario: Threaded HTTP program on the Nano ESP32

- **WHEN** a program that starts a thread and calls `HttpClient` is compiled for the Nano ESP32
- **THEN** the shim contains the ESP32 I/O worker and the scheduler's `JUNO_SLOT_WAITING_IO` state, and it compiles
  as C++

#### Scenario: Threaded SD program on the Cardputer

- **WHEN** a program that starts a thread and reads an `SdCard` is compiled for the Cardputer
- **THEN** the shim contains the ESP32 I/O worker and the SD job is dispatched through `juno_io_offload` under the
  storage owner

### Requirement: Worker placement and resources

The ESP32 I/O worker SHALL run as two statically allocated FreeRTOS tasks pinned to core 0, at a priority above idle
and below the Wi-Fi, lwIP and `esp_timer` tasks, each with a stack of at least 8192 bytes. The Juno threads SHALL
remain on the Arduino loop core, under the existing baton.

#### Scenario: Worker tasks are pinned to core 0

- **WHEN** the shim of a threaded ESP32 HTTP program is generated
- **THEN** it creates the worker tasks with `xTaskCreateStaticPinnedToCore` on core 0 with stacks of at least 8192
  bytes

#### Scenario: Boot diagnostic names both cores

- **WHEN** a threaded ESP32 program that uses the worker starts on the board
- **THEN** its serial output contains one line of the form `[juno-thread] java core=<n> io core=<m>`, with `n` the
  loop core and `m` equal to 0

### Requirement: Worker gating keeps unaffected output byte-identical

The ESP32 I/O worker and the device-owner prelude SHALL be emitted only when the program uses threads and a waiting
device feature. Starting Wi-Fi SHALL count as a waiting device call only on a core whose Wi-Fi start waits (the UNO
Q).

#### Scenario: Wi-Fi-only ESP32 program

- **WHEN** a program that only starts Wi-Fi, with or without threads, is compiled for the Nano ESP32 or the Cardputer
- **THEN** its generated `.S`, shim and report are byte-identical to the output before this change

#### Scenario: ESP32 program without threads

- **WHEN** a program without threads that uses HTTP is compiled for the Nano ESP32
- **THEN** no I/O worker is emitted and `juno_io_offload` runs its job inline

#### Scenario: UNO Q output unchanged

- **WHEN** the UNO Q examples are compiled
- **THEN** their generated output is byte-identical to the output before this change

### Requirement: Shared waits yield the baton

The UDP receive-with-timeout loop, the TCP read loop and the e-mail line reader SHALL wait with `juno_io_wait`, not
with `delay`, so that a thread waiting in them does not hold the baton on any board.

#### Scenario: UDP receive with a timeout

- **WHEN** a threaded program calls `DatagramSocket.receive` with a timeout and no datagram arrives
- **THEN** the generated loop waits through `juno_io_wait`, and other Juno threads keep running for the whole timeout

### Requirement: A waiting thread does not stall a CPU-bound thread on hardware

On both ESP32 boards, a thread that waits on a network call SHALL leave a concurrently running CPU-bound Juno thread
at least 80% of the rate it reaches when no thread is waiting.

#### Scenario: I/O-wait probe on the Nano ESP32 and the Cardputer

- **WHEN** the I/O-wait probe runs on the board with thread A waiting on an HTTPS connect to an unreachable host,
  then on a UDP receive with a 5 s timeout, while thread B increments a counter
- **THEN** the serial output, captured with `juno:monitor`, reports thread B's rate with thread A waiting as at least
  80% of its baseline rate

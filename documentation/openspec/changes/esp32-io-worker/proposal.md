## Why

The Nano ESP32 and the M5Stack Cardputer Adv run every Juno thread on the Arduino loop core (core 1) under one
baton, so only one thread runs Java at a time. That is correct and stays. The problem is what happens when a thread
waits inside a vendor call (DNS, `WiFiClient.connect`, the TLS handshake, SdFat, the server accept): it keeps the
baton for the whole wait, and every other Juno thread stalls. A network receiver thread next to a game loop, the
pattern the multiplayer examples use, freezes the game for the length of a connect.

The UNO Q already solves this with an I/O worker (`zephyr/io-worker.cpp`): the waiting call runs on a worker thread
while its caller is parked, and the baton moves on. The shared HTTP client, HTTP server and SD helpers already call
`juno_io_offload`. On ESP32 that call runs inline only because `Esp32CoreRuntime` does not turn the worker on.
Core 0 has spare cycles next to the Wi-Fi and lwIP tasks, so the worker can run there without competing with Java.

Separately, three shared loops hold the baton on every board: they spin with `delay(1)` instead of `juno_io_wait`.

## What Changes

- Replace `delay(1)` with `juno_io_wait` in `common/datagram.cpp` (`DatagramSocket.receive` with a timeout),
  `common/tcp.cpp` and `common/email-read-line.cpp`, so a waiting thread yields the baton on every board.
- Add `runtime/esp32/io-worker.cpp`: a FreeRTOS port of the UNO Q I/O worker that provides the same port contract
  (`JUNO_IO_WORKER_COUNT`, `juno_port_io_dispatch`, `juno_port_io_busy`, `juno_io_on_worker`). The two worker
  tasks are pinned to core 0 at low priority, with 8 KiB stacks.
- Add `ThreadRuntime.esp32IoWorker()`, and override `ioWorker()`/`ioWorkerPort()` in `Esp32CoreRuntime`. This
  covers both ESP32 cores with no board-specific branch.
- Fix the gating in `IoRuntime`. Today, Wi-Fi counts as a waiting feature on any core with a worker, because the
  UNO Q's `Bridge.begin()` waits. That moves behind a new `CoreRuntime.wifiBeginWaits()` capability, which only
  `ZephyrCoreRuntime` sets.
- Emit one boot diagnostic line from the runtime when the worker is used, naming the Java core and the I/O core.
- Add an I/O-wait probe example for the Nano ESP32 and the Cardputer, and document which core runs Java and which
  runs I/O.

## Capabilities

### New Capabilities

- `esp32-io-worker`: on the ESP32-S3 boards, a threaded program's waiting device calls run on an I/O worker on the
  other core, so a waiting thread no longer holds the Java core.

### Modified Capabilities

None as specs. The UNO Q worker's behavior is unchanged. The Wi-Fi gating clause moves behind a capability that
only the UNO Q sets, so the UNO Q's output stays byte-identical.

## Impact

- `juno-compiler/src/main/resources/runtime/esp32/io-worker.cpp` (new); `common/datagram.cpp`, `common/tcp.cpp`,
  `common/email-read-line.cpp`.
- `backend/runtime/ThreadRuntime.java`, `backend/runtime/IoRuntime.java`, `backend/runtime/spi/CoreRuntime.java`,
  `backend/runtime/core/Esp32CoreRuntime.java`, `backend/runtime/core/ZephyrCoreRuntime.java`.
- Tests: `ThreadsTest`, `CoreRuntimeTest`, `JunoCompilerTest`, `GeneratedAsmToolchainTest`.
- Generated output changes only for threaded ESP32 programs that use HTTP, HTTPS, the HTTP server, SD or LEGO
  Powered Up, and for any program that reaches the three `delay(1)` loops. Everything else stays byte-identical.
- Memory: about 16 KiB more static internal DRAM on ESP32 programs that use the worker. The 8 KiB arena is not
  affected.
- No new public API, Maven dependency or bytecode subset change.
- This is step 3 of the two-core plan in #241. Running Java on both cores at once, with the Java Memory Model kept,
  is the follow-up change `esp32-parallel-threads`. That change depends on this one: a vendor call that blocks would
  otherwise hold one of the two run tokens. It also depends on the worker's `WAITING_IO` state, which its
  stop-the-world GC handshake treats as already stopped. The register-window spill fix (`xtensa-gc-window-spill`)
  is independent and lands first.

## Source and Derivation

- **Requirements source:** GitHub issue [#241](https://github.com/jabrena/juno/issues/241), updated 2026-11-05
  with the review's corrections and the Option 1 decision.
- **Derived artifact:** this OpenSpec change, created 2026-11-05.
- **Direction:** issue → OpenSpec. This change does not synchronize back to the issue.

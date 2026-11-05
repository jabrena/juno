## 1. Shared waits yield the baton (PR: `fix(runtime)`)

- [ ] 1.1 Replace `delay(1)` with `juno_io_wait(1)` in `common/datagram.cpp`, `common/tcp.cpp` and `common/email-read-line.cpp`; confirm each helper is emitted after the device-owner prelude on every core that uses it.
- [ ] 1.2 Add `ThreadsTest` cases: a threaded UDP-receive program and a TCP-read program wait through `juno_io_wait` on UNO R4 WiFi, UNO Q and Nano ESP32.
- [ ] 1.3 Run the example `.S`/`Shim.cpp`/report diff (`skills/juno-debugging`); only programs that reach these helpers change.
- [ ] 1.4 `./mvnw test` green.

## 2. Wi-Fi gating capability (PR: `feat(esp32)`, part 1)

- [ ] 2.1 Add a default `CoreRuntime.wifiBeginWaits()` (false), and override it to true in `ZephyrCoreRuntime`.
- [ ] 2.2 In `IoRuntime.needed`/`preludeFor`/`usesWorker`, use `wifiBeginWaits()` instead of the `worker` flag for the `WIFI` clause.
- [ ] 2.3 Confirm the UNO Q example output is byte-identical.

## 3. ESP32 I/O worker (PR: `feat(esp32)`, part 2)

- [ ] 3.1 Add `runtime/esp32/io-worker.cpp`. It ports `zephyr/io-worker.cpp` to FreeRTOS: two static tasks pinned to core 0 at priority 2, `JUNO_IO_WORKER_STACK_BYTES = 8192`, binary semaphores, the same `__atomic` `busy` protocol, and `juno_io_on_worker` via `xTaskGetCurrentTaskHandle()`.
- [ ] 3.2 Add the one-time boot line `[juno-thread] java core=<n> io core=0`, printed from the caller's task before the first dispatch, never from a worker.
- [ ] 3.3 Add `ThreadRuntime.esp32IoWorker()`, and override `ioWorker()`/`ioWorkerPort()` in `Esp32CoreRuntime`.
- [ ] 3.4 `ThreadsTest`: mirror the UNO Q worker cases (worker present, worker omitted without waiting calls, compiles as C++, cancellation drains) for the Nano ESP32 and the Cardputer; add a Wi-Fi-only no-prelude case.
- [ ] 3.5 `CoreRuntimeTest`: `ioWorker()` and `wifiBeginWaits()` for `ESP32`, `M5STACK_ESP32` and the Zephyr core.
- [ ] 3.6 `JunoCompilerTest`: an ESP32 variant of the SD offload assertion.
- [ ] 3.7 `GeneratedAsmToolchainTest`: a threaded ESP32 HTTP shim, with FreeRTOS stubs in `src/test/resources/include` if they are missing.
- [ ] 3.8 Consider a runtime-report line for the worker stacks (open question in `design.md`).
- [ ] 3.9 `./mvnw test` green; example output diff limited to threaded ESP32 programs with waiting features.

## 4. Probe example and hardware verification (PR: `feat(examples)`)

- [ ] 4.1 Add an I/O-wait probe for the Nano ESP32 and a Cardputer variant. Thread B counts; thread A waits on an HTTPS connect to an unreachable host, then on a UDP receive with a 5 s timeout. Main prints the baseline rate and the with-wait rate as a percentage. Keep each serial line under 31 characters and repeat the output for the monitor.
- [ ] 4.2 `juno:verify` the probe and the existing threaded HTTPS examples on both boards; read the runtime risk report.
- [ ] 4.3 After asking first, `juno:upload` then `juno:monitor` on the Nano ESP32 and the Cardputer over USB serial. Check the boot line and that the ratio is at least 80%, and record the logs in the PR.
- [ ] 4.4 Watch for task-watchdog resets during the TLS case on the Nano ESP32. If they occur, try `tskNO_AFFINITY` and record the outcome in `design.md`.

## 5. Documentation (PR: `docs`)

- [ ] 5.1 Update the threads rows in `juno-site/src/main/resources/content/arduino-nano-esp32.md` and `m5stack-cardputer.md`: Java runs on the loop core, I/O on core 0.
- [ ] 5.2 `./mvnw clean verify -Psite -DskipTests` and `./mvnw -pl juno-site test`.

## 6. Closeout

- [ ] 6.1 Tick the I/O worker items in issue #241, and leave the issue open for `esp32-parallel-threads`.
- [ ] 6.2 Archive this change with the `close-spec` workflow.

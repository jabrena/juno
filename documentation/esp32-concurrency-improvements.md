# ESP32 concurrency: pending improvements

Ideas for making virtual threads and structured concurrency (`StructuredTaskScope`) faster and more robust on the
ESP32 boards, found while measuring the Nano ESP32 and the M5Stack Cardputer with the N-Queens and HTTP latency
benchmarks. Threads and scope subtasks run on the same scheduler, so every item applies to both.

## Where things stand

Measured with two carriers (both cores), unless noted. The Nano ESP32 and the Cardputer are ESP32-S3s; the Nano
builds with the Arduino-ESP32 2.x core, the Cardputer with M5Stack's ESP-IDF 5 core. The AI Thinker ESP32-CAM is a
classic ESP32 (ESP32-D0WD-V3) on Espressif's core.

| Benchmark | Board | Sequential / monolith | Threads / scope | Speed-up |
|---|---|---|---|---|
| N-Queens N=12 (`NQueensBenchmark`) | Nano ESP32 | 1.13 s | 0.733 s | 1.54× |
| | Cardputer | 1.12 s | 0.729 s | 1.54× |
| N-Queens N=12, one carrier | Nano ESP32 | 0.89 s | 0.96 s | 0.92× |
| HTTP, 4 × 1 s endpoint (`HttpLatencyBenchmark`) | Nano ESP32 | ~6.2 s | 2.46 s | 2.52× |
| | Cardputer | ~6.2 s | 2.46 s | 2.52× |
| HTTPS, 4 × 1 s endpoint (`HttpsLatencyBenchmark`) | Nano ESP32 | ~18.5 s | 14.75 s | 1.25× |
| | Cardputer | ~16.0 s | 12.29 s | 1.30× |
| | ESP32-CAM | ~18.5 s | 14.7–15.1 s | 1.20–1.25× |

The Cardputer matches the Nano wherever the work is the same, so the CPU-bound and HTTP results follow the chip and
Juno's runtime, not the Arduino core. Only HTTPS differs: each request is ~0.6 s faster on the Cardputer's newer TLS
stack, and its parallel requests are still about three-quarters of the sequential time (item 6). The ESP32-CAM, with
less RAM and an older chip, matches the Nano.

Re-measured on 2026-11-21 with `main` after #426–#428: the Nano ESP32 and the ESP32-CAM gave the same figures as
above, every request answered 200 (one `-1` in 48 parallel ESP32-CAM requests).

Already done:

- #412 — carriers give their core to idle work (no task-watchdog reset, no USB serial stall).
- #415 — cheap safepoints: a lock-free per-carrier slice check on the core's cycle counter, no `yield()` per loop
  iteration; also fixed the breathe step waking at every slice. N=12 monolith 7.64 s → 1.13 s.
- #416 — placement: a thread waiting in a join is not counted as load, a tie goes to the starter's carrier, and a slot
  is taken only if its task suits the target carrier. N=12 split 1.18 s → 0.733 s.
- #417 — four HTTP requests in flight on ESP32, and the HTTPS latency benchmark. HTTP 1.83× → 2.52×.
- #426 — an HTTP response that reached the socket before the deadline is read even when its thread ran only after
  it (it used to end with status 0; see item 7). Also the Portenta H7's TLS roots in flash, shared by every socket.
- #427 — on ports whose threads each have a native stack (today the Portenta), a connect leaves the runtime so the
  other threads run during a TLS handshake; one native call at a time. The ESP32 ports keep stubs (item 15).

## CPU-bound work

1. **Inline the safepoint check in the generated code.** Every method entry and loop iteration still makes a full
   call (`call8`) into the runtime only to learn that the slice is not over. Emitting the cycle-counter compare
   inline (read `CCOUNT`, compare with the carrier's mark, branch) removes millions of calls. Recursive code such as
   `NQueens.place()` benefits most, in both the monolith and the parallel modes. Expected to be the largest remaining
   CPU gain.
2. **Fewer safepoints.** A method needs an entry safepoint only if it can recurse; loops already have one on their
   back-edge. A call-graph pass could drop entry safepoints from every non-recursive method.
3. **One attention flag instead of two loads** on two carriers. Each safepoint reads both "a collection is stopping
   the world" and "a thread is being cancelled"; folding them into one word makes the common path a single load.
4. **Close the gap from 1.54× to ~1.9×** (the desktop JDK reaches 1.9× on the same split). Profile first. Likely
   causes: both cores fetching code through the same flash cache (the hottest generated functions could move to
   IRAM), the runtime lock still taken once per millisecond per core even when a core runs a single thread, and join
   polling (item 5).

## Fork and join cost

5. **Wake joiners instead of polling.** `join()` and `scope.join()` check once per millisecond (`delay(1)`), which
   dominates small tasks: N-Queens N=6 only breaks even (1.0×; a scope 0.90× on the Cardputer, whose threads average
   twice their best run). Signalling the joiner when its target finishes would make short forks cheap.

## I/O

6. **HTTPS handshakes look serialized across the chip.** Each request spends ~3.5 s in the TLS handshake, and four
   parallel requests take 14.75 s with or without good placement — four handshakes one after another. The Cardputer's
   ESP-IDF 5 TLS stack is faster (12.29 s) but just as serialized. Likely the shared crypto hardware; instrument the
   handshake phases to confirm.
7. **Occasional HTTPS status 0** (about 2 of 30 parallel requests on the Nano, 2 of 56 on the Cardputer). Likely
   fixed by #426: the response loop checked its 5 s deadline before the socket, so a request that only got the CPU
   after other threads' handshakes gave up with a response waiting. Not seen on the Nano or the ESP32-CAM since (about
   90 parallel HTTPS requests); confirm with longer runs before closing.
8. **HTTP below ~3.5×.** Four overlapped 1 s requests take 2.46 s rather than ~1.6 s: part of each request (connect or
   the Wi-Fi stack) is still serialized.
9. **ESP32-CAM with four TLS sessions: answered, it holds them.** 47 of 48 parallel requests answered 200 at
   1.20–1.25×, like the Nano; the build leaves 267 KB of heap. Flashing it needs the microSD card out (GPIO2, the
   card's data line, must be low for download mode) and IO0 held while the upload starts.

## Robustness

10. **Single-carrier `arena full`.** With `-Djuno.carriers=1` and the default 8 KiB arena, parking a deep recursion
    failed at N=12 (`cannot park virtual thread: arena full`). Reuse or size the stack chunks better, or warn at
    compile time.
11. **Tick-hook starvation (understand only).** Flagging slices from a FreeRTOS tick hook was even cheaper (0.94 s
    monolith) but starved threads about 600× for an unexplained reason, so it was dropped in favour of the cycle
    counter. Worth understanding before anyone tries it again.
12. **Intermittent dual-carrier panic under QEMU.** Once in CI (#417), `ManyVirtualThreads` on the Cardputer took a
    `LoadProhibited` at address 4 on core 1 mid-run, rebooted and then passed; the re-run passed, and 115 rounds on a
    real Cardputer were clean. `run.sh` now decodes a backtrace after `[juno-begin]` too, so the next occurrence
    names its function.

## Hardware coverage

13. **Pico 2 W: measured, and slow on two carriers.** N-Queens N=12 takes 0.908 s on one carrier but 5.6 s on two,
    and the split gains nothing (0.95×). Its dual-carrier back-edge still takes the shared runtime lock and calls
    `yield()` on every loop iteration, the cost #415 removed on the ESP32; give it the same lock-free per-carrier
    slice check. HTTP 1.90× and HTTPS 1.20–1.30× are fine. On two carriers about one plain-HTTP request in four to
    eight ended with status 0 before #426; re-check after it.

## Measurement

14. **Show placement in the benchmark output.** Print which core each worker ran on and the cost of one empty
    fork/join, so a "both workers on one core" result is visible without a diagnostic probe.

## Native calls

15. **Native calls on the dual-carrier ports.** #427 lets a thread leave the runtime for a blocking connect only on
    the Mbed port. The ESP32 and Pico dual-carrier ports also give every thread a FreeRTOS task with its own stack, so
    they could offer the same `juno_port_native_*` hooks. On the Portenta it brought no HTTPS speed-up, since the
    handshakes are CPU-bound on one core; with two cores a handshake could run beside another thread's Java.

## UNO R4 WiFi

Not an ESP32, but measured with the same benchmarks on 2026-11-21. The UNO R4 WiFi runs Juno's threads as continuations
on one carrier stack, with 32 KB of RAM; its TLS runs on the ESP32-S3 Wi-Fi module. `HttpLatencyBenchmark` and
`HttpsLatencyBenchmark` make two requests on it (`requestCount()`), as many as it lets be in flight.

| Benchmark (2 requests) | Sequential | Threads / scope | Speed-up |
|---|---|---|---|
| HTTP, 2 × 1 s endpoint | 3.10–3.18 s | 1.83–1.85 s | 1.68–1.73× |
| HTTPS, 2 × 1 s endpoint | 9.22–9.35 s | 7.66–8.29 s | 1.11–1.20× |
| N-Queens N=12 | 2.89 s | 4.54 s | 0.63× (one core) |

These need `-Djuno.Xss=5120 -Djuno.Xmx=7168`. With the board's defaults (2 KB carrier stack, 8 KiB arena) an HTTP
request on a virtual thread or a subtask crashes the board, already with one request:

- **Cause:** one WiFiS3 request needs between 4 KB (hangs) and 5 KB (works) of stack: Juno's 512-byte request buffer,
  newlib's `vsnprintf` behind every modem command, and the modem's `std::string` handling. On the 2 KB carrier stack it
  overflows silently into the heap below, and the board faults later in `WiFiClient::~WiFiClient()`, freeing the
  receive buffer whose heap block was overwritten (a bus fault the core reports as a main stack overflow). A
  request on `main` survives because the main stack has room below it.
- **Not virtual threads themselves:** `VirtualThreadsSelfTest` (start and join, locals across parks, scope subtasks, a
  deep chain parked at its bottom, six threads at once) passes at the default 2 KB stack and 8 KiB arena.
- **Not new:** the compiler from before #426 crashes the same way.
- **The arena matters too:** two parked HTTPS threads ran out of a 6 KiB arena after a few rounds (`cannot park
  virtual thread: arena full`); 7 KiB held for 13 rounds. Stack and arena share the RAM: 5 KB + 7 KiB links at 67% of
  RAM, 6 KB + 8 KiB does not.

16. **A real fix, so the UNO R4 WiFi works without hand-picked settings.** A compiler or runtime change, on its own
    branch and PR. Options:
    1. Give R4 programs that make network requests from threads a larger carrier stack automatically, with a matching
       arena.
    2. Detect a carrier-stack overflow and report it, instead of corrupting the heap silently.
    3. Make a request use less stack, for example by moving the 512-byte request buffer off the stack.

## Suggested order

Items 1–3 together as one safepoint change measured with N-Queens, then 13 (the Pico's back-edge), then 5, then
14.

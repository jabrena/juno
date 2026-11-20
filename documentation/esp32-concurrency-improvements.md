# ESP32 concurrency: pending improvements

Ideas for making virtual threads and structured concurrency (`StructuredTaskScope`) faster and more robust on the
ESP32 boards, found while measuring the Nano ESP32 and the M5Stack Cardputer with the N-Queens and HTTP latency
benchmarks. Threads and scope subtasks run on the same scheduler, so every item applies to both.

## Where things stand

Measured with two carriers (both cores), unless noted. Both boards are ESP32-S3s; the Nano ESP32 builds with the
Arduino-ESP32 2.x core, the Cardputer with M5Stack's ESP-IDF 5 core.

| Benchmark | Board | Sequential / monolith | Threads / scope | Speed-up |
|---|---|---|---|---|
| N-Queens N=12 (`NQueensBenchmark`) | Nano ESP32 | 1.13 s | 0.733 s | 1.54× |
| | Cardputer | 1.12 s | 0.729 s | 1.54× |
| N-Queens N=12, one carrier | Nano ESP32 | 0.89 s | 0.96 s | 0.92× |
| HTTP, 4 × 1 s endpoint (`HttpLatencyBenchmark`) | Nano ESP32 | ~6.2 s | 2.46 s | 2.52× |
| | Cardputer | ~6.2 s | 2.46 s | 2.52× |
| HTTPS, 4 × 1 s endpoint (`HttpsLatencyBenchmark`) | Nano ESP32 | ~18.5 s | 14.75 s | 1.25× |
| | Cardputer | ~16.0 s | 12.29 s | 1.30× |

The Cardputer matches the Nano wherever the work is the same, so the CPU-bound and HTTP results follow the chip and
Juno's runtime, not the Arduino core. Only HTTPS differs: each request is ~0.6 s faster on the Cardputer's newer TLS
stack, and its parallel requests are still about three-quarters of the sequential time (item 6).

Already done:

- #412 — carriers give their core to idle work (no task-watchdog reset, no USB serial stall).
- #415 — cheap safepoints: a lock-free per-carrier slice check on the core's cycle counter, no `yield()` per loop
  iteration; also fixed the breathe step waking at every slice. N=12 monolith 7.64 s → 1.13 s.
- #416 — placement: a thread waiting in a join is not counted as load, a tie goes to the starter's carrier, and a slot
  is taken only if its task suits the target carrier. N=12 split 1.18 s → 0.733 s.
- #417 — four HTTP requests in flight on ESP32, and the HTTPS latency benchmark. HTTP 1.83× → 2.52×.

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
7. **Occasional HTTPS status 0** (about 2 of 30 parallel requests on the Nano, 2 of 56 on the Cardputer). Find where
   a request ends with no status instead of a failure.
8. **HTTP below ~3.5×.** Four overlapped 1 s requests take 2.46 s rather than ~1.6 s: part of each request (connect or
   the Wi-Fi stack) is still serialized.
9. **ESP32-CAM with four TLS sessions.** The classic ESP32 has less RAM; check it after the in-flight limit went to
   four.

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

13. **Pico 2 W.** The placement change is shared with its dual-carrier port but only build-checked; run the N-Queens
    benchmark on the board.

## Measurement

14. **Show placement in the benchmark output.** Print which core each worker ran on and the cost of one empty
    fork/join, so a "both workers on one core" result is visible without a diagnostic probe.

## Suggested order

Items 1–3 together as one safepoint change measured with N-Queens, then 5, then 14.

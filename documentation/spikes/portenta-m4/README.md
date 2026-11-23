# Spike: can Juno use the Portenta H7's Cortex-M4?

Today Juno builds only the Portenta H7's M7 image, so every thread runs on one carrier. This spike checks, on real
hardware, the three things a second carrier on the M4 would depend on. It changes no compiler, API or carrier-count
code.

## Result (Portenta H7, 2026-11-23)

```text
[spike] run 4 m4=done locked=200000/200000 OK racy=199485 juno(1004)=672345 OK m7us=35836
[spike] run 5 m4=done locked=200000/200000 OK racy=199520 juno(1005)=674355 OK m7us=35884
[spike] run 6 m4=done locked=200000/200000 OK racy=199487 juno(1006)=675361 OK m7us=35849
```

| Question | Answer |
|---|---|
| Can the M7 start the M4? | Yes. The M4 image goes into flash bank 2 (`split=50_50`) and the M7 calls the core's `bootM4()`. |
| Does code Juno compiles for the M7 run on the M4? | Yes, unmodified. `SpikeMethod.compute(int)` (loop, branches, `sdiv`/`mls`, a `yield` call) returns the same result on the M4 as the M7's reference on every run. Both cores implement ARMv7E-M. |
| Can both cores share data under a lock? | Yes, with the hardware semaphore (HSEM) and a shared block in non-cacheable D3 SRAM, **if both sides add a memory barrier** (`__DMB()`) after taking the lock and before releasing it. |

Without the barriers, the "locked" counter lost about 17% of its updates (about 166,500 of 200,000), about as many
as the unlocked one: the M7's write buffer let the counter store land after the HSEM release. A Juno runtime lock
spanning both cores must include these barriers.

The unlocked counter still loses about 500 updates per run, which shows the two cores really do run at the same
time. With the lock, one M7 iteration (lock, increment, unlock, unlocked increment) takes about 360 ns while the M4
competes for the same lock.

## How it works

| Part | File |
|---|---|
| Shared block: the first 1 KiB of D3 SRAM (`0x38000000`), below the core's OpenAMP region at `0x38000400` | `m7-host/shared-block.h` (a copy is in `m4-worker/`, keep them identical) |
| M7: marks D3 SRAM non-cacheable (as the core's RPC library does), boots the M4, runs and prints each test | `m7-host/m7-host.ino` |
| M4: waits for a run, increments the counters, calls the Juno method | `m4-worker/m4-worker.ino` |
| The Java source of the method | `java/spike/SpikeMethod.java` |
| Juno's Thumb-2 output for that method (`juno_fn2`), renamed to `juno_spike_method` | `m4-worker/juno-spike-method.S` |

## Reproduce

```bash
D=documentation/spikes/portenta-m4
FQBN=arduino:mbed_portenta:envie_m7
arduino-cli compile --upload -p <PORT> --fqbn $FQBN:target_core=cm4,split=50_50 $D/m4-worker
arduino-cli compile --upload -p <PORT> --fqbn $FQBN:target_core=cm7,split=50_50 $D/m7-host
sleep 16 | script -q log ./mvnw -f juno-examples/pom.xml juno:monitor -Djuno.board=arduino-portenta-h7 -Djuno.port=<PORT>
```

`juno:flash` cannot upload a hand-written sketch or an M4 image, so the uploads use `arduino-cli`; the output is
read with `juno:monitor`. Upload the M4 image first. A DFU upload can fail transiently: check the exit status and
retry.

To regenerate `juno-spike-method.S`, compile `SpikeMethod` with the Juno CLI for `arduino-portenta-h7` and copy
`juno_fn2` from the generated assembly.

## What the spike does not cover

These decide how much work a real second carrier is:

- **The Juno runtime on the M4.** The method needed no runtime call. Real thread bodies call `juno_alloc`, the
  collector, safepoints and the scheduler, which today exist only in the M7 image. They would have to live in one
  shared copy at a fixed address that both images link against, with the runtime lock built on HSEM plus barriers.
- **The arena and the collector.** The arena is in AXI SRAM, which the M4 can reach, but the M7 caches it. Either the
  arena becomes non-cacheable on the M7 (slower) or every cross-core handoff cleans and invalidates the cache. The
  collector would also have to scan the stacks of threads on the M4.
- **`double` arithmetic.** The M4 has only a single-precision FPU. Code with `double` instructions would fault there,
  so such threads must stay on the M7 or the M4 needs a soft-float build.
- **Hardware calls.** Serial, Wi-Fi, SD and the display are driven by the M7's Mbed OS. A thread running on the M4
  that calls them has to hand the call back to the M7.
- **Memory layout.** The M4 image's RAM (D2 SRAM, `0x10000000`, 288 KiB) overlaps the region where the M7 keeps its
  Ethernet and USB buffers (`0x30040000`). This works in Arduino's own examples but needs checking with Wi-Fi in use.
- **Tooling.** `juno:flash` would need to build and upload both images, M4 first.

## Recommendation

The hard hardware questions have positive answers, so a second carrier is possible. The open items above are
runtime and tooling work, not hardware blockers. The cheapest useful next step is the **M4 offload for pure
compute**: run only thread bodies that need no runtime call, no `double` and no hardware on the M4, and keep
everything else on the M7. A full second carrier can grow from that once the shared runtime state is in place.

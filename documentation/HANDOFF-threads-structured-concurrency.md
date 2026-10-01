# HANDOFF: threads and structured concurrency in Juno

Status: **idea / design only. No code written, nothing scoped or approved.**
Origin: design discussion (ChatGPT share "Threads support in Juno", 2026-10-01), reviewed in a Claude Code session.
Claims about Juno's internals below come from that discussion plus `AGENTS.md` and
`juno-site/src/main/resources/content/features.md`; verify against the code before relying on them.

## Goal

Let ordinary Java compile to the UNO R4 WiFi / UNO Q, using standard JDK types rather than Juno-specific APIs:

```java
interface Sensor {
    int read();
}

public class App {
    static int readTemperature() { ... }
    static int readPressure()    { ... }

    public static void main() throws Exception {
        Thread blinker = new Thread(() -> {
            while (true) {
                led.high(); Delay.millis(500);
                led.low();  Delay.millis(500);
            }
        });
        blinker.start();

        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            var t = scope.fork(App::readTemperature);
            var p = scope.fork(App::readPressure);

            scope.join().throwIfFailed();
            process(t.get(), p.get());
        }
    }
}
```

The compiler recognises a supported subset of `Thread`, `Runnable`, `StructuredTaskScope` etc. as intrinsics and
lowers them onto a small task runtime. The JDK implementations are never compiled. This follows the existing
intrinsic-lowering pattern (`IntrinsicRegistry` / `IntrinsicLowering`).

## Current state (per features.md)

Unsupported today: interfaces, inheritance/polymorphic dispatch, threads, general lambdas / method references,
cross-method exception propagation (only local try/catch works). Conservative mark/sweep GC over a fixed 8 KiB arena,
rooted on the native call stack.

## Prerequisites, in dependency order

1. **Interfaces and `invokeinterface`.** Closed-world: direct call if one implementation is reachable, otherwise a
   switch on an object type id.
2. **Lambdas and method references (`invokedynamic`).** Non-capturing: lower to a function reference.
   Capturing: generated closure object holding captured fields.
3. **Exceptions across methods.** **Done 2026-10-01 (uncommitted).** Verified on the UNO Q (serial output of
   `ExceptionUnwinding` as expected) and under QEMU (`-Pqemu`, `QemuRunIT`, both boards); UNO R4 WiFi not flashed yet.
   - Mechanism: a pending-exception global (`juno_pending_exception`) set by `athrow`; a throwing frame returns, and
     callers poll it only after calls to methods that may unwind (`lowering/ThrowingMethods`, `CallGuard`). Handlers
     clear it via `juno_throw_catch`; unmatched classes jump to a shared propagate block; the entry point calls
     `juno_throw_check_escape`. Same shim and backend for both boards. `finally` and try-with-resources `close()` work.
   - An integer division by zero unwinds to callers (catchable in a caller) when any `catch`/`finally` in the program
     can receive `ArithmeticException`; otherwise it still panics, with no guard.
   - Remaining gaps: the pending slot is a single global (the thread runtime needs per-thread state, step 4/5);
     `addSuppressed` is dropped (a `close()` failure during unwinding is lost); no causes or stack traces.
   - Still unsupported: compound assignment on array elements (`dup2`; widening the opcode subset needs approval).
4. **Thread runtime.** `Thread.start/join/sleep/yield` as intrinsics onto a task runtime.
5. **Multi-stack GC rooting.** Scan every live thread's stack (per-thread stack bounds, saved SP, state, entry point).
6. **`synchronized` / `ReentrantLock`**, restricted subset.
7. **`StructuredTaskScope`** (`ShutdownOnFailure`, `ShutdownOnSuccess`): `fork`, `join`, `throwIfFailed`, `get`,
   lowered onto the task runtime.

## Design notes

- **Scheduler:** preferred first target is the UNO Q, mapping a Juno thread to a Zephyr thread via the board runtime
  shim. A custom scheduler (context switch, stacks, sleep/wakeup) makes Juno partly an RTOS; avoid for v1.
- **Board differences** go in a `CoreRuntime` implementation; boards without threads are marked by a
  `board/Capability` the `Linker` checks once. Never branch on a specific board in the backend.
- **Closed world helps:** all `Runnable` implementations, lambda targets and task entry points are known, so stacks
  can be sized at compile time. Candidate: report thread/stack usage in `inspect --risks` and reject over-budget
  programs before upload.
- **Unsupported JDK API** must raise a `CompileException`, never be silently ignored.
- **Biggest risk:** GC + exceptions + concurrency interacting. A suspended thread holding references, with an
  exception unwinding through generated frames, must stay correct. Design the runtime ABI before exposing `Thread`.

## Constraints from AGENTS.md

- **Ask first:** expanding the accepted bytecode subset (`BytecodeDecoder`, e.g. `invokeinterface`, `invokedynamic`)
  or relaxing `Descriptor.usesOnlyV01Types`.
- Substantial feature with real tradeoffs: scope via AskUserQuestion before coding.
- Each step: add/extend `JunoCompilerTest` / `GeneratedAsmToolchainTest`, keep output deterministic, verify an
  example end-to-end with `juno:verify`, and flash to hardware before committing backend/codegen changes.
- Documentation prose goes in `juno-site/src/main/resources/content/` (update `features.md` when support lands);
  never hand-edit `docs/`.

## Suggested next steps

1. Scope with the maintainer: confirm the standard-Java goal, and whether the UNO Q only is acceptable for v1.
2. Start with step 1 (interfaces, restricted dispatch) as its own change; it is useful independent of threads.
3. Write a short ADR for the task runtime ABI (stacks, GC roots, exception propagation) before steps 4-5.

## Open questions

- Is a Renesas (UNO R4) thread story required, or is UNO Q-only acceptable?
- Maximum threads and stack size policy; compile-time budget vs. user-configurable?
- Which `StructuredTaskScope` subset is worth supporting first?
- Does reference-valued static field support (currently missing) need to land before threads?

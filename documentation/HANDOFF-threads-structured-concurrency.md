# HANDOFF: threads and structured concurrency in Juno

Status: **steps 1-7 done (uncommitted, see below).**
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

        try (var scope = StructuredTaskScope.open()) {
            var t = scope.fork(App::readTemperature);
            var p = scope.fork(App::readPressure);

            scope.join();
            process(t.get(), p.get());
        }
    }
}
```

The compiler recognises a supported subset of `Thread`, `Runnable`, `StructuredTaskScope` etc. as intrinsics and
lowers them onto a small task runtime. The JDK implementations are never compiled. This follows the existing
intrinsic-lowering pattern (`IntrinsicRegistry` / `IntrinsicLowering`).

## Current state (per features.md)

Interfaces/`invokeinterface`, lambdas and method references, cross-method exceptions, cooperative threads,
restricted synchronization, and JDK 25 joiner-based structured task scopes now exist (steps 1-7 below). Still unsupported:
inheritance/polymorphic dispatch (so `extends Thread`) and the rest of the general `java.util.concurrent` API.
Conservative mark/sweep GC uses a fixed 8 KiB arena rooted on every live thread's stack and active task scope.

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
4. **Thread runtime.** **Done 2026-10-02 (uncommitted).** `new Thread(Runnable)`, `start`, `join`, `isAlive`,
   `setDaemon`, `Thread.sleep(long)`, `Thread.yield()`; `Delay.millis` becomes a sleep once the program has threads.
   Decisions (scoped with the maintainer): both boards; Runnable classes/lambdas first (no `extends Thread`);
   compile-time limits (`RuntimeLimits.MAX_THREADS` = 4 including main; stack 2 KiB R4 / 4 KiB Q).
   - Cooperative, board-independent scheduler in the shim (`backend/ThreadRuntime.scheduler`); switches only at loop
     backedges (rate-limited to 1/ms, `juno_thread_backedge` replaces `bl yield`), `Delay`, `sleep`, `yield`, `join`,
     so the arena, GC and pending-exception slot need no locks. The pending exception is saved/restored per switch.
   - Per-core port in `CoreRuntime.threadPort()`: UNO R4 = own stacks + a `juno_context_switch` asm routine
     (r4-r11, lr, s16-s31); UNO Q = one Zephyr thread per Juno thread passing a baton semaphore, so only one runs.
   - Front end: `linker/ThreadSupport` registers one synthetic `Runnable.run()` interface-call site when a `Thread`
     is created/started; `lowering/ThreadEntryLowering` turns its resolved dispatch into the exported function
     `juno_thread_entry(Runnable)` the shim's bootstrap calls on the new stack. `new Thread(r)` lowers like
     `Properties` (arena handle, `THREAD_NEW`). `Thread` and `Runnable` are accepted as parameter/field types.
   - Verified: `ThreadsTest` (generated text, both shims), QEMU `demo.Threads` (R4 only: the harness has no Zephyr
     kernel) equal to the JVM's output, real `arduino-cli compile` of `examples/Threads` for both boards.
     **Not yet flashed**: neither board. The UNO Q port (Zephyr threads, 4 KiB stacks, `k_thread_join` on slot
     reuse) is the riskier one and has only been compiled, never run.
5. **Multi-stack GC rooting.** **Done as part of 4** (`juno_thread_gc_scan`: current stack from the collector's frame,
   others from the saved sp; thread handles and joined threads are roots).
6. **`synchronized` / `volatile` / `ReentrantLock`**, restricted subset. **Done.** Volatile fields remain real memory
   reads/writes across loop backedges and calls; `synchronized (lock)` lowers `monitorenter`/`monitorexit` to eight
   bounded, reentrant cooperative monitors; concrete `ReentrantLock` supports construction, `lock()`, zero-argument
   `tryLock()`, and `unlock()` through the same runtime. Contention yields to the scheduler. Synchronized methods and
   all other `ReentrantLock`/`java.util.concurrent` APIs fail explicitly. Verified by `SynchronizationTest`,
   `GeneratedAsmToolchainTest`, QEMU `demo.Synchronization`, and the board-ready `examples/Synchronization` program.
7. **`StructuredTaskScope`**. **Done; updated to the JDK 25 preview API on 2026-10-03.** Juno supports `open()`,
   `open(Joiner)`, both `fork(Callable)` and `fork(Runnable)`, `join()`, `isCancelled()`, `close()`, the four
   non-predicate built-in joiners (`allSuccessfulOrThrow`, `anySuccessfulResultOrThrow`,
   `awaitAllSuccessfulOrThrow`, `awaitAll`), and `Subtask.state()`/`get()`/`exception()`. Fail-fast joiners cancel
   sibling scheduler slots; owner-thread and call-order violations raise `IllegalStateException`, while a joiner
   failure raises `StructuredTaskScope.FailedException`. The Java 21 `ShutdownOnFailure`/`ShutdownOnSuccess`
   classes are no longer recognized. Source must be compiled on JDK 25 with `--enable-preview`; the examples
   module supplies that compiler flag.
   `allSuccessfulOrThrow().join()` returns a non-null scope-backed token, but using it as a `Stream` remains
   unsupported because Juno does not implement the Stream API.
   Verified by `StructuredTaskScopeTest`, real ARM assembly and C++ shim compilation for both board ports,
   QEMU `demo.StructuredTasks` against its JVM oracle, and real `arduino-cli compile` for the UNO R4 WiFi and the
   UNO Q (`arduino:zephyr:unoq`, 2026-10-03).
   **To improve later** (found in the 2026-10-02 review; none is fixed or tested yet):
   - **Hardware:** flash `StructuredTasks` on both boards. QEMU covers the R4 only and the test `kernel.h` stubs
     `k_thread_abort` as a no-op, so the UNO Q cancel path (`k_thread_abort`, slot reuse after a cancel) has never
     run. Cover fail-fast cancellation and the `anySuccessfulResultOrThrow` winner.
   - **Non-recursive cancellation:** `juno_task_cancel` does not cancel a task's own inner scope. If an outer scope
     cancels a task that owns a scope, the inner subtasks are orphaned and keep their slots until they finish
     (`MAX_THREADS` = 4, so slots can run out; differs from JDK semantics). Affects both boards.
   - **Monitors on cancel:** a task cancelled while holding a `synchronized` or `ReentrantLock` monitor does not
     release it, so the lock can stay held forever. Release a cancelled slot's monitors in `juno_task_cancel`.
   - **JDK 25 exclusions:** custom `Joiner` implementations, `allUntil`, and the `Configuration` overload remain
     intentionally unsupported and produce `CompileException`; timeout configuration may be added later.

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

- ~~Is a Renesas (UNO R4) thread story required, or is UNO Q-only acceptable?~~ **Answered 2026-10-03: both boards**
  (already the case for steps 4-7; keep every thread/scope change working on both ports).
- Maximum threads and stack size policy; compile-time budget vs. user-configurable?
- ~~Should Juno also support the redesigned `StructuredTaskScope.open(Joiner)` API?~~ **Answered and implemented
  2026-10-03: yes, target the JDK 25 API only (corrected from "27" by the maintainer).** JDK 25 surface (from
  `javap` on 25.0.2, 2026-10-03):
  `StructuredTaskScope<T,R>` is now an interface with `open()`, `open(Joiner)`, `open(Joiner, Function<Configuration,Configuration>)`,
  `fork(Callable)`, `fork(Runnable)`, `join()` (returns `R`), `isCancelled()`, `close()`; `Joiner` has `allSuccessfulOrThrow`,
  `anySuccessfulResultOrThrow`, `awaitAllSuccessfulOrThrow`, `awaitAll`, `allUntil(Predicate)` plus default `onFork`/`onComplete`
  and `result()`; `Subtask` has `state()`, `get()`, `exception()`; `State` = `UNAVAILABLE|SUCCESS|FAILED`;
  `Configuration` = `withThreadFactory|withName|withTimeout`; exceptions `FailedException`, `TimeoutException`.
  Implemented v1 subset: `open()`, `open(Joiner)`, both `fork`s, `join()`, `close()`, `isCancelled()`, the four non-predicate
  `Joiner` factories, `Subtask.state/get/exception`, `State`, `FailedException`. Out of v1 (explicit `CompileException`):
  custom `Joiner` implementations, `allUntil`, `Configuration` (thread factory/name; timeout maybe later).
  Maintainer chose to remove the Java 21 `ShutdownOnFailure`/`ShutdownOnSuccess` surface and require
  `--enable-preview`; the build JDK remains 25.
- Does reference-valued static field support (currently missing) need to land before threads?

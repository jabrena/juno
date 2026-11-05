## Context

`juno_gc_mark` scans from `&stackMarker` up to `juno_gc_stack_top` (and, with threads, from each parked slot's
saved stack pointer). With the windowed ABI (`call8`/`entry`/`retw`), a caller's `a0`–`a15` can stay in the physical
register file and not on the stack. The scan reads memory only.

## Decisions

- **Use `xthal_window_spill()`** (from `<xtensa/hal.h>`, available in both Arduino-ESP32 2.x and 3.x). It writes
  every live window except the current one to its save area on the stack. `__builtin_unwind_init()` does not do
  this reliably for windowed Xtensa, so it is not used here.
- **Call it from the collector's entry, before taking the marker.** The current window belongs to `juno_gc_mark`
  itself, and its locals are not Java roots.
- **Wire it as a core-runtime hook:** a `default String gcRegisterSpill() { return ""; }` on `CoreRuntime`, which
  `Esp32CoreRuntime` overrides. `AllocatorRuntime` substitutes it into a `${JUNO_GC_REGISTER_SPILL}` placeholder.
  Every other core expands it to nothing, so their output is byte-identical.

## Risks / Trade-offs

- **Cost:** one spill per collection, a few hundred cycles. Negligible next to a mark/sweep.
- **The test may not reproduce the bug:** whether a window is still live depends on call depth and on how GCC
  allocates registers. The regression program uses a deep call chain and many locals to raise the odds. The fix
  stands on the ABI argument even if no board run shows the failure.

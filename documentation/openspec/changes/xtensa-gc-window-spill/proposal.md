## Why

Juno's collector is conservative. It finds roots by scanning the static area and the stack, from a marker in
`juno_gc_mark` (`runtime/common/allocator.cpp:157`) up to the stack top. On Xtensa's windowed ABI, a caller's live
registers stay in the register file until a window overflow writes them to the stack. The ESP32 runtime never forces
that write before the scan: nothing calls `xthal_window_spill`. A reference held only in a caller's register window
(for example, a runtime helper that allocates twice, such as `juno_new_rw`) can therefore be missed, and its object
freed while it is still in use.

The UNO Q port already does the equivalent on its own architecture (`__builtin_unwind_init()` in
`zephyr/thread-port.cpp:57`). The ESP32 port relies on blocking on a semaphore to spill windows, but that only covers
parked threads, not the thread that runs the collector. The defect affects every Xtensa program that allocates,
threaded or not. It has not yet been reproduced on a board.

## What Changes

- Before scanning the stack, the collector on Xtensa cores calls `xthal_window_spill()`, so every live register
  window of the collecting thread is in memory.
- The spill is supplied by the core runtime (a new `CoreRuntime` hook that is empty by default), so the backend does
  not branch on a board, and Thumb-2 and AVR output stays byte-identical.
- A regression program allocates while references live only in caller frames several calls deep, then forces a
  collection and checks that every object is still intact.

## Capabilities

### New Capabilities

- `xtensa-gc-window-spill`: the conservative collector sees every reference held by the collecting thread on Xtensa.

### Modified Capabilities

None.

## Impact

- `runtime/common/allocator.cpp` (a placeholder before the stack scan), `backend/runtime/AllocatorRuntime.java`,
  `backend/runtime/spi/CoreRuntime.java`, `backend/runtime/core/Esp32CoreRuntime.java`.
- Generated output changes for every Nano ESP32 and Cardputer program that allocates. Other boards stay
  byte-identical.
- This is step 1 of the two-core plan in #241. `esp32-parallel-threads` reuses the same spill when it parks a thread
  for a stop-the-world collection.

## Source and Derivation

- **Requirements source:** GitHub issue [#241](https://github.com/jabrena/juno/issues/241). The defect was found
  by the 2026-11-05 design panel, and the missing call was confirmed by grep over `runtime/`.
- **Derived artifact:** this OpenSpec change, created 2026-11-05.
- **Direction:** issue → OpenSpec.

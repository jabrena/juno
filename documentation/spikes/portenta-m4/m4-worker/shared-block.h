// The block both Portenta H7 cores share: the first 1 KiB of D3 SRAM (0x38000000). The Mbed core's OpenAMP region
// starts at 0x38000400, so this kilobyte is unused by the core. The M7 marks the whole D3 SRAM non-cacheable (as the
// core's RPC library does) so neither core reads a stale copy; the M4 has no data cache.
#pragma once
#include <stdint.h>

static constexpr uint32_t SPIKE_MAGIC = 0x4A554E4Fu;  // "JUNO"
static constexpr uint32_t SPIKE_HSEM_ID = 10u;        // Not used by the Mbed core or its HAL.
static constexpr uint32_t SPIKE_ITERATIONS = 100000u;

enum : uint32_t { SPIKE_M4_BOOTING = 0u, SPIKE_M4_READY = 1u };

struct SpikeShared {
  volatile uint32_t magic;          // Written by the M7 once the block is initialized.
  volatile uint32_t m4State;        // SPIKE_M4_*, written by the M4.
  volatile uint32_t m4Heartbeat;    // Incremented by the M4 in its idle loop.
  volatile uint32_t generation;     // The M7 raises it to start one run on both cores.
  volatile uint32_t m4DoneGeneration;
  volatile uint32_t lockedCounter;  // Incremented under the HSEM lock by both cores.
  volatile uint32_t racyCounter;    // Incremented without a lock by both cores.
  volatile int32_t junoArgument;    // Input of the Juno-compiled method the M4 runs.
  volatile int32_t junoResult;      // Its result, written by the M4.
};

static SpikeShared* const spikeShared = reinterpret_cast<SpikeShared*>(0x38000000u);

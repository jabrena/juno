// Portenta H7 M4 feasibility spike, M4 side. Waits for the M7 to start a run, then adds SPIKE_ITERATIONS to the
// shared counters (one under the HSEM lock, one without) and runs one Juno-compiled method.
#include "shared-block.h"
#include "stm32h7xx_hal.h"

extern "C" int32_t juno_spike_method(int32_t n);

static void lockShared() {
  while (HAL_HSEM_FastTake(SPIKE_HSEM_ID) != HAL_OK) {
  }
  __DMB();  // No shared read may happen before the lock is held.
}

static void unlockShared() {
  __DMB();  // Every shared write must land before the release.
  HAL_HSEM_Release(SPIKE_HSEM_ID, 0);
}

static uint32_t lastGeneration = 0;

void setup() {
  __HAL_RCC_HSEM_CLK_ENABLE();
  while (spikeShared->magic != SPIKE_MAGIC) {
  }
  lastGeneration = spikeShared->generation;
  spikeShared->m4State = SPIKE_M4_READY;
}

void loop() {
  uint32_t generation = spikeShared->generation;
  if (generation == lastGeneration) {
    spikeShared->m4Heartbeat = spikeShared->m4Heartbeat + 1;
    return;
  }
  lastGeneration = generation;
  for (uint32_t i = 0; i < SPIKE_ITERATIONS; i++) {
    lockShared();
    spikeShared->lockedCounter = spikeShared->lockedCounter + 1;
    unlockShared();
    spikeShared->racyCounter = spikeShared->racyCounter + 1;
  }
  spikeShared->junoResult = juno_spike_method(spikeShared->junoArgument);
  spikeShared->m4DoneGeneration = generation;
}

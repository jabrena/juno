// Portenta H7 M4 feasibility spike, M7 side. Boots the M4, then repeats one run every few seconds: both cores add
// SPIKE_ITERATIONS to a counter under the HSEM lock and to another counter with no lock, and the M4 runs one
// Juno-compiled method. The M4 has no USB serial, so the M7 prints every result.
#include "shared-block.h"
#include "stm32h7xx_hal.h"

static void markD3NonCacheable() {
  HAL_MPU_Disable();
  MPU_Region_InitTypeDef region = {};
  region.Enable = MPU_REGION_ENABLE;
  region.BaseAddress = D3_SRAM_BASE;
  region.Size = MPU_REGION_SIZE_64KB;
  region.AccessPermission = MPU_REGION_FULL_ACCESS;
  region.IsBufferable = MPU_ACCESS_NOT_BUFFERABLE;
  region.IsCacheable = MPU_ACCESS_NOT_CACHEABLE;
  region.IsShareable = MPU_ACCESS_NOT_SHAREABLE;
  region.Number = MPU_REGION_NUMBER15;
  region.TypeExtField = MPU_TEX_LEVEL1;
  region.SubRegionDisable = 0x00;
  region.DisableExec = MPU_INSTRUCTION_ACCESS_ENABLE;
  HAL_MPU_ConfigRegion(&region);
  HAL_MPU_Enable(MPU_PRIVILEGED_DEFAULT);
}

static void lockShared() {
  while (HAL_HSEM_FastTake(SPIKE_HSEM_ID) != HAL_OK) {
  }
  __DMB();  // No shared read may happen before the lock is held.
}

static void unlockShared() {
  __DMB();  // Every shared write must land before the release.
  HAL_HSEM_Release(SPIKE_HSEM_ID, 0);
}

// The same computation as the Juno method the M4 runs, so the M7 can check the M4's answer.
static int32_t expectedJunoResult(int32_t n) {
  int32_t total = 0;
  for (int32_t i = 1; i <= n; i++) {
    total += (i % 3 == 0) ? i * 2 : i;
  }
  return total;
}

static bool m4Ready = false;

void setup() {
  Serial.begin(9600);
  delay(2000);
  __HAL_RCC_HSEM_CLK_ENABLE();
  markD3NonCacheable();
  spikeShared->m4State = SPIKE_M4_BOOTING;
  spikeShared->m4Heartbeat = 0;
  spikeShared->generation = 0;
  spikeShared->m4DoneGeneration = 0;
  spikeShared->magic = SPIKE_MAGIC;
  bootM4();
  uint32_t start = millis();
  while (spikeShared->m4State != SPIKE_M4_READY && millis() - start < 3000) {
    delay(10);
  }
  m4Ready = spikeShared->m4State == SPIKE_M4_READY;
}

void loop() {
  if (!m4Ready) {
    Serial.print("[spike] M4 did not report ready; heartbeat=");
    Serial.println(spikeShared->m4Heartbeat);
    delay(3000);
    return;
  }
  uint32_t generation = spikeShared->generation + 1;
  spikeShared->lockedCounter = 0;
  spikeShared->racyCounter = 0;
  spikeShared->junoArgument = 1000 + static_cast<int32_t>(generation);
  spikeShared->junoResult = 0;
  spikeShared->generation = generation;  // Starts the M4.

  uint32_t started = micros();
  for (uint32_t i = 0; i < SPIKE_ITERATIONS; i++) {
    lockShared();
    spikeShared->lockedCounter = spikeShared->lockedCounter + 1;
    unlockShared();
    spikeShared->racyCounter = spikeShared->racyCounter + 1;
  }
  uint32_t m7Micros = micros() - started;

  uint32_t waitStart = millis();
  while (spikeShared->m4DoneGeneration != generation && millis() - waitStart < 5000) {
    delay(1);
  }
  bool m4Done = spikeShared->m4DoneGeneration == generation;
  int32_t expected = expectedJunoResult(spikeShared->junoArgument);

  Serial.print("[spike] run ");
  Serial.print(generation);
  Serial.print(m4Done ? " m4=done" : " m4=TIMEOUT");
  Serial.print(" locked=");
  Serial.print(spikeShared->lockedCounter);
  Serial.print("/");
  Serial.print(2 * SPIKE_ITERATIONS);
  Serial.print(spikeShared->lockedCounter == 2 * SPIKE_ITERATIONS ? " OK" : " LOST");
  Serial.print(" racy=");
  Serial.print(spikeShared->racyCounter);
  Serial.print(" juno(");
  Serial.print(spikeShared->junoArgument);
  Serial.print(")=");
  Serial.print(spikeShared->junoResult);
  Serial.print(spikeShared->junoResult == expected ? " OK" : " MISMATCH expected ");
  if (spikeShared->junoResult != expected) {
    Serial.print(expected);
  }
  Serial.print(" m7us=");
  Serial.println(m7Micros);
  delay(3000);
}

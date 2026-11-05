// Replaces the Nano ESP32 variant's variant.cpp in the emulator image; every other variant file (pin map, pin
// remapping) is the core's own. The board's version runs double-tap recovery from a static constructor: it marks RTC
// memory, pulses the RGB LED through LEDC, and restarts into the factory partition on a second boot. QEMU models
// neither LEDC nor that recovery image, so the harness keeps only what the rest of the core links against.
#include "Arduino.h"

extern "C" {
    void initVariant() {
    }
}

bool _recovery_marker_found;
bool _recovery_active;

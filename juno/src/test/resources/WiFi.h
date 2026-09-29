#pragma once

// The Zephyr core exposes the same Arduino WiFi/WiFiUDP surface under different headers. Reuse the
// offline test doubles so generated UNO Q shims can be syntax-checked without installing the core.
#include "WiFiS3.h"

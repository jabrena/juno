// Declarations-only stand-in for the Zephyr GPIO and devicetree API the UNO Q ParallelBus helpers use, so the
// generated shim can be syntax-checked offline. The pin table expands to a single unconnected pin.
#pragma once

#include <stdint.h>

struct device {
  int unused;
};
struct gpio_dt_spec {
  const struct device* port;
  uint8_t pin;
  uint16_t dt_flags;
};

#define DT_PATH(node) 0
#define DT_FOREACH_PROP_ELEM_SEP(node, prop, fn, sep) {nullptr, 0, 0}

inline int gpio_port_set_clr_bits_raw(const struct device*, uint32_t, uint32_t) {
  return 0;
}

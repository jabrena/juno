package io.github.jabrena.juno.backend;

/**
 * The shim half of {@code ParallelBus}. Each pin is resolved once to its GPIO port and bit; a byte then becomes one
 * set/clear write per port the data pins span, and the strobe two more. The bit-to-port bookkeeping is the same on
 * every core: a {@link CoreRuntime} only supplies its port handle type, how an Arduino pin number resolves to a port
 * and bit mask, and the write that sets some bits of a port and clears others in one go.
 */
final class ParallelBusRuntime {
    private ParallelBusRuntime() {
    }

    /**
     * @param prelude   includes and declarations the core's snippets need
     * @param portType  the C type of a port handle
     * @param resolve   statements setting {@code port} and {@code mask} from the Arduino pin number {@code pin}
     * @param write     a statement setting bits {@code set} and clearing bits {@code clear} of {@code port}
     */
    static String helpers(String prelude, String portType, String resolve, String write) {
        return """

                // ParallelBus: eight data pins (0-7) and the strobe (8), each resolved once to a port and a bit.
                ${PRELUDE}
                typedef ${PORT_TYPE} juno_bus_port_t;
                static juno_bus_port_t juno_bus_ports[9];
                static uint32_t juno_bus_masks[9];
                static uint8_t juno_bus_slots[9];
                static int32_t juno_bus_port_count;

                static inline void juno_bus_port_write(juno_bus_port_t port, uint32_t set, uint32_t clear) {
                  ${WRITE}
                }

                static void juno_bus_resolve(int32_t pin, int32_t index) {
                  juno_bus_port_t port;
                  uint32_t mask;
                  ${RESOLVE}
                  int32_t slot = 0;
                  while (slot < juno_bus_port_count && juno_bus_ports[slot] != port) {
                    slot++;
                  }
                  if (slot == juno_bus_port_count) {
                    juno_bus_ports[slot] = port;
                    juno_bus_port_count++;
                  }
                  juno_bus_slots[index] = static_cast<uint8_t>(slot);
                  juno_bus_masks[index] = mask;
                }

                extern "C" void juno_parallel_bus_begin(const int32_t* pins, int32_t strobe) {
                  juno_bus_port_count = 0;
                  for (int32_t i = 0; i < 8; i++) {
                    juno_bus_resolve(pins[i], i);
                  }
                  juno_bus_resolve(strobe, 8);
                  juno_bus_port_write(juno_bus_ports[juno_bus_slots[8]], juno_bus_masks[8], 0);
                }

                // The bits to set and to clear on each port so the data pins show value's low byte.
                static void juno_bus_levels(int32_t value, uint32_t* set, uint32_t* clear) {
                  for (int32_t slot = 0; slot < juno_bus_port_count; slot++) {
                    set[slot] = 0;
                    clear[slot] = 0;
                  }
                  for (int32_t i = 0; i < 8; i++) {
                    if ((value >> i) & 1) {
                      set[juno_bus_slots[i]] |= juno_bus_masks[i];
                    } else {
                      clear[juno_bus_slots[i]] |= juno_bus_masks[i];
                    }
                  }
                }

                static inline void juno_bus_drive(const uint32_t* set, const uint32_t* clear) {
                  for (int32_t slot = 0; slot < juno_bus_port_count; slot++) {
                    if ((set[slot] | clear[slot]) != 0) {
                      juno_bus_port_write(juno_bus_ports[slot], set[slot], clear[slot]);
                    }
                  }
                }

                static inline void juno_bus_strobe() {
                  juno_bus_port_t port = juno_bus_ports[juno_bus_slots[8]];
                  juno_bus_port_write(port, 0, juno_bus_masks[8]);
                  juno_bus_port_write(port, juno_bus_masks[8], 0);
                }

                extern "C" void juno_parallel_bus_write(int32_t value) {
                  uint32_t set[9];
                  uint32_t clear[9];
                  juno_bus_levels(value, set, clear);
                  juno_bus_drive(set, clear);
                  juno_bus_strobe();
                }

                // A color whose two bytes are equal sets the data pins once and then only toggles the strobe.
                extern "C" void juno_parallel_bus_repeat16(int32_t value, int32_t count) {
                  uint32_t highSet[9];
                  uint32_t highClear[9];
                  uint32_t lowSet[9];
                  uint32_t lowClear[9];
                  juno_bus_levels(value >> 8, highSet, highClear);
                  juno_bus_levels(value, lowSet, lowClear);
                  bool same = ((value >> 8) & 0xFF) == (value & 0xFF);
                  if (same) {
                    juno_bus_drive(highSet, highClear);
                  }
                  for (int32_t i = 0; i < count; i++) {
                    if (!same) {
                      juno_bus_drive(highSet, highClear);
                    }
                    juno_bus_strobe();
                    if (!same) {
                      juno_bus_drive(lowSet, lowClear);
                    }
                    juno_bus_strobe();
                  }
                }
                """
                .replace("${PRELUDE}", prelude)
                .replace("${PORT_TYPE}", portType)
                .replace("${RESOLVE}", resolve)
                .replace("${WRITE}", write);
    }
}

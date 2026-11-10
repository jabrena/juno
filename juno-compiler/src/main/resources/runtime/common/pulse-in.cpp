// Gpio.pulseIn: the core times the pulse, so a Juno thread switch in the middle cannot stretch it, as it could a pulse
// timed by polling the pin from Java. Returns 0 when no whole pulse arrives within the timeout.
extern "C" int32_t juno_pulse_in(int32_t pin, int32_t high, int32_t timeoutMicros) {
  return static_cast<int32_t>(pulseIn(${JUNO_PULSE_PIN}, high != 0 ? HIGH : LOW,
                                      static_cast<unsigned long>(timeoutMicros)));
}

static constexpr int LOW = 0, HIGH = 1;
static uint32_t nowMicros;
static uint32_t micros() { return ++nowMicros; }
static void delayMicroseconds(uint32_t duration) { nowMicros += duration; }
static int attachedPin = -1, detachedPin = -1;
static uint32_t frequency, resolution, duty, peakDuty;
// Nano startup allocates RGB channels 7, 6 and 5; tone reserves channel 0.
// LEDC channels share timers in pairs, so avoiding just a channel is insufficient.
static uint32_t timerFrequency[] = {440, 0, 1000, 1000};
static uint32_t timerResolution[] = {10, 0, 8, 8};
static uint32_t channelDuty[] = {511, 0, 0, 0, 0, 255, 255, 255};
static double ledcSetup(uint8_t channel, double hz, uint8_t bits) {
  frequency = hz;
  resolution = bits;
  timerFrequency[channel / 2] = hz;
  timerResolution[channel / 2] = bits;
  return hz;
}
static void ledcAttachPin(int pin, uint8_t) { attachedPin = toGpio(pin); }
static void ledcDetachPin(int pin) { detachedPin = toGpio(pin); attachedPin = -1; }
static void ledcWrite(uint8_t channel, uint32_t value) {
  duty = value;
  channelDuty[channel] = value;
  if (value > peakDuty) peakDuty = value;
}
// Match the installed ESP32 core: tone reconfigures the timer to 10 bits.
static uint32_t ledcWriteTone(uint8_t channel, uint32_t hz) {
  ledcSetup(channel, hz, 10);
  ledcWrite(channel, 511);
  return hz;
}

// INFRARED_RUNTIME

int main() {
  // Grove emitter on the carrier's SCL/A5 socket, receiver on A0.
  juno_ir_begin_grove(14, 19, 2400);
  assert(attachedPin == 12);
  assert(frequency == 38000 && resolution == 8 && duty == 0);
  assert(juno_ir_receiving() == 0 && lastPin == 1);
  juno_ir_mark(417);
  assert(peakDuty == 85 && duty == 0);
  assert(resolution == 8);
  assert(timerFrequency[0] == 440 && timerResolution[0] == 10);
  assert(timerFrequency[2] == 1000 && timerResolution[2] == 8);
  assert(timerFrequency[3] == 1000 && timerResolution[3] == 8);
  assert(channelDuty[0] == 511);
  for (int channel = 5; channel < 8; ++channel) assert(channelDuty[channel] == 255);

  // Switching to HX releases PWM on the old physical pin, then uses the new pair.
  juno_ir_begin_hx(19, 14, 2400);
  assert(detachedPin == 12 && attachedPin == -1);
  assert(juno_ir_receiving() == 0 && lastPin == 12);
  juno_ir_mark(417);
  assert(lastPin == 1 && lastValue == LOW);

  // Disabled pins remain disabled rather than being remapped to a GPIO.
  juno_ir_begin_grove(-1, -1, 2400);
  assert(attachedPin == -1);
  assert(juno_ir_read_byte(10) == -1);
  assert(juno_ir_echo_byte(0x55, 0) == -1);
}

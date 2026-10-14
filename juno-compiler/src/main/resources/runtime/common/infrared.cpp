
static int32_t juno_ir_rx_pin = -1;
static int32_t juno_ir_tx_pin = -1;
static uint32_t juno_ir_bit_us = 417;
// Waits around each edge of one carrier cycle, tuned by juno_ir_calibrate_carrier so that a cycle,
// pin writes and the caller's micros() check included, lasts one 38 kHz period on this board.
static uint32_t juno_ir_high_us = 8;
static uint32_t juno_ir_low_us = 8;

static void juno_ir_cycle(int level) {
  digitalWrite(juno_ir_tx_pin, level);
  if (juno_ir_high_us > 0) {
    delayMicroseconds(juno_ir_high_us);
  }
  digitalWrite(juno_ir_tx_pin, LOW);
  if (juno_ir_low_us > 0) {
    delayMicroseconds(juno_ir_low_us);
  }
}

// Times dark cycles (the LED stays off) and shortens the waits by what the pin writes and micros()
// cost here, since a fixed 8 + 8 us gives about 38 kHz only on a fast core and far less on AVR.
static void juno_ir_calibrate_carrier() {
  const int32_t periodTenthsUs = 263;
  const int cycles = 100;
  int32_t waitTenthsUs = 160;
  for (int pass = 0; pass < 3; pass++) {
    uint32_t start = micros();
    // A deadline that is never reached, so each cycle pays the same micros() check as a real carrier.
    uint32_t never = start + 0x40000000UL;
    for (int i = 0; i < cycles; i++) {
      if (static_cast<int32_t>(micros() - never) >= 0) {
        break;
      }
      juno_ir_cycle(LOW);
    }
    int32_t measuredTenthsUs = static_cast<int32_t>((micros() - start) * 10 / cycles);
    waitTenthsUs += periodTenthsUs - measuredTenthsUs;
    if (waitTenthsUs < 0) {
      waitTenthsUs = 0;
    }
    uint32_t waitUs = static_cast<uint32_t>((waitTenthsUs + 5) / 10);
    juno_ir_high_us = waitUs / 2;
    juno_ir_low_us = waitUs - juno_ir_high_us;
  }
}

extern "C" void juno_ir_begin(int32_t receivePin, int32_t transmitPin, int32_t baud) {
  juno_ir_rx_pin = receivePin;
  juno_ir_tx_pin = transmitPin;
  juno_ir_bit_us = baud > 0 ? static_cast<uint32_t>(1000000 / baud) : 417;
  if (receivePin >= 0) {
    pinMode(receivePin, INPUT);
  }
  if (transmitPin >= 0) {
    pinMode(transmitPin, OUTPUT);
    digitalWrite(transmitPin, LOW);
    juno_ir_calibrate_carrier();
  }
}

extern "C" int32_t juno_ir_receiving() {
  return juno_ir_rx_pin >= 0 && digitalRead(juno_ir_rx_pin) == LOW ? 1 : 0;
}

static void juno_ir_wait_until(uint32_t at) {
  while (static_cast<int32_t>(micros() - at) < 0) {
  }
}

// Modulates the IR LED until micros() reaches at. Every carrier goes through this loop, whose
// per-cycle cost juno_ir_calibrate_carrier measured, so callers do their bookkeeping between calls.
static void juno_ir_carrier_until(uint32_t at) {
  while (static_cast<int32_t>(micros() - at) < 0) {
    juno_ir_cycle(HIGH);
  }
}

extern "C" int32_t juno_ir_read_byte(int32_t timeoutMicros) {
  if (juno_ir_rx_pin < 0) {
    return -1;
  }
  uint32_t waitStart = micros();
  while (digitalRead(juno_ir_rx_pin) != LOW) {
    if (micros() - waitStart > static_cast<uint32_t>(timeoutMicros)) {
      return -1;
    }
  }
  uint32_t start = micros();
  int32_t value = 0;
  int ones = 0;
  // Sample in the middle of each bit; bit 0 is the start bit, 1..8 data, 9 parity.
  for (int bit = 1; bit <= 9; bit++) {
    juno_ir_wait_until(start + juno_ir_bit_us * bit + juno_ir_bit_us / 2);
    int level = digitalRead(juno_ir_rx_pin) == HIGH ? 1 : 0;
    if (bit <= 8) {
      value |= level << (bit - 1);
    }
    ones += level;
  }
  juno_ir_wait_until(start + juno_ir_bit_us * 10 + juno_ir_bit_us / 2);
  // Odd parity: data and parity bits hold an odd number of ones; the stop bit is high.
  if ((ones & 1) != 1 || digitalRead(juno_ir_rx_pin) != HIGH) {
    return -2;
  }
  return value;
}

// Holds the bit until the absolute time end, so the overrun of the last carrier cycle in a 0 bit never
// accumulates over a byte: timed from each bit's own start, a run of 0 bits drifted by a cycle per bit.
static void juno_ir_send_bit(int bit, uint32_t end) {
  if (bit != 0) {
    digitalWrite(juno_ir_tx_pin, LOW);
    juno_ir_wait_until(end);
    return;
  }
  juno_ir_carrier_until(end);
}

// Transmits a framed byte while sampling the receive pin, which hears the own LED through the
// demodulator, delayed by its response time: each bit is sampled in its middle plus latencyMicros.
extern "C" int32_t juno_ir_echo_byte(int32_t value, int32_t latencyMicros) {
  if (juno_ir_tx_pin < 0 || juno_ir_rx_pin < 0) {
    return -1;
  }
  int sent[11];
  int ones = 0;
  sent[0] = 0;
  for (int bit = 0; bit < 8; bit++) {
    sent[bit + 1] = (value >> bit) & 1;
    ones += sent[bit + 1];
  }
  sent[9] = (ones & 1) == 0 ? 1 : 0;
  sent[10] = 1;
  int heard[11];
  int sampled = 0;
  uint32_t start = micros();
  uint32_t total = juno_ir_bit_us * 11 + juno_ir_bit_us / 2 + static_cast<uint32_t>(latencyMicros);
  // Runs from one event (a bit boundary or a sample time) to the next, with the carrier on through
  // bits that are 0; any work inside the carrier loop would slow it below 38 kHz.
  int index = 0;
  uint32_t nextBit = juno_ir_bit_us;
  uint32_t nextSample = juno_ir_bit_us / 2 + static_cast<uint32_t>(latencyMicros);
  uint32_t elapsed = 0;
  while (elapsed < total) {
    uint32_t next = total;
    if (index < 11 && nextBit < next) {
      next = nextBit;
    }
    if (sampled < 11 && nextSample < next) {
      next = nextSample;
    }
    if (index < 11 && sent[index] == 0) {
      juno_ir_carrier_until(start + next);
    } else {
      digitalWrite(juno_ir_tx_pin, LOW);
      juno_ir_wait_until(start + next);
    }
    elapsed = micros() - start;
    while (index < 11 && elapsed >= nextBit) {
      index++;
      nextBit += juno_ir_bit_us;
    }
    while (sampled < 11 && elapsed >= nextSample) {
      heard[sampled] = digitalRead(juno_ir_rx_pin) == HIGH ? 1 : 0;
      sampled++;
      nextSample += juno_ir_bit_us;
    }
  }
  digitalWrite(juno_ir_tx_pin, LOW);
  if (heard[0] != 0) {
    return -1;
  }
  int32_t received = 0;
  int receivedOnes = 0;
  for (int bit = 0; bit < 8; bit++) {
    received |= heard[bit + 1] << bit;
    receivedOnes += heard[bit + 1];
  }
  receivedOnes += heard[9];
  if ((receivedOnes & 1) != 1 || heard[10] != 1) {
    return -2;
  }
  return received;
}

// A raw burst of the 38 kHz carrier, or silence, for protocols that encode bits in pulse lengths.
extern "C" void juno_ir_mark(int32_t durationMicros) {
  if (juno_ir_tx_pin < 0) {
    return;
  }
  juno_ir_carrier_until(micros() + static_cast<uint32_t>(durationMicros));
}

extern "C" void juno_ir_space(int32_t durationMicros) {
  if (juno_ir_tx_pin < 0) {
    return;
  }
  digitalWrite(juno_ir_tx_pin, LOW);
  juno_ir_wait_until(micros() + static_cast<uint32_t>(durationMicros));
}

extern "C" void juno_ir_write_byte(int32_t value) {
  if (juno_ir_tx_pin < 0) {
    return;
  }
  int ones = 0;
  uint32_t start = micros();
  juno_ir_send_bit(0, start + juno_ir_bit_us);
  for (int bit = 0; bit < 8; bit++) {
    int level = (value >> bit) & 1;
    ones += level;
    juno_ir_send_bit(level, start + juno_ir_bit_us * static_cast<uint32_t>(bit + 2));
  }
  juno_ir_send_bit((ones & 1) == 0 ? 1 : 0, start + juno_ir_bit_us * 10);
  juno_ir_send_bit(1, start + juno_ir_bit_us * 11);
}

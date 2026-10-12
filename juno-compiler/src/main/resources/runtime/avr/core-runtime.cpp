// AVR stack frames have no alignment, so a reference's two bytes can start at any stack address.
static void juno_gc_scan_bytes(const uint8_t* from, const uint8_t* to) {
  for (const uint8_t* cursor = from; cursor + sizeof(uintptr_t) <= to; cursor++) {
    uintptr_t word;
    memcpy(&word, cursor, sizeof word);
    juno_gc_mark_candidate(word);
  }
}

// Called directly by the generated code (size in r22-r25, the reference back in r22-r25).
extern "C" int32_t juno_avr_alloc(int32_t size) {
  return static_cast<int32_t>(reinterpret_cast<intptr_t>(juno_alloc(static_cast<uint32_t>(size), 4u)));
}

// Java int arithmetic with no AVR instruction, called directly with avr-gcc's convention.
extern "C" int32_t juno_avr_imul(int32_t a, int32_t b) {
  return static_cast<int32_t>(static_cast<uint32_t>(a) * static_cast<uint32_t>(b));
}

// The generated code checks for a zero divisor first and throws ArithmeticException.
extern "C" int32_t juno_avr_idiv(int32_t a, int32_t b) {
  if (b == 0) return 0;
  if (a == INT32_MIN && b == -1) return INT32_MIN;
  return a / b;
}

extern "C" int32_t juno_avr_irem(int32_t a, int32_t b) {
  if (b == 0) return a;
  if (b == -1) return 0;
  return a % b;
}

extern "C" int32_t juno_avr_ishl(int32_t a, int32_t b) {
  return static_cast<int32_t>(static_cast<uint32_t>(a) << (b & 31));
}

extern "C" int32_t juno_avr_ishr(int32_t a, int32_t b) {
  return a >> (b & 31);
}

extern "C" int32_t juno_avr_iushr(int32_t a, int32_t b) {
  return static_cast<int32_t>(static_cast<uint32_t>(a) >> (b & 31));
}

// The core's pin and time functions take 8- and 16-bit parameters; Java passes ints.
extern "C" void juno_pin_mode(int32_t pin, int32_t mode) {
  pinMode(static_cast<uint8_t>(pin), static_cast<PinMode>(mode));
}

extern "C" void juno_digital_write(int32_t pin, int32_t value) {
  digitalWrite(static_cast<uint8_t>(pin), static_cast<PinStatus>(value != 0 ? HIGH : LOW));
}

extern "C" int32_t juno_digital_read(int32_t pin) {
  return digitalRead(static_cast<uint8_t>(pin)) == HIGH ? 1 : 0;
}

extern "C" int32_t juno_analog_read(int32_t pin) {
  return analogRead(static_cast<uint8_t>(pin));
}

extern "C" void juno_analog_write(int32_t pin, int32_t value) {
  analogWrite(static_cast<uint8_t>(pin), static_cast<int>(value));
}

extern "C" int32_t juno_millis() {
  return static_cast<int32_t>(millis());
}

extern "C" int32_t juno_micros() {
  return static_cast<int32_t>(micros());
}

extern "C" void juno_delay_millis(int32_t milliseconds) {
  delay(static_cast<unsigned long>(milliseconds));
}

extern "C" void juno_delay_micros(int32_t microseconds) {
  delayMicroseconds(static_cast<unsigned int>(microseconds));
}

extern "C" void juno_serial_begin(int32_t baud) {
  Serial.begin(static_cast<unsigned long>(baud));
}

extern "C" void juno_serial_print(int32_t value) {
  Serial.print(static_cast<long>(value));
}

extern "C" void juno_serial_println(int32_t value) {
  Serial.println(static_cast<long>(value));
}

// Print has no 64-bit overloads on every core version; format it here.
static void juno_serial_write_long(int64_t value) {
  char digits[21];
  uint8_t index = sizeof digits - 1;
  digits[index] = '\0';
  uint64_t magnitude = value < 0 ? 0ull - static_cast<uint64_t>(value) : static_cast<uint64_t>(value);
  do {
    digits[--index] = static_cast<char>('0' + static_cast<uint8_t>(magnitude % 10u));
    magnitude /= 10u;
  } while (magnitude != 0u);
  if (value < 0) digits[--index] = '-';
  Serial.print(&digits[index]);
}

extern "C" void juno_serial_print_long(int64_t value) {
  juno_serial_write_long(value);
}

extern "C" void juno_serial_println_long(int64_t value) {
  juno_serial_write_long(value);
  Serial.println();
}

// Arduino's Print::printFloat with two decimals, as the 32-bit boards print a double, on binary64.
static void juno_serial_write_double(juno_f64 number) {
  if (juno_f64_is_nan(number)) { Serial.print("nan"); return; }
  if (juno_f64_exp(number) == 0x7FF) { Serial.print("inf"); return; }
  if (juno_f64_compare(number, ${OVERFLOW}) > 0 || juno_f64_compare(number, ${NEGATIVE_OVERFLOW}) < 0) {
    Serial.print("ovf");
    return;
  }
  if (juno_f64_compare(number, 0) < 0) {
    Serial.print('-');
    number ^= 0x8000000000000000ull;
  }
  juno_f64 rounding = ${HALF};
  for (uint8_t i = 0; i < 2; ++i) rounding = juno_f64_div(rounding, ${TEN});
  number = juno_f64_add(number, rounding);
  uint32_t integerPart = static_cast<uint32_t>(juno_f64_to_i64(number));
  juno_f64 remainder = juno_f64_sub(number, juno_f64_from_i64(integerPart));
  Serial.print(static_cast<unsigned long>(integerPart));
  Serial.print('.');
  for (uint8_t digits = 0; digits < 2; digits++) {
    remainder = juno_f64_mul(remainder, ${TEN});
    uint32_t toPrint = static_cast<uint32_t>(juno_f64_to_i64(remainder));
    Serial.print(static_cast<unsigned long>(toPrint));
    remainder = juno_f64_sub(remainder, juno_f64_from_i64(toPrint));
  }
}

extern "C" void juno_serial_print_float(juno_f32 value) {
  juno_serial_write_double(juno_f64_from_f32(value));
}

extern "C" void juno_serial_println_float(juno_f32 value) {
  juno_serial_write_double(juno_f64_from_f32(value));
  Serial.println();
}

extern "C" void juno_serial_print_double(juno_f64 value) {
  juno_serial_write_double(value);
}

extern "C" void juno_serial_println_double(juno_f64 value) {
  juno_serial_write_double(value);
  Serial.println();
}

// A runtime String (e.g. Throwable.getMessage()) may be null; Java prints "null".
extern "C" void juno_serial_print_str(const char* value) {
  Serial.print(value != nullptr ? value : "null");
}

extern "C" void juno_serial_println_str(const char* value) {
  Serial.println(value != nullptr ? value : "null");
}

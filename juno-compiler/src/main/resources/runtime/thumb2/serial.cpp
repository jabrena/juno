${JUNO_LED_MATRIX_FUNCTIONS}extern "C" void juno_serial_begin(int32_t baud) {
  Serial.begin(static_cast<unsigned long>(baud));
}

extern "C" void juno_serial_print(int32_t value) {
  Serial.print(value);
}

extern "C" void juno_serial_println(int32_t value) {
  Serial.println(value);
}

extern "C" void juno_serial_print_long(int64_t value) {
  Serial.print(static_cast<long long>(value));
}

extern "C" void juno_serial_println_long(int64_t value) {
  Serial.println(static_cast<long long>(value));
}

extern "C" JUNO_ASM_ABI void juno_serial_print_float(float value) {
  Serial.print(static_cast<double>(value));
}

extern "C" JUNO_ASM_ABI void juno_serial_println_float(float value) {
  Serial.println(static_cast<double>(value));
}

extern "C" JUNO_ASM_ABI void juno_serial_print_double(double value) {
  Serial.print(value);
}

extern "C" JUNO_ASM_ABI void juno_serial_println_double(double value) {
  Serial.println(value);
}

// A runtime String (e.g. Throwable.getMessage()) may be null; Java prints "null".
extern "C" void juno_serial_print_str(const char* value) {
  Serial.print(value != nullptr ? value : "null");
}

extern "C" void juno_serial_println_str(const char* value) {
  Serial.println(value != nullptr ? value : "null");
}

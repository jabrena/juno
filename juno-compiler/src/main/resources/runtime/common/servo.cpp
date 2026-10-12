
static Servo junoServos[NUM_DIGITAL_PINS];

extern "C" void juno_servo_attach(int32_t pin) {
  if (pin >= 0 && pin < NUM_DIGITAL_PINS) {
    junoServos[pin].attach(pin);
  }
}

extern "C" void juno_servo_write(int32_t pin, int32_t angleDegrees) {
  if (pin >= 0 && pin < NUM_DIGITAL_PINS) {
    junoServos[pin].write(angleDegrees);
  }
}

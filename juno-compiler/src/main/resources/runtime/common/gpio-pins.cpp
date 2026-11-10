static constexpr int32_t juno_gpio_digital_pins[] = {
${JUNO_GPIO_DIGITAL_PINS}, -1};
static constexpr int32_t juno_gpio_analog_pins[] = {
${JUNO_GPIO_ANALOG_PINS}, -1};
static constexpr int32_t juno_gpio_additional_digital_pins[] = {
${JUNO_GPIO_ADDITIONAL_DIGITAL_PINS}};
static constexpr int32_t juno_gpio_additional_analog_pins[] = {
${JUNO_GPIO_ADDITIONAL_ANALOG_PINS}};

extern "C" void* juno_alloc(uint32_t size, uint32_t alignment);
extern "C" void juno_throw_raise(int32_t exception);

static int32_t juno_gpio_suffix(const char* name, char prefix) {
  if (name == nullptr || name[0] != prefix || name[1] < '0' || name[1] > '9') return -1;
  int32_t value = name[1] - '0';
  if (name[2] == '\0') return value;
  if (name[1] == '0' || name[2] < '0' || name[2] > '9' || name[3] != '\0') return -1;
  return value * 10 + name[2] - '0';
}

static int32_t juno_gpio_alias(const char* name, const int32_t* pins) {
  if (name == nullptr) return -1;
  int32_t offset = 0;
  if (name[0] == 'G') {
    if (name[1] == 'P' && name[2] == 'I' && name[3] == 'O') {
      offset = 4;
    } else if (name[1] == 'P') {
      // The Raspberry Pi Pico's own GPn pin names.
      offset = 2;
    } else {
      offset = 1;
    }
  } else {
    return -1;
  }
  if (name[offset] < '0' || name[offset] > '9') return -1;
  int32_t value = name[offset] - '0';
  if (name[offset + 1] != '\0') {
    if (name[offset] == '0' || name[offset + 1] < '0' || name[offset + 1] > '9'
        || name[offset + 2] != '\0') return -1;
    value = value * 10 + name[offset + 1] - '0';
  }
  for (int32_t index = 0; pins[index] >= 0; index++) {
    if (pins[index] == value) return value;
  }
  return -1;
}

static int32_t juno_gpio_invalid_pin(const char* message) {
  auto* exception = static_cast<int32_t*>(juno_alloc(${JUNO_THROWABLE_BYTES}u, 4u));
  exception[${JUNO_CLASS_ID_WORD}] = ${JUNO_ILLEGAL_ARGUMENT_EXCEPTION_CLASS_ID};
  exception[${JUNO_MESSAGE_WORD}] = static_cast<int32_t>(reinterpret_cast<intptr_t>(message));
  juno_throw_raise(static_cast<int32_t>(reinterpret_cast<intptr_t>(exception)));
  return 0;
}

extern "C" int32_t juno_gpio_digital_pin(const char* name) {
  int32_t index = juno_gpio_suffix(name, 'D');
  if (index >= 0 && index < 14 && juno_gpio_digital_pins[index] >= 0) {
    return juno_gpio_digital_pins[index];
  }
  int32_t alias = juno_gpio_alias(name, juno_gpio_additional_digital_pins);
  return alias >= 0 ? alias : juno_gpio_invalid_pin("Invalid digital pin name");
}

extern "C" int32_t juno_gpio_analog_pin(const char* name) {
  int32_t index = juno_gpio_suffix(name, 'A');
  if (index >= 0 && index < 6 && juno_gpio_analog_pins[index] >= 0) {
    return juno_gpio_analog_pins[index];
  }
  int32_t alias = juno_gpio_alias(name, juno_gpio_additional_analog_pins);
  return alias >= 0 ? alias : juno_gpio_invalid_pin("Invalid analog pin name");
}

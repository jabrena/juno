static ${JUNO_LED_MATRIX_TYPE} juno_led_matrix;

extern "C" void juno_led_matrix_begin() {
  juno_led_matrix.begin();
}

extern "C" void juno_led_matrix_load_frame(${JUNO_LED_MATRIX_PARAMETERS}) {
${JUNO_LED_MATRIX_UNUSED}  const uint32_t frame[${JUNO_LED_MATRIX_WORDS}] = {
${JUNO_LED_MATRIX_VALUES}
  };
  juno_led_matrix.loadFrame(frame);
}

extern "C" void juno_led_matrix_clear() {
  const uint32_t frame[${JUNO_LED_MATRIX_WORDS}] = {};
  juno_led_matrix.loadFrame(frame);
}


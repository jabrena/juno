extern "C" JUNO_ASM_ABI void juno_string_concat_append_float(int32_t handle, float value) {
  char text[JUNO_STRING_SLOT_SIZE];
  juno_format_double(text, value);
  juno_string_concat_append_string(handle, text);
}

extern "C" JUNO_ASM_ABI void juno_string_concat_append_double(int32_t handle, double value) {
  char text[JUNO_STRING_SLOT_SIZE];
  juno_format_double(text, value);
  juno_string_concat_append_string(handle, text);
}

extern "C" JUNO_ASM_ABI int32_t juno_string_concat_measure_float(int32_t length, float value) {
  char text[JUNO_STRING_SLOT_SIZE];
  juno_format_double(text, value);
  return length + static_cast<int32_t>(strlen(text));
}

extern "C" JUNO_ASM_ABI int32_t juno_string_concat_measure_double(int32_t length, double value) {
  char text[JUNO_STRING_SLOT_SIZE];
  juno_format_double(text, value);
  return length + static_cast<int32_t>(strlen(text));
}

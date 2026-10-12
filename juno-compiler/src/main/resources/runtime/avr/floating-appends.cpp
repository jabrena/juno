
extern "C" void juno_string_concat_append_float(int32_t handle, juno_f32 value) {
  char text[JUNO_STRING_SLOT_SIZE];
  juno_format_double(text, juno_f64_from_f32(value));
  juno_string_concat_append_string(handle, text);
}

extern "C" void juno_string_concat_append_double(int32_t handle, juno_f64 value) {
  char text[JUNO_STRING_SLOT_SIZE];
  juno_format_double(text, value);
  juno_string_concat_append_string(handle, text);
}

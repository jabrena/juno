
extern "C" void juno_m5_keyboard_begin() {
  juno_m5_begin(true);
}

extern "C" void juno_m5_keyboard_update() {
  M5Cardputer.Keyboard.updateKeyList();
  M5Cardputer.Keyboard.updateKeysState();
}

extern "C" int32_t juno_m5_keyboard_is_changed() {
  return M5Cardputer.Keyboard.isChange();
}

extern "C" int32_t juno_m5_keyboard_is_pressed() {
  return M5Cardputer.Keyboard.isPressed() != 0;
}

extern "C" int32_t juno_m5_keyboard_is_key_pressed(int32_t key) {
  const auto& state = M5Cardputer.Keyboard.keysState();
  if (key == 8) return state.del;
  if (key == 9) return state.tab;
  if (key == 10) return state.enter;
  return M5Cardputer.Keyboard.isKeyPressed(static_cast<char>(key));
}

extern "C" int32_t juno_m5_keyboard_key_count() {
  const auto& state = M5Cardputer.Keyboard.keysState();
  return state.word.size() + state.del + state.tab + state.enter;
}

extern "C" int32_t juno_m5_keyboard_key_at(int32_t index) {
  if (index < 0) return 0;
  const auto& state = M5Cardputer.Keyboard.keysState();
  const int32_t characters = state.word.size();
  if (index < characters) return static_cast<uint8_t>(state.word[index]);
  index -= characters;
  if (state.del && index-- == 0) return 8;
  if (state.tab && index-- == 0) return 9;
  if (state.enter && index == 0) return 10;
  return 0;
}

extern "C" int32_t juno_m5_keyboard_is_shift_pressed() {
  return M5Cardputer.Keyboard.keysState().shift;
}

extern "C" int32_t juno_m5_keyboard_is_control_pressed() {
  return M5Cardputer.Keyboard.keysState().ctrl;
}

extern "C" int32_t juno_m5_keyboard_is_alt_pressed() {
  return M5Cardputer.Keyboard.keysState().alt;
}

extern "C" int32_t juno_m5_keyboard_is_fn_pressed() {
  return M5Cardputer.Keyboard.keysState().fn;
}

extern "C" int32_t juno_m5_keyboard_is_option_pressed() {
  return M5Cardputer.Keyboard.keysState().opt;
}

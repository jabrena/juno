
static bool juno_m5_started = false;
static bool juno_m5_keyboard_started = false;

static void juno_m5_begin(bool keyboard) {
  if (!juno_m5_started) {
    M5Cardputer.begin(M5.config(), keyboard);
    juno_m5_started = true;
    juno_m5_keyboard_started = keyboard;
  } else if (keyboard && !juno_m5_keyboard_started) {
    M5Cardputer.Keyboard.begin();
    juno_m5_keyboard_started = true;
  }
}

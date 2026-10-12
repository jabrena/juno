
extern "C" void juno_mouse_begin() {
  Mouse.begin();
}

extern "C" void juno_mouse_move(int32_t x, int32_t y) {
  Mouse.move(static_cast<signed char>(x), static_cast<signed char>(y));
}

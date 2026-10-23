
extern "C" void juno_m5_display_begin() {
  juno_m5_begin(false);
}

extern "C" int32_t juno_m5_display_width() {
  return M5Cardputer.Display.width();
}

extern "C" int32_t juno_m5_display_height() {
  return M5Cardputer.Display.height();
}

extern "C" void juno_m5_display_fill_screen(int32_t color) {
  M5Cardputer.Display.fillScreen(static_cast<uint16_t>(color));
}

extern "C" void juno_m5_display_draw_pixel(int32_t x, int32_t y, int32_t color) {
  M5Cardputer.Display.drawPixel(x, y, static_cast<uint16_t>(color));
}

extern "C" void juno_m5_display_fill_rect(int32_t x, int32_t y, int32_t width, int32_t height, int32_t color) {
  M5Cardputer.Display.fillRect(x, y, width, height, static_cast<uint16_t>(color));
}

extern "C" void juno_m5_display_set_cursor(int32_t x, int32_t y) {
  M5Cardputer.Display.setCursor(x, y);
}

extern "C" void juno_m5_display_set_text_color(int32_t foreground, int32_t background) {
  M5Cardputer.Display.setTextColor(static_cast<uint16_t>(foreground), static_cast<uint16_t>(background));
}

extern "C" void juno_m5_display_set_text_size(int32_t size) {
  M5Cardputer.Display.setTextSize(size);
}

extern "C" void juno_m5_display_print(const char* text) {
  M5Cardputer.Display.print(text);
}

extern "C" void juno_m5_display_println(const char* text) {
  M5Cardputer.Display.println(text);
}

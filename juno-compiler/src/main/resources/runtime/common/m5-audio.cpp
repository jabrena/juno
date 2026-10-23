extern "C" void juno_m5_audio_set_volume(int32_t volume) {
  juno_m5_begin(false);
  int32_t clamped_volume = volume < 0 ? 0 : (volume > 255 ? 255 : volume);
  M5Cardputer.Speaker.setVolume(static_cast<uint8_t>(clamped_volume));
}

extern "C" void juno_m5_audio_tone(int32_t frequency_hz, int32_t duration_millis) {
  juno_m5_begin(false);
  M5Cardputer.Speaker.tone(static_cast<uint16_t>(frequency_hz), static_cast<uint32_t>(duration_millis));
}

extern "C" void juno_m5_audio_stop() {
  juno_m5_begin(false);
  M5Cardputer.Speaker.stop();
}

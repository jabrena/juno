// ---- Juno named secrets in ESP32 non-volatile storage. One Preferences entry per logical slot, in a namespace of
// Juno's own. NVS is its own flash partition, so a value outlives a sketch upload; it is not a secure element, and a
// flash readout exposes it unless the board has flash encryption enabled (see Esp32Nvs). ----
static Preferences juno_nvs_store;
static bool juno_nvs_started = false;

static const char* juno_nvs_key(int32_t slot) {
  static char key[8];
  key[0] = 's';
  key[1] = static_cast<char>('0' + (slot / 10) % 10);
  key[2] = static_cast<char>('0' + slot % 10);
  key[3] = '\0';
  return key;
}

extern "C" int32_t juno_nvs_begin() {
  if (!juno_nvs_started) {
    juno_nvs_started = juno_nvs_store.begin("juno-secrets", false);
  }
  return juno_nvs_started ? 1 : 0;
}

extern "C" int32_t juno_nvs_read(int32_t slot, int8_t* buffer, int32_t capacity) {
  if (!juno_nvs_started || capacity <= 0) {
    return -1;
  }
  const char* key = juno_nvs_key(slot);
  size_t stored = juno_nvs_store.getBytesLength(key);
  if (stored == 0 || stored > static_cast<size_t>(capacity)) {
    return -1;
  }
  size_t copied = juno_nvs_store.getBytes(key, buffer, stored);
  return copied == stored ? static_cast<int32_t>(copied) : -1;
}

extern "C" int32_t juno_nvs_write(int32_t slot, int8_t* value, int32_t length) {
  if (!juno_nvs_started || length <= 0) {
    return 0;
  }
  size_t written = juno_nvs_store.putBytes(juno_nvs_key(slot), value, static_cast<size_t>(length));
  return written == static_cast<size_t>(length) ? 1 : 0;
}

extern "C" int32_t juno_nvs_clear(int32_t slot) {
  if (!juno_nvs_started) {
    return 0;
  }
  const char* key = juno_nvs_key(slot);
  // An entry that was never written is already clear, which is success: clearAll walks every slot.
  return (!juno_nvs_store.isKey(key) || juno_nvs_store.remove(key)) ? 1 : 0;
}

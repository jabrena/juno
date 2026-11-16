// ---- Juno named secrets in ESP32 non-volatile storage: the board's own on the ESP32 boards, the ESP32-S3
// coprocessor's on the UNO R4 WiFi, whose core's Preferences library has the same API and carries each call over the
// modem link. One Preferences entry per logical slot, in a namespace of Juno's own. NVS is its own flash partition,
// so a value outlives a sketch upload; it is not a secure element, and a flash readout exposes it unless the chip has
// flash encryption enabled (see Esp32Nvs). ----
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

#if defined(ARDUINO_UNOWIFIR4)
#include <WiFiS3.h>
#endif

extern "C" int32_t juno_nvs_begin() {
  if (!juno_nvs_started) {
#if defined(ARDUINO_UNOWIFIR4)
    // Starting the modem link soft-resets the ESP32-S3's Wi-Fi side, which then drops the first command: let a
    // harmless query take that, so Preferences' own begin is not the one left to time out.
    WiFi.firmwareVersion();
#endif
    juno_nvs_started = juno_nvs_store.begin("juno-secrets", false);
    if (!juno_nvs_started) {
      // The UNO R4 WiFi's ESP32-S3 keeps one namespace open across resets of the main MCU and refuses a begin while
      // one is: close whatever an earlier program left open, now that begin has brought the modem link up, and retry.
      juno_nvs_store.end();
      juno_nvs_started = juno_nvs_store.begin("juno-secrets", false);
    }
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
  // Success means the entry now reads as missing; one never written already is, which clearAll relies on as it walks
  // every slot. The length is asked rather than isKey(), which the UNO R4 WiFi answers through getType() and gets
  // wrong for blobs, and an empty slot is not removed: there, removing a missing key waits out the modem's timeout.
  if (juno_nvs_store.getBytesLength(key) == 0) {
    return 1;
  }
  juno_nvs_store.remove(key);
  return juno_nvs_store.getBytesLength(key) == 0 ? 1 : 0;
}

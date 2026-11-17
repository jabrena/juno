// ---- Juno named secrets in an AVR UNO's on-chip EEPROM (UNO R3), through the core's EEPROM library.
// The EEPROM is split into 64-byte slots: a length byte, then up to 63 bytes. A length of 0 or 0xFF (erased EEPROM)
// means the slot is empty. The values are plain text in the microcontroller (see AvrEeprom). EEPROM.update writes
// only the bytes that change, which spares the EEPROM's limited write endurance. ----
#define JUNO_EEPROM_SLOT_BYTES 64
#define JUNO_EEPROM_MAX_VALUE 63
#define JUNO_EEPROM_EMPTY 0xFF

extern "C" int32_t juno_eeprom_slot_count() {
  return static_cast<int32_t>(EEPROM.length() / JUNO_EEPROM_SLOT_BYTES);
}

static bool juno_eeprom_valid_slot(int32_t slot) {
  return slot >= 0 && slot < juno_eeprom_slot_count();
}

extern "C" int32_t juno_eeprom_read(int32_t slot, int8_t* buffer, int32_t capacity) {
  if (!juno_eeprom_valid_slot(slot) || capacity <= 0) {
    return -1;
  }
  int base = slot * JUNO_EEPROM_SLOT_BYTES;
  uint8_t length = EEPROM.read(base);
  if (length == 0 || length == JUNO_EEPROM_EMPTY || length > JUNO_EEPROM_MAX_VALUE || length > capacity) {
    return -1;
  }
  for (uint8_t i = 0; i < length; i++) {
    buffer[i] = static_cast<int8_t>(EEPROM.read(base + 1 + i));
  }
  return length;
}

static bool juno_eeprom_holds(int base, int8_t* value, int32_t length) {
  if (EEPROM.read(base) != static_cast<uint8_t>(length)) {
    return false;
  }
  for (int32_t i = 0; i < length; i++) {
    if (EEPROM.read(base + 1 + i) != static_cast<uint8_t>(value[i])) {
      return false;
    }
  }
  return true;
}

extern "C" int32_t juno_eeprom_write(int32_t slot, int8_t* value, int32_t length) {
  if (!juno_eeprom_valid_slot(slot) || length <= 0 || length > JUNO_EEPROM_MAX_VALUE) {
    return 0;
  }
  int base = slot * JUNO_EEPROM_SLOT_BYTES;
  if (juno_eeprom_holds(base, value, length)) {
    return 1;
  }
  // Marked empty first and given its length last, so a reset part-way leaves the slot missing, never a mix of the
  // old value and the new one.
  EEPROM.update(base, JUNO_EEPROM_EMPTY);
  for (int32_t i = 0; i < length; i++) {
    EEPROM.update(base + 1 + i, static_cast<uint8_t>(value[i]));
  }
  EEPROM.update(base, static_cast<uint8_t>(length));
  return juno_eeprom_holds(base, value, length) ? 1 : 0;
}

extern "C" int32_t juno_eeprom_clear(int32_t slot) {
  if (!juno_eeprom_valid_slot(slot)) {
    return 0;
  }
  // The whole slot is erased, not only its length byte, so no plain-text copy of the value is left behind.
  int base = slot * JUNO_EEPROM_SLOT_BYTES;
  for (int i = 0; i < JUNO_EEPROM_SLOT_BYTES; i++) {
    EEPROM.update(base + i, JUNO_EEPROM_EMPTY);
  }
  return EEPROM.read(base) == JUNO_EEPROM_EMPTY ? 1 : 0;
}

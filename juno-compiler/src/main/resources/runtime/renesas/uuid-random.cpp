extern "C" int32_t HW_SCE_McuSpecificInit(void);
extern "C" int32_t HW_SCE_GenerateRandomNumberSub(uint32_t* random);

extern "C" bool juno_uuid_random_bytes(uint8_t* target, uint32_t length) {
  if (length != 16u) return false;
  static bool initialized = false;
  if (!initialized) {
    if (HW_SCE_McuSpecificInit() != 0) return false;
    initialized = true;
  }
  uint32_t words[4];
  if (HW_SCE_GenerateRandomNumberSub(words) != 0) return false;
  const uint8_t* source = reinterpret_cast<const uint8_t*>(words);
  for (uint32_t index = 0; index < length; index++) {
    target[index] = source[index];
  }
  return true;
}

#include <cstddef>
#include <cstdint>
#include "mbedtls/entropy_poll.h"

extern "C" bool juno_uuid_random_bytes(uint8_t* target, uint32_t length) {
  if (target == nullptr || length != 16u) return false;
  size_t written = 0;
  return mbedtls_hardware_poll(nullptr, target, length, &written) == 0 && written == length;
}

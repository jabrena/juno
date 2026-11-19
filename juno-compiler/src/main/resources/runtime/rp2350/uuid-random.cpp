// The RP2350's hardware TRNG seeds the Pico SDK's random generator, and pico_rand mixes fresh TRNG samples into it on
// every call. Juno asks for 128 bits per UUID and copies them into the 16 bytes the UUID helper expects.
#include <pico/rand.h>

#include <cstring>

extern "C" bool juno_uuid_random_bytes(uint8_t* target, uint32_t length) {
  if (target == nullptr || length != 16u) return false;
  rng_128_t random;
  get_rand_128(&random);
  std::memcpy(target, random.r, sizeof(random.r));
  return true;
}

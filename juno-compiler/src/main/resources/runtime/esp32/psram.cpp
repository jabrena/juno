// Explicit caller-owned PSRAM byte buffers. The registry metadata remains in internal SRAM; only payloads are external.
#include <esp_heap_caps.h>
#include <string.h>
static constexpr uint32_t JUNO_PSRAM_CAPS = MALLOC_CAP_SPIRAM | MALLOC_CAP_8BIT;
static constexpr int32_t JUNO_PSRAM_MAX_HANDLES = 16;
static constexpr uint32_t JUNO_PSRAM_MAX_TOKEN = 0x7fffffffu;

struct JunoPsramSlot {
  void* pointer;
  uint32_t capacity;
  uint32_t token;
};

static JunoPsramSlot juno_psram_slots[JUNO_PSRAM_MAX_HANDLES] = {};
static uint32_t juno_psram_next_token = 1u;

static JunoPsramSlot* juno_psram_find(int32_t handle) {
  if (handle <= 0) return nullptr;
  uint32_t token = static_cast<uint32_t>(handle);
  for (int32_t index = 0; index < JUNO_PSRAM_MAX_HANDLES; index++) {
    JunoPsramSlot& slot = juno_psram_slots[index];
    if (slot.pointer != nullptr && slot.token == token) return &slot;
  }
  return nullptr;
}

static bool juno_psram_range_fits(int32_t offset, int32_t length, uint32_t capacity) {
  if (offset < 0 || length < 0) return false;
  uint32_t start = static_cast<uint32_t>(offset);
  uint32_t count = static_cast<uint32_t>(length);
  return start <= capacity && count <= capacity - start;
}

extern "C" int32_t juno_psram_is_available() {
  return heap_caps_get_total_size(JUNO_PSRAM_CAPS) > 0 ? 1 : 0;
}

extern "C" int32_t juno_psram_nominal_capacity() {
  return static_cast<int32_t>(ESP.getPsramSize());
}

extern "C" int32_t juno_psram_total_bytes() {
  return static_cast<int32_t>(heap_caps_get_total_size(JUNO_PSRAM_CAPS));
}

extern "C" int32_t juno_psram_free_bytes() {
  return static_cast<int32_t>(heap_caps_get_free_size(JUNO_PSRAM_CAPS));
}

extern "C" int32_t juno_psram_largest_free_block() {
  return static_cast<int32_t>(heap_caps_get_largest_free_block(JUNO_PSRAM_CAPS));
}

extern "C" int32_t juno_psram_allocate(int32_t byte_count) {
  if (byte_count <= 0) return -1;
  if (juno_psram_total_bytes() <= 0) return -2;
  JunoPsramSlot* free_slot = nullptr;
  for (int32_t index = 0; index < JUNO_PSRAM_MAX_HANDLES; index++) {
    if (juno_psram_slots[index].pointer == nullptr) {
      free_slot = &juno_psram_slots[index];
      break;
    }
  }
  if (free_slot == nullptr) return -6;
  if (static_cast<uint32_t>(byte_count) > static_cast<uint32_t>(juno_psram_largest_free_block())) return -3;
  if (juno_psram_next_token == 0u || juno_psram_next_token > JUNO_PSRAM_MAX_TOKEN) return -6;
  void* pointer = heap_caps_calloc(1u, static_cast<size_t>(byte_count), JUNO_PSRAM_CAPS);
  if (pointer == nullptr) return -3;
  free_slot->pointer = pointer;
  free_slot->capacity = static_cast<uint32_t>(byte_count);
  free_slot->token = juno_psram_next_token++;
  return static_cast<int32_t>(free_slot->token);
}

extern "C" int32_t juno_psram_capacity(int32_t handle) {
  JunoPsramSlot* slot = juno_psram_find(handle);
  return slot == nullptr ? -4 : static_cast<int32_t>(slot->capacity);
}

extern "C" int32_t juno_psram_read_byte(int32_t handle, int32_t offset) {
  JunoPsramSlot* slot = juno_psram_find(handle);
  if (slot == nullptr) return -4;
  if (!juno_psram_range_fits(offset, 1, slot->capacity)) return -5;
  return static_cast<int32_t>(static_cast<uint8_t*>(slot->pointer)[offset]);
}

extern "C" int32_t juno_psram_write_byte(int32_t handle, int32_t offset, int32_t value) {
  JunoPsramSlot* slot = juno_psram_find(handle);
  if (slot == nullptr) return -4;
  if (value < 0 || value > 255) return -1;
  if (!juno_psram_range_fits(offset, 1, slot->capacity)) return -5;
  static_cast<uint8_t*>(slot->pointer)[offset] = static_cast<uint8_t>(value);
  return 0;
}

extern "C" int32_t juno_psram_read(int32_t handle, int32_t source_offset, uint8_t* destination,
                                    int32_t destination_capacity, int32_t destination_offset, int32_t length) {
  JunoPsramSlot* slot = juno_psram_find(handle);
  if (slot == nullptr) return -4;
  if (destination == nullptr || destination_capacity < 0 || !juno_psram_range_fits(source_offset, length, slot->capacity)
      || !juno_psram_range_fits(destination_offset, length, static_cast<uint32_t>(destination_capacity))) return -5;
  if (length > 0) memcpy(destination + destination_offset,
                          static_cast<uint8_t*>(slot->pointer) + source_offset, static_cast<size_t>(length));
  return length;
}

extern "C" int32_t juno_psram_write(int32_t handle, int32_t destination_offset, const uint8_t* source,
                                     int32_t source_capacity, int32_t source_offset, int32_t length) {
  JunoPsramSlot* slot = juno_psram_find(handle);
  if (slot == nullptr) return -4;
  if (source == nullptr || source_capacity < 0 || !juno_psram_range_fits(destination_offset, length, slot->capacity)
      || !juno_psram_range_fits(source_offset, length, static_cast<uint32_t>(source_capacity))) return -5;
  if (length > 0) memcpy(static_cast<uint8_t*>(slot->pointer) + destination_offset,
                          source + source_offset, static_cast<size_t>(length));
  return length;
}

extern "C" int32_t juno_psram_release(int32_t handle) {
  JunoPsramSlot* slot = juno_psram_find(handle);
  if (slot == nullptr) return -4;
  void* pointer = slot->pointer;
  slot->pointer = nullptr;
  slot->capacity = 0u;
  slot->token = 0u;
  heap_caps_free(pointer);
  return 0;
}

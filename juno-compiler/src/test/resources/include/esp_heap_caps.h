// Host stand-in for the ESP-IDF heap header: the grayscale runtime only asks whether any PSRAM block is free.
#pragma once
#include <stddef.h>
#include <stdint.h>

#define MALLOC_CAP_SPIRAM (1u << 1)
#define MALLOC_CAP_8BIT (1u << 2)

size_t heap_caps_get_largest_free_block(uint32_t caps);

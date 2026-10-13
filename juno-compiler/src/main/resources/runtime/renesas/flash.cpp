// The core's fsp.ld places code and constants from __ROM_Start to __etext, then the initial values of .data
// right behind them; arduino-cli's size report counts the same sections against the 256 KiB upload limit.
extern "C" const uint8_t __ROM_Start[];
extern "C" const uint8_t __etext[];
extern "C" uint8_t __data_start__[];
extern "C" uint8_t __data_end__[];

extern "C" int32_t juno_memory_flash_used() {
  return static_cast<int32_t>((__etext - __ROM_Start) + (__data_end__ - __data_start__));
}

extern "C" int32_t juno_memory_flash_capacity() {
  return 262144;
}


#pragma once

#include <stddef.h>
#include <stdint.h>
#include <utility>

#define O_RDONLY 0
#define O_WRITE 2
#define O_CREAT 8
#define O_APPEND 16

class File32 {
public:
  File32() = default;
  File32(const File32&) = delete;
  File32& operator=(const File32&) = delete;
  File32(File32&&) = default;
  File32& operator=(File32&&) = default;
  int available() { return 0; }
  int read() { return -1; }
  int read(void*, size_t) { return 0; }
  bool seekSet(uint32_t) { return true; }
  uint32_t curPosition() { return 0; }
  size_t size() { return 0; }
  void println(const char*) {}
  void print(const char*) {}
  void close() {}
  operator bool() const { return true; }
};

class SdFat32 {
public:
  bool begin(int32_t) { return true; }
  bool exists(const char*) { return true; }
  File32 open(const char*, int) { return File32(); }
  bool remove(const char*) { return true; }
};

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
  bool isDir() const { return true; }
  bool openNext(File32*, int) { return false; }
  size_t getName(char*, size_t) { return 0; }
  operator bool() const { return true; }
};

class SdCard {
public:
  bool readSector(uint32_t, uint8_t*) { return true; }
};

class SdFat32 {
public:
  bool begin(int32_t) { return true; }
  bool exists(const char*) { return true; }
  bool mkdir(const char*, bool) { return true; }
  File32 open(const char*, int) { return File32(); }
  bool remove(const char*) { return true; }
  uint8_t fatType() const { return 32; }
  uint32_t clusterCount() const { return 0; }
  uint8_t sectorsPerCluster() const { return 64; }
  uint32_t fatStartSector() const { return 0; }
  SdCard* card() { return &m_card; }

private:
  SdCard m_card;
};

// Stand-in for SdFat under simavr: a card in RAM with a few small files, so the shim's file and Properties helpers
// run on the 8-bit target. The card holds "config.txt" from the start.
#pragma once

#include <stddef.h>
#include <stdint.h>
#include <string.h>

#define O_RDONLY 0
#define O_WRITE 2
#define O_CREAT 8
#define O_APPEND 16

struct JunoHarnessFile {
  char name[16];
  char data[96];
  uint16_t size;
  bool used;
};

extern JunoHarnessFile juno_harness_files[4];

class File32 {
public:
  int available() { return file_ != nullptr && cursor_ < file_->size ? 1 : 0; }
  int read() { return available() != 0 ? static_cast<uint8_t>(file_->data[cursor_++]) : -1; }
  uint32_t size() { return file_ != nullptr ? file_->size : 0; }
  size_t print(const char* text) {
    size_t length = strlen(text);
    for (size_t i = 0; i < length && file_->size < sizeof(file_->data); i++) file_->data[file_->size++] = text[i];
    return length;
  }
  size_t println(const char* text) { return print(text) + print("\n"); }
  void close() { file_ = nullptr; }
  explicit operator bool() const { return file_ != nullptr; }

  JunoHarnessFile* file_ = nullptr;
  uint16_t cursor_ = 0;
};

class SdFat32 {
public:
  bool begin(uint8_t chipSelect) { return chipSelect != 0; }
  bool exists(const char* path) { return find(path) != nullptr; }
  File32 open(const char* path, int flags) {
    File32 file;
    file.file_ = find(path);
    if (file.file_ == nullptr && (flags & O_CREAT) != 0) {
      for (JunoHarnessFile& candidate : juno_harness_files) {
        if (!candidate.used) {
          candidate.used = true;
          candidate.size = 0;
          strncpy(candidate.name, path, sizeof(candidate.name) - 1);
          file.file_ = &candidate;
          break;
        }
      }
    }
    return file;
  }
  bool remove(const char* path) {
    JunoHarnessFile* file = find(path);
    if (file == nullptr) return false;
    file->used = false;
    return true;
  }

private:
  JunoHarnessFile* find(const char* path) {
    for (JunoHarnessFile& candidate : juno_harness_files) {
      if (candidate.used && strcmp(candidate.name, path) == 0) return &candidate;
    }
    return nullptr;
  }
};

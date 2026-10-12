
// ---- java.io.RandomAccessFile (mode "r") ----------------------------------------------------------
static constexpr int32_t JUNO_RAF_IO = ${JUNO_RAF_IO};
static constexpr int32_t JUNO_RAF_FILE_NOT_FOUND = ${JUNO_RAF_FILE_NOT_FOUND};
static constexpr int32_t JUNO_RAF_EOF = ${JUNO_RAF_EOF};
static constexpr int32_t JUNO_RAF_INDEX_OUT_OF_BOUNDS = ${JUNO_RAF_INDEX_OUT_OF_BOUNDS};

static void juno_raf_raise(int32_t classId, const char* message) {
  auto* exception = static_cast<int32_t*>(juno_alloc(${JUNO_THROWABLE_BYTES}u, 4u));
  exception[${JUNO_CLASS_ID_WORD}] = classId;
  exception[${JUNO_MESSAGE_WORD}] = static_cast<int32_t>(reinterpret_cast<intptr_t>(message));
  juno_throw_raise(static_cast<int32_t>(reinterpret_cast<intptr_t>(exception)));
}

// A null receiver is a NullPointerException; a closed file an IOException, as on the JVM.
static File32* juno_raf_file(int32_t handle) {
  if (handle == 0) {
    juno_throw_raise(0);
    return nullptr;
  }
  int32_t index = handle - 1;
  if (index < 0 || index >= JUNO_SD_MAX_OPEN_FILES || !juno_sd_file_used[index]) {
    juno_raf_raise(JUNO_RAF_IO, "Stream Closed");
    return nullptr;
  }
  return &juno_sd_files[index];
}

// capacity is the buffer's compile-time length, or -1 when the compiler could not know it.
static bool juno_raf_in_bounds(const uint8_t* buffer, int32_t capacity, int32_t offset, int32_t length) {
  if (buffer == nullptr) {
    juno_throw_raise(0);
    return false;
  }
  if (offset < 0 || length < 0 || (capacity >= 0 && length > capacity - offset)) {
    juno_raf_raise(JUNO_RAF_INDEX_OUT_OF_BOUNDS, "Range out of bounds for the buffer");
    return false;
  }
  return true;
}

extern "C" int32_t juno_raf_open(const char* path) {
  for (int32_t i = 0; i < JUNO_SD_MAX_OPEN_FILES; i++) {
    if (!juno_sd_file_used[i]) {
      juno_sd_files[i] = juno_sd.open(path, O_RDONLY);
      if (!juno_sd_files[i]) {
        juno_raf_raise(JUNO_RAF_FILE_NOT_FOUND, path);
        return 0;
      }
      juno_sd_file_used[i] = true;
      return i + 1;
    }
  }
  juno_raf_raise(JUNO_RAF_IO, "Too many open files");
  return 0;
}

extern "C" int32_t juno_raf_read(int32_t handle) {
  File32* file = juno_raf_file(handle);
  return file == nullptr ? -1 : file->read();
}

extern "C" int32_t juno_raf_read_bytes(
    int32_t handle, uint8_t* buffer, int32_t capacity, int32_t offset, int32_t length) {
  File32* file = juno_raf_file(handle);
  if (file == nullptr || !juno_raf_in_bounds(buffer, capacity, offset, length)) return -1;
  if (length == 0) return 0;
  int32_t count = static_cast<int32_t>(file->read(buffer + offset, static_cast<size_t>(length)));
  if (count < 0) {
    juno_raf_raise(JUNO_RAF_IO, "Read error");
    return -1;
  }
  return count == 0 ? -1 : count;
}

extern "C" void juno_raf_read_fully(
    int32_t handle, uint8_t* buffer, int32_t capacity, int32_t offset, int32_t length) {
  File32* file = juno_raf_file(handle);
  if (file == nullptr || !juno_raf_in_bounds(buffer, capacity, offset, length)) return;
  while (length > 0) {
    int32_t count = static_cast<int32_t>(file->read(buffer + offset, static_cast<size_t>(length)));
    if (count < 0) {
      juno_raf_raise(JUNO_RAF_IO, "Read error");
      return;
    }
    if (count == 0) {
      juno_raf_raise(JUNO_RAF_EOF, nullptr);
      return;
    }
    offset += count;
    length -= count;
  }
}

// FAT32 files stop at 4 GiB, and SdFat cannot seek a read-only file past its end.
extern "C" void juno_raf_seek(int32_t handle, uint32_t positionLow, int32_t positionHigh) {
  File32* file = juno_raf_file(handle);
  if (file == nullptr) return;
  if (positionHigh < 0) {
    juno_raf_raise(JUNO_RAF_IO, "Negative seek offset");
  } else if (positionHigh != 0 || positionLow > file->size() || !file->seekSet(positionLow)) {
    juno_raf_raise(JUNO_RAF_IO, "Seek past the end of the file");
  }
}

extern "C" int64_t juno_raf_get_file_pointer(int32_t handle) {
  File32* file = juno_raf_file(handle);
  return file == nullptr ? 0 : static_cast<int64_t>(file->curPosition());
}

extern "C" int64_t juno_raf_length(int32_t handle) {
  File32* file = juno_raf_file(handle);
  return file == nullptr ? 0 : static_cast<int64_t>(file->size());
}

extern "C" int32_t juno_raf_skip_bytes(int32_t handle, int32_t count) {
  File32* file = juno_raf_file(handle);
  if (file == nullptr || count <= 0) return 0;
  uint32_t position = file->curPosition();
  uint32_t remaining = static_cast<uint32_t>(file->size()) - position;
  uint32_t skipped = static_cast<uint32_t>(count) < remaining ? static_cast<uint32_t>(count) : remaining;
  if (!file->seekSet(position + skipped)) {
    juno_raf_raise(JUNO_RAF_IO, "Seek error");
    return 0;
  }
  return static_cast<int32_t>(skipped);
}

// Closing an already closed file does nothing, as in the JDK.
extern "C" void juno_raf_close(int32_t handle) {
  if (handle == 0) {
    juno_throw_raise(0);
    return;
  }
  int32_t index = handle - 1;
  if (index < 0 || index >= JUNO_SD_MAX_OPEN_FILES || !juno_sd_file_used[index]) return;
  juno_sd_files[index].close();
  juno_sd_file_used[index] = false;
}


// Board-side InputStreams. Juno lowers every java.io.InputStream call to these functions, keyed by the stream's
// handle: a runtime that backs an InputStream registers its handle here with its own hooks, and any other handle goes
// on to the socket runtime, then to the SD card's files, whichever of the two the program links.
struct JunoInputStream {
  int32_t handle;
  int32_t (*available)(int32_t);
  int32_t (*read)(int32_t);
  int32_t (*readBytes)(int32_t, uint8_t*, int32_t, int32_t);
};

static constexpr int32_t JUNO_INPUT_STREAM_CAPACITY = 4;
static JunoInputStream juno_input_streams[JUNO_INPUT_STREAM_CAPACITY];
static int32_t juno_input_stream_count = 0;

extern "C" int32_t juno_socket_stream_owns(int32_t handle) __attribute__((weak));
extern "C" int32_t juno_socket_stream_read(int32_t handle, uint8_t* payload, int32_t offset, int32_t length)
    __attribute__((weak));
extern "C" int32_t juno_sd_file_available(int32_t handle) __attribute__((weak));
extern "C" int32_t juno_sd_file_read(int32_t handle) __attribute__((weak));
extern "C" int32_t juno_sd_file_read_bytes(int32_t handle, uint8_t* buffer, int32_t offset, int32_t length)
    __attribute__((weak));
extern "C" void juno_sd_file_close(int32_t handle) __attribute__((weak));

extern "C" void juno_input_stream_register(int32_t handle, int32_t (*available)(int32_t), int32_t (*read)(int32_t),
                                           int32_t (*readBytes)(int32_t, uint8_t*, int32_t, int32_t)) {
  // Resource-backed streams use stable handles and may be opened again for a later HTTP request.
  // Replace the existing hooks instead of consuming another table slot for the same handle.
  for (int32_t index = 0; index < juno_input_stream_count; index++) {
    if (juno_input_streams[index].handle == handle) {
      juno_input_streams[index] = {handle, available, read, readBytes};
      return;
    }
  }
  if (juno_input_stream_count == JUNO_INPUT_STREAM_CAPACITY) {
    juno_panic();
    return;
  }
  juno_input_streams[juno_input_stream_count++] = {handle, available, read, readBytes};
}

static const JunoInputStream* juno_input_stream(int32_t handle) {
  for (int32_t index = 0; index < juno_input_stream_count; index++) {
    if (handle != 0 && juno_input_streams[index].handle == handle) return &juno_input_streams[index];
  }
  return nullptr;
}

static bool juno_input_stream_is_socket(int32_t handle) {
  return juno_socket_stream_owns != nullptr && juno_socket_stream_owns(handle);
}

extern "C" void juno_input_stream_init(int32_t) {
}

extern "C" int32_t juno_input_stream_available(int32_t handle) {
  if (const JunoInputStream* stream = juno_input_stream(handle)) return stream->available(handle);
  return juno_sd_file_available != nullptr ? juno_sd_file_available(handle) : 0;
}

extern "C" int32_t juno_input_stream_read(int32_t handle) {
  if (const JunoInputStream* stream = juno_input_stream(handle)) return stream->read(handle);
  if (juno_input_stream_is_socket(handle)) {
    uint8_t value;
    return juno_socket_stream_read(handle, &value, 0, 1) == 1 ? value : -1;
  }
  return juno_sd_file_read != nullptr ? juno_sd_file_read(handle) : -1;
}

extern "C" int32_t juno_input_stream_read_bytes(int32_t handle, uint8_t* buffer, int32_t offset, int32_t length) {
  if (const JunoInputStream* stream = juno_input_stream(handle)) return stream->readBytes(handle, buffer, offset, length);
  if (juno_input_stream_is_socket(handle)) return juno_socket_stream_read(handle, buffer, offset, length);
  return juno_sd_file_read_bytes != nullptr ? juno_sd_file_read_bytes(handle, buffer, offset, length) : -1;
}

extern "C" void juno_input_stream_close(int32_t handle) {
  if (juno_input_stream(handle) != nullptr || juno_input_stream_is_socket(handle)) return;
  if (juno_sd_file_close != nullptr) juno_sd_file_close(handle);
}

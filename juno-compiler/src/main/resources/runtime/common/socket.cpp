// ---- java.net.Socket over the single TCP connection (IPv4) -----------------------------------------
static constexpr int32_t JUNO_SOCKET_IO = ${JUNO_SOCKET_IO};
static constexpr int32_t JUNO_SOCKET_ILLEGAL_ARGUMENT = ${JUNO_SOCKET_ILLEGAL_ARGUMENT};

// The default read and write wait, since the JDK's unlimited wait would hang a board.
static constexpr int32_t JUNO_SOCKET_DEFAULT_TIMEOUT_MILLIS = 5000;

struct JunoSocket {
  int32_t timeoutMillis;
};

struct JunoInetAddressOctets {
  int32_t octets[4];
};

// The one open socket's handle, which the SD read path checks before treating a handle as a file.
static int32_t juno_socket_stream_handle = 0;

static void juno_socket_raise(int32_t classId, const char* message) {
  auto* exception = static_cast<int32_t*>(juno_alloc(${JUNO_THROWABLE_BYTES}u, 4u));
  exception[${JUNO_CLASS_ID_WORD}] = classId;
  exception[${JUNO_MESSAGE_WORD}] = static_cast<int32_t>(reinterpret_cast<intptr_t>(message));
  juno_throw_raise(static_cast<int32_t>(reinterpret_cast<intptr_t>(exception)));
}

template <typename T>
static T* juno_socket_pointer(int32_t handle) {
  if (handle == 0) {
    juno_throw_raise(0);
    return nullptr;
  }
  return reinterpret_cast<T*>(static_cast<uintptr_t>(static_cast<uint32_t>(handle)));
}

extern "C" int32_t juno_socket_new(int32_t addressHandle, int32_t port) {
  auto* address = juno_socket_pointer<JunoInetAddressOctets>(addressHandle);
  if (address == nullptr) return 0;
  if (port <= 0 || port > 65535) {
    juno_socket_raise(JUNO_SOCKET_ILLEGAL_ARGUMENT, "Port out of range");
    return 0;
  }
  if (juno_tcp_open(address->octets, port) == 0) {
    juno_socket_raise(JUNO_SOCKET_IO, "Connection refused or timed out");
    return 0;
  }
  auto* socket = static_cast<JunoSocket*>(juno_alloc(sizeof(JunoSocket), 4u));
  socket->timeoutMillis = JUNO_SOCKET_DEFAULT_TIMEOUT_MILLIS;
  juno_socket_stream_handle = static_cast<int32_t>(reinterpret_cast<intptr_t>(socket));
  return juno_socket_stream_handle;
}

// The streams are the socket itself, so both accessors hand back the same handle.
extern "C" int32_t juno_socket_get_output_stream(int32_t handle) {
  return juno_socket_pointer<JunoSocket>(handle) == nullptr ? 0 : handle;
}

extern "C" int32_t juno_socket_get_input_stream(int32_t handle) {
  return juno_socket_pointer<JunoSocket>(handle) == nullptr ? 0 : handle;
}

extern "C" void juno_socket_set_so_timeout(int32_t handle, int32_t timeoutMillis) {
  auto* socket = juno_socket_pointer<JunoSocket>(handle);
  if (socket == nullptr) return;
  if (timeoutMillis < 0) {
    juno_socket_raise(JUNO_SOCKET_ILLEGAL_ARGUMENT, "Timeout must not be negative");
    return;
  }
  // Zero would be the JDK's unlimited wait, which the transport cannot express: treat it as a single poll.
  socket->timeoutMillis = timeoutMillis == 0 ? 1 : timeoutMillis;
}

extern "C" void juno_socket_output_stream_write(int32_t handle, const uint8_t* payload, int32_t offset,
                                                int32_t length) {
  if (juno_socket_pointer<JunoSocket>(handle) == nullptr) return;
  if (payload == nullptr) {
    juno_throw_raise(0);
    return;
  }
  if (offset < 0 || length < 0) {
    juno_socket_raise(JUNO_SOCKET_ILLEGAL_ARGUMENT, "Range out of bounds for the buffer");
    return;
  }
  if (juno_tcp_write(payload + offset, length) != length) {
    juno_socket_raise(JUNO_SOCKET_IO, "Write failed: connection closed");
  }
}

extern "C" int32_t juno_socket_stream_owns(int32_t handle) {
  return handle != 0 && handle == juno_socket_stream_handle ? 1 : 0;
}

// Called from the SD input stream's read, which hands over the socket's handle; same contract as that read.
extern "C" int32_t juno_socket_stream_read(int32_t handle, uint8_t* payload, int32_t offset, int32_t length) {
  auto* socket = juno_socket_pointer<JunoSocket>(handle);
  if (socket == nullptr) return -1;
  if (payload == nullptr) {
    juno_throw_raise(0);
    return -1;
  }
  if (offset < 0 || length < 0) {
    juno_socket_raise(JUNO_SOCKET_ILLEGAL_ARGUMENT, "Range out of bounds for the buffer");
    return -1;
  }
  if (length == 0) return 0;
  if (juno_tcp_read(payload, offset, length, socket->timeoutMillis) != length) {
    juno_socket_raise(JUNO_SOCKET_IO, "Read failed: connection closed or timed out");
    return -1;
  }
  return length;
}

extern "C" void juno_socket_close(int32_t handle) {
  if (juno_socket_pointer<JunoSocket>(handle) == nullptr) return;
  juno_socket_stream_handle = 0;
  juno_tcp_close();
}

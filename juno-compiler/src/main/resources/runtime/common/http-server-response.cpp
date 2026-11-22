
// The JDK HttpExchange response: sendResponseHeaders writes the status line and headers at once, the body stream's
// bytes collect in this buffer and leave whenever it fills, and closing the stream sends the rest and ends the
// connection. A length above zero is sent as Content-Length, and -1 (no body) as Content-Length: 0; zero leaves it out,
// so the body ends when the connection closes, as a stream of unknown length (an MJPEG stream, for one) needs.
static constexpr int32_t JUNO_HTTP_SERVER_RESPONSE_CAPACITY = 512;
static uint8_t juno_http_server_response_buffer[JUNO_HTTP_SERVER_RESPONSE_CAPACITY];
static int32_t juno_http_server_response_length = 0;
static bool juno_http_server_response_open = false;
// Set when the client stops taking bytes; every later write then throws IOException, as the JDK's stream does.
static bool juno_http_server_response_failed = false;

struct JunoHttpServerResponseHeadJob {
  int32_t status;
  const char* contentType;
  int32_t length;
};

static void juno_http_server_response_head_job(void* argument) {
  auto* job = static_cast<JunoHttpServerResponseHeadJob*>(argument);
  JunoHttpClient& client = juno_http_server_client;
  client.print("HTTP/1.1 ");
  client.print(job->status);
  client.print(" \r\nContent-Type: ");
  client.print(job->contentType);
  if (job->length != 0) {
    client.print("\r\nContent-Length: ");
    client.print(job->length > 0 ? job->length : 0);
  }
  client.print("\r\nConnection: close\r\n\r\n");
}

static void juno_http_server_response_flush_job(void*) {
  if (juno_http_server_response_length > 0 && !juno_http_server_response_failed) {
    size_t length = static_cast<size_t>(juno_http_server_response_length);
    if (juno_http_server_client.write(juno_http_server_response_buffer, length) != length
        || !juno_http_server_client.connected()) {
      juno_http_server_response_failed = true;
    }
  }
  juno_http_server_response_length = 0;
}

static void juno_http_server_response_flush() {
  JunoIoGuard guard(JUNO_IO_NETWORK);
  juno_io_offload(juno_http_server_response_flush_job, nullptr);
}

// Buffers one body byte; false once the client has stopped taking bytes.
static bool juno_http_server_response_append(uint8_t value) {
  if (!juno_http_server_response_open) return true;
  if (juno_http_server_response_length == JUNO_HTTP_SERVER_RESPONSE_CAPACITY) juno_http_server_response_flush();
  if (juno_http_server_response_failed) return false;
  juno_http_server_response_buffer[juno_http_server_response_length++] = value;
  return true;
}

// The body stream's writes are socket-stream calls, which check for a pending exception afterwards.
static void juno_http_server_response_raise() {
  juno_socket_raise(JUNO_SOCKET_IO, "The client closed the connection");
}

static void juno_http_server_response_end_job(void*) {
  juno_http_server_response_flush_job(nullptr);
  juno_http_server_client.stop();
  juno_http_server_release();
}

extern "C" void juno_socket_register_output_stream(
    int32_t handle, void (*writeHook)(int32_t, const uint8_t*, int32_t, int32_t),
    void (*byteHook)(int32_t, int32_t), void (*closeHook)(int32_t));

static void juno_http_server_response_stream_write(int32_t, const uint8_t* payload, int32_t offset, int32_t length) {
  if (payload == nullptr || offset < 0 || length < 0) return;
  for (int32_t index = 0; index < length; index++) {
    if (!juno_http_server_response_append(payload[offset + index])) {
      juno_http_server_response_raise();
      return;
    }
  }
}

static void juno_http_server_response_stream_write_byte(int32_t, int32_t value) {
  if (!juno_http_server_response_append(static_cast<uint8_t>(value))) juno_http_server_response_raise();
}

static void juno_http_server_response_stream_close(int32_t) {
  if (!juno_http_server_response_open) return;
  juno_http_server_response_open = false;
  JunoIoGuard guard(JUNO_IO_NETWORK);
  juno_io_offload(juno_http_server_response_end_job, nullptr);
}

extern "C" void juno_http_server_response_stream(int32_t handle) {
  juno_socket_register_output_stream(handle, juno_http_server_response_stream_write,
      juno_http_server_response_stream_write_byte, juno_http_server_response_stream_close);
}

extern "C" void juno_http_server_response_begin(int32_t status, const char* contentType, int32_t length) {
  juno_http_server_response_length = 0;
  juno_http_server_response_open = true;
  juno_http_server_response_failed = false;
  JunoIoGuard guard(JUNO_IO_NETWORK);
  JunoHttpServerResponseHeadJob job = {status, contentType, length};
  juno_io_offload(juno_http_server_response_head_job, &job);
}

extern "C" void juno_http_server_response_write_byte(int32_t value) {
  juno_http_server_response_append(static_cast<uint8_t>(value));
}

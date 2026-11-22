
// The JDK HttpExchange request body: the program's own body buffer, registered once as a board-side InputStream
// (common/streams.cpp). accept() fills the buffer and reports it through the hook below; a body larger than the buffer leaves the rest with the client, and
// reads past the buffer take it from the client, within the same five-second wait accept() gives a request.
static constexpr unsigned long JUNO_HTTP_SERVER_REQUEST_READ_MILLIS = 5000;
static const uint8_t* juno_http_server_request_data = nullptr;
static int32_t juno_http_server_request_length = 0;
static int32_t juno_http_server_request_position = 0;
static int32_t juno_http_server_request_unread = 0;

extern "C" void juno_input_stream_register(int32_t handle, int32_t (*available)(int32_t), int32_t (*read)(int32_t),
                                           int32_t (*readBytes)(int32_t, uint8_t*, int32_t, int32_t));

struct JunoHttpServerRequestReadJob {
  uint8_t* buffer;
  int32_t length;
  int32_t result;
};

// Reads up to job->length of the body's unread bytes from the client; -1 when the client sends none in time.
static void juno_http_server_request_read_job(void* argument) {
  auto* job = static_cast<JunoHttpServerRequestReadJob*>(argument);
  JunoHttpClient& client = juno_http_server_client;
  int32_t count = 0;
  const unsigned long deadline = millis() + JUNO_HTTP_SERVER_REQUEST_READ_MILLIS;
  while (count < job->length && millis() < deadline) {
    if (!client.available()) {
      if (count > 0 || !client.connected()) break;
      juno_io_wait(1);
      continue;
    }
    int value = client.read();
    if (value < 0) {
      juno_io_wait(1);
      continue;
    }
    job->buffer[count++] = static_cast<uint8_t>(value);
  }
  job->result = count > 0 ? count : -1;
}

static int32_t juno_http_server_request_read_client(uint8_t* buffer, int32_t length) {
  if (juno_http_server_request_unread <= 0) return -1;
  int32_t wanted = length < juno_http_server_request_unread ? length : juno_http_server_request_unread;
  JunoIoGuard guard(JUNO_IO_NETWORK);
  JunoHttpServerRequestReadJob job = {buffer, wanted, -1};
  juno_io_offload(juno_http_server_request_read_job, &job);
  if (job.result > 0) {
    juno_http_server_request_unread -= job.result;
  } else {
    juno_http_server_request_unread = 0;
  }
  return job.result;
}

static int32_t juno_http_server_request_available(int32_t) {
  return juno_http_server_request_length - juno_http_server_request_position + juno_http_server_request_unread;
}

static int32_t juno_http_server_request_read(int32_t) {
  if (juno_http_server_request_position < juno_http_server_request_length) {
    return juno_http_server_request_data[juno_http_server_request_position++];
  }
  uint8_t value;
  return juno_http_server_request_read_client(&value, 1) == 1 ? value : -1;
}

static int32_t juno_http_server_request_read_bytes(int32_t, uint8_t* buffer, int32_t offset, int32_t length) {
  if (buffer == nullptr || offset < 0 || length < 0) return -1;
  if (length == 0) return 0;
  int32_t remaining = juno_http_server_request_length - juno_http_server_request_position;
  if (remaining <= 0) return juno_http_server_request_read_client(buffer + offset, length);
  int32_t count = length < remaining ? length : remaining;
  memcpy(buffer + offset, juno_http_server_request_data + juno_http_server_request_position,
         static_cast<size_t>(count));
  juno_http_server_request_position += count;
  return count;
}

static void juno_http_server_request_body(const uint8_t* bodyBuffer, int32_t length, int32_t unread) {
  juno_http_server_request_data = bodyBuffer;
  juno_http_server_request_length = bodyBuffer == nullptr || length < 0 ? 0 : length;
  juno_http_server_request_position = 0;
  juno_http_server_request_unread = unread;
}

// Hooked into accept() at startup, since this file is linked only into a program that reads a request body: the
// exchange registers its stream on the first getRequestBody(), after accept() has already read that first request.
static const bool juno_http_server_request_hooked = (juno_http_server_request_received = juno_http_server_request_body,
                                                     true);

extern "C" void juno_http_server_request_stream(int32_t handle, const uint8_t*) {
  juno_input_stream_register(handle, juno_http_server_request_available, juno_http_server_request_read,
                             juno_http_server_request_read_bytes);
}

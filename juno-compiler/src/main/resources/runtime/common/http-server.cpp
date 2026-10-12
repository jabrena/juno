
static char juno_http_server_method_buf[8];
static char juno_http_server_path_buf[96];

// Cancelled while it waits for a request: drop the client, so the next accept() starts afresh.
static void juno_http_server_abort(void*) {
  juno_http_server_client.stop();
  juno_http_server_release();
}

struct JunoHttpServerAcceptJob {
  uint8_t* bodyBuffer;
  int32_t bodyBufferLength;
  int32_t result;
};

static int32_t juno_http_server_accept_request(uint8_t* bodyBuffer, int32_t bodyBufferLength);

static void juno_http_server_accept_job(void* argument) {
  auto* job = static_cast<JunoHttpServerAcceptJob*>(argument);
  job->result = juno_http_server_accept_request(job->bodyBuffer, job->bodyBufferLength);
}

extern "C" int32_t juno_http_server_accept(uint8_t* bodyBuffer, int32_t bodyBufferLength) {
  JunoIoGuard guard(JUNO_IO_NETWORK, nullptr, juno_http_server_abort);
  JunoHttpServerAcceptJob job = {bodyBuffer, bodyBufferLength, -1};
  juno_io_offload(juno_http_server_accept_job, &job);
  return job.result;
}

static int32_t juno_http_server_accept_request(uint8_t* bodyBuffer, int32_t bodyBufferLength) {
  if (!juno_http_server_next_client()) return -1;
  JunoHttpClient& client = juno_http_server_client;

  int32_t methodLength = 0;
  int32_t pathLength = 0;
  int32_t requestLinePhase = 0; // 0 = method, 1 = path, 2 = skip rest of the line
  bool gotRequestLine = false;
  bool inBody = false;
  char recent[4] = {0, 0, 0, 0};
  bool hasContentLength = false;
  bool inContentLengthValue = false;
  int32_t contentLength = 0;
  int32_t contentLengthMatch = 0;
  const char* contentLengthMarker = "content-length:";
  int32_t written = 0;

  const unsigned long deadline = millis() + 5000;
  while (millis() < deadline) {
    if (!client.available()) {
      if (!client.connected()) break;
      juno_io_wait(1);
      continue;
    }
    int value = client.read();
    if (value < 0) break;
    char c = static_cast<char>(value);

    if (!gotRequestLine) {
      if (requestLinePhase == 0) {
        if (c == ' ') {
          requestLinePhase = 1;
        } else if (methodLength < static_cast<int32_t>(sizeof(juno_http_server_method_buf)) - 1) {
          juno_http_server_method_buf[methodLength++] = c;
        }
      } else if (requestLinePhase == 1) {
        if (c == ' ') {
          requestLinePhase = 2;
        } else if (pathLength < static_cast<int32_t>(sizeof(juno_http_server_path_buf)) - 1) {
          juno_http_server_path_buf[pathLength++] = c;
        }
      } else if (c == '\n') {
        gotRequestLine = true;
      }
      continue;
    }

    if (!inBody) {
      char lower = (c >= 'A' && c <= 'Z') ? static_cast<char>(c - 'A' + 'a') : c;
      if (inContentLengthValue) {
        if (c >= '0' && c <= '9') contentLength = contentLength * 10 + (c - '0');
        else if (c != ' ') inContentLengthValue = false;
      } else if (lower == contentLengthMarker[contentLengthMatch]) {
        contentLengthMatch++;
        if (contentLengthMarker[contentLengthMatch] == 0) {
          inContentLengthValue = true;
          hasContentLength = true;
          contentLength = 0;
        }
      } else {
        contentLengthMatch = (lower == contentLengthMarker[0]) ? 1 : 0;
      }
      recent[0] = recent[1];
      recent[1] = recent[2];
      recent[2] = recent[3];
      recent[3] = c;
      if (recent[0] == '\r' && recent[1] == '\n' && recent[2] == '\r' && recent[3] == '\n') {
        inBody = true;
        if (!hasContentLength) break;
      }
      continue;
    }

    if (written < bodyBufferLength) bodyBuffer[written] = static_cast<uint8_t>(c);
    written++;
    if (written >= contentLength) break;
  }
  juno_http_server_method_buf[methodLength] = '\0';
  juno_http_server_path_buf[pathLength] = '\0';
  return written < bodyBufferLength ? written : bodyBufferLength;
}

static int32_t juno_http_server_pool_copy(const char* text, int32_t length) {
  if (length < 0 || static_cast<uint32_t>(length) >= JUNO_STRING_SLOT_SIZE) return 0;
  char* slot = juno_string_slots[juno_string_slot_cursor];
  juno_string_slot_cursor = (juno_string_slot_cursor + 1u) % JUNO_STRING_SLOT_COUNT;
  for (int32_t i = 0; i < length; i++) slot[i] = text[i];
  slot[length] = '\0';
  return static_cast<int32_t>(reinterpret_cast<intptr_t>(slot));
}

extern "C" int32_t juno_http_server_method() {
  return juno_http_server_pool_copy(juno_http_server_method_buf,
      static_cast<int32_t>(strlen(juno_http_server_method_buf)));
}

extern "C" int32_t juno_http_server_path() {
  return juno_http_server_pool_copy(juno_http_server_path_buf,
      static_cast<int32_t>(strlen(juno_http_server_path_buf)));
}

struct JunoHttpServerRespondJob {
  int32_t status;
  const char* contentType;
  const char* body;
  int32_t bodyLength;
};

static void juno_http_server_respond_job(void* argument) {
  auto* job = static_cast<JunoHttpServerRespondJob*>(argument);
  int32_t status = job->status;
  const char* contentType = job->contentType;
  const char* body = job->body;
  int32_t bodyLength = job->bodyLength;
  JunoHttpClient& client = juno_http_server_client;
  client.print("HTTP/1.1 ");
  client.print(status);
  client.print(" \r\nContent-Type: ");
  client.print(contentType);
  client.print("\r\nContent-Length: ");
  client.print(bodyLength);
  client.print("\r\nConnection: close\r\n\r\n");
  client.write(reinterpret_cast<const uint8_t*>(body), static_cast<size_t>(bodyLength));
  client.stop();
  juno_http_server_release();
}

static void juno_http_server_respond_bytes(int32_t status, const char* contentType,
                                             const char* body, int32_t bodyLength) {
  JunoIoGuard guard(JUNO_IO_NETWORK);
  JunoHttpServerRespondJob job = {status, contentType, body, bodyLength};
  ${JUNO_HTTP_SERVER_RESPOND_PREPARE}
  juno_io_offload(juno_http_server_respond_job, &job);
}

extern "C" void juno_http_server_respond(int32_t status, const char* contentType, const char* body) {
  juno_http_server_respond_bytes(status, contentType, body, static_cast<int32_t>(strlen(body)));
}

extern "C" void juno_http_server_respond_builder(int32_t status, const char* contentType,
                                                   int32_t bodyHandle) {
  auto* header = reinterpret_cast<int32_t*>(static_cast<intptr_t>(bodyHandle));
  int32_t length = header[0];
  const uint8_t* buffer = juno_string_builder_buffer(bodyHandle);
  juno_http_server_respond_bytes(status, contentType, reinterpret_cast<const char*>(buffer), length);
}


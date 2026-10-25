
static char juno_http_server_method_buf[8];
static char juno_http_server_path_buf[96];
static int32_t juno_http_server_content_length_value = -1;
static constexpr int32_t JUNO_HTTP_SERVER_DISCONNECTED_POLLS = 3;

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
  juno_http_server_content_length_value = -1;

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
  bool complete = false;
  int32_t disconnectedPolls = 0;

  // A client may send its request in several TCP segments, and on the UNO R4 WiFi available(), connected() and read()
  // are each a round trip to the WiFi module that can fail once without the peer having gone. So a gap in the data, a
  // failed read or a single "not connected" only waits for more; the peer counts as gone after several in a row.
  const unsigned long deadline = millis() + 5000;
  while (millis() < deadline) {
    if (!client.available()) {
      if (!client.connected() && ++disconnectedPolls >= JUNO_HTTP_SERVER_DISCONNECTED_POLLS) break;
      juno_io_wait(1);
      continue;
    }
    int value = client.read();
    if (value < 0) {
      juno_io_wait(1);
      continue;
    }
    disconnectedPolls = 0;
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
        // The request line's CRLF starts the blank-line match, so a request with no headers ends too.
        recent[2] = '\r';
        recent[3] = '\n';
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
        if (!hasContentLength || contentLength <= 0) {
          complete = true;
          break;
        }
      }
      continue;
    }

    if (written < bodyBufferLength) bodyBuffer[written] = static_cast<uint8_t>(c);
    written++;
    if (written >= contentLength) {
      complete = true;
      break;
    }
  }
  if (!complete) {
    // A request cut off before its headers (or declared body) ended must not reach the program: its partial path
    // would be answered as if the client had asked for it, e.g. a 404 for a uuid that does exist.
    client.stop();
    juno_http_server_release();
    juno_http_server_method_buf[0] = '\0';
    juno_http_server_path_buf[0] = '\0';
    return -1;
  }
  juno_http_server_method_buf[methodLength] = '\0';
  juno_http_server_path_buf[pathLength] = '\0';
  if (hasContentLength) juno_http_server_content_length_value = contentLength;
  return written < bodyBufferLength ? written : bodyBufferLength;
}

extern "C" int32_t juno_http_server_content_length() {
  return juno_http_server_content_length_value;
}

// The request buffers are already null-terminated and outlive the call, so they are returned as-is:
// copying into a runtime-string slot would turn any path of JUNO_STRING_SLOT_SIZE bytes or more
// (e.g. /api/v1/mailbox/<uuid>) into null and panic the caller before it could respond.
extern "C" int32_t juno_http_server_method() {
  return static_cast<int32_t>(reinterpret_cast<intptr_t>(juno_http_server_method_buf));
}

extern "C" int32_t juno_http_server_path() {
  return static_cast<int32_t>(reinterpret_cast<intptr_t>(juno_http_server_path_buf));
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


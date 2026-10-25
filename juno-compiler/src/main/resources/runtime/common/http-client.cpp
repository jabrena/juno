
// Whether caller-owned request headers already start a line with "Content-Type:" (any case), so the
// default JSON content type is not sent a second time.
static bool juno_http_has_content_type(const uint8_t* headers, int32_t length) {
  static const char name[] = "content-type:";
  const int32_t nameLength = static_cast<int32_t>(sizeof(name) - 1);
  int32_t lineStart = 0;
  while (headers != nullptr && lineStart + nameLength <= length) {
    int32_t matched = 0;
    while (matched < nameLength) {
      uint8_t c = headers[lineStart + matched];
      if (c >= 'A' && c <= 'Z') c = static_cast<uint8_t>(c + ('a' - 'A'));
      if (c != static_cast<uint8_t>(name[matched])) break;
      matched++;
    }
    if (matched == nameLength) return true;
    while (lineStart < length && headers[lineStart] != '\n') lineStart++;
    lineStart++;
  }
  return false;
}

// How long a request waits to connect (on ESP32 cores, whose clients take a connect timeout) and then for its
// response, in milliseconds. HttpClient.setTimeout changes it for every later request.
static uint32_t juno_http_timeout_ms = 5000u;

extern "C" void juno_http_set_timeout(int32_t millisToWait) {
  if (millisToWait > 0) {
    juno_http_timeout_ms = static_cast<uint32_t>(millisToWait);
  }
}

#if defined(__AVR__)
static constexpr int32_t JUNO_HTTP_REQUEST_CHUNK_BYTES = 64;
#else
static constexpr int32_t JUNO_HTTP_REQUEST_CHUNK_BYTES = 512;
#endif

// Collects the request into chunks so a small request leaves in one write: a print per piece becomes a TCP segment
// per piece on some cores (the ESP32's among them), and a server that reads slowly can see the request cut short.
template <typename Client>
class JunoHttpRequestWriter {
  Client& client;
  uint8_t chunk[JUNO_HTTP_REQUEST_CHUNK_BYTES];
  int32_t used = 0;

public:
  explicit JunoHttpRequestWriter(Client& target) : client(target) {}

  void bytes(const uint8_t* value, int32_t length) {
    for (int32_t index = 0; index < length; index++) {
      if (used == JUNO_HTTP_REQUEST_CHUNK_BYTES) flush();
      chunk[used++] = value[index];
    }
  }

  void text(const char* value) {
    bytes(reinterpret_cast<const uint8_t*>(value), static_cast<int32_t>(strlen(value)));
  }

  void number(uint32_t value) {
    char digits[11];
    int32_t count = 0;
    do {
      digits[count++] = static_cast<char>('0' + value % 10u);
      value /= 10u;
    } while (value != 0u);
    while (count > 0) {
      const uint8_t digit = static_cast<uint8_t>(digits[--count]);
      bytes(&digit, 1);
    }
  }

  void flush() {
    if (used > 0) client.write(chunk, static_cast<size_t>(used));
    used = 0;
  }
};

template <typename Client>
static bool juno_http_connect(Client& client, const char* host, int32_t port) {
#if defined(ARDUINO_ARCH_ESP32)
  return client.connect(host, static_cast<uint16_t>(port), static_cast<int32_t>(juno_http_timeout_ms));
#else
  return client.connect(host, static_cast<uint16_t>(port));
#endif
}

template <typename Client>
static int32_t juno_http_request(Client& client, const char* method, const char* host, int32_t port,
                                  const char* path, const char* body,
                                  const uint8_t* requestHeadersBuffer, int32_t requestHeadersLength,
                                  const uint8_t* requestBodyBuffer, int32_t requestBodyLength,
                                  uint8_t* responseBuffer, int32_t responseBufferLength,
                                  uint8_t* headersBuffer, int32_t headersBufferLength,
                                  int32_t* statusAndHeadersLength) {
  if (!juno_http_connect(client, host, port)) {
    statusAndHeadersLength[0] = 0;
    statusAndHeadersLength[1] = 0;
    return -1;
  }
  JunoHttpRequestWriter<Client> request(client);
  request.text(method);
  request.text(" ");
  request.text(path);
  request.text(" HTTP/1.1\r\nHost: ");
  request.text(host);
  request.text("\r\nConnection: close\r\n");
  if (requestHeadersBuffer != nullptr && requestHeadersLength > 0) {
    request.bytes(requestHeadersBuffer, requestHeadersLength);
  }
  if (body != nullptr || requestBodyLength >= 0) {
    const int32_t bodyLength = body != nullptr ? static_cast<int32_t>(strlen(body)) : requestBodyLength;
    if (!juno_http_has_content_type(requestHeadersBuffer, requestHeadersLength)) {
      request.text("Content-Type: application/json\r\n");
    }
    request.text("Content-Length: ");
    request.number(static_cast<uint32_t>(bodyLength));
    request.text("\r\n\r\n");
    if (body != nullptr) {
      request.text(body);
    } else if (bodyLength > 0) {
      request.bytes(requestBodyBuffer, bodyLength);
    }
  } else {
    request.text("\r\n");
  }
  request.flush();

  const unsigned long deadline = millis() + juno_http_timeout_ms;
  // The status line ("HTTP/1.1 200 OK\r\n") is parsed for its numeric status code
  // only; everything else on it (the HTTP version and the reason phrase) is skipped.
  bool inStatusLine = true;
  int32_t statusLinePhase = 0; // 0 = before the version's trailing space, 1 = digits, 2 = skip rest
  int32_t statusValue = 0;
  bool inBody = false;
  int32_t headersWritten = 0;
  char recent[4] = {0, 0, 0, 0};
  bool chunked = false;
  int32_t chunkedMatch = 0;
  const char* chunkedMarker = "chunked";

  // A non-chunked response's length is usually known up front via Content-Length,
  // detected the same rolling-match way as "chunked" above (safe for the same reason:
  // no internal repeated-character overlap). Reading exactly that many body bytes lets
  // the loop return as soon as the response is complete, instead of only ever stopping
  // via the connection closing or the fixed deadline below — relying solely on the
  // latter risks abandoning the socket mid-response, which can wedge the WiFi module's
  // TLS state for the next request on some servers/networks.
  bool hasContentLength = false;
  bool inContentLengthValue = false;
  int32_t contentLength = 0;
  int32_t contentLengthMatch = 0;
  const char* contentLengthMarker = "content-length:";

  int32_t written = 0;
  int32_t chunkState = 0; // 0 = reading hex size, 1 = chunk data, 2 = trailing CRLF, 3 = done
  int32_t chunkRemaining = 0;
  int32_t chunkSizeValue = 0;
  bool chunkExtension = false;
  int availableBytes = 0;

  while (millis() < deadline) {
    // Some transports refill over RPC on every available() call. Drain the reported batch
    // before polling again, otherwise a request can spend its entire deadline fetching single bytes.
    if (availableBytes == 0) availableBytes = client.available();
    if (availableBytes <= 0) {
      availableBytes = 0;
      if (!client.connected()) break;
      juno_io_wait(1);
      continue;
    }
    int value = client.read();
    if (value < 0) break;
    availableBytes--;
    char c = static_cast<char>(value);

    if (inStatusLine) {
      if (statusLinePhase == 0) {
        if (c == ' ') statusLinePhase = 1;
      } else if (statusLinePhase == 1) {
        if (c >= '0' && c <= '9') statusValue = statusValue * 10 + (c - '0');
        else statusLinePhase = 2;
      }
      if (c == '\n') inStatusLine = false;
      continue;
    }

    if (!inBody) {
      char lower = (c >= 'A' && c <= 'Z') ? static_cast<char>(c - 'A' + 'a') : c;
      if (lower == chunkedMarker[chunkedMatch]) {
        chunkedMatch++;
        if (chunkedMarker[chunkedMatch] == 0) chunked = true;
      } else {
        chunkedMatch = (lower == chunkedMarker[0]) ? 1 : 0;
      }
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
      }
      if (headersWritten < headersBufferLength) headersBuffer[headersWritten] = static_cast<uint8_t>(c);
      headersWritten++;
      // An empty body (a 204, say) is complete at the blank line; do not wait for the server to close.
      if (inBody && !chunked && hasContentLength && contentLength == 0) break;
      continue;
    }

    if (!chunked) {
      if (written < responseBufferLength) responseBuffer[written] = static_cast<uint8_t>(c);
      written++;
      if (hasContentLength && written >= contentLength) break;
      continue;
    }

    switch (chunkState) {
      case 0:
        if (c == '\r') break;
        if (c == '\n') {
          chunkRemaining = chunkSizeValue;
          chunkSizeValue = 0;
          chunkExtension = false;
          chunkState = (chunkRemaining == 0) ? 3 : 1;
          break;
        }
        if (chunkExtension) break;
        if (c >= '0' && c <= '9') chunkSizeValue = chunkSizeValue * 16 + (c - '0');
        else if (c >= 'a' && c <= 'f') chunkSizeValue = chunkSizeValue * 16 + (c - 'a' + 10);
        else if (c >= 'A' && c <= 'F') chunkSizeValue = chunkSizeValue * 16 + (c - 'A' + 10);
        else chunkExtension = true;
        break;
      case 1:
        if (written < responseBufferLength) responseBuffer[written] = static_cast<uint8_t>(c);
        written++;
        chunkRemaining--;
        if (chunkRemaining == 0) chunkState = 2;
        break;
      case 2:
        if (c == '\n') chunkState = 0;
        break;
      default:
        break;
    }
    if (chunkState == 3) break;
  }
  client.stop();
  statusAndHeadersLength[0] = statusValue;
  statusAndHeadersLength[1] = headersWritten < headersBufferLength ? headersWritten : headersBufferLength;
  return written < responseBufferLength ? written : responseBufferLength;
}

struct JunoHttpJob {
  const char* method;
  const char* host;
  int32_t port;
  const char* path;
  const char* body;
  const uint8_t* requestHeadersBuffer;
  int32_t requestHeadersLength;
  const uint8_t* requestBodyBuffer;
  int32_t requestBodyLength;
  uint8_t* responseBuffer;
  int32_t responseBufferLength;
  uint8_t* headersBuffer;
  int32_t headersBufferLength;
  int32_t* statusAndHeadersLength;
  void* client;
  int32_t result;
};

template <typename Client>
static void juno_http_job(void* argument) {
  JunoHttpJob* job = static_cast<JunoHttpJob*>(argument);
  Client* client = static_cast<Client*>(job->client);
  job->result = juno_http_request(*client, job->method, job->host, job->port, job->path, job->body,
                                  job->requestHeadersBuffer, job->requestHeadersLength,
                                  job->requestBodyBuffer, job->requestBodyLength,
                                  job->responseBuffer, job->responseBufferLength, job->headersBuffer,
                                  job->headersBufferLength, job->statusAndHeadersLength);
}

template <typename Client>
static int32_t juno_http_run(const char* method, const char* host, int32_t port, const char* path,
                             const char* body,
                             const uint8_t* requestHeadersBuffer, int32_t requestHeadersLength,
                             const uint8_t* requestBodyBuffer, int32_t requestBodyLength,
                             uint8_t* responseBuffer, int32_t responseBufferLength,
                             uint8_t* headersBuffer, int32_t headersBufferLength,
                             int32_t* statusAndHeadersLength) {
  Client client;
  JunoHttpJob job = {method, host, port, path, body,
                     requestHeadersBuffer, requestHeadersLength, requestBodyBuffer, requestBodyLength,
                     responseBuffer, responseBufferLength,
                     headersBuffer, headersBufferLength, statusAndHeadersLength, &client, 0};
  ${JUNO_HTTP_RUN_PREPARE}
  juno_io_offload(juno_http_job<Client>, &job);
  return job.result;
}


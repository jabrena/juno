
template <typename Client>
static int32_t juno_http_request(Client& client, const char* method, const char* host, int32_t port,
                                  const char* path, const char* body,
                                  uint8_t* responseBuffer, int32_t responseBufferLength,
                                  uint8_t* headersBuffer, int32_t headersBufferLength,
                                  int32_t* statusAndHeadersLength) {
  // Waiting for the response lets the other threads run; another HTTP call waits its turn.
  JunoIoGuard guard(JUNO_IO_NETWORK, &client, [](void* handle) { static_cast<Client*>(handle)->stop(); });
  if (!client.connect(host, static_cast<uint16_t>(port))) {
    statusAndHeadersLength[0] = 0;
    statusAndHeadersLength[1] = 0;
    return -1;
  }
  client.print(method);
  client.print(' ');
  client.print(path);
  client.print(" HTTP/1.1\r\nHost: ");
  client.print(host);
  client.print("\r\nConnection: close\r\n");
  if (body != nullptr) {
    client.print("Content-Type: application/json\r\nContent-Length: ");
    client.print(static_cast<unsigned long>(strlen(body)));
    client.print("\r\n\r\n");
    client.print(body);
  } else {
    client.print("\r\n");
  }

  const unsigned long deadline = millis() + 5000;
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

  while (millis() < deadline) {
    if (!client.available()) {
      if (!client.connected()) break;
      juno_io_wait(1);
      continue;
    }
    int value = client.read();
    if (value < 0) break;
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
  uint8_t* responseBuffer;
  int32_t responseBufferLength;
  uint8_t* headersBuffer;
  int32_t headersBufferLength;
  int32_t* statusAndHeadersLength;
  int32_t result;
};

template <typename Client>
static void juno_http_job(void* argument) {
  JunoHttpJob* job = static_cast<JunoHttpJob*>(argument);
  Client client;
  job->result = juno_http_request(client, job->method, job->host, job->port, job->path, job->body,
                                  job->responseBuffer, job->responseBufferLength, job->headersBuffer,
                                  job->headersBufferLength, job->statusAndHeadersLength);
}

template <typename Client>
static int32_t juno_http_run(const char* method, const char* host, int32_t port, const char* path,
                             const char* body, uint8_t* responseBuffer, int32_t responseBufferLength,
                             uint8_t* headersBuffer, int32_t headersBufferLength,
                             int32_t* statusAndHeadersLength) {
  JunoHttpJob job = {method, host, port, path, body, responseBuffer, responseBufferLength,
                     headersBuffer, headersBufferLength, statusAndHeadersLength, 0};
  ${JUNO_HTTP_RUN_PREPARE}
  juno_io_offload(juno_http_job<Client>, &job);
  return job.result;
}


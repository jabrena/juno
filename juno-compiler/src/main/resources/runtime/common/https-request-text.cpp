
static const int32_t JUNO_HTTPS_REQUEST_TEXT_HEADERS_CAPACITY = 1024;

// A request whose target (path?query) is a NUL-terminated String of any length, for the board-side java.net.http.
extern "C" int32_t juno_https_request_text(const char* host, int32_t port, const char* method, const char* target,
                                            const uint8_t* requestHeadersBuffer, int32_t requestHeadersLength,
                                            const uint8_t* requestBodyBuffer, int32_t requestBodyLength,
                                            uint8_t* responseBuffer, int32_t responseBufferLength,
                                            uint8_t* headersBuffer, int32_t headersBufferLength,
                                            int32_t* statusAndHeadersLength) {
  statusAndHeadersLength[0] = 0;
  statusAndHeadersLength[1] = 0;
  if (host == nullptr || method == nullptr || target == nullptr
      || requestHeadersLength < 0 || requestHeadersLength >= JUNO_HTTPS_REQUEST_TEXT_HEADERS_CAPACITY
      || requestBodyLength < -1 || responseBufferLength < 0 || headersBufferLength < 0) {
    return -1;
  }
  return juno_http_run<JunoHttpsClient>(method, host, port, target, nullptr,
                                        requestHeadersBuffer, requestHeadersLength,
                                        requestBodyBuffer, requestBodyLength,
                                        responseBuffer, responseBufferLength,
                                        headersBuffer, headersBufferLength, statusAndHeadersLength);
}

static const int32_t JUNO_HTTP_REQUEST_PATH_CAPACITY = 256;
static const int32_t JUNO_HTTP_REQUEST_HEADERS_CAPACITY = 1024;

extern "C" int32_t juno_http_request_buffer(const char* host, int32_t port, const char* method,
                                               const uint8_t* pathBuffer, int32_t pathLength,
                                               const uint8_t* requestHeadersBuffer, int32_t requestHeadersLength,
                                               const uint8_t* requestBodyBuffer, int32_t requestBodyLength,
                                               uint8_t* responseBuffer, int32_t responseBufferLength,
                                               uint8_t* headersBuffer, int32_t headersBufferLength,
                                               int32_t* statusAndHeadersLength) {
  statusAndHeadersLength[0] = 0;
  statusAndHeadersLength[1] = 0;
  if (host == nullptr || method == nullptr || pathLength < 0
      || pathLength >= JUNO_HTTP_REQUEST_PATH_CAPACITY
      || requestHeadersLength < 0 || requestHeadersLength >= JUNO_HTTP_REQUEST_HEADERS_CAPACITY
      || requestBodyLength < -1 || responseBufferLength < 0 || headersBufferLength < 0) {
    return -1;
  }
  char path[JUNO_HTTP_REQUEST_PATH_CAPACITY];
  memcpy(path, pathBuffer, static_cast<size_t>(pathLength));
  path[pathLength] = '\0';
  return juno_http_run<${JUNO_HTTP_CLIENT_TYPE}>(method, host, port, path, nullptr,
                                        requestHeadersBuffer, requestHeadersLength,
                                        requestBodyBuffer, requestBodyLength,
                                        responseBuffer, responseBufferLength,
                                        headersBuffer, headersBufferLength, statusAndHeadersLength);
}

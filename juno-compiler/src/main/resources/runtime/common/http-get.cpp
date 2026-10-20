
extern "C" int32_t juno_http_get(const char* host, int32_t port, const char* path,
                                  uint8_t* responseBuffer, int32_t responseBufferLength,
                                  uint8_t* headersBuffer, int32_t headersBufferLength,
                                  int32_t* statusAndHeadersLength) {
  return juno_http_run<${JUNO_HTTP_CLIENT_TYPE}>("GET", host, port, path, nullptr,
                           nullptr, 0, nullptr, -1,
                           responseBuffer, responseBufferLength,
                           headersBuffer, headersBufferLength, statusAndHeadersLength);
}

extern "C" int32_t juno_http_post(const char* host, int32_t port, const char* path, const char* body,
                                   uint8_t* responseBuffer, int32_t responseBufferLength,
                                   uint8_t* headersBuffer, int32_t headersBufferLength,
                                   int32_t* statusAndHeadersLength) {
  return juno_http_run<${JUNO_HTTP_CLIENT_TYPE}>("POST", host, port, path, body,
                           nullptr, 0, nullptr, -1,
                           responseBuffer, responseBufferLength,
                           headersBuffer, headersBufferLength, statusAndHeadersLength);
}

extern "C" int32_t juno_http_post_buffer(const char* host, int32_t port, const char* path,
                                           const uint8_t* requestHeadersBuffer,
                                           int32_t requestHeadersLength,
                                           const uint8_t* requestBodyBuffer,
                                           int32_t requestBodyLength,
                                           uint8_t* responseBuffer, int32_t responseBufferLength,
                                           uint8_t* headersBuffer, int32_t headersBufferLength,
                                           int32_t* statusAndHeadersLength) {
  return juno_http_run<${JUNO_HTTP_CLIENT_TYPE}>("POST", host, port, path, nullptr,
                           requestHeadersBuffer, requestHeadersLength,
                           requestBodyBuffer, requestBodyLength,
                           responseBuffer, responseBufferLength,
                           headersBuffer, headersBufferLength, statusAndHeadersLength);
}

extern "C" int32_t juno_http_delete(const char* host, int32_t port, const char* path,
                                     uint8_t* responseBuffer, int32_t responseBufferLength,
                                     uint8_t* headersBuffer, int32_t headersBufferLength,
                                     int32_t* statusAndHeadersLength) {
  return juno_http_run<${JUNO_HTTP_CLIENT_TYPE}>("DELETE", host, port, path, nullptr,
                           nullptr, 0, nullptr, -1,
                           responseBuffer, responseBufferLength,
                           headersBuffer, headersBufferLength, statusAndHeadersLength);
}

extern "C" int32_t juno_http_patch(const char* host, int32_t port, const char* path, const char* body,
                                    uint8_t* responseBuffer, int32_t responseBufferLength,
                                    uint8_t* headersBuffer, int32_t headersBufferLength,
                                    int32_t* statusAndHeadersLength) {
  return juno_http_run<${JUNO_HTTP_CLIENT_TYPE}>("PATCH", host, port, path, body,
                           nullptr, 0, nullptr, -1,
                           responseBuffer, responseBufferLength,
                           headersBuffer, headersBufferLength, statusAndHeadersLength);
}

extern "C" int32_t juno_http_query(const char* host, int32_t port, const char* path, const char* body,
                                    uint8_t* responseBuffer, int32_t responseBufferLength,
                                    uint8_t* headersBuffer, int32_t headersBufferLength,
                                    int32_t* statusAndHeadersLength) {
  return juno_http_run<${JUNO_HTTP_CLIENT_TYPE}>("QUERY", host, port, path, body,
                           nullptr, 0, nullptr, -1,
                           responseBuffer, responseBufferLength,
                           headersBuffer, headersBufferLength, statusAndHeadersLength);
}


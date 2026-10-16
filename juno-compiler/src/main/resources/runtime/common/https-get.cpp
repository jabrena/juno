
extern "C" int32_t juno_https_get(const char* host, int32_t port, const char* path,
                                   uint8_t* responseBuffer, int32_t responseBufferLength,
                                   uint8_t* headersBuffer, int32_t headersBufferLength,
                                   int32_t* statusAndHeadersLength) {
  return juno_http_run<JunoHttpsClient>("GET", host, port, path, nullptr,
                           nullptr, 0, nullptr, -1,
                           responseBuffer, responseBufferLength,
                           headersBuffer, headersBufferLength, statusAndHeadersLength);
}

extern "C" int32_t juno_https_post(const char* host, int32_t port, const char* path, const char* body,
                                    uint8_t* responseBuffer, int32_t responseBufferLength,
                                    uint8_t* headersBuffer, int32_t headersBufferLength,
                                    int32_t* statusAndHeadersLength) {
  return juno_http_run<JunoHttpsClient>("POST", host, port, path, body,
                           nullptr, 0, nullptr, -1,
                           responseBuffer, responseBufferLength,
                           headersBuffer, headersBufferLength, statusAndHeadersLength);
}

extern "C" int32_t juno_https_delete(const char* host, int32_t port, const char* path,
                                      uint8_t* responseBuffer, int32_t responseBufferLength,
                                      uint8_t* headersBuffer, int32_t headersBufferLength,
                                      int32_t* statusAndHeadersLength) {
  return juno_http_run<JunoHttpsClient>("DELETE", host, port, path, nullptr,
                           nullptr, 0, nullptr, -1,
                           responseBuffer, responseBufferLength,
                           headersBuffer, headersBufferLength, statusAndHeadersLength);
}

extern "C" int32_t juno_https_patch(const char* host, int32_t port, const char* path, const char* body,
                                     uint8_t* responseBuffer, int32_t responseBufferLength,
                                     uint8_t* headersBuffer, int32_t headersBufferLength,
                                     int32_t* statusAndHeadersLength) {
  return juno_http_run<JunoHttpsClient>("PATCH", host, port, path, body,
                           nullptr, 0, nullptr, -1,
                           responseBuffer, responseBufferLength,
                           headersBuffer, headersBufferLength, statusAndHeadersLength);
}

extern "C" int32_t juno_https_query(const char* host, int32_t port, const char* path, const char* body,
                                     uint8_t* responseBuffer, int32_t responseBufferLength,
                                     uint8_t* headersBuffer, int32_t headersBufferLength,
                                     int32_t* statusAndHeadersLength) {
  return juno_http_run<JunoHttpsClient>("QUERY", host, port, path, body,
                           nullptr, 0, nullptr, -1,
                           responseBuffer, responseBufferLength,
                           headersBuffer, headersBufferLength, statusAndHeadersLength);
}


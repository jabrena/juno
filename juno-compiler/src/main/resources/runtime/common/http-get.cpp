
extern "C" int32_t juno_http_get(const char* host, int32_t port, const char* path,
                                  uint8_t* responseBuffer, int32_t responseBufferLength,
                                  uint8_t* headersBuffer, int32_t headersBufferLength,
                                  int32_t* statusAndHeadersLength) {
  return juno_http_run<WiFiClient>("GET", host, port, path, nullptr,
                           responseBuffer, responseBufferLength,
                           headersBuffer, headersBufferLength, statusAndHeadersLength);
}

extern "C" int32_t juno_http_post(const char* host, int32_t port, const char* path, const char* body,
                                   uint8_t* responseBuffer, int32_t responseBufferLength,
                                   uint8_t* headersBuffer, int32_t headersBufferLength,
                                   int32_t* statusAndHeadersLength) {
  return juno_http_run<WiFiClient>("POST", host, port, path, body,
                           responseBuffer, responseBufferLength,
                           headersBuffer, headersBufferLength, statusAndHeadersLength);
}

extern "C" int32_t juno_http_delete(const char* host, int32_t port, const char* path,
                                     uint8_t* responseBuffer, int32_t responseBufferLength,
                                     uint8_t* headersBuffer, int32_t headersBufferLength,
                                     int32_t* statusAndHeadersLength) {
  return juno_http_run<WiFiClient>("DELETE", host, port, path, nullptr,
                           responseBuffer, responseBufferLength,
                           headersBuffer, headersBufferLength, statusAndHeadersLength);
}

extern "C" int32_t juno_http_patch(const char* host, int32_t port, const char* path, const char* body,
                                    uint8_t* responseBuffer, int32_t responseBufferLength,
                                    uint8_t* headersBuffer, int32_t headersBufferLength,
                                    int32_t* statusAndHeadersLength) {
  return juno_http_run<WiFiClient>("PATCH", host, port, path, body,
                           responseBuffer, responseBufferLength,
                           headersBuffer, headersBufferLength, statusAndHeadersLength);
}

extern "C" int32_t juno_http_query(const char* host, int32_t port, const char* path, const char* body,
                                    uint8_t* responseBuffer, int32_t responseBufferLength,
                                    uint8_t* headersBuffer, int32_t headersBufferLength,
                                    int32_t* statusAndHeadersLength) {
  return juno_http_run<WiFiClient>("QUERY", host, port, path, body,
                           responseBuffer, responseBufferLength,
                           headersBuffer, headersBufferLength, statusAndHeadersLength);
}


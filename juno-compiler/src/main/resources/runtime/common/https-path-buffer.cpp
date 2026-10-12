
static const int32_t JUNO_HTTP_PATH_BUFFER_CAPACITY = 256;

extern "C" int32_t juno_https_get_path_buffer(const char* host, int32_t port,
                                               const uint8_t* pathBuffer, int32_t pathLength,
                                               uint8_t* responseBuffer, int32_t responseBufferLength,
                                               uint8_t* headersBuffer, int32_t headersBufferLength,
                                               int32_t* statusAndHeadersLength) {
  statusAndHeadersLength[0] = 0;
  statusAndHeadersLength[1] = 0;
  if (pathLength < 0 || pathLength >= JUNO_HTTP_PATH_BUFFER_CAPACITY) {
    return -1;
  }
  char path[JUNO_HTTP_PATH_BUFFER_CAPACITY];
  memcpy(path, pathBuffer, (size_t) pathLength);
  path[pathLength] = '\0';
  return juno_https_get(host, port, path, responseBuffer, responseBufferLength,
                        headersBuffer, headersBufferLength, statusAndHeadersLength);
}


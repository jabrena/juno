
extern "C" int32_t juno_https_request_text_body(const char* host, int32_t port, const char* method, const char* target,
                                                 const uint8_t* requestHeadersBuffer, int32_t requestHeadersLength,
                                                 const uint8_t* requestBodyBuffer, int32_t requestBodyLength,
                                                 uint8_t* headersBuffer, int32_t headersBufferLength,
                                                 int32_t* statusAndHeadersLength) {
  return juno_http_text_request<JunoHttpsClient>(host, port, method, target, requestHeadersBuffer, requestHeadersLength,
                                                requestBodyBuffer, requestBodyLength, headersBuffer, headersBufferLength,
                                                statusAndHeadersLength);
}

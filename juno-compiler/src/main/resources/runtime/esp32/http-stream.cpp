// Streamed responses on the ESP32 transport: the status and headers go out first with no length, then each part is
// written to the same client until juno_http_server_end_stream closes it. The client and the release hook are the
// transport's own (http-server-transport.cpp), so this block is emitted after it.
extern "C" void juno_http_server_begin_stream(int32_t status, const char* contentType) {
  JunoHttpClient& client = juno_http_server_client;
  client.print("HTTP/1.1 ");
  client.print(status);
  client.print(" \r\nContent-Type: ");
  client.print(contentType);
  client.print("\r\nCache-Control: no-cache\r\nConnection: close\r\n\r\n");
}

extern "C" int32_t juno_http_server_stream_bytes(const uint8_t* data, int32_t length) {
  JunoHttpClient& client = juno_http_server_client;
  if (!client.connected()) return 0;
  return client.write(data, static_cast<size_t>(length)) == static_cast<size_t>(length) ? 1 : 0;
}

extern "C" int32_t juno_http_server_stream_text(const char* text) {
  JunoHttpClient& client = juno_http_server_client;
  if (!client.connected()) return 0;
  return client.print(text) == strlen(text) ? 1 : 0;
}

extern "C" void juno_http_server_end_stream() {
  juno_http_server_client.stop();
  juno_http_server_release();
}

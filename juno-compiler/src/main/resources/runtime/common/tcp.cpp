${JUNO_TCP_CLIENT_HELPERS}
static JunoTcpClient juno_tcp_client;

extern "C" int32_t juno_tcp_open(const int32_t* endpoint, int32_t port) {
  if (endpoint == nullptr || port <= 0 || port > 65535) return 0;
  for (int index = 0; index < 4; index++) {
    if (endpoint[index] < 0 || endpoint[index] > 255) return 0;
  }
  juno_tcp_client.stop();
  IPAddress address(static_cast<uint8_t>(endpoint[0]), static_cast<uint8_t>(endpoint[1]),
                    static_cast<uint8_t>(endpoint[2]), static_cast<uint8_t>(endpoint[3]));
  return juno_tcp_client.connect(address, static_cast<uint16_t>(port)) ? 1 : 0;
}

extern "C" int32_t juno_tcp_write(const uint8_t* payload, int32_t length) {
  if (payload == nullptr || length < 0 || !juno_tcp_client.connected()) return -1;
  return static_cast<int32_t>(juno_tcp_client.write(payload, static_cast<size_t>(length)));
}

extern "C" int32_t juno_tcp_read(uint8_t* payload, int32_t offset, int32_t length, int32_t timeoutMillis) {
  if (payload == nullptr || offset < 0 || length < 0 || timeoutMillis <= 0) return -1;
  juno_tcp_client.setTimeout(static_cast<uint32_t>(timeoutMillis));
  unsigned long deadline = millis() + static_cast<unsigned long>(timeoutMillis);
  int32_t received = 0;
  while (received < length && millis() < deadline) {
    if (juno_tcp_client.available() <= 0) {
      if (!juno_tcp_client.connected()) return -1;
      delay(1);
      continue;
    }
    // One bulk read for everything waiting: on the UNO Q each single-byte read is a round trip over the Bridge.
    int32_t wanted = length - received;
    int32_t ready = juno_tcp_client.available();
    int32_t chunk = ready < wanted ? ready : wanted;
    int32_t count = static_cast<int32_t>(juno_tcp_client.read(payload + offset + received, static_cast<size_t>(chunk)));
    if (count <= 0) return -1;
    received += count;
  }
  return received == length ? received : -1;
}

extern "C" void juno_tcp_close() {
  juno_tcp_client.stop();
}

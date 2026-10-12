
${JUNO_UDP_DECLARATION}
static bool juno_udp_listening = false;

static IPAddress juno_udp_discovery_group() {
  // Limited broadcast. A multicast group would be tidier, but neither board really joins one:
  // the UNO Q's BridgeUDP::beginMulticast ignores the address and just binds the port, and
  // the UNO R4 WiFi's modem cannot join at all. A socket bound to the port receives broadcast.
  return IPAddress(255, 255, 255, 255);
}

static bool juno_udp_valid_port(int32_t port) {
  return port > 0 && port <= 65535;
}

static int32_t juno_udp_send_to(const IPAddress& address, int32_t port,
                                const uint8_t* payload, int32_t length) {
  if (!juno_udp_listening || !juno_udp_valid_port(port) || payload == nullptr || length < 0) {
    return -1;
  }
  if (juno_udp.beginPacket(address, static_cast<uint16_t>(port)) != 1) return -1;
  size_t written = juno_udp.write(payload, static_cast<size_t>(length));
  if (written != static_cast<size_t>(length)) {
    juno_udp.endPacket();
    return -1;
  }
  return juno_udp.endPacket() == 1 ? length : -1;
}

extern "C" int32_t juno_udp_listen(int32_t localPort) {
  if (!juno_udp_valid_port(localPort)) return 0;
  if (juno_udp_listening) juno_udp.stop();
  juno_udp_listening = juno_udp.beginMulticast(
      juno_udp_discovery_group(), static_cast<uint16_t>(localPort)) == 1;
  // Not every WiFi stack accepts the multicast call (the UNO R4 WiFi's does not). A plain socket
  // receives the same broadcast and unicast packets, so fall back to it.
  if (!juno_udp_listening) {
    juno_udp_listening = juno_udp.begin(static_cast<uint16_t>(localPort)) == 1;
  }
  return juno_udp_listening ? 1 : 0;
}

extern "C" int32_t juno_udp_send(const int32_t* address, int32_t remotePort,
                                  const uint8_t* payload, int32_t length) {
  if (address == nullptr) return -1;
  for (int32_t i = 0; i < 4; i++) {
    if (address[i] < 0 || address[i] > 255) return -1;
  }
  IPAddress remote(static_cast<uint8_t>(address[0]), static_cast<uint8_t>(address[1]),
                   static_cast<uint8_t>(address[2]), static_cast<uint8_t>(address[3]));
  return juno_udp_send_to(remote, remotePort, payload, length);
}

extern "C" int32_t juno_udp_broadcast(int32_t remotePort, const uint8_t* payload,
                                       int32_t length) {
  return juno_udp_send_to(juno_udp_discovery_group(), remotePort, payload, length);
}

extern "C" int32_t juno_udp_receive(uint8_t* payload, int32_t capacity, int32_t* source) {
  if (!juno_udp_listening || payload == nullptr || source == nullptr || capacity <= 0) return -1;
  int32_t packetLength = static_cast<int32_t>(juno_udp.parsePacket());
  if (packetLength <= 0) return 0;
  auto remote = juno_udp.remoteIP();
  source[0] = remote[0];
  source[1] = remote[1];
  source[2] = remote[2];
  source[3] = remote[3];
  source[4] = static_cast<int32_t>(juno_udp.remotePort());
  int32_t wanted = packetLength < capacity ? packetLength : capacity;
  int32_t copied = static_cast<int32_t>(juno_udp.read(payload, wanted));
  if (copied < 0) return -1;
  while (juno_udp.available() > 0) juno_udp.read();
  return copied;
}

extern "C" void juno_udp_stop() {
  if (juno_udp_listening) juno_udp.stop();
  juno_udp_listening = false;
}

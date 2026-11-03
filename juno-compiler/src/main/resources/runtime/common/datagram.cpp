// ---- java.net.InetAddress, DatagramPacket and DatagramSocket (IPv4, over the one UDP socket) --------
static constexpr int32_t JUNO_DATAGRAM_IO = ${JUNO_DATAGRAM_IO};
static constexpr int32_t JUNO_DATAGRAM_ILLEGAL_ARGUMENT = ${JUNO_DATAGRAM_ILLEGAL_ARGUMENT};

struct JunoInetAddress {
  int32_t octets[4];
};

// capacity is the length the packet's constructor was given, which a datagram can fill at most. A received datagram
// gets a fresh address object, as on the JDK, so a program can keep the sender of one datagram while the next arrives.
struct JunoDatagramPacket {
  uint8_t* data;
  int32_t capacity;
  int32_t length;
  int32_t address;
  int32_t port;
};

struct JunoDatagramSocket {
  int32_t timeoutMillis;
};

static void juno_datagram_raise(int32_t classId, const char* message) {
  auto* exception = static_cast<int32_t*>(juno_alloc(${JUNO_THROWABLE_BYTES}u, 4u));
  exception[${JUNO_CLASS_ID_WORD}] = classId;
  exception[${JUNO_MESSAGE_WORD}] = static_cast<int32_t>(reinterpret_cast<intptr_t>(message));
  juno_throw_raise(static_cast<int32_t>(reinterpret_cast<intptr_t>(exception)));
}

// A null handle is a NullPointerException; every accessor returns nullptr after raising one.
template <typename T>
static T* juno_datagram_pointer(int32_t handle) {
  if (handle == 0) {
    juno_throw_raise(0);
    return nullptr;
  }
  return reinterpret_cast<T*>(static_cast<uintptr_t>(static_cast<uint32_t>(handle)));
}

template <typename T>
static int32_t juno_datagram_handle(T* value) {
  return static_cast<int32_t>(reinterpret_cast<intptr_t>(value));
}

extern "C" int32_t juno_inet_address_get_by_address(const uint8_t* bytes) {
  if (bytes == nullptr) {
    juno_throw_raise(0);
    return 0;
  }
  auto* address = static_cast<JunoInetAddress*>(juno_alloc(sizeof(JunoInetAddress), 4u));
  for (int32_t index = 0; index < 4; index++) {
    address->octets[index] = static_cast<int32_t>(bytes[index]);
  }
  return juno_datagram_handle(address);
}

static int32_t juno_datagram_packet_allocate(uint8_t* data, int32_t length, int32_t address, int32_t port) {
  if (data == nullptr) {
    juno_throw_raise(0);
    return 0;
  }
  if (length < 0) {
    juno_datagram_raise(JUNO_DATAGRAM_ILLEGAL_ARGUMENT, "Negative packet length");
    return 0;
  }
  auto* packet = static_cast<JunoDatagramPacket*>(juno_alloc(sizeof(JunoDatagramPacket), 4u));
  packet->data = data;
  packet->capacity = length;
  packet->length = length;
  packet->address = address;
  packet->port = port;
  return juno_datagram_handle(packet);
}

extern "C" int32_t juno_datagram_packet_new_receive(uint8_t* data, int32_t length) {
  return juno_datagram_packet_allocate(data, length, 0, 0);
}

// "/a.b.c.d", as the JDK's InetAddress.toString() writes an address with no host name.
extern "C" int32_t juno_inet_address_to_string(int32_t handle) {
  auto* address = juno_datagram_pointer<JunoInetAddress>(handle);
  if (address == nullptr) return 0;
  char* text = static_cast<char*>(juno_alloc(20u, 1u));
  int32_t length = 0;
  text[length++] = '/';
  for (int32_t index = 0; index < 4; index++) {
    int32_t octet = address->octets[index];
    if (index > 0) text[length++] = '.';
    if (octet >= 100) text[length++] = static_cast<char>('0' + octet / 100);
    if (octet >= 10) text[length++] = static_cast<char>('0' + (octet / 10) % 10);
    text[length++] = static_cast<char>('0' + octet % 10);
  }
  text[length] = '\0';
  return juno_datagram_handle(text);
}

extern "C" int32_t juno_inet_address_equals(int32_t handle, int32_t otherHandle) {
  if (handle == 0 || otherHandle == 0) return 0;
  auto* left = juno_datagram_pointer<JunoInetAddress>(handle);
  auto* right = juno_datagram_pointer<JunoInetAddress>(otherHandle);
  if (left == nullptr || right == nullptr) return 0;
  for (int32_t index = 0; index < 4; index++) {
    if (left->octets[index] != right->octets[index]) return 0;
  }
  return 1;
}

extern "C" int32_t juno_datagram_packet_new_send(uint8_t* data, int32_t length, int32_t address, int32_t port) {
  return juno_datagram_packet_allocate(data, length, address, port);
}

extern "C" int32_t juno_datagram_packet_get_data(int32_t handle) {
  auto* packet = juno_datagram_pointer<JunoDatagramPacket>(handle);
  return packet == nullptr ? 0 : static_cast<int32_t>(reinterpret_cast<intptr_t>(packet->data));
}

extern "C" int32_t juno_datagram_packet_get_length(int32_t handle) {
  auto* packet = juno_datagram_pointer<JunoDatagramPacket>(handle);
  return packet == nullptr ? 0 : packet->length;
}

extern "C" void juno_datagram_packet_set_length(int32_t handle, int32_t length) {
  auto* packet = juno_datagram_pointer<JunoDatagramPacket>(handle);
  if (packet == nullptr) return;
  if (length < 0 || length > packet->capacity) {
    juno_datagram_raise(JUNO_DATAGRAM_ILLEGAL_ARGUMENT, "Length exceeds the packet's buffer");
    return;
  }
  packet->length = length;
}

extern "C" int32_t juno_datagram_packet_get_address(int32_t handle) {
  auto* packet = juno_datagram_pointer<JunoDatagramPacket>(handle);
  return packet == nullptr ? 0 : packet->address;
}

extern "C" void juno_datagram_packet_set_address(int32_t handle, int32_t address) {
  auto* packet = juno_datagram_pointer<JunoDatagramPacket>(handle);
  if (packet != nullptr) packet->address = address;
}

extern "C" int32_t juno_datagram_packet_get_port(int32_t handle) {
  auto* packet = juno_datagram_pointer<JunoDatagramPacket>(handle);
  return packet == nullptr ? 0 : packet->port;
}

extern "C" void juno_datagram_packet_set_port(int32_t handle, int32_t port) {
  auto* packet = juno_datagram_pointer<JunoDatagramPacket>(handle);
  if (packet != nullptr) packet->port = port;
}

extern "C" int32_t juno_datagram_socket_new(int32_t localPort) {
  if (localPort <= 0 || localPort > 65535) {
    juno_datagram_raise(JUNO_DATAGRAM_ILLEGAL_ARGUMENT, "Port out of range");
    return 0;
  }
  if (juno_udp_listen(localPort) == 0) {
    juno_datagram_raise(JUNO_DATAGRAM_IO, "Address already in use");
    return 0;
  }
  auto* socket = static_cast<JunoDatagramSocket*>(juno_alloc(sizeof(JunoDatagramSocket), 4u));
  socket->timeoutMillis = 0;
  return juno_datagram_handle(socket);
}

extern "C" void juno_datagram_socket_send(int32_t socketHandle, int32_t packetHandle) {
  if (juno_datagram_pointer<JunoDatagramSocket>(socketHandle) == nullptr) return;
  auto* packet = juno_datagram_pointer<JunoDatagramPacket>(packetHandle);
  if (packet == nullptr) return;
  if (packet->address == 0) {
    juno_datagram_raise(JUNO_DATAGRAM_ILLEGAL_ARGUMENT, "Packet has no destination address");
    return;
  }
  auto* address = juno_datagram_pointer<JunoInetAddress>(packet->address);
  if (address == nullptr) return;
  if (juno_udp_send(address->octets, packet->port, packet->data, packet->length) < 0) {
    juno_datagram_raise(JUNO_DATAGRAM_IO, "Send failed: socket closed or packet not sent");
  }
}

extern "C" void juno_datagram_socket_receive(int32_t socketHandle, int32_t packetHandle) {
  auto* socket = juno_datagram_pointer<JunoDatagramSocket>(socketHandle);
  if (socket == nullptr) return;
  auto* packet = juno_datagram_pointer<JunoDatagramPacket>(packetHandle);
  if (packet == nullptr) return;
  int32_t source[5];
  unsigned long start = millis();
  for (;;) {
    int32_t received = juno_udp_receive(packet->data, packet->capacity, source);
    if (received < 0) {
      juno_datagram_raise(JUNO_DATAGRAM_IO, "Socket closed");
      return;
    }
    if (received > 0) {
      auto* sender = static_cast<JunoInetAddress*>(juno_alloc(sizeof(JunoInetAddress), 4u));
      for (int32_t index = 0; index < 4; index++) sender->octets[index] = source[index];
      packet->address = juno_datagram_handle(sender);
      packet->port = source[4];
      packet->length = received;
      return;
    }
    // A timeout returns an empty packet rather than raising, so a poll loop allocates nothing; zero polls once.
    if (millis() - start >= static_cast<unsigned long>(socket->timeoutMillis)) {
      packet->length = 0;
      return;
    }
    delay(1);
  }
}

extern "C" void juno_datagram_socket_set_so_timeout(int32_t socketHandle, int32_t timeoutMillis) {
  auto* socket = juno_datagram_pointer<JunoDatagramSocket>(socketHandle);
  if (socket == nullptr) return;
  if (timeoutMillis < 0) {
    juno_datagram_raise(JUNO_DATAGRAM_ILLEGAL_ARGUMENT, "Timeout must not be negative");
    return;
  }
  socket->timeoutMillis = timeoutMillis;
}

extern "C" void juno_datagram_socket_close(int32_t socketHandle) {
  if (juno_datagram_pointer<JunoDatagramSocket>(socketHandle) == nullptr) return;
  juno_udp_stop();
}


template <typename Client>
static int32_t juno_read_line(Client& client, char* buffer, int32_t bufferCapacity, unsigned long deadline) {
  int32_t length = 0;
  while (true) {
    if (!client.available()) {
      if (!client.connected()) return -1;
      if (millis() >= deadline) return -1;
      delay(1);
      continue;
    }
    int value = client.read();
    if (value < 0) return -1;
    char c = static_cast<char>(value);
    if (c == '\n') break;
    if (c != '\r' && length < bufferCapacity - 1) buffer[length] = c;
    if (c != '\r') length++;
  }
  int32_t written = length < bufferCapacity - 1 ? length : bufferCapacity - 1;
  buffer[written] = 0;
  return written;
}


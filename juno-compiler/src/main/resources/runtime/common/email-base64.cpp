static int32_t juno_base64_encode(const char* data, int32_t length, char* out, int32_t outCapacity) {
  static const char table[] = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
  int32_t needed = ((length + 2) / 3) * 4;
  if (needed + 1 > outCapacity) return -1;
  int32_t o = 0;
  for (int32_t i = 0; i < length; i += 3) {
    uint32_t b0 = static_cast<uint8_t>(data[i]);
    uint32_t b1 = (i + 1 < length) ? static_cast<uint8_t>(data[i + 1]) : 0;
    uint32_t b2 = (i + 2 < length) ? static_cast<uint8_t>(data[i + 2]) : 0;
    uint32_t triple = (b0 << 16) | (b1 << 8) | b2;
    out[o++] = table[(triple >> 18) & 0x3F];
    out[o++] = table[(triple >> 12) & 0x3F];
    out[o++] = (i + 1 < length) ? table[(triple >> 6) & 0x3F] : '=';
    out[o++] = (i + 2 < length) ? table[triple & 0x3F] : '=';
  }
  out[o] = 0;
  return o;
}

template <typename Client>
static int32_t juno_smtp_read_reply(Client& client, unsigned long deadline) {
  char line[128];
  int32_t code = -1;
  while (true) {
    int32_t lineLength = juno_read_line(client, line, sizeof(line), deadline);
    if (lineLength < 4) return -1;
    code = (line[0] - '0') * 100 + (line[1] - '0') * 10 + (line[2] - '0');
    if (line[3] != '-') break;
  }
  return code;
}


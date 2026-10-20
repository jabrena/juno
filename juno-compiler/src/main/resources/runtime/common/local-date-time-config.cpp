static char juno_local_date_time_host[64] = ${JUNO_SNTP_DEFAULT_HOST};
static int32_t juno_local_date_time_port = ${JUNO_SNTP_DEFAULT_PORT};

static bool juno_local_date_time_valid_host(const char* host) {
  uint32_t length = static_cast<uint32_t>(strlen(host));
  if (length == 0u || length >= sizeof(juno_local_date_time_host)) return false;
  for (uint32_t index = 0; index < length; index++) {
    char character = host[index];
    bool letterOrDigit = (character >= 'a' && character <= 'z') || (character >= 'A' && character <= 'Z')
        || (character >= '0' && character <= '9');
    if (!letterOrDigit && character != '.' && character != '-') return false;
  }
  return true;
}

static bool juno_local_date_time_parse_port(const char* text, int32_t* port) {
  int32_t parsed = 0;
  if (*text == 0) return false;
  for (const char* cursor = text; *cursor != 0; cursor++) {
    if (*cursor < '0' || *cursor > '9') return false;
    parsed = parsed * 10 + *cursor - '0';
    if (parsed > 65535) return false;
  }
  if (parsed == 0) return false;
  *port = parsed;
  return true;
}

static const char* juno_local_date_time_property(int32_t properties, const char* key) {
  int32_t value = juno_properties_get(properties, key);
  return value == 0 ? nullptr
      : reinterpret_cast<const char*>(static_cast<uintptr_t>(static_cast<uint32_t>(value)));
}

extern "C" int32_t juno_local_date_time_configure(int32_t properties) {
  const char* host = juno_local_date_time_property(properties, "sntp.host");
  const char* portText = juno_local_date_time_property(properties, "sntp.port");
  if (host == nullptr && portText == nullptr) return 0;

  int32_t port = juno_local_date_time_port;
  if (host != nullptr && !juno_local_date_time_valid_host(host)) return 0;
  if (portText != nullptr && !juno_local_date_time_parse_port(portText, &port)) return 0;

  if (host != nullptr) memcpy(juno_local_date_time_host, host, strlen(host) + 1u);
  juno_local_date_time_port = port;
  return 1;
}

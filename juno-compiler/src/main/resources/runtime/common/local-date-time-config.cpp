
static char juno_local_date_time_host[64] = ${JUNO_LOCAL_DATE_TIME_DEFAULT_HOST};
static int32_t juno_local_date_time_port = ${JUNO_LOCAL_DATE_TIME_DEFAULT_PORT};
static char juno_local_date_time_path[96] = ${JUNO_LOCAL_DATE_TIME_DEFAULT_PATH};
static char juno_local_date_time_json_path[97] = ${JUNO_LOCAL_DATE_TIME_DEFAULT_JSON_PATH};

static bool juno_local_date_time_copy(const char* source, char* target, uint32_t capacity) {
  uint32_t length = static_cast<uint32_t>(strlen(source));
  if (length == 0u || length >= capacity) return false;
  memcpy(target, source, length + 1u);
  return true;
}

static bool juno_local_date_time_parse_url(
    const char* url, char* host, int32_t* port, char* path) {
  static const char* prefix = "https://";
  if (strncmp(url, prefix, 8u) != 0) return false;
  const char* authority = url + 8;
  const char* pathStart = strchr(authority, '/');
  const char* queryStart = strchr(authority, '?');
  if (pathStart == nullptr || (queryStart != nullptr && queryStart < pathStart)) pathStart = queryStart;
  const char* authorityEnd = pathStart == nullptr ? authority + strlen(authority) : pathStart;
  const char* colon = nullptr;
  for (const char* cursor = authority; cursor < authorityEnd; cursor++) {
    if (*cursor == ':') colon = cursor;
  }
  const char* hostEnd = colon == nullptr ? authorityEnd : colon;
  uint32_t hostLength = static_cast<uint32_t>(hostEnd - authority);
  if (hostLength == 0u || hostLength >= 64u) return false;
  memcpy(host, authority, hostLength);
  host[hostLength] = 0;
  *port = 443;
  if (colon != nullptr) {
    int32_t parsedPort = 0;
    for (const char* cursor = colon + 1; cursor < authorityEnd; cursor++) {
      if (*cursor < '0' || *cursor > '9') return false;
      parsedPort = parsedPort * 10 + *cursor - '0';
      if (parsedPort > 65535) return false;
    }
    if (parsedPort == 0) return false;
    *port = parsedPort;
  }
  if (pathStart == nullptr) return juno_local_date_time_copy("/", path, 96u);
  if (*pathStart == '?') {
    path[0] = '/';
    return juno_local_date_time_copy(pathStart, path + 1, 95u);
  }
  return juno_local_date_time_copy(pathStart, path, 96u);
}

static bool juno_local_date_time_valid_json_path(const char* path) {
  uint32_t length = static_cast<uint32_t>(strlen(path));
  if (length == 0u || length >= 97u || path[0] == '.' || path[length - 1u] == '.') return false;
  for (uint32_t index = 0; index < length; index++) {
    if (path[index] == '.' && path[index + 1u] == '.') return false;
    if (static_cast<uint8_t>(path[index]) < 32u) return false;
  }
  return true;
}

static const char* juno_local_date_time_property(int32_t properties, const char* key) {
  int32_t value = juno_properties_get(properties, key);
  return value == 0 ? nullptr
      : reinterpret_cast<const char*>(static_cast<uintptr_t>(static_cast<uint32_t>(value)));
}

extern "C" int32_t juno_local_date_time_configure(int32_t properties) {
  const char* url = juno_local_date_time_property(properties, "time.api.url");
  const char* jsonPath = juno_local_date_time_property(properties, "time.api.jsonPath");
  if (url == nullptr && jsonPath == nullptr) return 0;

  char host[64];
  char path[96];
  char configuredJsonPath[97];
  memcpy(host, juno_local_date_time_host, sizeof(host));
  memcpy(path, juno_local_date_time_path, sizeof(path));
  memcpy(configuredJsonPath, juno_local_date_time_json_path, sizeof(configuredJsonPath));
  int32_t port = juno_local_date_time_port;
  if (url != nullptr && !juno_local_date_time_parse_url(url, host, &port, path)) return 0;
  if (jsonPath != nullptr && (!juno_local_date_time_valid_json_path(jsonPath)
      || !juno_local_date_time_copy(jsonPath, configuredJsonPath, sizeof(configuredJsonPath)))) return 0;

  memcpy(juno_local_date_time_host, host, sizeof(host));
  memcpy(juno_local_date_time_path, path, sizeof(path));
  memcpy(juno_local_date_time_json_path, configuredJsonPath, sizeof(configuredJsonPath));
  juno_local_date_time_port = port;
  return 1;
}


static constexpr int32_t JUNO_SD_MAX_OPEN_FILES = ${JUNO_SD_MAX_OPEN_FILES};
static constexpr int32_t JUNO_SD_BEGIN_ATTEMPTS = 3;
static constexpr uint32_t JUNO_SD_BEGIN_RETRY_DELAY_MS = 250;
static constexpr int32_t JUNO_PROPERTIES_MAX_ENTRIES = ${JUNO_PROPERTIES_MAX_ENTRIES};
static constexpr int32_t JUNO_PROPERTIES_KEY_CAPACITY = ${JUNO_PROPERTIES_KEY_CAPACITY};
static constexpr int32_t JUNO_PROPERTIES_VALUE_CAPACITY = ${JUNO_PROPERTIES_VALUE_CAPACITY};
static constexpr int32_t JUNO_PROPERTIES_LINE_CAPACITY = ${JUNO_PROPERTIES_LINE_CAPACITY};

${JUNO_SD_SPI_DECLARATION}
static SdFat32 juno_sd;
static File32 juno_sd_files[JUNO_SD_MAX_OPEN_FILES];
static bool juno_sd_file_used[JUNO_SD_MAX_OPEN_FILES];

static File32* juno_sd_file(int32_t handle) {
  int32_t index = handle - 1;
  if (index < 0 || index >= JUNO_SD_MAX_OPEN_FILES || !juno_sd_file_used[index]) {
    juno_panic();
  }
  return &juno_sd_files[index];
}

extern "C" int32_t juno_sd_begin(int32_t chipSelectPin) {
  JunoIoGuard guard(JUNO_IO_STORAGE);
  for (int32_t i = 0; i < JUNO_SD_MAX_OPEN_FILES; i++) {
    if (juno_sd_file_used[i]) juno_sd_files[i].close();
    juno_sd_file_used[i] = false;
  }
  // A card that survived a host reset without losing power can fail its first init.
  for (int32_t attempt = 0; attempt < JUNO_SD_BEGIN_ATTEMPTS; attempt++) {
    if (${JUNO_SD_BEGIN_EXPRESSION}) return 1;
    juno_io_wait(JUNO_SD_BEGIN_RETRY_DELAY_MS);
  }
  return 0;
}

extern "C" int32_t juno_sd_exists(const char* path) {
  return juno_sd.exists(path) ? 1 : 0;
}

extern "C" int32_t juno_sd_size(const char* path) {
  File32 file = juno_sd.open(path, O_RDONLY);
  if (!file) return -1;
  uint32_t size = file.size();
  file.close();
  return size > 0x7fffffffu ? 0x7fffffff : static_cast<int32_t>(size);
}

extern "C" int32_t juno_sd_open(const char* path) {
  for (int32_t i = 0; i < JUNO_SD_MAX_OPEN_FILES; i++) {
    if (!juno_sd_file_used[i]) {
      juno_sd_files[i] = juno_sd.open(path, O_RDONLY);
      if (!juno_sd_files[i]) return 0;
      juno_sd_file_used[i] = true;
      return i + 1;
    }
  }
  return 0;
}

extern "C" int32_t juno_sd_file_available(int32_t handle) {
  return juno_sd_file(handle)->available() > 0 ? 1 : 0;
}

extern "C" int32_t juno_sd_file_read(int32_t handle) {
  return juno_sd_file(handle)->read();
}

extern "C" void juno_sd_file_close(int32_t handle) {
  int32_t index = handle - 1;
  File32* file = juno_sd_file(handle);
  file->close();
  juno_sd_file_used[index] = false;
}

extern "C" int32_t juno_sd_file_append_line(const char* path, const char* line) {
  File32 file = juno_sd.open(path, O_WRITE | O_CREAT | O_APPEND);
  if (!file) return 0;
  file.println(line);
  file.close();
  return 1;
}

extern "C" int32_t juno_sd_file_append_text(const char* path, const char* text) {
  File32 file = juno_sd.open(path, O_WRITE | O_CREAT | O_APPEND);
  if (!file) return 0;
  file.print(text);
  file.close();
  return 1;
}

extern "C" int32_t juno_sd_remove(const char* path) {
  return juno_sd.remove(path) ? 1 : 0;
}

struct JunoPropertyEntry {
  char key[JUNO_PROPERTIES_KEY_CAPACITY];
  char value[JUNO_PROPERTIES_VALUE_CAPACITY];
};

struct JunoProperties {
  int32_t count;
  JunoPropertyEntry entries[JUNO_PROPERTIES_MAX_ENTRIES];
};

static JunoProperties* juno_properties(int32_t handle) {
  if (handle == 0) juno_panic();
  return reinterpret_cast<JunoProperties*>(static_cast<intptr_t>(handle));
}

extern "C" int32_t juno_properties_new() {
  auto* properties = static_cast<JunoProperties*>(juno_alloc(sizeof(JunoProperties), 4));
  properties->count = 0;
  return static_cast<int32_t>(reinterpret_cast<intptr_t>(properties));
}

static char* juno_properties_trim_left(char* text) {
  while (*text == ' ' || *text == '\t' || *text == '\f') text++;
  return text;
}

static void juno_properties_trim_right(char* text) {
  int32_t length = static_cast<int32_t>(strlen(text));
  while (length > 0) {
    char c = text[length - 1];
    if (c != ' ' && c != '\t' && c != '\f') break;
    text[--length] = '\0';
  }
}

static bool juno_properties_put(JunoProperties* properties, char* line) {
  char* key = juno_properties_trim_left(line);
  juno_properties_trim_right(key);
  if (*key == '\0' || *key == '#' || *key == '!') return true;

  char* separator = key;
  while (*separator != '\0' && *separator != '=' && *separator != ':') separator++;
  if (*separator == '\0') return true;
  *separator = '\0';
  char* value = juno_properties_trim_left(separator + 1);
  juno_properties_trim_right(key);
  juno_properties_trim_right(value);

  size_t keyLength = strlen(key);
  size_t valueLength = strlen(value);
  if (keyLength == 0 || keyLength >= JUNO_PROPERTIES_KEY_CAPACITY
      || valueLength >= JUNO_PROPERTIES_VALUE_CAPACITY) return false;

  int32_t index = 0;
  while (index < properties->count && strcmp(properties->entries[index].key, key) != 0) index++;
  if (index == properties->count) {
    if (properties->count >= JUNO_PROPERTIES_MAX_ENTRIES) return false;
    properties->count++;
  }
  memcpy(properties->entries[index].key, key, keyLength + 1u);
  memcpy(properties->entries[index].value, value, valueLength + 1u);
  return true;
}

extern "C" void juno_properties_load(int32_t propertiesHandle, int32_t fileHandle) {
  File32* file = juno_sd_file(fileHandle);
  JunoProperties* properties = juno_properties(propertiesHandle);
  char line[JUNO_PROPERTIES_LINE_CAPACITY];
  int32_t length = 0;
  while (file->available() > 0) {
    int32_t value = file->read();
    if (value < 0) juno_panic();
    if (value == '\r') continue;
    if (value == '\n') {
      line[length] = '\0';
      if (!juno_properties_put(properties, line)) juno_panic();
      length = 0;
    } else {
      if (length >= JUNO_PROPERTIES_LINE_CAPACITY - 1) juno_panic();
      line[length++] = static_cast<char>(value);
    }
  }
  if (length > 0) {
    line[length] = '\0';
    if (!juno_properties_put(properties, line)) juno_panic();
  }
}

extern "C" int32_t juno_properties_get(int32_t handle, const char* key) {
  JunoProperties* properties = juno_properties(handle);
  for (int32_t i = 0; i < properties->count; i++) {
    if (strcmp(properties->entries[i].key, key) == 0) {
      return static_cast<int32_t>(reinterpret_cast<intptr_t>(properties->entries[i].value));
    }
  }
  return 0;
}

extern "C" int32_t juno_properties_get_default(
    int32_t handle, const char* key, const char* defaultValue) {
  int32_t value = juno_properties_get(handle, key);
  return value == 0
      ? static_cast<int32_t>(reinterpret_cast<intptr_t>(defaultValue))
      : value;
}

extern "C" int32_t juno_properties_size(int32_t handle) {
  return juno_properties(handle)->count;
}

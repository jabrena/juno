template <typename Client>
static int32_t juno_pop3_expect_ok(Client& client, unsigned long deadline) {
  char line[64];
  int32_t lineLength = juno_read_line(client, line, sizeof(line), deadline);
  return (lineLength >= 3 && line[0] == '+' && line[1] == 'O' && line[2] == 'K') ? 1 : 0;
}

static int32_t juno_pop3_parse_count(const char* line, int32_t lineLength) {
  int32_t i = 3;
  while (i < lineLength && line[i] == ' ') i++;
  int32_t count = 0;
  bool any = false;
  while (i < lineLength && line[i] >= '0' && line[i] <= '9') {
    count = count * 10 + (line[i] - '0');
    any = true;
    i++;
  }
  return any ? count : -1;
}

template <typename Client>
static int32_t juno_pop3_login(Client& client, const char* username, const char* password,
                                unsigned long deadline) {
  if (!juno_pop3_expect_ok(client, deadline)) return -2;
  client.print("USER "); client.print(username); client.print("\r\n");
  if (!juno_pop3_expect_ok(client, deadline)) return -3;
  client.print("PASS "); client.print(password); client.print("\r\n");
  if (!juno_pop3_expect_ok(client, deadline)) return -4;
  return 0;
}

extern "C" int32_t juno_pop3_message_count(const char* host, int32_t port,
                                            const char* username, const char* password) {
  ${JUNO_TLS_CLIENT_DECLARATION}
  if (!client.connect(host, static_cast<uint16_t>(port))) return -1;
  unsigned long deadline = millis() + 10000;
  int32_t loginResult = juno_pop3_login(client, username, password, deadline);
  if (loginResult != 0) { client.stop(); return loginResult; }
  client.print("STAT\r\n");
  char line[64];
  int32_t lineLength = juno_read_line(client, line, sizeof(line), deadline);
  if (lineLength < 3 || line[0] != '+' || line[1] != 'O' || line[2] != 'K') { client.stop(); return -5; }
  int32_t count = juno_pop3_parse_count(line, lineLength);
  client.print("QUIT\r\n");
  client.stop();
  return count;
}

static bool juno_starts_with_ci(const char* line, int32_t lineLength, const char* prefix) {
  int32_t i = 0;
  while (prefix[i] != 0) {
    if (i >= lineLength) return false;
    char a = line[i];
    char b = prefix[i];
    char la = (a >= 'A' && a <= 'Z') ? static_cast<char>(a - 'A' + 'a') : a;
    char lb = (b >= 'A' && b <= 'Z') ? static_cast<char>(b - 'A' + 'a') : b;
    if (la != lb) return false;
    i++;
  }
  return true;
}

static void juno_pop3_append_header(uint8_t* headersBuffer, int32_t headersBufferLength, int32_t* headersWritten,
                                     const char* label, const char* value, int32_t valueLength) {
  int32_t written = *headersWritten;
  for (int32_t i = 0; label[i] != 0; i++) {
    if (written < headersBufferLength) headersBuffer[written] = static_cast<uint8_t>(label[i]);
    written++;
  }
  for (int32_t i = 0; i < valueLength; i++) {
    if (written < headersBufferLength) headersBuffer[written] = static_cast<uint8_t>(value[i]);
    written++;
  }
  if (written < headersBufferLength) headersBuffer[written] = '\n';
  written++;
  *headersWritten = written;
}

static void juno_pop3_extract_header(const char* line, int32_t lineLength, int32_t prefixLength, const char* label,
                                      uint8_t* headersBuffer, int32_t headersBufferLength, int32_t* headersWritten) {
  int32_t valueStart = prefixLength;
  if (valueStart < lineLength && line[valueStart] == ' ') valueStart++;
  juno_pop3_append_header(headersBuffer, headersBufferLength, headersWritten, label,
                           line + valueStart, lineLength - valueStart);
}

extern "C" int32_t juno_pop3_read_latest(const char* host, int32_t port,
                                          const char* username, const char* password,
                                          uint8_t* headersBuffer, int32_t headersBufferLength,
                                          uint8_t* bodyBuffer, int32_t bodyBufferLength,
                                          int32_t* status) {
  status[0] = 0;
  ${JUNO_TLS_CLIENT_DECLARATION}
  if (!client.connect(host, static_cast<uint16_t>(port))) return -1;
  unsigned long deadline = millis() + 20000;

  int32_t loginResult = juno_pop3_login(client, username, password, deadline);
  if (loginResult != 0) { client.stop(); return loginResult; }

  client.print("STAT\r\n");
  char statLine[64];
  int32_t statLineLength = juno_read_line(client, statLine, sizeof(statLine), deadline);
  if (statLineLength < 3 || statLine[0] != '+' || statLine[1] != 'O' || statLine[2] != 'K') {
    client.stop();
    return -5;
  }
  int32_t count = juno_pop3_parse_count(statLine, statLineLength);
  if (count <= 0) {
    client.print("QUIT\r\n");
    client.stop();
    return -6;
  }

  client.print("RETR ");
  client.print(count);
  client.print("\r\n");
  if (!juno_pop3_expect_ok(client, deadline)) { client.stop(); return -7; }

  bool inHeaders = true;
  int32_t headersWritten = 0;
  int32_t bodyWritten = 0;
  char lineBuf[256];
  while (true) {
    int32_t lineLength = juno_read_line(client, lineBuf, sizeof(lineBuf), deadline);
    if (lineLength < 0) { client.stop(); return -8; }
    const char* effectiveLine = lineBuf;
    int32_t effectiveLength = lineLength;
    if (lineLength > 0 && lineBuf[0] == '.') {
      effectiveLine = lineBuf + 1;
      effectiveLength = lineLength - 1;
      if (effectiveLength == 0) break;
    }
    if (inHeaders) {
      if (effectiveLength == 0) {
        inHeaders = false;
        continue;
      }
      if (juno_starts_with_ci(effectiveLine, effectiveLength, "from:")) {
        juno_pop3_extract_header(effectiveLine, effectiveLength, 5, "From: ",
                                  headersBuffer, headersBufferLength, &headersWritten);
      } else if (juno_starts_with_ci(effectiveLine, effectiveLength, "subject:")) {
        juno_pop3_extract_header(effectiveLine, effectiveLength, 8, "Subject: ",
                                  headersBuffer, headersBufferLength, &headersWritten);
      }
      continue;
    }
    for (int32_t i = 0; i < effectiveLength; i++) {
      if (bodyWritten < bodyBufferLength) bodyBuffer[bodyWritten] = static_cast<uint8_t>(effectiveLine[i]);
      bodyWritten++;
    }
    if (bodyWritten < bodyBufferLength) bodyBuffer[bodyWritten] = '\n';
    bodyWritten++;
  }

  client.print("QUIT\r\n");
  client.stop();
  status[0] = headersWritten < headersBufferLength ? headersWritten : headersBufferLength;
  return bodyWritten < bodyBufferLength ? bodyWritten : bodyBufferLength;
}

extern "C" int32_t juno_pop3_read_subject(const char* host, int32_t port,
                                           const char* username, const char* password,
                                           int32_t messageNumber,
                                           uint8_t* subjectBuffer, int32_t subjectBufferLength) {
  ${JUNO_TLS_CLIENT_DECLARATION}
  if (!client.connect(host, static_cast<uint16_t>(port))) return -1;
  unsigned long deadline = millis() + 15000;

  int32_t loginResult = juno_pop3_login(client, username, password, deadline);
  if (loginResult != 0) { client.stop(); return loginResult; }

  client.print("TOP ");
  client.print(messageNumber);
  client.print(" 0\r\n");
  if (!juno_pop3_expect_ok(client, deadline)) { client.stop(); return -5; }

  bool foundSubject = false;
  int32_t subjectWritten = 0;
  char lineBuf[256];
  while (true) {
    int32_t lineLength = juno_read_line(client, lineBuf, sizeof(lineBuf), deadline);
    if (lineLength < 0) { client.stop(); return -6; }
    const char* effectiveLine = lineBuf;
    int32_t effectiveLength = lineLength;
    if (lineLength > 0 && lineBuf[0] == '.') {
      effectiveLine = lineBuf + 1;
      effectiveLength = lineLength - 1;
      if (effectiveLength == 0) break;
    }
    if (!foundSubject && juno_starts_with_ci(effectiveLine, effectiveLength, "subject:")) {
      foundSubject = true;
      int32_t valueStart = 8;
      if (valueStart < effectiveLength && effectiveLine[valueStart] == ' ') valueStart++;
      for (int32_t i = valueStart; i < effectiveLength; i++) {
        if (subjectWritten < subjectBufferLength) subjectBuffer[subjectWritten] = static_cast<uint8_t>(effectiveLine[i]);
        subjectWritten++;
      }
    }
  }

  client.print("QUIT\r\n");
  client.stop();
  return subjectWritten < subjectBufferLength ? subjectWritten : subjectBufferLength;
}


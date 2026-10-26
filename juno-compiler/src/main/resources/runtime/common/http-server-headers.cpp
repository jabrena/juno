// @@ STATE
// The request's header lines as received, up to the buffer's size: enough for a key header after the usual few.
static char juno_http_server_headers_buf[384];
static int32_t juno_http_server_headers_length = 0;
// @@ RESET
  juno_http_server_headers_length = 0;
// @@ CAPTURE
      if (juno_http_server_headers_length < static_cast<int32_t>(sizeof(juno_http_server_headers_buf))) {
        juno_http_server_headers_buf[juno_http_server_headers_length++] = c;
      }
// @@ LOOKUP
static char juno_http_server_lower(char c) {
  return (c >= 'A' && c <= 'Z') ? static_cast<char>(c - 'A' + 'a') : c;
}

// Copies the value of the request header called name (matched case-insensitively, spaces around the value trimmed)
// into out, truncated to outLength, and returns its length; -1 when the request has no such header.
extern "C" int32_t juno_http_server_header(const char* name, uint8_t* out, int32_t outLength) {
  const char* lines = juno_http_server_headers_buf;
  int32_t lineStart = 0;
  while (lineStart < juno_http_server_headers_length) {
    int32_t lineEnd = lineStart;
    while (lineEnd < juno_http_server_headers_length && lines[lineEnd] != '\n') lineEnd++;
    int32_t position = lineStart;
    int32_t matched = 0;
    while (name[matched] != '\0' && position < lineEnd
           && juno_http_server_lower(lines[position]) == juno_http_server_lower(name[matched])) {
      matched++;
      position++;
    }
    if (name[matched] == '\0' && position < lineEnd && lines[position] == ':') {
      position++;
      while (position < lineEnd && (lines[position] == ' ' || lines[position] == '\t')) position++;
      int32_t valueEnd = lineEnd;
      while (valueEnd > position
             && (lines[valueEnd - 1] == '\r' || lines[valueEnd - 1] == ' ' || lines[valueEnd - 1] == '\t')) {
        valueEnd--;
      }
      int32_t copied = 0;
      while (position < valueEnd && copied < outLength) out[copied++] = static_cast<uint8_t>(lines[position++]);
      return copied;
    }
    lineStart = lineEnd + 1;
  }
  return -1;
}


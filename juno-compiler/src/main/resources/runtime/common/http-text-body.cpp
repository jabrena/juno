
// Receives a response body into a runtime String sized to it, for java.net.http's BodyHandlers.ofString(). A body with
// a Content-Length is allocated once, at its exact size; one of unknown length starts small and doubles, the smaller
// copy left for the next collection. The arena is collected at most once, at the start, before the String exists, so
// a collection never sees it half-built; a body that does not fit marks the request too large instead of stopping the
// program, as juno_alloc would.
static constexpr uint32_t JUNO_HTTP_TEXT_FIRST_CAPACITY = 64u;

struct JunoHttpTextSink : JunoHttpBodySink {
  char* text;
  uint32_t capacity;
  uint32_t length;
  bool tooLarge;
};

static char* juno_http_text_allocate(uint32_t bytes) {
  return static_cast<char*>(juno_gc_try_allocate(juno_gc_round_up4(bytes)));
}

static void juno_http_text_start(JunoHttpBodySink* base, int32_t contentLength) {
  JunoHttpTextSink* sink = static_cast<JunoHttpTextSink*>(base);
  // Room for the terminating NUL too.
  uint32_t wanted = contentLength >= 0 ? static_cast<uint32_t>(contentLength) + 1u : JUNO_HTTP_TEXT_FIRST_CAPACITY;
  char* text = juno_http_text_allocate(wanted);
  if (text == nullptr) {
    juno_gc_collect();
    text = juno_http_text_allocate(wanted);
  }
  if (text == nullptr) {
    sink->tooLarge = true;
    return;
  }
  sink->text = text;
  sink->capacity = juno_gc_round_up4(wanted);
}

static void juno_http_text_put(JunoHttpBodySink* base, uint8_t value) {
  JunoHttpTextSink* sink = static_cast<JunoHttpTextSink*>(base);
  if (sink->tooLarge || sink->text == nullptr) return;
  if (sink->length + 1u >= sink->capacity) {
    uint32_t grown = sink->capacity * 2u;
    char* bigger = juno_http_text_allocate(grown);
    if (bigger == nullptr) {
      sink->tooLarge = true;
      return;
    }
    for (uint32_t index = 0; index < sink->length; index++) bigger[index] = sink->text[index];
    sink->text = bigger;
    sink->capacity = juno_gc_round_up4(grown);
  }
  sink->text[sink->length++] = static_cast<char>(value);
}

// Sends a request through juno_http_run and returns its body as a runtime String, or 0 (null) when the request
// failed; statusAndHeadersLength[2] is 1 when the body did not fit in the arena.
template <typename Client>
static int32_t juno_http_text_request(const char* host, int32_t port, const char* method, const char* target,
                                      const uint8_t* requestHeadersBuffer, int32_t requestHeadersLength,
                                      const uint8_t* requestBodyBuffer, int32_t requestBodyLength,
                                      uint8_t* headersBuffer, int32_t headersBufferLength,
                                      int32_t* statusAndHeadersLength) {
  statusAndHeadersLength[0] = 0;
  statusAndHeadersLength[1] = 0;
  statusAndHeadersLength[2] = 0;
  if (host == nullptr || method == nullptr || target == nullptr || requestHeadersLength < 0
      || requestBodyLength < -1 || headersBufferLength < 0) {
    return 0;
  }
  JunoHttpTextSink sink;
  sink.start = juno_http_text_start;
  sink.put = juno_http_text_put;
  sink.text = nullptr;
  sink.capacity = 0u;
  sink.length = 0u;
  sink.tooLarge = false;
  int32_t result = juno_http_run<Client>(method, host, port, target, nullptr,
                                         requestHeadersBuffer, requestHeadersLength,
                                         requestBodyBuffer, requestBodyLength, nullptr, 0,
                                         headersBuffer, headersBufferLength, statusAndHeadersLength, &sink);
  if (result < 0) return 0;
  if (sink.tooLarge) {
    statusAndHeadersLength[2] = 1;
    return 0;
  }
  if (sink.text == nullptr) return static_cast<int32_t>(reinterpret_cast<intptr_t>(""));
  sink.text[sink.length] = '\0';
  return static_cast<int32_t>(reinterpret_cast<intptr_t>(sink.text));
}

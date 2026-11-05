#include <cassert>
#include <cstdint>
#include <cstring>
#include <string>
#include <vector>

static unsigned long now = 0;
static unsigned long millis() { return now; }
static void juno_io_wait(int delay) { now += delay; }

enum { JUNO_IO_NETWORK = 1 };
struct JunoIoGuard {
  JunoIoGuard(int, void*, void (*)(void*)) {}
};
static void juno_io_offload(void (*job)(void*), void* argument) { job(argument); }

// Delivers the request in segments, with an empty gap after each one. Like the UNO R4 WiFi's modem link, a gap can
// also report the peer as not connected once, and a read can fail once, while the peer is still sending.
class SegmentedClient {
  std::vector<std::string> segments;
  size_t segment = 0;
  size_t cursor = 0;
  bool gap = false;
  bool flaky;
  bool failedRead = false;
public:
  bool stopped = false;
  SegmentedClient() : flaky(false) {}
  SegmentedClient(std::vector<std::string> parts, bool flakyLink) : segments(parts), flaky(flakyLink) {}
  int available() {
    if (gap) {
      gap = false;
      return 0;
    }
    if (segment >= segments.size()) return 0;
    return static_cast<int>(segments[segment].size() - cursor);
  }
  bool connected() {
    if (segment >= segments.size()) return false;
    return !flaky;
  }
  int read() {
    if (flaky && !failedRead) {
      failedRead = true;
      return -1;
    }
    if (segment >= segments.size()) return -1;
    int value = static_cast<unsigned char>(segments[segment][cursor++]);
    if (cursor == segments[segment].size()) {
      segment++;
      cursor = 0;
      gap = true;
    }
    return value;
  }
  void stop() { stopped = true; }
};

using JunoHttpClient = SegmentedClient;
static JunoHttpClient juno_http_server_client;
static bool juno_http_server_has_client = false;
static int releases = 0;

static bool juno_http_server_next_client() { return juno_http_server_has_client; }
static void juno_http_server_release() { releases++; }

// HTTP_SERVER_ACCEPT

static int32_t accept(std::vector<std::string> segments, bool flaky, uint8_t* body, int32_t capacity) {
  now = 0;
  releases = 0;
  juno_http_server_client = SegmentedClient(segments, flaky);
  juno_http_server_has_client = true;
  return juno_http_server_accept(body, capacity);
}

// juno_http_server_path()/method() hand back 32-bit board pointers, so a 64-bit host reads the buffers directly.
static const char* path() { return juno_http_server_path_buf; }

static const char* method() { return juno_http_server_method_buf; }

int main() {
  uint8_t body[64] = {};
  const std::string uuidPath = "/api/v1/mailbox/0f8fad5b-d9cb-469f-a165-70867728950e";

  // A GET split mid-path, over a link that misreports once, still yields the whole path.
  int32_t length = accept({"GET", " ", uuidPath.substr(0, 20), uuidPath.substr(20) + " HTTP/1.1\r\nHost: a\r\n",
                           "Connection: close\r\n", "\r\n"}, true, body, sizeof(body));
  assert(length == 0);
  assert(std::strcmp(method(), "GET") == 0);
  assert(uuidPath == path());
  assert(!juno_http_server_client.stopped);
  assert(juno_http_server_content_length() == -1);

  // A POST body that arrives after its headers is read in full.
  length = accept({"POST /api/v1/mailbox/ HTTP/1.1\r\nContent-Length: 9\r\n\r\n", "{\"a\":", "\"b\"}"}, true,
                  body, sizeof(body));
  assert(length == 9);
  assert(std::memcmp(body, "{\"a\":\"b\"}", 9) == 0);
  assert(juno_http_server_content_length() == 9);

  // An explicit empty body ends at the blank line instead of waiting out the deadline.
  length = accept({"DELETE /x HTTP/1.1\r\nContent-Length: 0\r\n\r\n"}, false, body, sizeof(body));
  assert(length == 0);
  assert(now < 100);

  // No headers at all: the blank line right after the request line ends the request.
  length = accept({"GET / HTTP/1.1\r\n\r\n"}, false, body, sizeof(body));
  assert(length == 0);
  assert(std::strcmp(path(), "/") == 0);

  // A peer that leaves mid-path is dropped, never handed to the program with a partial path.
  length = accept({"GET ", uuidPath.substr(0, 20)}, false, body, sizeof(body));
  assert(length == -1);
  assert(juno_http_server_client.stopped);
  assert(releases == 1);
  assert(std::strcmp(path(), "") == 0);

  // So is one that leaves before the declared body arrived.
  length = accept({"POST / HTTP/1.1\r\nContent-Length: 11\r\n\r\n{\"a\""}, false, body, sizeof(body));
  assert(length == -1);
  assert(juno_http_server_client.stopped);

#ifdef JUNO_TEST_HEADERS
  // A header is found whatever the case of its name, with the spaces around its value trimmed.
  uint8_t key[16] = {};
  length = accept({"GET /api/v1/mailbox/ HTTP/1.1\r\nHost: a\r\nmailbox-api-key:  1234567890 \r\n", "\r\n"}, true,
                  body, sizeof(body));
  assert(length == 0);
  assert(juno_http_server_header("Mailbox-API-Key", key, sizeof(key)) == 10);
  assert(std::memcmp(key, "1234567890", 10) == 0);
  // A value longer than the caller's buffer is cut to it.
  assert(juno_http_server_header("Mailbox-API-Key", key, 4) == 4);
  // A missing header, or a name that is only a prefix of one, reads as absent.
  assert(juno_http_server_header("Authorization", key, sizeof(key)) == -1);
  assert(juno_http_server_header("Mailbox-API", key, sizeof(key)) == -1);
  // The next request starts from its own headers.
  length = accept({"GET / HTTP/1.1\r\n\r\n"}, false, body, sizeof(body));
  assert(length == 0);
  assert(juno_http_server_header("Mailbox-API-Key", key, sizeof(key)) == -1);
#endif
}

#include <algorithm>
#include <cassert>
#include <cstdint>
#include <cstring>
#include <string>

static unsigned long now = 0;
static unsigned long millis() { return now; }
static void juno_io_wait(int delay) { now += delay; }

// Like RouterBridge, available() incurs an RPC even when unread bytes remain buffered.
class RpcClient {
  std::string response;
  size_t cursor = 0;
  size_t buffered = 0;
public:
  int polls = 0;
  explicit RpcClient(std::string response) : response(response) {}
  int connect(const char*, uint16_t) { return 1; }
  template<typename T> void print(T) {}
  void write(const uint8_t*, size_t) {}
  void stop() {}
  bool connected() { return cursor < response.size(); }
  int available() {
    now += 10;
    polls++;
    // Exercise an empty poll before each response, too.
    if (polls == 1) return 0;
    buffered = std::min<size_t>(511, response.size() - cursor);
    return static_cast<int>(buffered);
  }
  int read() {
    if (buffered == 0) return -1;
    buffered--;
    return static_cast<unsigned char>(response[cursor++]);
  }
};

// HTTP_CODEC

static void check(const std::string& response, const std::string& expected, int capacity) {
  now = 0;
  RpcClient client(response);
  uint8_t body[4096] = {};
  uint8_t headers[64] = {};
  int32_t status[2] = {};
  int length = juno_http_request(client, "GET", "example.test", 443, "/", nullptr,
      nullptr, 0, nullptr, -1, body, capacity, headers, sizeof(headers), status);
  int expectedLength = std::min<int>(capacity, expected.size());
  assert(status[0] == 200);
  assert(length == expectedLength);
  assert(std::memcmp(body, expected.data(), expectedLength) == 0);
  assert(!client.connected()); // A small caller buffer must still drain the response.
  assert(client.polls < 20);
  assert(now < 5000);
}

int main() {
  std::string body(3424, 'x');
  std::string headers = "HTTP/1.1 200 OK\r\nX-Padding: " + std::string(800, 'h') + "\r\n";
  std::string chunked = headers + "Transfer-Encoding: chunked\r\n\r\n"
      "6b0\r\n" + body.substr(0, 1712) + "\r\n6b0\r\n" + body.substr(1712) + "\r\n0\r\n";
  check(chunked, body, 4096);
  check(chunked, body, 32);
  check(headers + "Content-Length: 3424\r\n\r\n" + body, body, 4096);
  check(headers + "Connection: close\r\n\r\n" + body, body, 4096);
}

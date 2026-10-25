
// Each tcp/read asks the router for as many bytes as the client buffer has room for, capped at the 256-byte RPC buffer
// minus a 20-byte header; but the reply is a MessagePack array in which every byte >= 0x80 takes two bytes, so a
// non-ASCII (e.g. UTF-8) request overflowed the reply and stalled the bridge for good. 112 bytes keeps the worst case
// (112 * 2 + 20) inside 256.
static constexpr size_t JUNO_HTTP_SERVER_BUFFER = 112;
using JunoHttpClient = BridgeTCPClient<JUNO_HTTP_SERVER_BUFFER>;
alignas(BridgeTCPServer<JUNO_HTTP_SERVER_BUFFER>) static unsigned char
    juno_http_server_storage[sizeof(BridgeTCPServer<JUNO_HTTP_SERVER_BUFFER>)];
static BridgeTCPServer<JUNO_HTTP_SERVER_BUFFER>* juno_http_server_instance = nullptr;
static JunoHttpClient juno_http_server_client(Bridge);

extern "C" void juno_http_server_begin(int32_t port) {
  juno_http_server_instance = new (juno_http_server_storage)
      BridgeTCPServer<JUNO_HTTP_SERVER_BUFFER>(Bridge, IPAddress(0, 0, 0, 0), static_cast<uint16_t>(port));
  juno_http_server_instance->begin();
}

static bool juno_http_server_next_client() {
  if (juno_http_server_instance == nullptr) return false;
  JunoHttpClient client = juno_http_server_instance->accept();
  if (!client.connected()) return false;
  juno_http_server_client = client;
  return true;
}

static void juno_http_server_release() {
  juno_http_server_instance->disconnect();
}

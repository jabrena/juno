
using JunoHttpClient = BridgeTCPClient<512>;
alignas(BridgeTCPServer<512>) static unsigned char juno_http_server_storage[sizeof(BridgeTCPServer<512>)];
static BridgeTCPServer<512>* juno_http_server_instance = nullptr;
static JunoHttpClient juno_http_server_client(Bridge);

extern "C" void juno_http_server_begin(int32_t port) {
  juno_http_server_instance = new (juno_http_server_storage)
      BridgeTCPServer<512>(Bridge, IPAddress(0, 0, 0, 0), static_cast<uint16_t>(port));
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

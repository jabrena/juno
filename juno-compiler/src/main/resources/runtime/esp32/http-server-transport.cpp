
using JunoHttpClient = WiFiClient;
alignas(WiFiServer) static unsigned char juno_http_server_storage[sizeof(WiFiServer)];
static WiFiServer* juno_http_server_instance = nullptr;
static JunoHttpClient juno_http_server_client;

extern "C" void juno_http_server_begin(int32_t port) {
  juno_http_server_instance = new (juno_http_server_storage) WiFiServer(static_cast<uint16_t>(port));
  juno_http_server_instance->begin();
}

static bool juno_http_server_next_client() {
  if (juno_http_server_instance == nullptr) return false;
  WiFiClient client = juno_http_server_instance->available();
  if (!client) return false;
  juno_http_server_client = client;
  return true;
}

static void juno_http_server_release() {
}

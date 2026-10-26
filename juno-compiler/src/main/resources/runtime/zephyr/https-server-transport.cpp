
#include <ESP_SSLClient.h>

// HttpServer.beginTls on the UNO Q. The router's listener on Linux (tcp/listen, tcp/accept) only carries bytes, so the
// TLS server session runs here on the MCU, with the BearSSL bundled in the ESP_SSLClient library, over the accepted
// bridge connection.

// Same bound as the plain transport: a tcp/read reply carries its bytes in the 256-byte RPC buffer, where every byte
// >= 0x80 takes two, and TLS records are all binary.
static constexpr size_t JUNO_HTTP_SERVER_BUFFER = 112;
// A tcp/write request carries its bytes the same way, so records leave in pieces of at most this size.
static constexpr size_t JUNO_HTTPS_WRITE_BYTES = 96;
static constexpr unsigned long JUNO_HTTPS_HANDSHAKE_MILLIS = 10000;
static constexpr unsigned long JUNO_HTTPS_CLOSE_MILLIS = 2000;
static constexpr size_t JUNO_HTTPS_DER_CAPACITY = 2048;
// Each cached session takes 100 bytes; a resumed session skips the key exchange on the next connection.
static constexpr size_t JUNO_HTTPS_SESSION_CACHE_BYTES = 8 * 100;

extern "C" int z_impl_sys_csrand_get(void* target, uint32_t length);

using JunoBridgeSocket = BridgeTCPClient<JUNO_HTTP_SERVER_BUFFER>;

static bool juno_https_configured = false;
static br_ssl_server_context juno_https_server;
static unsigned char juno_https_io[BR_SSL_BUFSIZE_BIDI];
static br_ssl_session_cache_lru juno_https_cache;
static unsigned char juno_https_cache_store[JUNO_HTTPS_SESSION_CACHE_BYTES];
static unsigned char juno_https_certificate_der[JUNO_HTTPS_DER_CAPACITY];
static br_x509_certificate juno_https_chain[1];
static br_skey_decoder_context juno_https_key;

// The accepted connection the shared request parser reads from: the plain bridge socket after begin(), or a BearSSL
// server session over it after beginTls(). The engine is driven by hand, one socket transfer at a time.
class JunoHttpsServerClient {
  JunoBridgeSocket socket{Bridge};
  bool secure = false;
  bool socketFailed = false;

  static br_ssl_engine_context* engine() { return &juno_https_server.eng; }

  unsigned state() {
    return socketFailed ? BR_SSL_CLOSED : br_ssl_engine_current_state(engine());
  }

  // Moves one batch between the socket and the engine; true when bytes moved.
  bool pump() {
    unsigned current = state();
    if (current & BR_SSL_CLOSED) return false;
    size_t length;
    if (current & BR_SSL_SENDREC) {
      unsigned char* data = br_ssl_engine_sendrec_buf(engine(), &length);
      if (length > JUNO_HTTPS_WRITE_BYTES) length = JUNO_HTTPS_WRITE_BYTES;
      size_t written = socket.write(data, length);
      if (written == 0) {
        socketFailed = true;
        return false;
      }
      br_ssl_engine_sendrec_ack(engine(), written);
      return true;
    }
    if ((current & BR_SSL_RECVREC) && socket.available() > 0) {
      unsigned char* data = br_ssl_engine_recvrec_buf(engine(), &length);
      int received = socket.read(data, length);
      if (received > 0) {
        br_ssl_engine_recvrec_ack(engine(), static_cast<size_t>(received));
        return true;
      }
    }
    return false;
  }

  // Pumps until the engine can do one of {@code wanted}; false when it closes or the deadline passes first.
  bool waitFor(unsigned wanted, unsigned long millisToWait) {
    const unsigned long deadline = millis() + millisToWait;
    while (millis() < deadline) {
      unsigned current = state();
      if (current & BR_SSL_CLOSED) return false;
      if (current & wanted) return true;
      if (!pump()) {
        if (!socket.connected()) return false;
        juno_io_wait(1);
      }
    }
    return false;
  }

public:
  // Takes over an accepted connection; with TLS configured it completes the handshake first, false when that fails.
  bool open(const JunoBridgeSocket& accepted) {
    socket = accepted;
    secure = false;
    socketFailed = false;
    if (!juno_https_configured) return true;
    unsigned char seed[32];
    z_impl_sys_csrand_get(seed, sizeof(seed));
    br_ssl_engine_inject_entropy(engine(), seed, sizeof(seed));
    if (!br_ssl_server_reset(&juno_https_server)) return false;
    const unsigned long started = millis();
    if (!waitFor(BR_SSL_SENDAPP | BR_SSL_RECVAPP, JUNO_HTTPS_HANDSHAKE_MILLIS)) {
      Serial.print("HTTPS handshake failed, BearSSL error ");
      Serial.println(br_ssl_engine_last_error(engine()));
      return false;
    }
    Serial.print("HTTPS handshake ms ");
    Serial.println(millis() - started);
    secure = true;
    return true;
  }

  int available() {
    if (!secure) return socket.available();
    while (!(state() & (BR_SSL_RECVAPP | BR_SSL_CLOSED)) && pump()) {
    }
    if (!(state() & BR_SSL_RECVAPP)) return 0;
    size_t length;
    br_ssl_engine_recvapp_buf(engine(), &length);
    return static_cast<int>(length);
  }

  bool connected() {
    if (!secure) return socket.connected();
    unsigned current = state();
    return (current & BR_SSL_RECVAPP) || (!(current & BR_SSL_CLOSED) && socket.connected());
  }

  int read() {
    if (!secure) return socket.read();
    if (!(state() & BR_SSL_RECVAPP)) return -1;
    size_t length;
    unsigned char* data = br_ssl_engine_recvapp_buf(engine(), &length);
    int value = data[0];
    br_ssl_engine_recvapp_ack(engine(), 1);
    return value;
  }

  size_t write(const uint8_t* data, size_t length) {
    if (!secure) return socket.write(data, length);
    size_t written = 0;
    while (written < length && waitFor(BR_SSL_SENDAPP, JUNO_HTTPS_HANDSHAKE_MILLIS)) {
      size_t room;
      unsigned char* target = br_ssl_engine_sendapp_buf(engine(), &room);
      size_t chunk = length - written < room ? length - written : room;
      memcpy(target, data + written, chunk);
      br_ssl_engine_sendapp_ack(engine(), chunk);
      written += chunk;
    }
    return written;
  }

  size_t print(const char* text) {
    return write(reinterpret_cast<const uint8_t*>(text), strlen(text));
  }

  size_t print(int32_t value) {
    char digits[12];
    snprintf(digits, sizeof(digits), "%ld", static_cast<long>(value));
    return print(digits);
  }

  void stop() {
    if (secure) {
      br_ssl_engine_flush(engine(), 0);
      br_ssl_engine_close(engine());
      // Sends what is still queued and the close_notify; the engine reports closed once both have left.
      const unsigned long deadline = millis() + JUNO_HTTPS_CLOSE_MILLIS;
      while (millis() < deadline && !(state() & BR_SSL_CLOSED) && (state() & BR_SSL_SENDREC) && pump()) {
      }
      secure = false;
    }
    socket.stop();
  }
};

using JunoHttpClient = JunoHttpsServerClient;
alignas(BridgeTCPServer<JUNO_HTTP_SERVER_BUFFER>) static unsigned char
    juno_http_server_storage[sizeof(BridgeTCPServer<JUNO_HTTP_SERVER_BUFFER>)];
static BridgeTCPServer<JUNO_HTTP_SERVER_BUFFER>* juno_http_server_instance = nullptr;
static JunoHttpClient juno_http_server_client;

extern "C" void juno_http_server_begin(int32_t port) {
  juno_http_server_instance = new (juno_http_server_storage)
      BridgeTCPServer<JUNO_HTTP_SERVER_BUFFER>(Bridge, IPAddress(0, 0, 0, 0), static_cast<uint16_t>(port));
  juno_http_server_instance->begin();
}

struct JunoPemSink {
  unsigned char* target;
  size_t capacity;
  size_t length;
  bool overflow;
};

static void juno_pem_append(void* context, const void* data, size_t length) {
  auto* sink = static_cast<JunoPemSink*>(context);
  if (sink->length + length > sink->capacity) {
    sink->overflow = true;
    return;
  }
  memcpy(sink->target + sink->length, data, length);
  sink->length += length;
}

// Decodes the first PEM object of {@code text} into {@code target}; its DER length, or 0 when it does not decode.
static size_t juno_pem_decode(const uint8_t* text, int32_t length, unsigned char* target, size_t capacity) {
  if (text == nullptr || length <= 0) return 0;
  br_pem_decoder_context decoder;
  br_pem_decoder_init(&decoder);
  JunoPemSink sink = {target, capacity, 0, false};
  bool inObject = false;
  size_t offset = 0;
  const size_t total = static_cast<size_t>(length);
  // The decoder only sees the END line once a newline follows it, which the caller's text need not end with.
  for (int pass = 0; pass < 2; pass++) {
    const unsigned char* source = pass == 0 ? text : reinterpret_cast<const unsigned char*>("\n");
    const size_t sourceLength = pass == 0 ? total : 1;
    offset = 0;
    while (offset < sourceLength) {
      offset += br_pem_decoder_push(&decoder, source + offset, sourceLength - offset);
      switch (br_pem_decoder_event(&decoder)) {
        case BR_PEM_BEGIN_OBJ:
          br_pem_decoder_setdest(&decoder, juno_pem_append, &sink);
          inObject = true;
          break;
        case BR_PEM_END_OBJ:
          if (inObject) return sink.overflow ? 0 : sink.length;
          break;
        case BR_PEM_ERROR:
          return 0;
        default:
          break;
      }
    }
  }
  return 0;
}

extern "C" int32_t juno_http_server_begin_tls(int32_t port, const uint8_t* certificate, int32_t certificateLength,
                                                const uint8_t* privateKey, int32_t privateKeyLength) {
  size_t certificateDer = juno_pem_decode(certificate, certificateLength, juno_https_certificate_der,
                                          sizeof(juno_https_certificate_der));
  if (certificateDer == 0) return 0;
  // The key decoder keeps its own copy of the key, so its DER is wiped from this scratch buffer right after.
  static unsigned char keyDer[JUNO_HTTPS_DER_CAPACITY];
  size_t keyLength = juno_pem_decode(privateKey, privateKeyLength, keyDer, sizeof(keyDer));
  if (keyLength == 0) return 0;
  br_skey_decoder_init(&juno_https_key);
  br_skey_decoder_push(&juno_https_key, keyDer, keyLength);
  memset(keyDer, 0, sizeof(keyDer));
  if (br_skey_decoder_last_error(&juno_https_key) != 0) return 0;

  juno_https_chain[0].data = juno_https_certificate_der;
  juno_https_chain[0].data_len = certificateDer;
  int keyType = br_skey_decoder_key_type(&juno_https_key);
  if (keyType == BR_KEYTYPE_EC) {
    br_ssl_server_init_full_ec(&juno_https_server, juno_https_chain, 1, BR_KEYTYPE_EC,
                               br_skey_decoder_get_ec(&juno_https_key));
  } else if (keyType == BR_KEYTYPE_RSA) {
    br_ssl_server_init_full_rsa(&juno_https_server, juno_https_chain, 1, br_skey_decoder_get_rsa(&juno_https_key));
  } else {
    return 0;
  }
  br_ssl_engine_set_buffer(&juno_https_server.eng, juno_https_io, sizeof(juno_https_io), 1);
  br_ssl_session_cache_lru_init(&juno_https_cache, juno_https_cache_store, sizeof(juno_https_cache_store));
  br_ssl_server_set_cache(&juno_https_server, &juno_https_cache.vtable);
  juno_https_configured = true;
  juno_http_server_begin(port);
  return 1;
}

static bool juno_http_server_next_client() {
  if (juno_http_server_instance == nullptr) return false;
  JunoBridgeSocket client = juno_http_server_instance->accept();
  if (!client.connected()) return false;
  if (juno_http_server_client.open(client)) return true;
  juno_http_server_instance->disconnect();
  return false;
}

static void juno_http_server_release() {
  juno_http_server_instance->disconnect();
}

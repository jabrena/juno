
#include <mbedtls/version.h>
#include <mbedtls/ssl.h>
#include <mbedtls/x509_crt.h>
#include <mbedtls/pk.h>
#include <mbedtls/entropy.h>
#include <mbedtls/ctr_drbg.h>

// The handshake shares accept()'s budget: a client that has not finished it by then is dropped.
static constexpr unsigned long JUNO_HTTPS_HANDSHAKE_MILLIS = 5000;
// Responses are collected into records of this size, so the headers and a small body leave as one TLS record.
static constexpr size_t JUNO_HTTPS_RECORD_BYTES = 512;

static bool juno_https_configured = false;
static mbedtls_ssl_config juno_https_config;
static mbedtls_x509_crt juno_https_certificate;
static mbedtls_pk_context juno_https_key;
static mbedtls_entropy_context juno_https_entropy;
static mbedtls_ctr_drbg_context juno_https_random;

static int juno_https_send(void* context, const unsigned char* data, size_t length) {
  auto* socket = static_cast<WiFiClient*>(context);
  size_t written = socket->write(data, length);
  if (written > 0) return static_cast<int>(written);
  return socket->connected() ? MBEDTLS_ERR_SSL_WANT_WRITE : MBEDTLS_ERR_SSL_CONN_EOF;
}

static int juno_https_receive(void* context, unsigned char* data, size_t length) {
  auto* socket = static_cast<WiFiClient*>(context);
  int waiting = socket->available();
  if (waiting <= 0) return socket->connected() ? MBEDTLS_ERR_SSL_WANT_READ : MBEDTLS_ERR_SSL_CONN_EOF;
  size_t wanted = length < static_cast<size_t>(waiting) ? length : static_cast<size_t>(waiting);
  int received = socket->read(data, wanted);
  return received > 0 ? received : MBEDTLS_ERR_SSL_WANT_READ;
}

// The accepted connection the shared request parser reads from: the plain WiFiClient after begin(), or a TLS server
// session over it after beginTls(). Reads keep one byte of lookahead, since mbedTLS only learns that application data
// is waiting by decrypting a record.
class JunoHttpsServerClient {
  WiFiClient socket;
  mbedtls_ssl_context session;
  bool secure = false;
  bool closed = false;
  int lookahead = -1;
  uint8_t record[JUNO_HTTPS_RECORD_BYTES];
  size_t recordUsed = 0;

  void fill() {
    if (lookahead >= 0 || closed) return;
    unsigned char value;
    int result = mbedtls_ssl_read(&session, &value, 1);
    if (result == 1) {
      lookahead = value;
    } else if (result != MBEDTLS_ERR_SSL_WANT_READ && result != MBEDTLS_ERR_SSL_WANT_WRITE) {
      closed = true;
    }
  }

  void flush() {
    size_t sent = 0;
    while (sent < recordUsed) {
      int result = mbedtls_ssl_write(&session, record + sent, recordUsed - sent);
      if (result > 0) {
        sent += static_cast<size_t>(result);
      } else if (result != MBEDTLS_ERR_SSL_WANT_READ && result != MBEDTLS_ERR_SSL_WANT_WRITE) {
        break;
      }
    }
    recordUsed = 0;
  }

public:
  JunoHttpsServerClient() { mbedtls_ssl_init(&session); }

  // Takes over an accepted connection; with TLS configured it completes the handshake first, false when that fails.
  bool open(const WiFiClient& accepted) {
    socket = accepted;
    secure = false;
    closed = false;
    lookahead = -1;
    recordUsed = 0;
    if (!juno_https_configured) return true;
    mbedtls_ssl_free(&session);
    mbedtls_ssl_init(&session);
    if (mbedtls_ssl_setup(&session, &juno_https_config) != 0) {
      socket.stop();
      return false;
    }
    mbedtls_ssl_set_bio(&session, &socket, juno_https_send, juno_https_receive, nullptr);
    const unsigned long deadline = millis() + JUNO_HTTPS_HANDSHAKE_MILLIS;
    int result;
    while ((result = mbedtls_ssl_handshake(&session)) != 0) {
      if ((result != MBEDTLS_ERR_SSL_WANT_READ && result != MBEDTLS_ERR_SSL_WANT_WRITE) || millis() >= deadline) {
        mbedtls_ssl_free(&session);
        mbedtls_ssl_init(&session);
        socket.stop();
        return false;
      }
      juno_io_wait(1);
    }
    secure = true;
    return true;
  }

  int available() {
    if (!secure) return socket.available();
    fill();
    return (lookahead >= 0 ? 1 : 0) + static_cast<int>(mbedtls_ssl_get_bytes_avail(&session));
  }

  bool connected() {
    if (!secure) return socket.connected();
    return lookahead >= 0 || mbedtls_ssl_get_bytes_avail(&session) > 0 || (!closed && socket.connected());
  }

  int read() {
    if (!secure) return socket.read();
    fill();
    int value = lookahead;
    lookahead = -1;
    return value;
  }

  size_t write(const uint8_t* data, size_t length) {
    if (!secure) return socket.write(data, length);
    for (size_t index = 0; index < length; index++) {
      if (recordUsed == JUNO_HTTPS_RECORD_BYTES) flush();
      record[recordUsed++] = data[index];
    }
    return length;
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
      flush();
      mbedtls_ssl_close_notify(&session);
      mbedtls_ssl_free(&session);
      mbedtls_ssl_init(&session);
      secure = false;
    }
    socket.stop();
  }
};

using JunoHttpClient = JunoHttpsServerClient;
alignas(WiFiServer) static unsigned char juno_http_server_storage[sizeof(WiFiServer)];
static WiFiServer* juno_http_server_instance = nullptr;
static JunoHttpClient juno_http_server_client;

extern "C" void juno_http_server_begin(int32_t port) {
  juno_http_server_instance = new (juno_http_server_storage) WiFiServer(static_cast<uint16_t>(port));
  juno_http_server_instance->begin();
}

// PEM parsing wants the text NUL-terminated and counted with its NUL, which the caller's buffer need not have room for.
static bool juno_https_parse(const uint8_t* text, int32_t length, bool key) {
  if (text == nullptr || length <= 0) return false;
  auto* copy = static_cast<unsigned char*>(malloc(static_cast<size_t>(length) + 1));
  if (copy == nullptr) return false;
  memcpy(copy, text, static_cast<size_t>(length));
  copy[length] = '\0';
  int result;
  if (key) {
#if MBEDTLS_VERSION_MAJOR >= 3
    result = mbedtls_pk_parse_key(&juno_https_key, copy, static_cast<size_t>(length) + 1, nullptr, 0,
                                  mbedtls_ctr_drbg_random, &juno_https_random);
#else
    result = mbedtls_pk_parse_key(&juno_https_key, copy, static_cast<size_t>(length) + 1, nullptr, 0);
#endif
  } else {
    result = mbedtls_x509_crt_parse(&juno_https_certificate, copy, static_cast<size_t>(length) + 1);
  }
  free(copy);
  return result == 0;
}

extern "C" int32_t juno_http_server_begin_tls(int32_t port, const uint8_t* certificate, int32_t certificateLength,
                                                const uint8_t* privateKey, int32_t privateKeyLength) {
  static const char personalization[] = "juno-https-server";
  mbedtls_ssl_config_init(&juno_https_config);
  mbedtls_x509_crt_init(&juno_https_certificate);
  mbedtls_pk_init(&juno_https_key);
  mbedtls_entropy_init(&juno_https_entropy);
  mbedtls_ctr_drbg_init(&juno_https_random);
  bool ready = mbedtls_ctr_drbg_seed(&juno_https_random, mbedtls_entropy_func, &juno_https_entropy,
                                     reinterpret_cast<const unsigned char*>(personalization),
                                     sizeof(personalization) - 1) == 0
      && juno_https_parse(certificate, certificateLength, false)
      && juno_https_parse(privateKey, privateKeyLength, true)
      && mbedtls_ssl_config_defaults(&juno_https_config, MBEDTLS_SSL_IS_SERVER, MBEDTLS_SSL_TRANSPORT_STREAM,
                                     MBEDTLS_SSL_PRESET_DEFAULT) == 0
      && mbedtls_ssl_conf_own_cert(&juno_https_config, &juno_https_certificate, &juno_https_key) == 0;
  if (!ready) return 0;
  mbedtls_ssl_conf_rng(&juno_https_config, mbedtls_ctr_drbg_random, &juno_https_random);
  juno_https_configured = true;
  juno_http_server_begin(port);
  return 1;
}

static bool juno_http_server_next_client() {
  if (juno_http_server_instance == nullptr) return false;
  WiFiClient client = juno_http_server_instance->available();
  if (!client) return false;
  return juno_http_server_client.open(client);
}

static void juno_http_server_release() {
}

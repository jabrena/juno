#include <WiFiServerSecureBearSSL.h>

using JunoHttpClient = BearSSL::WiFiClientSecure;
alignas(BearSSL::WiFiServerSecure) static unsigned char juno_http_server_storage[sizeof(BearSSL::WiFiServerSecure)];
static BearSSL::WiFiServerSecure* juno_http_server_instance = nullptr;
static JunoHttpClient juno_http_server_client;
static BearSSL::X509List* juno_http_server_certificate = nullptr;
static BearSSL::PrivateKey* juno_http_server_private_key = nullptr;

static char* juno_http_server_copy_pem(const uint8_t* text, int32_t length) {
  if (text == nullptr || length <= 0) return nullptr;
  auto* copy = static_cast<char*>(malloc(static_cast<size_t>(length) + 1));
  if (copy == nullptr) return nullptr;
  memcpy(copy, text, static_cast<size_t>(length));
  copy[length] = '\0';
  return copy;
}

extern "C" int32_t juno_http_server_begin_tls(int32_t port, const uint8_t* certificate, int32_t certificateLength,
                                                const uint8_t* privateKey, int32_t privateKeyLength) {
  char* certificateText = juno_http_server_copy_pem(certificate, certificateLength);
  char* privateKeyText = juno_http_server_copy_pem(privateKey, privateKeyLength);
  if (certificateText == nullptr || privateKeyText == nullptr) {
    free(certificateText);
    free(privateKeyText);
    return 0;
  }
  delete juno_http_server_certificate;
  delete juno_http_server_private_key;
  juno_http_server_certificate = new BearSSL::X509List(certificateText);
  juno_http_server_private_key = new BearSSL::PrivateKey(privateKeyText);
  free(certificateText);
  free(privateKeyText);
  if (juno_http_server_certificate->getCount() == 0
      || (!juno_http_server_private_key->isRSA() && !juno_http_server_private_key->isEC())) {
    return 0;
  }
  juno_http_server_instance = new (juno_http_server_storage) BearSSL::WiFiServerSecure(static_cast<uint16_t>(port));
  if (juno_http_server_private_key->isRSA()) {
    juno_http_server_instance->setRSACert(juno_http_server_certificate, juno_http_server_private_key);
  } else {
    juno_http_server_instance->setECCert(juno_http_server_certificate, BR_KEYTYPE_EC, juno_http_server_private_key);
  }
  juno_http_server_instance->begin();
  return 1;
}

static bool juno_http_server_next_client() {
  if (juno_http_server_instance == nullptr) return false;
  JunoHttpClient client = juno_http_server_instance->accept();
  if (!client) return false;
  juno_http_server_client = client;
  return true;
}

static void juno_http_server_release() {
}

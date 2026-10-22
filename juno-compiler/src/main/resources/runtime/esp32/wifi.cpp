extern "C" void juno_wifi_begin(const char* ssid, const char* password) {
  WiFi.begin(ssid, password);
}

// The access point runs in the core's softAP mode; WPA2 needs a passphrase of 8+ characters, so an empty one opens it.
extern "C" void juno_wifi_begin_ap(const char* ssid, const char* password) {
  if (password != nullptr && password[0] != '\0') {
    WiFi.softAP(ssid, password);
  } else {
    WiFi.softAP(ssid);
  }
}

// 7 is Wifi.STATUS_AP_LISTENING: the station is not connected but the access point is up.
extern "C" int32_t juno_wifi_status() {
  if (WiFi.status() != WL_CONNECTED && WiFi.softAPIP() != IPAddress(0, 0, 0, 0)) {
    return 7;
  }
  return static_cast<int32_t>(WiFi.status());
}

// The station address once connected; otherwise the access point's address (192.168.4.1 by default).
extern "C" void juno_wifi_local_ip(int32_t* octets) {
  auto ip = WiFi.status() == WL_CONNECTED ? WiFi.localIP() : WiFi.softAPIP();
  octets[0] = ip[0];
  octets[1] = ip[1];
  octets[2] = ip[2];
  octets[3] = ip[3];
}

extern "C" void juno_wifi_gateway_ip(int32_t* octets) {
  auto ip = WiFi.gatewayIP();
  octets[0] = ip[0];
  octets[1] = ip[1];
  octets[2] = ip[2];
  octets[3] = ip[3];
}

extern "C" void juno_wifi_mac_address(int32_t* octets) {
  uint8_t mac[6] = {0, 0, 0, 0, 0, 0};
  WiFi.macAddress(mac);
  for (int i = 0; i < 6; i++) {
    octets[i] = mac[i];
  }
}

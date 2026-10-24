
extern "C" void juno_wifi_begin(const char* ssid, const char* password) {
  WiFi.begin(ssid, password);
}

extern "C" void juno_wifi_begin_ap(const char* ssid, const char* password) {
  // WiFiS3 only accepts a WPA2 passphrase of 8+ characters; an empty one opens the network.
  if (password != nullptr && password[0] != '\0') {
    WiFi.beginAP(ssid, password);
  } else {
    WiFi.beginAP(ssid);
  }
}

extern "C" int32_t juno_wifi_status() {
  return static_cast<int32_t>(WiFi.status());
}

extern "C" void juno_wifi_local_ip(int32_t* octets) {
  // WiFiS3.localIP() returns the active interface address in station and access-point modes.
  auto ip = WiFi.localIP();
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

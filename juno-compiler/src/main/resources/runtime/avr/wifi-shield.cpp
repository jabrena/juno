extern "C" void juno_wifi_begin(const char* ssid, const char* password) {
  // The legacy WiFi Shield library takes a mutable SSID it never writes; WiFi.begin blocks until it connects or gives up.
  WiFi.begin(const_cast<char*>(ssid), password);
}

extern "C" int32_t juno_wifi_status() {
  return static_cast<int32_t>(WiFi.status());
}

extern "C" void juno_wifi_local_ip(int32_t* octets) {
  IPAddress ip = WiFi.localIP();
  for (uint8_t i = 0; i < 4; i++) {
    octets[i] = ip[i];
  }
}

extern "C" void juno_wifi_gateway_ip(int32_t* octets) {
  IPAddress ip = WiFi.gatewayIP();
  for (uint8_t i = 0; i < 4; i++) {
    octets[i] = ip[i];
  }
}

extern "C" void juno_wifi_mac_address(int32_t* octets) {
  uint8_t mac[6] = {0, 0, 0, 0, 0, 0};
  WiFi.macAddress(mac);
  for (uint8_t i = 0; i < 6; i++) {
    octets[i] = mac[i];
  }
}

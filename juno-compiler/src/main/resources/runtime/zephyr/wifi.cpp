
// Starting the bridge waits for the Linux side; it runs on the I/O worker, so other threads keep going.
static void juno_wifi_bridge_begin(void*) {
  Bridge.begin();
}

extern "C" void juno_wifi_begin(const char* ssid, const char* password) {
  // UNO Q Wi-Fi is configured by Linux (App Lab/nmcli), not by the MCU sketch.
  static_cast<void>(ssid);
  static_cast<void>(password);
  JunoIoGuard guard(JUNO_IO_NETWORK);
  juno_io_offload(juno_wifi_bridge_begin, nullptr);
}

extern "C" void juno_wifi_begin_ap(const char* ssid, const char* password) {
  // The MCU cannot create an access point: the hotspot belongs to Linux
  // (nmcli device wifi hotspot ssid <ssid> password <password>). Only start the bridge.
  static_cast<void>(ssid);
  static_cast<void>(password);
  JunoIoGuard guard(JUNO_IO_NETWORK);
  juno_io_offload(juno_wifi_bridge_begin, nullptr);
}

extern "C" int32_t juno_wifi_status() {
  return Bridge ? 3 : 0;
}

extern "C" void juno_wifi_local_ip(int32_t* octets) {
  // arduino-router 0.4.x does not expose the Linux interface address over RPC.
  octets[0] = 0;
  octets[1] = 0;
  octets[2] = 0;
  octets[3] = 0;
}

extern "C" void juno_wifi_gateway_ip(int32_t* octets) {
  // Likewise not exposed over RPC.
  octets[0] = 0;
  octets[1] = 0;
  octets[2] = 0;
  octets[3] = 0;
}

extern "C" void juno_wifi_mac_address(int32_t* octets) {
  // Likewise not exposed over RPC.
  for (int i = 0; i < 6; i++) {
    octets[i] = 0;
  }
}

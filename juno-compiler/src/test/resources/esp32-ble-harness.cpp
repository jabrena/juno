#include <cassert>
#include <cstdint>
#include <cstring>
#include <string>

using portMUX_TYPE = int;
#define portMUX_INITIALIZER_UNLOCKED 0
static bool inCritical = false;
static int waits = 0;
static void portENTER_CRITICAL(portMUX_TYPE*) { assert(!inCritical); inCritical = true; }
static void portEXIT_CRITICAL(portMUX_TYPE*) { assert(inCritical); inCritical = false; }
static void delay(int ms) { assert(!inCritical && ms > 0); waits++; }

enum esp_ble_addr_type_t { BLE_ADDR_TYPE_PUBLIC, BLE_ADDR_TYPE_RANDOM };
class BLEAddress {
  uint8_t bytes[6] = {1, 2, 3, 4, 5, 6};
 public:
  BLEAddress() = default;
  explicit BLEAddress(uint8_t* address) { memcpy(bytes, address, 6); }
  uint8_t (*getNative())[6] { return &bytes; }
};
class BLEUUID {
 public:
  std::string value;
  explicit BLEUUID(std::string uuid) : value(uuid) {}
};
class BLERemoteCharacteristic {
 public:
  bool response = true;
  uint8_t written[32] = {};
  size_t size = 0;
  void registerForNotify(void (*)(BLERemoteCharacteristic*, uint8_t*, size_t, bool)) {}
  void writeValue(uint8_t* data, size_t length, bool withResponse) {
    response = withResponse;
    size = length;
    memcpy(written, data, length);
  }
};
class BLERemoteService {
 public:
  BLERemoteCharacteristic remote;
  BLERemoteCharacteristic* getCharacteristic(const char*) { return &remote; }
};
class BLEClient {
 public:
  BLERemoteService service;
  bool connected = false;
  bool connect(BLEAddress, esp_ble_addr_type_t type) {
    assert(type == BLE_ADDR_TYPE_RANDOM);
    connected = true;
    return true;
  }
  bool isConnected() { return connected; }
  BLERemoteService* getService(const char*) { return &service; }
  void disconnect() { connected = false; }
};
class BLEAdvertisedDevice {
 public:
  std::string service;
  explicit BLEAdvertisedDevice(std::string uuid) : service(uuid) {}
  bool isAdvertisingService(BLEUUID uuid) { return service == uuid.value; }
  bool haveManufacturerData() { return true; }
  std::string getManufacturerData() { return std::string("\x97\x03\x00\x40", 4); }
  BLEAddress getAddress() { return BLEAddress(); }
  esp_ble_addr_type_t getAddressType() { return BLE_ADDR_TYPE_RANDOM; }
};
class BLEAdvertisedDeviceCallbacks {
 public:
  virtual void onResult(BLEAdvertisedDevice) = 0;
};
class BLEScanResults {};
class BLEScan {
 public:
  BLEAdvertisedDeviceCallbacks* callbacks = nullptr;
  bool active = false;
  uint32_t duration = 0;
  uint32_t elapsed = 0;
  void setActiveScan(bool) {}
  void setAdvertisedDeviceCallbacks(BLEAdvertisedDeviceCallbacks* cb, bool) { callbacks = cb; }
  bool start(uint32_t seconds, void (*)(BLEScanResults), bool) {
    duration = seconds;
    elapsed = 0;
    active = true;
    return true;
  }
  void advance(uint32_t seconds) {
    elapsed += seconds;
    if (duration != 0 && elapsed >= duration) active = false;
  }
  void advertise(const char* uuid) {
    if (active) callbacks->onResult(BLEAdvertisedDevice(uuid));
  }
  void stop() { active = false; }
};
class BLEDevice {
 public:
  static void init(const char*) {}
  static BLEClient* createClient() { static BLEClient client; return &client; }
  static BLEScan* getScan() { static BLEScan scan; return &scan; }
};

// ESP32_BLE

int main() {
  assert(juno_ble.begin());
  juno_ble.scanForUuid("lego-service");
  BLEScan* scan = BLEDevice::getScan();
  assert(!juno_ble.available());
  assert(waits > 0);
  scan->advertise("other-service");
  assert(!juno_ble.available());

  // A hub switched on after the original 30-second scan must still be found.
  scan->advance(45);
  scan->advertise("lego-service");
  JunoBLEDevice hub = juno_ble.available();
  assert(hub);
  assert(!juno_ble.available());
  uint8_t manufacturer[4];
  assert(hub.manufacturerData(manufacturer, 4) == 4);
  assert(manufacturer[0] == 0x97 && manufacturer[1] == 3 && manufacturer[3] == 0x40);
  juno_ble.stopScan();
  assert(!scan->active);
  assert(hub.connect());
  assert(hub.discoverService("lego-service"));
  JunoBLECharacteristic characteristic = hub.characteristic("lego-characteristic");
  uint8_t command[] = {8, 0, 0x81, 0x32, 0x11, 0x51, 0, 9};
  characteristic.writeValue(command, sizeof(command), false);
  assert(!characteristic.remote->response);
  assert(characteristic.remote->size == sizeof(command));
  assert(memcmp(characteristic.remote->written, command, sizeof(command)) == 0);
  characteristic.writeValue(command, sizeof(command), true);
  assert(characteristic.remote->response);
  hub.disconnect();
  assert(!hub.connected());
  return 0;
}

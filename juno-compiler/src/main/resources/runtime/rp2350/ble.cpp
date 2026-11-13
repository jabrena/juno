#include <list>

// Adapter from arduino-pico's BTstack BLE API to the small ArduinoBLE-shaped surface used by Juno's
// LEGO Powered UP central. The core owns the scan list and remote GATT objects; this adapter only keeps
// the current connection and a bounded notification queue.
enum JunoPicoBleEvent { BLEUpdated = 1 };

class JunoPicoBleDevice;
class JunoPicoBleCharacteristic;
using JunoPicoBleHandler = void (*)(JunoPicoBleDevice, JunoPicoBleCharacteristic);

static constexpr int JUNO_PICO_BLE_BYTES = 32;
static constexpr int JUNO_PICO_BLE_QUEUE = 8;
static uint8_t juno_pico_ble_queue[JUNO_PICO_BLE_QUEUE][JUNO_PICO_BLE_BYTES];
static int32_t juno_pico_ble_queue_lengths[JUNO_PICO_BLE_QUEUE];
static int32_t juno_pico_ble_queue_head = 0;
static int32_t juno_pico_ble_queue_count = 0;
static uint8_t juno_pico_ble_current[JUNO_PICO_BLE_BYTES];
static int32_t juno_pico_ble_current_length = 0;
static JunoPicoBleHandler juno_pico_ble_handler = nullptr;
static BLERemoteCharacteristic* juno_pico_ble_notifying = nullptr;
static BLEClient* juno_pico_ble_client = nullptr;
static BLERemoteService* juno_pico_ble_service = nullptr;

static void juno_pico_ble_notify(BLERemoteCharacteristic*, const uint8_t* data, uint32_t length) {
  if (juno_pico_ble_queue_count == JUNO_PICO_BLE_QUEUE) return;
  int32_t slot = (juno_pico_ble_queue_head + juno_pico_ble_queue_count) % JUNO_PICO_BLE_QUEUE;
  int32_t copied = length < JUNO_PICO_BLE_BYTES ? static_cast<int32_t>(length) : JUNO_PICO_BLE_BYTES;
  memcpy(juno_pico_ble_queue[slot], data, copied);
  juno_pico_ble_queue_lengths[slot] = copied;
  juno_pico_ble_queue_count++;
}

class JunoPicoBleCharacteristic {
 public:
  BLERemoteCharacteristic* remote = nullptr;

  explicit operator bool() const {
    return remote != nullptr;
  }

  void setEventHandler(int event, JunoPicoBleHandler handler) {
    if (event == BLEUpdated) juno_pico_ble_handler = handler;
  }

  bool subscribe() {
    if (remote == nullptr) return false;
    juno_pico_ble_notifying = remote;
    remote->onNotify(juno_pico_ble_notify);
    return remote->enableNotifications();
  }

  int valueLength() const {
    return juno_pico_ble_current_length;
  }

  const uint8_t* value() const {
    return juno_pico_ble_current;
  }

  void writeValue(uint8_t* data, int length, bool) {
    if (remote != nullptr) remote->setValue(data, static_cast<size_t>(length));
  }
};

class JunoPicoBleDevice {
 public:
  BLEAddress address;
  bool valid = false;
  uint8_t manufacturer[JUNO_PICO_BLE_BYTES] = {};
  int32_t manufacturerLength = 0;

  explicit operator bool() const {
    return valid;
  }

  int manufacturerDataLength() const {
    return manufacturerLength;
  }

  int manufacturerData(uint8_t* buffer, int length) const {
    int32_t copied = manufacturerLength < length ? manufacturerLength : length;
    memcpy(buffer, manufacturer, copied);
    return copied;
  }

  bool connect() {
    if (!valid) return false;
    juno_pico_ble_client = BLE.client();
    return juno_pico_ble_client != nullptr && juno_pico_ble_client->connect(address);
  }

  bool discoverService(const char* uuid) {
    if (juno_pico_ble_client == nullptr || !juno_pico_ble_client->connected()) return false;
    juno_pico_ble_service = juno_pico_ble_client->service(BLEUUID(String(uuid)));
    return juno_pico_ble_service != nullptr;
  }

  JunoPicoBleCharacteristic characteristic(const char* uuid) const {
    JunoPicoBleCharacteristic found;
    if (juno_pico_ble_service != nullptr) {
      found.remote = juno_pico_ble_service->characteristic(BLEUUID(String(uuid)));
    }
    return found;
  }

  void disconnect() {
    if (juno_pico_ble_client != nullptr) juno_pico_ble_client->disconnect();
    juno_pico_ble_service = nullptr;
    juno_pico_ble_notifying = nullptr;
  }

  bool connected() const {
    return juno_pico_ble_client != nullptr && juno_pico_ble_client->connected();
  }
};

static BLEScanReport* juno_pico_ble_scan_results = nullptr;
static BLEScanReport::iterator juno_pico_ble_scan_next;

class JunoPicoBle {
 public:
  bool begin() {
    static bool started = false;
    if (!started) {
      BLE.begin("juno");
      started = true;
    }
    return true;
  }

  void poll() {
    if (juno_pico_ble_handler == nullptr || juno_pico_ble_queue_count == 0 || juno_pico_ble_notifying == nullptr) return;
    memcpy(juno_pico_ble_current, juno_pico_ble_queue[juno_pico_ble_queue_head],
           juno_pico_ble_queue_lengths[juno_pico_ble_queue_head]);
    juno_pico_ble_current_length = juno_pico_ble_queue_lengths[juno_pico_ble_queue_head];
    juno_pico_ble_queue_head = (juno_pico_ble_queue_head + 1) % JUNO_PICO_BLE_QUEUE;
    juno_pico_ble_queue_count--;
    JunoPicoBleDevice device;
    device.valid = true;
    JunoPicoBleCharacteristic characteristic;
    characteristic.remote = juno_pico_ble_notifying;
    juno_pico_ble_handler(device, characteristic);
  }

  void scanForUuid(const char* uuid) {
    BLE.clearScan();
    juno_pico_ble_scan_results = BLE.scan(BLEUUID(String(uuid)), 1);
    juno_pico_ble_scan_next = juno_pico_ble_scan_results->begin();
  }

  JunoPicoBleDevice available() {
    JunoPicoBleDevice device;
    if (juno_pico_ble_scan_results == nullptr || juno_pico_ble_scan_next == juno_pico_ble_scan_results->end()) return device;
    BLEAdvertising& advertisement = *juno_pico_ble_scan_next++;
    device.valid = true;
    device.address = advertisement.getAddress();
    const uint8_t* manufacturer = nullptr;
    int length = advertisement.getManufacturerData(&manufacturer);
    device.manufacturerLength = length < JUNO_PICO_BLE_BYTES ? length : JUNO_PICO_BLE_BYTES;
    if (manufacturer != nullptr && device.manufacturerLength > 0) {
      memcpy(device.manufacturer, manufacturer, device.manufacturerLength);
    }
    return device;
  }

  void stopScan() {
  }
};

static JunoPicoBle juno_pico_ble;

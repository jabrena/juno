// A BLE central with the calls the LEGO runtime makes on ArduinoBLE, over the core's BLE library. Scan results and
// notifications arrive on the BLE task: they are stored here, and the program's handler runs from poll(), on the
// program's own task.
enum JunoBleEvent { BLEUpdated = 1 };

class JunoBLEDevice;
class JunoBLECharacteristic;
typedef void (*JunoBleHandler)(JunoBLEDevice device, JunoBLECharacteristic characteristic);

static const int JUNO_BLE_SCAN_SECONDS = 30;
static const int JUNO_BLE_BYTES = 32;
static const int JUNO_BLE_QUEUE = 8;

static portMUX_TYPE juno_ble_mux = portMUX_INITIALIZER_UNLOCKED;
static bool juno_ble_begun = false;
static std::string juno_ble_scan_uuid;

// The latest advertisement that matched the scan's service UUID, handed out once by available().
static bool juno_ble_found = false;
static uint8_t juno_ble_found_address[6];
static esp_ble_addr_type_t juno_ble_found_type = BLE_ADDR_TYPE_PUBLIC;
static uint8_t juno_ble_found_manufacturer[JUNO_BLE_BYTES];
static int32_t juno_ble_found_manufacturer_length = 0;

// One hub at a time: its client, the service the program discovered on it, and the characteristic that notifies.
static BLEClient* juno_ble_client = nullptr;
static BLERemoteService* juno_ble_service = nullptr;
static BLERemoteCharacteristic* juno_ble_notifying = nullptr;
static JunoBleHandler juno_ble_handler = nullptr;

// Notifications waiting for poll(), oldest first; a full queue drops the newest one.
static uint8_t juno_ble_queue[JUNO_BLE_QUEUE][JUNO_BLE_BYTES];
static int32_t juno_ble_queue_length[JUNO_BLE_QUEUE];
static int32_t juno_ble_queue_head = 0;
static int32_t juno_ble_queue_count = 0;
// The notification the handler is reading.
static uint8_t juno_ble_value[JUNO_BLE_BYTES];
static int32_t juno_ble_value_length = 0;

static void juno_ble_on_notify(BLERemoteCharacteristic* characteristic, uint8_t* data, size_t length, bool isNotify) {
  static_cast<void>(characteristic);
  static_cast<void>(isNotify);
  portENTER_CRITICAL(&juno_ble_mux);
  if (juno_ble_queue_count < JUNO_BLE_QUEUE) {
    int32_t slot = (juno_ble_queue_head + juno_ble_queue_count) % JUNO_BLE_QUEUE;
    int32_t stored = length < JUNO_BLE_BYTES ? static_cast<int32_t>(length) : JUNO_BLE_BYTES;
    memcpy(juno_ble_queue[slot], data, stored);
    juno_ble_queue_length[slot] = stored;
    juno_ble_queue_count++;
  }
  portEXIT_CRITICAL(&juno_ble_mux);
}

class JunoBLECharacteristic {
 public:
  BLERemoteCharacteristic* remote = nullptr;

  explicit operator bool() const {
    return remote != nullptr;
  }

  bool subscribe() {
    if (remote == nullptr) return false;
    juno_ble_notifying = remote;
    remote->registerForNotify(juno_ble_on_notify);
    return true;
  }

  const uint8_t* value() const {
    return juno_ble_value;
  }

  int valueLength() const {
    return juno_ble_value_length;
  }

  void writeValue(const uint8_t* data, int length, bool withoutResponse) {
    if (remote == nullptr) return;
    remote->writeValue(const_cast<uint8_t*>(data), static_cast<size_t>(length), !withoutResponse);
  }

  void setEventHandler(int event, JunoBleHandler handler) {
    if (event == BLEUpdated) juno_ble_handler = handler;
  }
};

class JunoBLEDevice {
 public:
  bool valid = false;
  uint8_t address[6] = {0, 0, 0, 0, 0, 0};
  esp_ble_addr_type_t addressType = BLE_ADDR_TYPE_PUBLIC;
  uint8_t manufacturer[JUNO_BLE_BYTES] = {};
  int32_t manufacturerLength = 0;

  explicit operator bool() const {
    return valid;
  }

  int manufacturerDataLength() const {
    return manufacturerLength;
  }

  int manufacturerData(uint8_t* buffer, int length) const {
    int32_t copied = manufacturerLength < length ? manufacturerLength : length;
    for (int32_t i = 0; i < copied; i++) {
      buffer[i] = manufacturer[i];
    }
    return copied;
  }

  bool connect() {
    if (!valid) return false;
    if (juno_ble_client == nullptr) juno_ble_client = BLEDevice::createClient();
    return juno_ble_client->connect(BLEAddress(address), addressType);
  }

  bool discoverService(const char* uuid) {
    if (juno_ble_client == nullptr || !juno_ble_client->isConnected()) return false;
    juno_ble_service = juno_ble_client->getService(uuid);
    return juno_ble_service != nullptr;
  }

  JunoBLECharacteristic characteristic(const char* uuid) {
    JunoBLECharacteristic found;
    if (juno_ble_service != nullptr) {
      found.remote = juno_ble_service->getCharacteristic(uuid);
    }
    return found;
  }

  void disconnect() {
    if (juno_ble_client != nullptr) juno_ble_client->disconnect();
    juno_ble_service = nullptr;
    juno_ble_notifying = nullptr;
  }

  bool connected() const {
    return juno_ble_client != nullptr && juno_ble_client->isConnected();
  }
};

class JunoBleScanCallbacks : public BLEAdvertisedDeviceCallbacks {
 public:
  void onResult(BLEAdvertisedDevice advertised) override {
    if (!advertised.isAdvertisingService(BLEUUID(juno_ble_scan_uuid))) return;
    std::string manufacturer = advertised.haveManufacturerData() ? advertised.getManufacturerData() : std::string();
    portENTER_CRITICAL(&juno_ble_mux);
    memcpy(juno_ble_found_address, *advertised.getAddress().getNative(), 6);
    juno_ble_found_type = advertised.getAddressType();
    int32_t stored = manufacturer.size() < JUNO_BLE_BYTES ? static_cast<int32_t>(manufacturer.size()) : JUNO_BLE_BYTES;
    memcpy(juno_ble_found_manufacturer, manufacturer.data(), stored);
    juno_ble_found_manufacturer_length = stored;
    juno_ble_found = true;
    portEXIT_CRITICAL(&juno_ble_mux);
  }
};

static JunoBleScanCallbacks juno_ble_scan_callbacks;

class JunoBLEBle {
 public:
  bool begin() {
    if (!juno_ble_begun) {
      BLEDevice::init("juno");
      juno_ble_begun = true;
    }
    return true;
  }

  // Hands the queued notifications to the handler, on the calling task.
  void poll() {
    while (true) {
      bool queued = false;
      portENTER_CRITICAL(&juno_ble_mux);
      if (juno_ble_queue_count > 0) {
        int32_t length = juno_ble_queue_length[juno_ble_queue_head];
        memcpy(juno_ble_value, juno_ble_queue[juno_ble_queue_head], length);
        juno_ble_value_length = length;
        juno_ble_queue_head = (juno_ble_queue_head + 1) % JUNO_BLE_QUEUE;
        juno_ble_queue_count--;
        queued = true;
      }
      portEXIT_CRITICAL(&juno_ble_mux);
      if (!queued) return;
      if (juno_ble_handler != nullptr && juno_ble_notifying != nullptr) {
        JunoBLEDevice hub;
        hub.valid = true;
        JunoBLECharacteristic characteristic;
        characteristic.remote = juno_ble_notifying;
        juno_ble_handler(hub, characteristic);
      }
    }
  }

  void scanForUuid(const char* uuid) {
    juno_ble_scan_uuid = uuid;
    portENTER_CRITICAL(&juno_ble_mux);
    juno_ble_found = false;
    portEXIT_CRITICAL(&juno_ble_mux);
    BLEScan* scan = BLEDevice::getScan();
    scan->setActiveScan(true);
    scan->setAdvertisedDeviceCallbacks(&juno_ble_scan_callbacks, false);
    scan->start(JUNO_BLE_SCAN_SECONDS, nullptr, false);
  }

  // The advertisement found since the last call, or an invalid device when there is none.
  JunoBLEDevice available() {
    JunoBLEDevice device;
    portENTER_CRITICAL(&juno_ble_mux);
    if (juno_ble_found) {
      device.valid = true;
      memcpy(device.address, juno_ble_found_address, 6);
      device.addressType = juno_ble_found_type;
      memcpy(device.manufacturer, juno_ble_found_manufacturer, juno_ble_found_manufacturer_length);
      device.manufacturerLength = juno_ble_found_manufacturer_length;
      juno_ble_found = false;
    }
    portEXIT_CRITICAL(&juno_ble_mux);
    return device;
  }

  void stopScan() {
    BLEDevice::getScan()->stop();
  }
};

static JunoBLEBle juno_ble;

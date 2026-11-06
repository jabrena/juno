
// The board's BLE central behind BluetoothLe: scan for the first device advertising a service, connect, subscribe to
// one characteristic, then write messages to it and read its notifications back. It knows no protocol; the LEGO Wireless
// Protocol is framed and decoded in Java (LegoWirelessPort). The BLEDevice, BLECharacteristic and BLE names are the
// core's (BleNames).

// Notifications wait in a small queue until the program reads them; a notification past the queue's end is dropped.
static const int JUNO_BLE_MESSAGE_BYTES = 32;
static const int JUNO_BLE_QUEUE_SLOTS = 16;
static uint8_t juno_ble_queue[JUNO_BLE_QUEUE_SLOTS][JUNO_BLE_MESSAGE_BYTES];
static int32_t juno_ble_queue_length[JUNO_BLE_QUEUE_SLOTS];
static int32_t juno_ble_queue_head = 0;
static int32_t juno_ble_queue_count = 0;

// The service and characteristic UUIDs of the connected device, copied so a program's string need not outlive a call.
static const int JUNO_BLE_UUID_BYTES = 40;
static char juno_ble_characteristic[JUNO_BLE_UUID_BYTES];
// BLECharacteristic is reference counted but has no copy assignment, so it is never kept in a static: each write
// looks it up again on the connected device, which is a plain-copy BLEDevice.
static BLEDevice juno_ble_device;
static bool juno_ble_started = false;

// ArduinoBLE is polled only once BLE.begin() has succeeded: on the UNO R4 WiFi a poll goes through the ESP32 modem,
// and polling it before begin stalled the first call for about 10 s.
static void juno_ble_poll() {
  if (juno_ble_started) {
    BLE.poll();
  }
}

static void juno_ble_copy_uuid(char* destination, const char* source) {
  int32_t i = 0;
  while (source[i] != '\0' && i < JUNO_BLE_UUID_BYTES - 1) {
    destination[i] = source[i];
    i++;
  }
  destination[i] = '\0';
}

static void juno_ble_on_notification(BLEDevice device, BLECharacteristic characteristic) {
  static_cast<void>(device);
  int32_t length = characteristic.valueLength();
  if (length <= 0 || juno_ble_queue_count == JUNO_BLE_QUEUE_SLOTS) {
    return;
  }
  int32_t slot = (juno_ble_queue_head + juno_ble_queue_count) % JUNO_BLE_QUEUE_SLOTS;
  int32_t kept = length < JUNO_BLE_MESSAGE_BYTES ? length : JUNO_BLE_MESSAGE_BYTES;
  const uint8_t* value = characteristic.value();
  for (int32_t i = 0; i < kept; i++) {
    juno_ble_queue[slot][i] = value[i];
  }
  juno_ble_queue_length[slot] = kept;
  juno_ble_queue_count++;
}

// The wait loops below call juno_ble_wait() so that, in a program with tasks, another task can run while this one
// waits on the radio; without tasks it does nothing. Every entry takes the BLE device guard (IoRuntime) so a second
// task cannot re-enter the BLE calls while another is inside one (runtime code is never preempted, so a task only
// switches inside one at a wait; this never blocks a single-task program).
${JUNO_BLE_WAIT}
// The BLE device guard: owner-tracked, so a cancelled subtask does not leave the device locked.
struct JunoBleGuard : JunoIoGuard {
  JunoBleGuard() : JunoIoGuard(JUNO_IO_BLE) {
  }
};

static bool juno_ble_connected() {
  juno_ble_poll();
  return juno_ble_device && juno_ble_device.connected();
}

extern "C" int32_t juno_ble_connect(const char* serviceUuid, const char* characteristicUuid, int32_t timeoutMillis,
    uint8_t* advertisement, int32_t capacity) {
  JunoBleGuard juno_ble_guard;
  if (!juno_ble_started) {
    if (!BLE.begin()) {
      return -1;
    }
    juno_ble_started = true;
  }
  // A new connection starts with no notifications from the last one.
  juno_ble_queue_count = 0;
  juno_ble_queue_head = 0;
  juno_ble_copy_uuid(juno_ble_characteristic, characteristicUuid);
  BLE.scanForUuid(serviceUuid);
  uint32_t started = millis();
  while (timeoutMillis <= 0 || millis() - started < static_cast<uint32_t>(timeoutMillis)) {
    BLEDevice candidate = BLE.available();
    if (!candidate) {
      juno_ble_wait();
      continue;
    }
    BLE.stopScan();
    // The advertised manufacturer data, copied up to the caller's capacity; its length is the result.
    int32_t advertised = 0;
    if (advertisement != nullptr && capacity > 0) {
      int32_t wanted = candidate.manufacturerDataLength() < capacity ? candidate.manufacturerDataLength() : capacity;
      if (wanted > 0) {
        advertised = candidate.manufacturerData(advertisement, wanted);
      }
    }
    if (candidate.connect()) {
      if (candidate.discoverService(serviceUuid)) {
        // Copy-initialized, never assigned: see juno_ble_device above.
        BLECharacteristic characteristic = candidate.characteristic(juno_ble_characteristic);
        if (characteristic) {
          characteristic.setEventHandler(BLEUpdated, juno_ble_on_notification);
          characteristic.subscribe();
          juno_ble_device = candidate;
          return advertised;
        }
      }
      candidate.disconnect();
    }
    BLE.scanForUuid(serviceUuid);
  }
  BLE.stopScan();
  return -1;
}

extern "C" int32_t juno_ble_is_connected() {
  JunoBleGuard juno_ble_guard;
  return juno_ble_connected() ? 1 : 0;
}

extern "C" void juno_ble_write(uint8_t* message, int32_t length) {
  JunoBleGuard juno_ble_guard;
  if (message == nullptr || length <= 0 || !juno_ble_connected()) {
    return;
  }
  BLECharacteristic characteristic = juno_ble_device.characteristic(juno_ble_characteristic);
  if (!characteristic) {
    return;
  }
  characteristic.writeValue(message, length, false);
}

// The next queued notification, copied up to the caller's capacity; 0 when none is waiting (the wait yields).
extern "C" int32_t juno_ble_read(uint8_t* buffer, int32_t capacity) {
  JunoBleGuard juno_ble_guard;
  juno_ble_poll();
  if (juno_ble_queue_count == 0) {
    juno_ble_wait();
    return 0;
  }
  int32_t length = juno_ble_queue_length[juno_ble_queue_head];
  int32_t count = length < capacity ? length : capacity;
  if (buffer != nullptr) {
    for (int32_t i = 0; i < count; i++) {
      buffer[i] = juno_ble_queue[juno_ble_queue_head][i];
    }
  }
  juno_ble_queue_head = (juno_ble_queue_head + 1) % JUNO_BLE_QUEUE_SLOTS;
  juno_ble_queue_count--;
  return count;
}

extern "C" void juno_ble_disconnect() {
  JunoBleGuard juno_ble_guard;
  if (juno_ble_device) {
    juno_ble_device.disconnect();
  }
}

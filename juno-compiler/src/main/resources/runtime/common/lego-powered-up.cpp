
static const char juno_lego_service_uuid[] = "00001623-1212-efde-1623-785feabcd123";
static const char juno_lego_characteristic_uuid[] = "00001624-1212-efde-1623-785feabcd123";
// BLECharacteristic is reference counted but has no copy assignment, so it is never kept in a
// static: each write looks it up again on the connected hub, which is a plain-copy BLEDevice.
static BLEDevice juno_lego_hub;
static bool juno_lego_ble_started = false;
// ArduinoBLE is polled only once BLE.begin() has succeeded: on the UNO R4 WiFi a poll goes through the
// ESP32 modem, and polling it before begin stalled the first call for about 10 s.
static void juno_lego_poll() {
  if (juno_lego_ble_started) {
    BLE.poll();
  }
}
// The status LED's current input mode: -1 not set up, 0 color index, 1 RGB.
static int juno_lego_led_mode = -1;
static int32_t juno_lego_hub_type = 0;
// The I/O device type (Hub Attached I/O event 0x01) of each external port A..D; 0 means nothing attached.
static int32_t juno_lego_port_devices[4] = {0, 0, 0, 0};

// Latest single-value Port Value (0x45) reading per enabled port; a port of 0xFF marks a free slot.
static const int JUNO_LEGO_SENSOR_SLOTS = 8;
static uint8_t juno_lego_sensor_ports[JUNO_LEGO_SENSOR_SLOTS];
static int32_t juno_lego_sensor_values[JUNO_LEGO_SENSOR_SLOTS];
// The value bytes of the latest report, for modes that report several values.
static const int JUNO_LEGO_RAW_BYTES = 16;
static uint8_t juno_lego_sensor_raw[JUNO_LEGO_SENSOR_SLOTS][JUNO_LEGO_RAW_BYTES];
static int32_t juno_lego_sensor_raw_size[JUNO_LEGO_SENSOR_SLOTS];
// How many reports each port has delivered since it was enabled.
static int32_t juno_lego_sensor_reports[JUNO_LEGO_SENSOR_SLOTS];

// Hub Properties (0x01) the hub reported; -1 or 0 until the hub sends them. They are requested on
// the first property call after connecting, so programs that never ask add no BLE traffic.
static int32_t juno_lego_battery = -1;
static int32_t juno_lego_button = 0;
static int32_t juno_lego_rssi = 0;
static int32_t juno_lego_firmware = -1;
static int32_t juno_lego_hardware = -1;
static uint8_t juno_lego_name[20];
static int32_t juno_lego_name_length = 0;
static bool juno_lego_properties_requested = false;

// Virtual ports the hub created by pairing two motors (Hub Attached I/O event 0x02); an id of 0xFF is free.
static const int JUNO_LEGO_LINK_SLOTS = 4;
static uint8_t juno_lego_link_ids[JUNO_LEGO_LINK_SLOTS] = {0xFF, 0xFF, 0xFF, 0xFF};
static uint8_t juno_lego_link_a[JUNO_LEGO_LINK_SLOTS];
static uint8_t juno_lego_link_b[JUNO_LEGO_LINK_SLOTS];

static void juno_lego_clear_links() {
  for (int i = 0; i < JUNO_LEGO_LINK_SLOTS; i++) {
    juno_lego_link_ids[i] = 0xFF;
  }
}

static int32_t juno_lego_find_link(uint8_t a, uint8_t b) {
  for (int i = 0; i < JUNO_LEGO_LINK_SLOTS; i++) {
    if (juno_lego_link_ids[i] != 0xFF && ((juno_lego_link_a[i] == a && juno_lego_link_b[i] == b)
        || (juno_lego_link_a[i] == b && juno_lego_link_b[i] == a))) {
      return juno_lego_link_ids[i];
    }
  }
  return -1;
}

static void juno_lego_forget_link(uint8_t id) {
  for (int i = 0; i < JUNO_LEGO_LINK_SLOTS; i++) {
    if (juno_lego_link_ids[i] == id) {
      juno_lego_link_ids[i] = 0xFF;
    }
  }
}

// Hub Attached I/O (0x04): [length, hub id, 0x04, port, event, ...]. Event 0x02 announces a virtual
// port: device type (2 bytes), then the two ports it pairs; event 0x00 removes a port.
static void juno_lego_receive_attached(const uint8_t* message, int length, int header) {
  uint8_t port = message[header + 2];
  uint8_t event = message[header + 3];
  if (port < 4) {
    juno_lego_port_devices[port] = event == 0x01 && length >= header + 6
        ? (message[header + 4] | (message[header + 5] << 8)) : 0;
  }
  if (event == 0x00) {
    juno_lego_forget_link(port);
  } else if (event == 0x02 && length >= header + 8) {
    juno_lego_forget_link(port);
    for (int i = 0; i < JUNO_LEGO_LINK_SLOTS; i++) {
      if (juno_lego_link_ids[i] == 0xFF) {
        juno_lego_link_ids[i] = port;
        juno_lego_link_a[i] = message[header + 6];
        juno_lego_link_b[i] = message[header + 7];
        break;
      }
    }
  }
}

static void juno_lego_clear_properties() {
  juno_lego_battery = -1;
  juno_lego_button = 0;
  juno_lego_rssi = 0;
  juno_lego_firmware = -1;
  juno_lego_hardware = -1;
  juno_lego_name_length = 0;
  juno_lego_properties_requested = false;
}

static void juno_lego_clear_sensors() {
  for (int i = 0; i < JUNO_LEGO_SENSOR_SLOTS; i++) {
    juno_lego_sensor_ports[i] = 0xFF;
    juno_lego_sensor_values[i] = 0;
    juno_lego_sensor_raw_size[i] = 0;
    juno_lego_sensor_reports[i] = 0;
  }
}

static int juno_lego_sensor_slot(uint8_t port) {
  for (int i = 0; i < JUNO_LEGO_SENSOR_SLOTS; i++) {
    if (juno_lego_sensor_ports[i] == port) {
      return i;
    }
  }
  return -1;
}

// Decodes one upstream message: [length (1 or 2 bytes), hub id, type, port, value...].
// Only a single 8/16/32-bit little-endian value is decoded; other sizes keep the first byte.
static int32_t juno_lego_le32(const uint8_t* data) {
  return static_cast<int32_t>(static_cast<uint32_t>(data[0]) | (static_cast<uint32_t>(data[1]) << 8)
      | (static_cast<uint32_t>(data[2]) << 16) | (static_cast<uint32_t>(data[3]) << 24));
}

// Hub Properties update (0x01, operation 0x06): [length, hub id, 0x01, property, 0x06, value...].
static void juno_lego_receive_property(const uint8_t* data, int size, uint8_t property) {
  switch (property) {
    case 0x01:
      juno_lego_name_length = size < static_cast<int>(sizeof(juno_lego_name)) ? size
          : static_cast<int>(sizeof(juno_lego_name));
      for (int i = 0; i < juno_lego_name_length; i++) {
        juno_lego_name[i] = data[i];
      }
      break;
    case 0x02: if (size >= 1) juno_lego_button = data[0] != 0 ? 1 : 0; break;
    case 0x03: if (size >= 4) juno_lego_firmware = juno_lego_le32(data); break;
    case 0x04: if (size >= 4) juno_lego_hardware = juno_lego_le32(data); break;
    case 0x05: if (size >= 1) juno_lego_rssi = static_cast<int8_t>(data[0]); break;
    case 0x06: if (size >= 1) juno_lego_battery = data[0]; break;
    default: break;
  }
}

static void juno_lego_receive(const uint8_t* message, int length) {
  int header = (length > 0 && (message[0] & 0x80) != 0) ? 2 : 1;
  if (length >= header + 4 && message[header + 1] == 0x04) {
    juno_lego_receive_attached(message, length, header);
    return;
  }
  if (length >= header + 4 && message[header + 1] == 0x01 && message[header + 3] == 0x06) {
    juno_lego_receive_property(message + header + 4, length - header - 4, message[header + 2]);
    return;
  }
  if (length < header + 4 || message[header + 1] != 0x45) {
    return;
  }
  int slot = juno_lego_sensor_slot(message[header + 2]);
  if (slot < 0) {
    return;
  }
  const uint8_t* value = message + header + 3;
  int size = length - header - 3;
  int kept = size < JUNO_LEGO_RAW_BYTES ? size : JUNO_LEGO_RAW_BYTES;
  for (int i = 0; i < kept; i++) {
    juno_lego_sensor_raw[slot][i] = value[i];
  }
  juno_lego_sensor_raw_size[slot] = kept;
  juno_lego_sensor_reports[slot]++;
  if (size == 4) {
    juno_lego_sensor_values[slot] = static_cast<int32_t>(static_cast<uint32_t>(value[0])
        | (static_cast<uint32_t>(value[1]) << 8) | (static_cast<uint32_t>(value[2]) << 16)
        | (static_cast<uint32_t>(value[3]) << 24));
  } else if (size == 2) {
    juno_lego_sensor_values[slot] = static_cast<int16_t>(value[0] | (value[1] << 8));
  } else {
    juno_lego_sensor_values[slot] = static_cast<int8_t>(value[0]);
  }
}

static void juno_lego_on_notification(BLEDevice device, BLECharacteristic characteristic) {
  static_cast<void>(device);
  juno_lego_receive(characteristic.value(), characteristic.valueLength());
}

// The wait loops below call juno_lego_wait() so that, in a program with tasks, another task can run while
// this one waits on the radio; without tasks it does nothing. juno_lego_hub_* entries take the BLE device
// guard (IoRuntime) so a second task cannot re-enter the BLE calls while another is inside one (runtime
// code is never preempted, so a task only switches inside one at a wait; this never blocks a single-task
// program).
${JUNO_LEGO_WAIT}
// The BLE device guard: owner-tracked, so a cancelled subtask does not leave the hub locked.
struct JunoLegoGuard : JunoIoGuard {
  JunoLegoGuard() : JunoIoGuard(JUNO_IO_BLE) {
  }
};

static bool juno_lego_connected() {
  juno_lego_poll();
  return juno_lego_hub && juno_lego_hub.connected();
}

static void juno_lego_send(uint8_t type, const uint8_t* payload, int length) {
  if (!juno_lego_connected()) {
    return;
  }
  BLECharacteristic characteristic = juno_lego_hub.characteristic(juno_lego_characteristic_uuid);
  if (!characteristic) {
    return;
  }
  uint8_t message[16];
  message[0] = static_cast<uint8_t>(length + 3);
  message[1] = 0x00;
  message[2] = type;
  for (int i = 0; i < length; i++) {
    message[i + 3] = payload[i];
  }
  characteristic.writeValue(message, length + 3, false);
}

// Port output command (0x81), executed immediately with feedback (0x11), as a
// WriteDirectModeData (0x51) of one signed byte in mode 0: POWER for motors, COLOR for the LED.
static void juno_lego_write_mode0(int32_t port, int32_t value) {
  const uint8_t payload[] = {static_cast<uint8_t>(port), 0x11, 0x51, 0x00, static_cast<uint8_t>(value)};
  juno_lego_send(0x81, payload, sizeof(payload));
}

static uint8_t juno_lego_led_port() {
  switch (juno_lego_hub_type) {
    case 0x20: return 0x11;
    case 0x42: return 0x34;
    default: return 0x32;
  }
}

extern "C" int32_t juno_lego_hub_connect(int32_t timeoutMillis) {
  JunoLegoGuard juno_lego_guard;
  if (juno_lego_connected()) {
    return 1;
  }
  if (!juno_lego_ble_started) {
    if (!BLE.begin()) {
      return 0;
    }
    juno_lego_ble_started = true;
  }
  // A new connection starts with no ports reporting values.
  juno_lego_clear_sensors();
  juno_lego_clear_properties();
  juno_lego_clear_links();
  for (int i = 0; i < 4; i++) {
    juno_lego_port_devices[i] = 0;
  }
  juno_lego_led_mode = -1;
  BLE.scanForUuid(juno_lego_service_uuid);
  uint32_t started = millis();
  while (timeoutMillis <= 0 || millis() - started < static_cast<uint32_t>(timeoutMillis)) {
    BLEDevice candidate = BLE.available();
    if (!candidate) {
      juno_lego_wait();
      continue;
    }
    BLE.stopScan();
    // Manufacturer data: LEGO company id 0x0397 (little endian), button state, system type id.
    uint8_t advertised[4] = {0, 0, 0, 0};
    int32_t type = 0;
    if (candidate.manufacturerDataLength() >= 4 && candidate.manufacturerData(advertised, 4) == 4
        && advertised[0] == 0x97 && advertised[1] == 0x03) {
      type = advertised[3];
    }
    if (candidate.connect()) {
      if (candidate.discoverService(juno_lego_service_uuid)) {
        // Copy-initialized, never assigned: see juno_lego_hub above.
        BLECharacteristic characteristic = candidate.characteristic(juno_lego_characteristic_uuid);
        if (characteristic) {
          characteristic.setEventHandler(BLEUpdated, juno_lego_on_notification);
          characteristic.subscribe();
          juno_lego_hub = candidate;
          juno_lego_hub_type = type;
          return 1;
        }
      }
      candidate.disconnect();
    }
    BLE.scanForUuid(juno_lego_service_uuid);
  }
  BLE.stopScan();
  return 0;
}

extern "C" int32_t juno_lego_hub_is_connected() {
  JunoLegoGuard juno_lego_guard;
  return juno_lego_connected() ? 1 : 0;
}

extern "C" int32_t juno_lego_hub_type_id() {
  JunoLegoGuard juno_lego_guard;
  return juno_lego_hub_type;
}

extern "C" void juno_lego_hub_set_motor_power(int32_t port, int32_t powerPercent) {
  JunoLegoGuard juno_lego_guard;
  if (powerPercent > 100) {
    powerPercent = 100;
  } else if (powerPercent < -100) {
    powerPercent = -100;
  }
  juno_lego_write_mode0(port, powerPercent);
}

extern "C" void juno_lego_hub_brake_motor(int32_t port) {
  JunoLegoGuard juno_lego_guard;
  juno_lego_write_mode0(port, 127);
}

// Port output command (0x81) StartSpeed (subcommand 0x07) with a speed of 0: the motor's speed control
// keeps it where it is, so it resists being turned, unlike the brake, which only slows it.
extern "C" void juno_lego_hub_hold_motor(int32_t port) {
  JunoLegoGuard juno_lego_guard;
  const uint8_t payload[] = {static_cast<uint8_t>(port), 0x11, 0x07, 0x00, 100, 0x00};
  juno_lego_send(0x81, payload, sizeof(payload));
}

// Port input format setup (0x41): puts the LED in the given mode (0 color index, 1 RGB), delta 1,
// notifications off.
static void juno_lego_led_use_mode(uint8_t port, int mode) {
  if (juno_lego_led_mode != mode) {
    const uint8_t setup[] = {port, static_cast<uint8_t>(mode), 0x01, 0x00, 0x00, 0x00, 0x00};
    juno_lego_send(0x41, setup, sizeof(setup));
    juno_lego_led_mode = juno_lego_connected() ? mode : -1;
  }
}

extern "C" void juno_lego_hub_set_led_color(int32_t color) {
  JunoLegoGuard juno_lego_guard;
  uint8_t port = juno_lego_led_port();
  juno_lego_led_use_mode(port, 0);
  juno_lego_write_mode0(port, color);
}

extern "C" void juno_lego_hub_set_led_rgb(int32_t red, int32_t green, int32_t blue) {
  JunoLegoGuard juno_lego_guard;
  uint8_t port = juno_lego_led_port();
  juno_lego_led_use_mode(port, 1);
  // WriteDirectModeData (0x51) of three bytes in mode 1: red, green, blue.
  const uint8_t payload[] = {port, 0x11, 0x51, 0x01, static_cast<uint8_t>(red & 0xFF),
      static_cast<uint8_t>(green & 0xFF), static_cast<uint8_t>(blue & 0xFF)};
  juno_lego_send(0x81, payload, sizeof(payload));
}

extern "C" void juno_lego_hub_enable_sensor(int32_t port, int32_t mode) {
  JunoLegoGuard juno_lego_guard;
  uint8_t portId = static_cast<uint8_t>(port);
  int slot = juno_lego_sensor_slot(portId);
  if (slot < 0) {
    slot = juno_lego_sensor_slot(0xFF);
  }
  if (slot < 0 || !juno_lego_connected()) {
    return;
  }
  juno_lego_sensor_ports[slot] = portId;
  juno_lego_sensor_values[slot] = 0;
  juno_lego_sensor_raw_size[slot] = 0;
  juno_lego_sensor_reports[slot] = 0;
  // Port input format setup (0x41): report every change (delta 1) of this mode, notifications on.
  const uint8_t setup[] = {portId, static_cast<uint8_t>(mode), 0x01, 0x00, 0x00, 0x00, 0x01};
  juno_lego_send(0x41, setup, sizeof(setup));
}

extern "C" int32_t juno_lego_hub_read_sensor(int32_t port) {
  JunoLegoGuard juno_lego_guard;
  juno_lego_poll();
  int slot = juno_lego_sensor_slot(static_cast<uint8_t>(port));
  return slot < 0 ? 0 : juno_lego_sensor_values[slot];
}

// Hub Properties message (0x01): [property, operation]. Operation 0x02 subscribes to updates,
// 0x05 asks for one update.
static void juno_lego_property_request(uint8_t property, uint8_t operation) {
  const uint8_t payload[] = {property, operation};
  juno_lego_send(0x01, payload, sizeof(payload));
}

// Asks the hub for its properties the first time one is needed, then gives the answers up to
// 300 ms to arrive; later calls just return what the notifications have updated.
static void juno_lego_ensure_properties() {
  if (juno_lego_properties_requested || !juno_lego_connected()) {
    return;
  }
  juno_lego_properties_requested = true;
  juno_lego_property_request(0x02, 0x02);
  juno_lego_property_request(0x05, 0x02);
  juno_lego_property_request(0x06, 0x02);
  juno_lego_property_request(0x01, 0x05);
  juno_lego_property_request(0x03, 0x05);
  juno_lego_property_request(0x04, 0x05);
  uint32_t started = millis();
  while (millis() - started < 300 && (juno_lego_battery < 0 || juno_lego_firmware < 0
      || juno_lego_hardware < 0 || juno_lego_name_length == 0)) {
    juno_lego_poll();
    juno_lego_wait();
  }
}

extern "C" int32_t juno_lego_hub_battery_percent() {
  JunoLegoGuard juno_lego_guard;
  juno_lego_ensure_properties();
  juno_lego_poll();
  return juno_lego_battery;
}

extern "C" int32_t juno_lego_hub_button_pressed() {
  JunoLegoGuard juno_lego_guard;
  juno_lego_ensure_properties();
  juno_lego_poll();
  return juno_lego_button;
}

extern "C" int32_t juno_lego_hub_rssi() {
  JunoLegoGuard juno_lego_guard;
  juno_lego_ensure_properties();
  juno_lego_poll();
  return juno_lego_rssi;
}

extern "C" int32_t juno_lego_hub_firmware_version() {
  JunoLegoGuard juno_lego_guard;
  juno_lego_ensure_properties();
  juno_lego_poll();
  return juno_lego_firmware;
}

extern "C" int32_t juno_lego_hub_hardware_version() {
  JunoLegoGuard juno_lego_guard;
  juno_lego_ensure_properties();
  juno_lego_poll();
  return juno_lego_hardware;
}

extern "C" int32_t juno_lego_hub_port_device(int32_t port) {
  JunoLegoGuard juno_lego_guard;
  juno_lego_poll();
  return port >= 0 && port < 4 ? juno_lego_port_devices[port] : 0;
}

extern "C" int32_t juno_lego_hub_name(uint8_t* buffer, int32_t capacity) {
  JunoLegoGuard juno_lego_guard;
  juno_lego_ensure_properties();
  juno_lego_poll();
  if (buffer == nullptr || capacity <= 0) {
    return 0;
  }
  int32_t count = juno_lego_name_length < capacity ? juno_lego_name_length : capacity;
  for (int32_t i = 0; i < count; i++) {
    buffer[i] = juno_lego_name[i];
  }
  return count;
}

// The index-th value of the latest report as a signed little-endian integer of width 1, 2 or 4 bytes;
// 0 when the report has none that wide.
extern "C" int32_t juno_lego_hub_read_sensor_value(int32_t port, int32_t index, int32_t width) {
  JunoLegoGuard juno_lego_guard;
  juno_lego_poll();
  int slot = juno_lego_sensor_slot(static_cast<uint8_t>(port));
  if (slot < 0 || index < 0 || (width != 1 && width != 2 && width != 4)
      || juno_lego_sensor_raw_size[slot] < (index + 1) * width) {
    return 0;
  }
  const uint8_t* value = juno_lego_sensor_raw[slot] + index * width;
  if (width == 4) {
    return juno_lego_le32(value);
  }
  if (width == 2) {
    return static_cast<int16_t>(value[0] | (value[1] << 8));
  }
  return static_cast<int8_t>(value[0]);
}

extern "C" int32_t juno_lego_hub_sensor_report_count(int32_t port) {
  JunoLegoGuard juno_lego_guard;
  juno_lego_poll();
  int slot = juno_lego_sensor_slot(static_cast<uint8_t>(port));
  return slot < 0 ? 0 : juno_lego_sensor_reports[slot];
}

extern "C" int32_t juno_lego_hub_sensor_report_size(int32_t port) {
  JunoLegoGuard juno_lego_guard;
  juno_lego_poll();
  int slot = juno_lego_sensor_slot(static_cast<uint8_t>(port));
  return slot < 0 ? 0 : juno_lego_sensor_raw_size[slot];
}

// Virtual Port Setup (0x61): connect (0x01) two motor ports into one virtual port, whose id the hub
// announces in a Hub Attached I/O event; waits up to 500 ms for it. Returns the id or -1.
extern "C" int32_t juno_lego_hub_link_motors(int32_t portA, int32_t portB) {
  JunoLegoGuard juno_lego_guard;
  int32_t existing = juno_lego_find_link(static_cast<uint8_t>(portA), static_cast<uint8_t>(portB));
  if (existing >= 0) {
    return existing;
  }
  if (!juno_lego_connected()) {
    return -1;
  }
  const uint8_t payload[] = {0x01, static_cast<uint8_t>(portA), static_cast<uint8_t>(portB)};
  juno_lego_send(0x61, payload, sizeof(payload));
  uint32_t started = millis();
  while (true) {
    juno_lego_poll();
    juno_lego_wait();
    int32_t id = juno_lego_find_link(static_cast<uint8_t>(portA), static_cast<uint8_t>(portB));
    if (id >= 0) {
      return id;
    }
    if (millis() - started >= 500) {
      return -1;
    }
  }
}

extern "C" void juno_lego_hub_unlink_motors(int32_t virtualPort) {
  JunoLegoGuard juno_lego_guard;
  const uint8_t payload[] = {0x00, static_cast<uint8_t>(virtualPort)};
  juno_lego_send(0x61, payload, sizeof(payload));
  juno_lego_forget_link(static_cast<uint8_t>(virtualPort));
}

// Port output command (0x81) StartPower with two powers (subcommand 0x02), executed immediately
// with feedback (0x11): one message drives both motors of a virtual port.
static void juno_lego_write_linked_power(int32_t virtualPort, int32_t first, int32_t second) {
  const uint8_t payload[] = {static_cast<uint8_t>(virtualPort), 0x11, 0x02, static_cast<uint8_t>(first),
      static_cast<uint8_t>(second)};
  juno_lego_send(0x81, payload, sizeof(payload));
}

static int32_t juno_lego_clamp_power(int32_t power) {
  return power > 100 ? 100 : (power < -100 ? -100 : power);
}

extern "C" void juno_lego_hub_set_linked_motor_power(int32_t virtualPort, int32_t first, int32_t second) {
  JunoLegoGuard juno_lego_guard;
  juno_lego_write_linked_power(virtualPort, juno_lego_clamp_power(first), juno_lego_clamp_power(second));
}

extern "C" void juno_lego_hub_brake_linked_motors(int32_t virtualPort) {
  JunoLegoGuard juno_lego_guard;
  juno_lego_write_linked_power(virtualPort, 127, 127);
}

extern "C" void juno_lego_hub_disconnect() {
  JunoLegoGuard juno_lego_guard;
  if (juno_lego_hub) {
    juno_lego_hub.disconnect();
  }
  juno_lego_led_mode = -1;
}

extern "C" void juno_lego_hub_switch_off() {
  JunoLegoGuard juno_lego_guard;
  // Hub action (0x02): switch off hub (0x01).
  const uint8_t payload[] = {0x01};
  juno_lego_send(0x02, payload, sizeof(payload));
  juno_lego_led_mode = -1;
}

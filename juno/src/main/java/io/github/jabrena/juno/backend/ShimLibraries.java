package io.github.jabrena.juno.backend;

import io.github.jabrena.juno.RuntimeLimits;
import io.github.jabrena.juno.linker.ThrowableTypes;

import java.util.List;

/**
 * Source text of the runtime shim's optional, self-contained C++ helper blocks for peripherals,
 * storage, strings, exceptions and 64-bit/floating-point arithmetic — each appended by
 * {@link RuntimeShim} only when the lowered program actually calls into it.
 */
final class ShimLibraries {
    private ShimLibraries() {
    }

    /** USB HID mouse bindings; {@code Mouse.h} is an optional library, included only on use. */
    static String mouseHelpers() {
        return """

                extern "C" void juno_mouse_begin() {
                  Mouse.begin();
                }

                extern "C" void juno_mouse_move(int32_t x, int32_t y) {
                  Mouse.move(static_cast<signed char>(x), static_cast<signed char>(y));
                }
                """;
    }

    /** One {@code Servo} per digital pin, attached on first use. */
    static String servoHelpers() {
        return """

                static Servo junoServos[NUM_DIGITAL_PINS];

                extern "C" void juno_servo_attach(int32_t pin) {
                  if (pin >= 0 && pin < NUM_DIGITAL_PINS) {
                    junoServos[pin].attach(pin);
                  }
                }

                extern "C" void juno_servo_write(int32_t pin, int32_t angleDegrees) {
                  if (pin >= 0 && pin < NUM_DIGITAL_PINS) {
                    junoServos[pin].write(angleDegrees);
                  }
                }
                """;
    }

    /** Register reads and writes on the primary {@code Wire} bus, with the core-bundled {@code Wire} library. */
    static String i2cHelpers() {
        return """

                extern "C" void juno_i2c_begin() {
                  Wire.begin();
                }

                extern "C" void juno_i2c_write_register(int32_t address, int32_t reg, int32_t value) {
                  Wire.beginTransmission(static_cast<uint8_t>(address));
                  Wire.write(static_cast<uint8_t>(reg));
                  Wire.write(static_cast<uint8_t>(value));
                  Wire.endTransmission();
                }

                // Reads count (1 or 2) bytes from reg, little endian; -1 if the device did not answer.
                static int32_t juno_i2c_read(int32_t address, int32_t reg, uint8_t count) {
                  Wire.beginTransmission(static_cast<uint8_t>(address));
                  Wire.write(static_cast<uint8_t>(reg));
                  if (Wire.endTransmission(false) != 0) {
                    return -1;
                  }
                  if (Wire.requestFrom(static_cast<uint8_t>(address), count) != count) {
                    return -1;
                  }
                  int32_t value = 0;
                  for (uint8_t i = 0; i < count; i++) {
                    value |= static_cast<int32_t>(Wire.read() & 0xFF) << (8 * i);
                  }
                  return value;
                }

                extern "C" int32_t juno_i2c_read_register(int32_t address, int32_t reg) {
                  return juno_i2c_read(address, reg, 1);
                }

                extern "C" int32_t juno_i2c_read_register16(int32_t address, int32_t reg) {
                  return juno_i2c_read(address, reg, 2);
                }
                """;
    }

    /**
     * Framed bytes over 38 kHz infrared, bit-banged on two plain pins: 8 data bits LSB first, odd parity
     * and one stop bit at the configured baud rate, with a software carrier for a 0 bit when transmitting.
     * The receive pin is an IR demodulator module's output, which idles high and goes low in a burst.
     */
    static String infraredHelpers() {
        return """

                static int32_t juno_ir_rx_pin = -1;
                static int32_t juno_ir_tx_pin = -1;
                static uint32_t juno_ir_bit_us = 417;

                extern "C" void juno_ir_begin(int32_t receivePin, int32_t transmitPin, int32_t baud) {
                  juno_ir_rx_pin = receivePin;
                  juno_ir_tx_pin = transmitPin;
                  juno_ir_bit_us = baud > 0 ? static_cast<uint32_t>(1000000 / baud) : 417;
                  if (receivePin >= 0) {
                    pinMode(receivePin, INPUT);
                  }
                  if (transmitPin >= 0) {
                    pinMode(transmitPin, OUTPUT);
                    digitalWrite(transmitPin, LOW);
                  }
                }

                extern "C" int32_t juno_ir_receiving() {
                  return juno_ir_rx_pin >= 0 && digitalRead(juno_ir_rx_pin) == LOW ? 1 : 0;
                }

                static void juno_ir_wait_until(uint32_t at) {
                  while (static_cast<int32_t>(micros() - at) < 0) {
                  }
                }

                extern "C" int32_t juno_ir_read_byte(int32_t timeoutMicros) {
                  if (juno_ir_rx_pin < 0) {
                    return -1;
                  }
                  uint32_t waitStart = micros();
                  while (digitalRead(juno_ir_rx_pin) != LOW) {
                    if (micros() - waitStart > static_cast<uint32_t>(timeoutMicros)) {
                      return -1;
                    }
                  }
                  uint32_t start = micros();
                  int32_t value = 0;
                  int ones = 0;
                  // Sample in the middle of each bit; bit 0 is the start bit, 1..8 data, 9 parity.
                  for (int bit = 1; bit <= 9; bit++) {
                    juno_ir_wait_until(start + juno_ir_bit_us * bit + juno_ir_bit_us / 2);
                    int level = digitalRead(juno_ir_rx_pin) == HIGH ? 1 : 0;
                    if (bit <= 8) {
                      value |= level << (bit - 1);
                    }
                    ones += level;
                  }
                  juno_ir_wait_until(start + juno_ir_bit_us * 10 + juno_ir_bit_us / 2);
                  // Odd parity: data and parity bits hold an odd number of ones; the stop bit is high.
                  if ((ones & 1) != 1 || digitalRead(juno_ir_rx_pin) != HIGH) {
                    return -2;
                  }
                  return value;
                }

                // Holds the IR LED dark (bit 1) or modulated at about 38 kHz (bit 0) for one bit time.
                static void juno_ir_send_bit(int bit) {
                  uint32_t start = micros();
                  while (micros() - start < juno_ir_bit_us) {
                    if (bit == 0) {
                      digitalWrite(juno_ir_tx_pin, HIGH);
                      delayMicroseconds(8);
                    }
                    digitalWrite(juno_ir_tx_pin, LOW);
                    if (bit == 0) {
                      delayMicroseconds(8);
                    }
                  }
                }

                // Transmits a framed byte while sampling the receive pin, which hears the own LED through the
                // demodulator, delayed by its response time: each bit is sampled in its middle plus latencyMicros.
                extern "C" int32_t juno_ir_echo_byte(int32_t value, int32_t latencyMicros) {
                  if (juno_ir_tx_pin < 0 || juno_ir_rx_pin < 0) {
                    return -1;
                  }
                  int sent[11];
                  int ones = 0;
                  sent[0] = 0;
                  for (int bit = 0; bit < 8; bit++) {
                    sent[bit + 1] = (value >> bit) & 1;
                    ones += sent[bit + 1];
                  }
                  sent[9] = (ones & 1) == 0 ? 1 : 0;
                  sent[10] = 1;
                  int heard[11];
                  int sampled = 0;
                  uint32_t start = micros();
                  uint32_t total = juno_ir_bit_us * 11 + juno_ir_bit_us / 2 + static_cast<uint32_t>(latencyMicros);
                  uint32_t elapsed = 0;
                  while (elapsed < total) {
                    elapsed = micros() - start;
                    uint32_t index = elapsed / juno_ir_bit_us;
                    if (index < 11 && sent[index] == 0) {
                      digitalWrite(juno_ir_tx_pin, HIGH);
                      delayMicroseconds(8);
                      digitalWrite(juno_ir_tx_pin, LOW);
                      delayMicroseconds(8);
                    } else {
                      digitalWrite(juno_ir_tx_pin, LOW);
                    }
                    while (sampled < 11 && elapsed >= juno_ir_bit_us * sampled + juno_ir_bit_us / 2
                        + static_cast<uint32_t>(latencyMicros)) {
                      heard[sampled] = digitalRead(juno_ir_rx_pin) == HIGH ? 1 : 0;
                      sampled++;
                    }
                  }
                  digitalWrite(juno_ir_tx_pin, LOW);
                  if (heard[0] != 0) {
                    return -1;
                  }
                  int32_t received = 0;
                  int receivedOnes = 0;
                  for (int bit = 0; bit < 8; bit++) {
                    received |= heard[bit + 1] << bit;
                    receivedOnes += heard[bit + 1];
                  }
                  receivedOnes += heard[9];
                  if ((receivedOnes & 1) != 1 || heard[10] != 1) {
                    return -2;
                  }
                  return received;
                }

                // A raw burst of the 38 kHz carrier, or silence, for protocols that encode bits in pulse lengths.
                extern "C" void juno_ir_mark(int32_t durationMicros) {
                  if (juno_ir_tx_pin < 0) {
                    return;
                  }
                  uint32_t start = micros();
                  while (micros() - start < static_cast<uint32_t>(durationMicros)) {
                    digitalWrite(juno_ir_tx_pin, HIGH);
                    delayMicroseconds(8);
                    digitalWrite(juno_ir_tx_pin, LOW);
                    delayMicroseconds(8);
                  }
                }

                extern "C" void juno_ir_space(int32_t durationMicros) {
                  if (juno_ir_tx_pin < 0) {
                    return;
                  }
                  digitalWrite(juno_ir_tx_pin, LOW);
                  juno_ir_wait_until(micros() + static_cast<uint32_t>(durationMicros));
                }

                extern "C" void juno_ir_write_byte(int32_t value) {
                  if (juno_ir_tx_pin < 0) {
                    return;
                  }
                  int ones = 0;
                  juno_ir_send_bit(0);
                  for (int bit = 0; bit < 8; bit++) {
                    int level = (value >> bit) & 1;
                    ones += level;
                    juno_ir_send_bit(level);
                  }
                  juno_ir_send_bit((ones & 1) == 0 ? 1 : 0);
                  juno_ir_send_bit(1);
                }
                """;
    }

    /**
     * A BLE central for one LEGO Powered Up hub, speaking LEGO Wireless Protocol 3.0 through the
     * optional {@code ArduinoBLE} library: every command is one write to the hub's single
     * characteristic, framed as {@code [length, hub id 0, message type, payload...]}, and the hub's
     * notifications on that characteristic carry the sensor values {@code enableSensor} asked for.
     */
    static String legoPoweredUpHelpers() {
        return """

                static const char juno_lego_service_uuid[] = "00001623-1212-efde-1623-785feabcd123";
                static const char juno_lego_characteristic_uuid[] = "00001624-1212-efde-1623-785feabcd123";
                // BLECharacteristic is reference counted but has no copy assignment, so it is never kept in a
                // static: each write looks it up again on the connected hub, which is a plain-copy BLEDevice.
                static BLEDevice juno_lego_hub;
                static bool juno_lego_ble_started = false;
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

                static bool juno_lego_connected() {
                  BLE.poll();
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
                  return juno_lego_connected() ? 1 : 0;
                }

                extern "C" int32_t juno_lego_hub_type_id() {
                  return juno_lego_hub_type;
                }

                extern "C" void juno_lego_hub_set_motor_power(int32_t port, int32_t powerPercent) {
                  if (powerPercent > 100) {
                    powerPercent = 100;
                  } else if (powerPercent < -100) {
                    powerPercent = -100;
                  }
                  juno_lego_write_mode0(port, powerPercent);
                }

                extern "C" void juno_lego_hub_brake_motor(int32_t port) {
                  juno_lego_write_mode0(port, 127);
                }

                // Port output command (0x81) StartSpeed (subcommand 0x07) with a speed of 0: the motor's speed control
                // keeps it where it is, so it resists being turned, unlike the brake, which only slows it.
                extern "C" void juno_lego_hub_hold_motor(int32_t port) {
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
                  uint8_t port = juno_lego_led_port();
                  juno_lego_led_use_mode(port, 0);
                  juno_lego_write_mode0(port, color);
                }

                extern "C" void juno_lego_hub_set_led_rgb(int32_t red, int32_t green, int32_t blue) {
                  uint8_t port = juno_lego_led_port();
                  juno_lego_led_use_mode(port, 1);
                  // WriteDirectModeData (0x51) of three bytes in mode 1: red, green, blue.
                  const uint8_t payload[] = {port, 0x11, 0x51, 0x01, static_cast<uint8_t>(red & 0xFF),
                      static_cast<uint8_t>(green & 0xFF), static_cast<uint8_t>(blue & 0xFF)};
                  juno_lego_send(0x81, payload, sizeof(payload));
                }

                extern "C" void juno_lego_hub_enable_sensor(int32_t port, int32_t mode) {
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
                  BLE.poll();
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
                    BLE.poll();
                  }
                }

                extern "C" int32_t juno_lego_hub_battery_percent() {
                  juno_lego_ensure_properties();
                  BLE.poll();
                  return juno_lego_battery;
                }

                extern "C" int32_t juno_lego_hub_button_pressed() {
                  juno_lego_ensure_properties();
                  BLE.poll();
                  return juno_lego_button;
                }

                extern "C" int32_t juno_lego_hub_rssi() {
                  juno_lego_ensure_properties();
                  BLE.poll();
                  return juno_lego_rssi;
                }

                extern "C" int32_t juno_lego_hub_firmware_version() {
                  juno_lego_ensure_properties();
                  BLE.poll();
                  return juno_lego_firmware;
                }

                extern "C" int32_t juno_lego_hub_hardware_version() {
                  juno_lego_ensure_properties();
                  BLE.poll();
                  return juno_lego_hardware;
                }

                extern "C" int32_t juno_lego_hub_port_device(int32_t port) {
                  BLE.poll();
                  return port >= 0 && port < 4 ? juno_lego_port_devices[port] : 0;
                }

                extern "C" int32_t juno_lego_hub_name(uint8_t* buffer, int32_t capacity) {
                  juno_lego_ensure_properties();
                  BLE.poll();
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
                  BLE.poll();
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
                  BLE.poll();
                  int slot = juno_lego_sensor_slot(static_cast<uint8_t>(port));
                  return slot < 0 ? 0 : juno_lego_sensor_reports[slot];
                }

                extern "C" int32_t juno_lego_hub_sensor_report_size(int32_t port) {
                  BLE.poll();
                  int slot = juno_lego_sensor_slot(static_cast<uint8_t>(port));
                  return slot < 0 ? 0 : juno_lego_sensor_raw_size[slot];
                }

                // Virtual Port Setup (0x61): connect (0x01) two motor ports into one virtual port, whose id the hub
                // announces in a Hub Attached I/O event; waits up to 500 ms for it. Returns the id or -1.
                extern "C" int32_t juno_lego_hub_link_motors(int32_t portA, int32_t portB) {
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
                    BLE.poll();
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
                  juno_lego_write_linked_power(virtualPort, juno_lego_clamp_power(first), juno_lego_clamp_power(second));
                }

                extern "C" void juno_lego_hub_brake_linked_motors(int32_t virtualPort) {
                  juno_lego_write_linked_power(virtualPort, 127, 127);
                }

                extern "C" void juno_lego_hub_disconnect() {
                  if (juno_lego_hub) {
                    juno_lego_hub.disconnect();
                  }
                  juno_lego_led_mode = -1;
                }

                extern "C" void juno_lego_hub_switch_off() {
                  // Hub action (0x02): switch off hub (0x01).
                  const uint8_t payload[] = {0x01};
                  juno_lego_send(0x02, payload, sizeof(payload));
                  juno_lego_led_mode = -1;
                }
                """;
    }

    /**
     * Read-only {@code java.io.RandomAccessFile}: a handle into the same open-file table as {@code SdCard.open}.
     * Every failure leaves the JDK's own exception pending (the caller's call guard routes it to a handler) and
     * returns a value the program never reads. Needs {@link #sdHelpers()} and {@link #exceptionHelpers} first.
     */
    static String randomAccessFileHelpers(int ioClassId, int fileNotFoundClassId, int eofClassId,
                                          int indexOutOfBoundsClassId) {
        return """

                // ---- java.io.RandomAccessFile (mode "r") ----------------------------------------------------------
                static constexpr int32_t JUNO_RAF_IO = ${JUNO_RAF_IO};
                static constexpr int32_t JUNO_RAF_FILE_NOT_FOUND = ${JUNO_RAF_FILE_NOT_FOUND};
                static constexpr int32_t JUNO_RAF_EOF = ${JUNO_RAF_EOF};
                static constexpr int32_t JUNO_RAF_INDEX_OUT_OF_BOUNDS = ${JUNO_RAF_INDEX_OUT_OF_BOUNDS};

                static void juno_raf_raise(int32_t classId, const char* message) {
                  auto* exception = static_cast<int32_t*>(juno_alloc(${JUNO_THROWABLE_BYTES}u, 4u));
                  exception[${JUNO_CLASS_ID_WORD}] = classId;
                  exception[${JUNO_MESSAGE_WORD}] = static_cast<int32_t>(reinterpret_cast<intptr_t>(message));
                  juno_throw_raise(static_cast<int32_t>(reinterpret_cast<intptr_t>(exception)));
                }

                // A null receiver is a NullPointerException; a closed file an IOException, as on the JVM.
                static File32* juno_raf_file(int32_t handle) {
                  if (handle == 0) {
                    juno_throw_raise(0);
                    return nullptr;
                  }
                  int32_t index = handle - 1;
                  if (index < 0 || index >= JUNO_SD_MAX_OPEN_FILES || !juno_sd_file_used[index]) {
                    juno_raf_raise(JUNO_RAF_IO, "Stream Closed");
                    return nullptr;
                  }
                  return &juno_sd_files[index];
                }

                // capacity is the buffer's compile-time length, or -1 when the compiler could not know it.
                static bool juno_raf_in_bounds(const uint8_t* buffer, int32_t capacity, int32_t offset, int32_t length) {
                  if (buffer == nullptr) {
                    juno_throw_raise(0);
                    return false;
                  }
                  if (offset < 0 || length < 0 || (capacity >= 0 && length > capacity - offset)) {
                    juno_raf_raise(JUNO_RAF_INDEX_OUT_OF_BOUNDS, "Range out of bounds for the buffer");
                    return false;
                  }
                  return true;
                }

                extern "C" int32_t juno_raf_open(const char* path) {
                  for (int32_t i = 0; i < JUNO_SD_MAX_OPEN_FILES; i++) {
                    if (!juno_sd_file_used[i]) {
                      juno_sd_files[i] = juno_sd.open(path, O_RDONLY);
                      if (!juno_sd_files[i]) {
                        juno_raf_raise(JUNO_RAF_FILE_NOT_FOUND, path);
                        return 0;
                      }
                      juno_sd_file_used[i] = true;
                      return i + 1;
                    }
                  }
                  juno_raf_raise(JUNO_RAF_IO, "Too many open files");
                  return 0;
                }

                extern "C" int32_t juno_raf_read(int32_t handle) {
                  File32* file = juno_raf_file(handle);
                  return file == nullptr ? -1 : file->read();
                }

                extern "C" int32_t juno_raf_read_bytes(
                    int32_t handle, uint8_t* buffer, int32_t capacity, int32_t offset, int32_t length) {
                  File32* file = juno_raf_file(handle);
                  if (file == nullptr || !juno_raf_in_bounds(buffer, capacity, offset, length)) return -1;
                  if (length == 0) return 0;
                  int32_t count = static_cast<int32_t>(file->read(buffer + offset, static_cast<size_t>(length)));
                  if (count < 0) {
                    juno_raf_raise(JUNO_RAF_IO, "Read error");
                    return -1;
                  }
                  return count == 0 ? -1 : count;
                }

                extern "C" void juno_raf_read_fully(
                    int32_t handle, uint8_t* buffer, int32_t capacity, int32_t offset, int32_t length) {
                  File32* file = juno_raf_file(handle);
                  if (file == nullptr || !juno_raf_in_bounds(buffer, capacity, offset, length)) return;
                  while (length > 0) {
                    int32_t count = static_cast<int32_t>(file->read(buffer + offset, static_cast<size_t>(length)));
                    if (count < 0) {
                      juno_raf_raise(JUNO_RAF_IO, "Read error");
                      return;
                    }
                    if (count == 0) {
                      juno_raf_raise(JUNO_RAF_EOF, nullptr);
                      return;
                    }
                    offset += count;
                    length -= count;
                  }
                }

                // FAT32 files stop at 4 GiB, and SdFat cannot seek a read-only file past its end.
                extern "C" void juno_raf_seek(int32_t handle, uint32_t positionLow, int32_t positionHigh) {
                  File32* file = juno_raf_file(handle);
                  if (file == nullptr) return;
                  if (positionHigh < 0) {
                    juno_raf_raise(JUNO_RAF_IO, "Negative seek offset");
                  } else if (positionHigh != 0 || positionLow > file->size() || !file->seekSet(positionLow)) {
                    juno_raf_raise(JUNO_RAF_IO, "Seek past the end of the file");
                  }
                }

                extern "C" int64_t juno_raf_get_file_pointer(int32_t handle) {
                  File32* file = juno_raf_file(handle);
                  return file == nullptr ? 0 : static_cast<int64_t>(file->curPosition());
                }

                extern "C" int64_t juno_raf_length(int32_t handle) {
                  File32* file = juno_raf_file(handle);
                  return file == nullptr ? 0 : static_cast<int64_t>(file->size());
                }

                extern "C" int32_t juno_raf_skip_bytes(int32_t handle, int32_t count) {
                  File32* file = juno_raf_file(handle);
                  if (file == nullptr || count <= 0) return 0;
                  uint32_t position = file->curPosition();
                  uint32_t remaining = static_cast<uint32_t>(file->size()) - position;
                  uint32_t skipped = static_cast<uint32_t>(count) < remaining ? static_cast<uint32_t>(count) : remaining;
                  if (!file->seekSet(position + skipped)) {
                    juno_raf_raise(JUNO_RAF_IO, "Seek error");
                    return 0;
                  }
                  return static_cast<int32_t>(skipped);
                }

                // Closing an already closed file does nothing, as in the JDK.
                extern "C" void juno_raf_close(int32_t handle) {
                  if (handle == 0) {
                    juno_throw_raise(0);
                    return;
                  }
                  int32_t index = handle - 1;
                  if (index < 0 || index >= JUNO_SD_MAX_OPEN_FILES || !juno_sd_file_used[index]) return;
                  juno_sd_files[index].close();
                  juno_sd_file_used[index] = false;
                }
                """
                .replace("${JUNO_RAF_IO}", Integer.toString(ioClassId))
                .replace("${JUNO_RAF_FILE_NOT_FOUND}", Integer.toString(fileNotFoundClassId))
                .replace("${JUNO_RAF_EOF}", Integer.toString(eofClassId))
                .replace("${JUNO_RAF_INDEX_OUT_OF_BOUNDS}", Integer.toString(indexOutOfBoundsClassId))
                .replace("${JUNO_THROWABLE_BYTES}", Integer.toString(ThrowableTypes.HEADER_BYTES))
                .replace("${JUNO_CLASS_ID_WORD}", Integer.toString(ThrowableTypes.CLASS_ID_OFFSET / AsmEmitter.WORD))
                .replace("${JUNO_MESSAGE_WORD}", Integer.toString(ThrowableTypes.MESSAGE_OFFSET / AsmEmitter.WORD));
    }

    /** Exposes the arena allocator's current usage and its fixed capacity. */
    static String memoryHelpers() {
        return """

                extern "C" int32_t juno_memory_arena_used() {
                  return static_cast<int32_t>(juno_arena_used);
                }

                extern "C" int32_t juno_memory_arena_capacity() {
                  return static_cast<int32_t>(sizeof(juno_arena));
                }
                """;
    }

    /** Arduino core {@code random()}/{@code randomSeed()} bindings. */
    static String randomHelpers() {
        return """

                extern "C" void juno_random_seed(int32_t seed) {
                  randomSeed(static_cast<unsigned long>(static_cast<uint32_t>(seed)));
                }

                extern "C" int32_t juno_random_next_bound(int32_t bound) {
                  return static_cast<int32_t>(random(static_cast<long>(bound)));
                }

                extern "C" int32_t juno_random_next_range(int32_t origin, int32_t bound) {
                  return static_cast<int32_t>(random(static_cast<long>(origin), static_cast<long>(bound)));
                }
                """;
    }

    /** {@code WiFiS3} station-mode bindings. */
    static String wifiHelpers() {
        return """

                extern "C" void juno_wifi_begin(const char* ssid, const char* password) {
                  WiFi.begin(ssid, password);
                }

                extern "C" void juno_wifi_begin_ap(const char* ssid, const char* password) {
                  // WiFiS3 only accepts a WPA2 passphrase of 8+ characters; an empty one opens the network.
                  if (password != nullptr && password[0] != '\\0') {
                    WiFi.beginAP(ssid, password);
                  } else {
                    WiFi.beginAP(ssid);
                  }
                }

                extern "C" int32_t juno_wifi_status() {
                  return static_cast<int32_t>(WiFi.status());
                }

                extern "C" void juno_wifi_local_ip(int32_t* octets) {
                  auto ip = WiFi.localIP();
                  octets[0] = ip[0];
                  octets[1] = ip[1];
                  octets[2] = ip[2];
                  octets[3] = ip[3];
                }
                """;
    }

    /** Arduino SD read/append bindings plus a bounded, arena-backed {@code key=value} parser. */
    static String sdHelpers() {
        return """

                static constexpr int32_t JUNO_SD_MAX_OPEN_FILES = 4;
                static constexpr int32_t JUNO_SD_BEGIN_ATTEMPTS = 3;
                static constexpr uint32_t JUNO_SD_BEGIN_RETRY_DELAY_MS = 250;
                static constexpr int32_t JUNO_PROPERTIES_MAX_ENTRIES = 16;
                static constexpr int32_t JUNO_PROPERTIES_KEY_CAPACITY = 32;
                static constexpr int32_t JUNO_PROPERTIES_VALUE_CAPACITY = 64;
                static constexpr int32_t JUNO_PROPERTIES_LINE_CAPACITY = 96;

                static SdFat32 juno_sd;
                static File32 juno_sd_files[JUNO_SD_MAX_OPEN_FILES];
                static bool juno_sd_file_used[JUNO_SD_MAX_OPEN_FILES];

                static File32* juno_sd_file(int32_t handle) {
                  int32_t index = handle - 1;
                  if (index < 0 || index >= JUNO_SD_MAX_OPEN_FILES || !juno_sd_file_used[index]) {
                    juno_panic();
                  }
                  return &juno_sd_files[index];
                }

                extern "C" int32_t juno_sd_begin(int32_t chipSelectPin) {
                  for (int32_t i = 0; i < JUNO_SD_MAX_OPEN_FILES; i++) {
                    if (juno_sd_file_used[i]) juno_sd_files[i].close();
                    juno_sd_file_used[i] = false;
                  }
                  // A card that survived a host reset without losing power can fail its first init.
                  for (int32_t attempt = 0; attempt < JUNO_SD_BEGIN_ATTEMPTS; attempt++) {
                    if (juno_sd.begin(chipSelectPin)) return 1;
                    delay(JUNO_SD_BEGIN_RETRY_DELAY_MS);
                  }
                  return 0;
                }

                extern "C" int32_t juno_sd_exists(const char* path) {
                  return juno_sd.exists(path) ? 1 : 0;
                }

                extern "C" int32_t juno_sd_size(const char* path) {
                  File32 file = juno_sd.open(path, O_RDONLY);
                  if (!file) return -1;
                  uint32_t size = file.size();
                  file.close();
                  return size > 0x7fffffffu ? 0x7fffffff : static_cast<int32_t>(size);
                }

                extern "C" int32_t juno_sd_open(const char* path) {
                  for (int32_t i = 0; i < JUNO_SD_MAX_OPEN_FILES; i++) {
                    if (!juno_sd_file_used[i]) {
                      juno_sd_files[i] = juno_sd.open(path, O_RDONLY);
                      if (!juno_sd_files[i]) return 0;
                      juno_sd_file_used[i] = true;
                      return i + 1;
                    }
                  }
                  return 0;
                }

                extern "C" int32_t juno_sd_file_available(int32_t handle) {
                  return juno_sd_file(handle)->available() > 0 ? 1 : 0;
                }

                extern "C" int32_t juno_sd_file_read(int32_t handle) {
                  return juno_sd_file(handle)->read();
                }

                extern "C" void juno_sd_file_close(int32_t handle) {
                  int32_t index = handle - 1;
                  File32* file = juno_sd_file(handle);
                  file->close();
                  juno_sd_file_used[index] = false;
                }

                extern "C" int32_t juno_sd_file_append_line(const char* path, const char* line) {
                  File32 file = juno_sd.open(path, O_WRITE | O_CREAT | O_APPEND);
                  if (!file) return 0;
                  file.println(line);
                  file.close();
                  return 1;
                }

                extern "C" int32_t juno_sd_file_append_text(const char* path, const char* text) {
                  File32 file = juno_sd.open(path, O_WRITE | O_CREAT | O_APPEND);
                  if (!file) return 0;
                  file.print(text);
                  file.close();
                  return 1;
                }

                extern "C" int32_t juno_sd_remove(const char* path) {
                  return juno_sd.remove(path) ? 1 : 0;
                }

                struct JunoPropertyEntry {
                  char key[JUNO_PROPERTIES_KEY_CAPACITY];
                  char value[JUNO_PROPERTIES_VALUE_CAPACITY];
                };

                struct JunoProperties {
                  int32_t count;
                  JunoPropertyEntry entries[JUNO_PROPERTIES_MAX_ENTRIES];
                };

                static JunoProperties* juno_properties(int32_t handle) {
                  if (handle == 0) juno_panic();
                  return reinterpret_cast<JunoProperties*>(static_cast<intptr_t>(handle));
                }

                extern "C" int32_t juno_properties_new() {
                  auto* properties = static_cast<JunoProperties*>(juno_alloc(sizeof(JunoProperties), 4));
                  properties->count = 0;
                  return static_cast<int32_t>(reinterpret_cast<intptr_t>(properties));
                }

                static char* juno_properties_trim_left(char* text) {
                  while (*text == ' ' || *text == '\\t' || *text == '\\f') text++;
                  return text;
                }

                static void juno_properties_trim_right(char* text) {
                  int32_t length = static_cast<int32_t>(strlen(text));
                  while (length > 0) {
                    char c = text[length - 1];
                    if (c != ' ' && c != '\\t' && c != '\\f') break;
                    text[--length] = '\\0';
                  }
                }

                static bool juno_properties_put(JunoProperties* properties, char* line) {
                  char* key = juno_properties_trim_left(line);
                  juno_properties_trim_right(key);
                  if (*key == '\\0' || *key == '#' || *key == '!') return true;

                  char* separator = key;
                  while (*separator != '\\0' && *separator != '=' && *separator != ':') separator++;
                  if (*separator == '\\0') return true;
                  *separator = '\\0';
                  char* value = juno_properties_trim_left(separator + 1);
                  juno_properties_trim_right(key);
                  juno_properties_trim_right(value);

                  size_t keyLength = strlen(key);
                  size_t valueLength = strlen(value);
                  if (keyLength == 0 || keyLength >= JUNO_PROPERTIES_KEY_CAPACITY
                      || valueLength >= JUNO_PROPERTIES_VALUE_CAPACITY) return false;

                  int32_t index = 0;
                  while (index < properties->count && strcmp(properties->entries[index].key, key) != 0) index++;
                  if (index == properties->count) {
                    if (properties->count >= JUNO_PROPERTIES_MAX_ENTRIES) return false;
                    properties->count++;
                  }
                  memcpy(properties->entries[index].key, key, keyLength + 1u);
                  memcpy(properties->entries[index].value, value, valueLength + 1u);
                  return true;
                }

                extern "C" void juno_properties_load(int32_t propertiesHandle, int32_t fileHandle) {
                  File32* file = juno_sd_file(fileHandle);
                  JunoProperties* properties = juno_properties(propertiesHandle);
                  char line[JUNO_PROPERTIES_LINE_CAPACITY];
                  int32_t length = 0;
                  while (file->available() > 0) {
                    int32_t value = file->read();
                    if (value < 0) juno_panic();
                    if (value == '\\r') continue;
                    if (value == '\\n') {
                      line[length] = '\\0';
                      if (!juno_properties_put(properties, line)) juno_panic();
                      length = 0;
                    } else {
                      if (length >= JUNO_PROPERTIES_LINE_CAPACITY - 1) juno_panic();
                      line[length++] = static_cast<char>(value);
                    }
                  }
                  if (length > 0) {
                    line[length] = '\\0';
                    if (!juno_properties_put(properties, line)) juno_panic();
                  }
                }

                extern "C" int32_t juno_properties_get(int32_t handle, const char* key) {
                  JunoProperties* properties = juno_properties(handle);
                  for (int32_t i = 0; i < properties->count; i++) {
                    if (strcmp(properties->entries[i].key, key) == 0) {
                      return static_cast<int32_t>(reinterpret_cast<intptr_t>(properties->entries[i].value));
                    }
                  }
                  return 0;
                }

                extern "C" int32_t juno_properties_get_default(
                    int32_t handle, const char* key, const char* defaultValue) {
                  int32_t value = juno_properties_get(handle, key);
                  return value == 0
                      ? static_cast<int32_t>(reinterpret_cast<intptr_t>(defaultValue))
                      : value;
                }

                extern "C" int32_t juno_properties_size(int32_t handle) {
                  return juno_properties(handle)->count;
                }
                """;
    }

    /**
     * {@code extern "C"} {@code juno_l*} helpers for 64-bit arithmetic, reused as plain C++ rather
     * than hand-rolled 64-bit assembly, consistent with this backend's existing shim-delegation
     * philosophy for anything nontrivial (see this class's doc).
     */
    static String longHelpers() {
        return """

                extern "C" int64_t juno_ladd(int64_t a, int64_t b) {
                  return static_cast<int64_t>(static_cast<uint64_t>(a) + static_cast<uint64_t>(b));
                }
                extern "C" int64_t juno_lsub(int64_t a, int64_t b) {
                  return static_cast<int64_t>(static_cast<uint64_t>(a) - static_cast<uint64_t>(b));
                }
                extern "C" int64_t juno_lmul(int64_t a, int64_t b) {
                  return static_cast<int64_t>(static_cast<uint64_t>(a) * static_cast<uint64_t>(b));
                }
                extern "C" int64_t juno_ldiv(int64_t a, int64_t b) {
                  if (b == 0) juno_panic();
                  if (a == INT64_MIN && b == -1) return INT64_MIN;
                  return a / b;
                }
                extern "C" int64_t juno_lrem(int64_t a, int64_t b) {
                  if (b == 0) juno_panic();
                  if (a == INT64_MIN && b == -1) return 0;
                  return a % b;
                }
                extern "C" int64_t juno_lneg(int64_t value) {
                  return static_cast<int64_t>(0ull - static_cast<uint64_t>(value));
                }
                extern "C" int64_t juno_lshl(int64_t a, int32_t b) {
                  return static_cast<int64_t>(static_cast<uint64_t>(a) << (b & 63));
                }
                extern "C" int64_t juno_lshr(int64_t a, int32_t b) {
                  uint32_t shift = static_cast<uint32_t>(b) & 63u;
                  uint64_t value = static_cast<uint64_t>(a);
                  if (shift == 0 || a >= 0) return static_cast<int64_t>(value >> shift);
                  return static_cast<int64_t>((value >> shift) | (~0ull << (64u - shift)));
                }
                extern "C" int64_t juno_lushr(int64_t a, int32_t b) {
                  return static_cast<int64_t>(static_cast<uint64_t>(a) >> (b & 63));
                }
                extern "C" int64_t juno_land(int64_t a, int64_t b) { return a & b; }
                extern "C" int64_t juno_lor(int64_t a, int64_t b) { return a | b; }
                extern "C" int64_t juno_lxor(int64_t a, int64_t b) { return a ^ b; }
                extern "C" int32_t juno_lcmp(int64_t a, int64_t b) {
                  return (a > b) - (a < b);
                }

                """;
    }

    static String runtimeStringHelpers() {
        return """

                static constexpr uint32_t JUNO_STRING_SLOT_COUNT = 8;
                static constexpr uint32_t JUNO_STRING_SLOT_SIZE = ${JUNO_STRING_SLOT_SIZE};
                static char juno_string_slots[JUNO_STRING_SLOT_COUNT][JUNO_STRING_SLOT_SIZE];
                static uint32_t juno_string_slot_cursor = 0;

                static const char* juno_string_pointer(int32_t value) {
                  if (value == 0) juno_panic();
                  return reinterpret_cast<const char*>(static_cast<intptr_t>(value));
                }

                extern "C" int32_t juno_string_value_of_int(int32_t value) {
                  char* buffer = juno_string_slots[juno_string_slot_cursor];
                  juno_string_slot_cursor = (juno_string_slot_cursor + 1u) % JUNO_STRING_SLOT_COUNT;
                  char reversed[JUNO_STRING_SLOT_SIZE];
                  uint32_t magnitude = value < 0
                      ? 0u - static_cast<uint32_t>(value)
                      : static_cast<uint32_t>(value);
                  uint32_t count = 0;
                  do {
                    reversed[count++] = static_cast<char>('0' + magnitude % 10u);
                    magnitude /= 10u;
                  } while (magnitude != 0u);
                  uint32_t index = 0;
                  if (value < 0) buffer[index++] = '-';
                  while (count > 0u) buffer[index++] = reversed[--count];
                  buffer[index] = '\\0';
                  return static_cast<int32_t>(reinterpret_cast<intptr_t>(buffer));
                }

                // Fixed-precision formatting, not Java's shortest-round-trip Double.toString: the
                // fractional part is truncated toward zero at 6 digits (trailing zeros trimmed), so a
                // value whose exact binary representation sits just below the intended decimal (e.g.
                // 21.2 stored as 21.199999999999999...) can format one unit low in the last digit.
                // Formats into a caller-owned buffer so string concatenation can format without taking a pool slot.
                static void juno_format_double(char* buffer, double value) {
                  if (isnan(value) || isinf(value)) juno_panic();
                  bool negative = value < 0.0;
                  double magnitude = negative ? -value : value;
                  if (magnitude >= 1.0e9) juno_panic();
                  auto integerPart = static_cast<uint32_t>(magnitude);
                  static constexpr uint32_t FRACTION_DIGITS = 6;
                  static constexpr uint32_t FRACTION_SCALE = 1000000u;
                  double fraction = magnitude - static_cast<double>(integerPart);
                  auto fractionDigits = static_cast<uint32_t>(fraction * static_cast<double>(FRACTION_SCALE));
                  if (fractionDigits >= FRACTION_SCALE) fractionDigits = FRACTION_SCALE - 1u;
                  char reversed[JUNO_STRING_SLOT_SIZE];
                  uint32_t count = 0;
                  uint32_t integerDigits = integerPart;
                  do {
                    reversed[count++] = static_cast<char>('0' + integerDigits % 10u);
                    integerDigits /= 10u;
                  } while (integerDigits != 0u);
                  uint32_t index = 0;
                  if (negative) buffer[index++] = '-';
                  while (count > 0u) buffer[index++] = reversed[--count];
                  if (fractionDigits != 0u) {
                    char fractionText[FRACTION_DIGITS];
                    for (uint32_t i = FRACTION_DIGITS; i > 0u; --i) {
                      fractionText[i - 1u] = static_cast<char>('0' + fractionDigits % 10u);
                      fractionDigits /= 10u;
                    }
                    uint32_t fractionLength = FRACTION_DIGITS;
                    while (fractionLength > 1u && fractionText[fractionLength - 1u] == '0') fractionLength--;
                    buffer[index++] = '.';
                    for (uint32_t i = 0; i < fractionLength; ++i) buffer[index++] = fractionText[i];
                  } else {
                    // Java's Double.toString never prints a bare integer: 3.0, not 3.
                    buffer[index++] = '.';
                    buffer[index++] = '0';
                  }
                  buffer[index] = '\\0';
                }

                extern "C" JUNO_ASM_ABI int32_t juno_string_value_of_double(double value) {
                  char* buffer = juno_string_slots[juno_string_slot_cursor];
                  juno_string_slot_cursor = (juno_string_slot_cursor + 1u) % JUNO_STRING_SLOT_COUNT;
                  juno_format_double(buffer, value);
                  return static_cast<int32_t>(reinterpret_cast<intptr_t>(buffer));
                }

                extern "C" int32_t juno_string_length(int32_t value) {
                  return static_cast<int32_t>(strlen(juno_string_pointer(value)));
                }

                extern "C" int32_t juno_string_char_at(int32_t value, int32_t index) {
                  const char* text = juno_string_pointer(value);
                  int32_t length = static_cast<int32_t>(strlen(text));
                  if (index < 0 || index >= length) juno_panic();
                  return static_cast<uint8_t>(text[index]);
                }

                extern "C" int32_t juno_string_equals(int32_t a, int32_t b) {
                  return strcmp(juno_string_pointer(a), juno_string_pointer(b)) == 0 ? 1 : 0;
                }

                extern "C" int32_t juno_string_concat_new() {
                  char* buffer = juno_string_slots[juno_string_slot_cursor];
                  juno_string_slot_cursor = (juno_string_slot_cursor + 1u) % JUNO_STRING_SLOT_COUNT;
                  buffer[0] = '\\0';
                  return static_cast<int32_t>(reinterpret_cast<intptr_t>(buffer));
                }

                static void juno_string_concat_append_byte(int32_t handle, char value) {
                  char* buffer = reinterpret_cast<char*>(static_cast<intptr_t>(handle));
                  uint32_t length = static_cast<uint32_t>(strlen(buffer));
                  if (length + 1u >= JUNO_STRING_SLOT_SIZE) juno_panic();
                  buffer[length] = value;
                  buffer[length + 1u] = '\\0';
                }

                extern "C" void juno_string_concat_append_string(int32_t handle, const char* value) {
                  const char* text = value != nullptr ? value : "null";
                  while (*text != '\\0') juno_string_concat_append_byte(handle, *text++);
                }

                extern "C" void juno_string_concat_append_boolean(int32_t handle, int32_t value) {
                  juno_string_concat_append_string(handle, value != 0 ? "true" : "false");
                }

                extern "C" void juno_string_concat_append_char(int32_t handle, int32_t value) {
                  juno_string_concat_append_byte(handle, static_cast<char>(value));
                }

                extern "C" void juno_string_concat_append_int(int32_t handle, int32_t value) {
                  char reversed[11];
                  uint32_t magnitude = value < 0
                      ? 0u - static_cast<uint32_t>(value)
                      : static_cast<uint32_t>(value);
                  uint32_t count = 0;
                  do {
                    reversed[count++] = static_cast<char>('0' + magnitude % 10u);
                    magnitude /= 10u;
                  } while (magnitude != 0u);
                  if (value < 0) juno_string_concat_append_byte(handle, '-');
                  while (count > 0u) juno_string_concat_append_byte(handle, reversed[--count]);
                }

                extern "C" void juno_string_concat_append_long(int32_t handle, int64_t value) {
                  char reversed[20];
                  uint64_t magnitude = value < 0
                      ? 0ull - static_cast<uint64_t>(value)
                      : static_cast<uint64_t>(value);
                  uint32_t count = 0;
                  do {
                    reversed[count++] = static_cast<char>('0' + magnitude % 10ull);
                    magnitude /= 10ull;
                  } while (magnitude != 0ull);
                  if (value < 0) juno_string_concat_append_byte(handle, '-');
                  while (count > 0u) juno_string_concat_append_byte(handle, reversed[--count]);
                }

                extern "C" JUNO_ASM_ABI void juno_string_concat_append_float(int32_t handle, float value) {
                  char text[JUNO_STRING_SLOT_SIZE];
                  juno_format_double(text, value);
                  juno_string_concat_append_string(handle, text);
                }

                extern "C" JUNO_ASM_ABI void juno_string_concat_append_double(int32_t handle, double value) {
                  char text[JUNO_STRING_SLOT_SIZE];
                  juno_format_double(text, value);
                  juno_string_concat_append_string(handle, text);
                }
                """.replace("${JUNO_STRING_SLOT_SIZE}", Integer.toString(RuntimeLimits.STRING_SLOT_CAPACITY_BYTES));
    }

    /**
     * {@code StringBuilder} is represented as an arena-allocated header (its {@code length} and
     * {@code capacity}, as two {@code int32_t}s) immediately followed by its {@code capacity}-byte
     * buffer — a single {@code juno_alloc} block, addressed by the header's own pointer (cast to
     * {@code int32_t} the same way every other handle in this file is). {@code toString()} copies
     * the written bytes into the same rotating string-slot pool {@link #runtimeStringHelpers()}
     * uses for every other runtime string, so it needs that pool already declared.
     */
    static String stringBuilderHelpers() {
        return """

                static constexpr uint32_t JUNO_STRING_BUILDER_HEADER_WORDS = 2; // length, capacity

                extern "C" int32_t juno_string_builder_new(int32_t capacity) {
                  if (capacity < 0) juno_panic();
                  uint32_t bytes = JUNO_STRING_BUILDER_HEADER_WORDS * sizeof(int32_t) + static_cast<uint32_t>(capacity);
                  auto* header = reinterpret_cast<int32_t*>(juno_alloc(bytes, alignof(int32_t)));
                  header[0] = 0;         // length
                  header[1] = capacity;  // capacity
                  return static_cast<int32_t>(reinterpret_cast<intptr_t>(header));
                }

                static uint8_t* juno_string_builder_buffer(int32_t handle) {
                  auto* header = reinterpret_cast<int32_t*>(static_cast<intptr_t>(handle));
                  return reinterpret_cast<uint8_t*>(header + JUNO_STRING_BUILDER_HEADER_WORDS);
                }

                extern "C" int32_t juno_string_builder_append_char(int32_t handle, int32_t value) {
                  auto* header = reinterpret_cast<int32_t*>(static_cast<intptr_t>(handle));
                  if (header[0] < header[1]) {
                    juno_string_builder_buffer(handle)[header[0]] = static_cast<uint8_t>(value);
                    header[0]++;
                  }
                  return handle;
                }

                extern "C" int32_t juno_string_builder_append_string(int32_t handle, const char* text) {
                  auto* header = reinterpret_cast<int32_t*>(static_cast<intptr_t>(handle));
                  uint8_t* buffer = juno_string_builder_buffer(handle);
                  for (const char* c = text; *c != '\\0' && header[0] < header[1]; c++) {
                    buffer[header[0]] = static_cast<uint8_t>(*c);
                    header[0]++;
                  }
                  return handle;
                }

                extern "C" int32_t juno_string_builder_to_string(int32_t handle) {
                  auto* header = reinterpret_cast<int32_t*>(static_cast<intptr_t>(handle));
                  int32_t length = header[0];
                  if (static_cast<uint32_t>(length) >= JUNO_STRING_SLOT_SIZE) juno_panic();
                  char* slot = juno_string_slots[juno_string_slot_cursor];
                  juno_string_slot_cursor = (juno_string_slot_cursor + 1u) % JUNO_STRING_SLOT_COUNT;
                  const uint8_t* buffer = juno_string_builder_buffer(handle);
                  for (int32_t i = 0; i < length; i++) slot[i] = static_cast<char>(buffer[i]);
                  slot[length] = '\\0';
                  return static_cast<int32_t>(reinterpret_cast<intptr_t>(slot));
                }
                """;
    }

    /**
     * {@code athrow}'s runtime half: the thrown object's class id selects a handler when the throwing
     * method catches that class ({@code caughtMask} bit set); otherwise it reports the exception the
     * way the JVM does and panics. {@code throw null} reports a {@code NullPointerException}.
     */
    static String exceptionHelpers(List<String> throwableClasses) {
        StringBuilder names = new StringBuilder();
        if (throwableClasses.isEmpty()) {
            names.append("  \"\",\n"); // only `throw null` reaches the runtime; C++ forbids an empty array
        }
        for (String className : throwableClasses) {
            names.append("  \"").append(className.replace('/', '.')).append("\",\n");
        }
        return """
                static const char* const juno_throwable_names[] = {
                ${JUNO_THROWABLE_NAMES}};

                // threadId is -1 for the main thread, else the number in "Thread-N".
                static void juno_throw_report(int32_t exception, int32_t threadId) {
                  Serial.print("Exception in thread \\"");
                  if (threadId < 0) {
                    Serial.print("main");
                  } else {
                    Serial.print("Thread-");
                    Serial.print(threadId);
                  }
                  Serial.print("\\" ");
                  if (exception == 0) {
                    Serial.println("java.lang.NullPointerException");
                    return;
                  }
                  const int32_t* header = reinterpret_cast<const int32_t*>(static_cast<intptr_t>(exception));
                  Serial.print(juno_throwable_names[header[${JUNO_CLASS_ID_WORD}]]);
                  const char* message = reinterpret_cast<const char*>(static_cast<intptr_t>(header[${JUNO_MESSAGE_WORD}]));
                  if (message != nullptr) {
                    Serial.print(": ");
                    Serial.print(message);
                  }
                  Serial.println();
                }

                extern "C" [[noreturn]] void juno_throw_uncaught(int32_t exception) {
                  juno_throw_report(exception, -1);
                  juno_panic();
                }

                // The exception unwinding through generated frames: set by athrow, left set while frames
                // return, cleared when a handler takes it. Callers poll it after calls that can throw.
                static int32_t juno_pending_exception = 0;

                extern "C" void juno_throw_raise(int32_t exception) {
                  if (exception == 0) juno_throw_uncaught(exception);
                  juno_pending_exception = exception;
                }

                extern "C" int32_t juno_throw_pending() {
                  return juno_pending_exception;
                }

                // Class id of the pending exception if caughtMask has its bit (and clears it), else -1.
                extern "C" int32_t juno_throw_catch(int32_t caughtMask) {
                  int32_t classId = reinterpret_cast<const int32_t*>(
                      static_cast<intptr_t>(juno_pending_exception))[${JUNO_CLASS_ID_WORD}];
                  if ((static_cast<uint32_t>(caughtMask) >> classId & 1u) == 0u) return -1;
                  juno_pending_exception = 0;
                  return classId;
                }

                extern "C" int32_t juno_throw_dispatch(int32_t exception, int32_t caughtMask) {
                  juno_throw_raise(exception);
                  return juno_throw_catch(caughtMask);
                }

                // Called where the entry point returns: an exception still pending has no handler left.
                extern "C" void juno_throw_check_escape() {
                  if (juno_pending_exception != 0) juno_throw_uncaught(juno_pending_exception);
                }
                """.replace("${JUNO_THROWABLE_NAMES}", names.toString())
                .replace("${JUNO_CLASS_ID_WORD}", Integer.toString(ThrowableTypes.CLASS_ID_OFFSET / AsmEmitter.WORD))
                .replace("${JUNO_MESSAGE_WORD}", Integer.toString(ThrowableTypes.MESSAGE_OFFSET / AsmEmitter.WORD));
    }

    /** {@code extern "C"} float helpers, called from assembly with float values in core registers (see {@code JUNO_ASM_ABI}). */
    static String floatHelpers() {
        return """

                extern "C" JUNO_ASM_ABI float juno_fadd(float a, float b) { return a + b; }
                extern "C" JUNO_ASM_ABI float juno_fsub(float a, float b) { return a - b; }
                extern "C" JUNO_ASM_ABI float juno_fmul(float a, float b) { return a * b; }
                extern "C" JUNO_ASM_ABI float juno_fdiv(float a, float b) { return a / b; }
                extern "C" JUNO_ASM_ABI float juno_frem(float a, float b) { return fmodf(a, b); }
                extern "C" JUNO_ASM_ABI int32_t juno_fcmp(float a, float b, int32_t nanResult) {
                  if (isnan(a) || isnan(b)) return nanResult;
                  return (a > b) - (a < b);
                }
                extern "C" JUNO_ASM_ABI float juno_i2f(int32_t value) { return static_cast<float>(value); }
                extern "C" JUNO_ASM_ABI int32_t juno_f2i(float value) {
                  if (isnan(value)) return 0;
                  if (value >= 0x1.0p31f) return INT32_MAX;
                  if (value <= -0x1.0p31f) return INT32_MIN;
                  return static_cast<int32_t>(value);
                }
                extern "C" JUNO_ASM_ABI float juno_l2f(int64_t value) { return static_cast<float>(value); }
                extern "C" JUNO_ASM_ABI int64_t juno_f2l(float value) {
                  if (isnan(value)) return 0;
                  if (value >= 0x1.0p63f) return INT64_MAX;
                  if (value <= -0x1.0p63f) return INT64_MIN;
                  return static_cast<int64_t>(value);
                }

                """;
    }

    /** {@code extern "C"} soft-float helpers for {@code double}; see {@link #floatHelpers}. */
    static String doubleHelpers() {
        return """

                extern "C" JUNO_ASM_ABI double juno_dadd(double a, double b) { return a + b; }
                extern "C" JUNO_ASM_ABI double juno_dsub(double a, double b) { return a - b; }
                extern "C" JUNO_ASM_ABI double juno_dmul(double a, double b) { return a * b; }
                extern "C" JUNO_ASM_ABI double juno_ddiv(double a, double b) { return a / b; }
                extern "C" JUNO_ASM_ABI double juno_drem(double a, double b) { return fmod(a, b); }
                extern "C" JUNO_ASM_ABI int32_t juno_dcmp(double a, double b, int32_t nanResult) {
                  if (isnan(a) || isnan(b)) return nanResult;
                  return (a > b) - (a < b);
                }
                extern "C" JUNO_ASM_ABI double juno_i2d(int32_t value) { return static_cast<double>(value); }
                extern "C" JUNO_ASM_ABI int32_t juno_d2i(double value) {
                  if (isnan(value)) return 0;
                  if (value >= 0x1.0p31) return INT32_MAX;
                  if (value <= -0x1.0p31) return INT32_MIN;
                  return static_cast<int32_t>(value);
                }
                extern "C" JUNO_ASM_ABI double juno_l2d(int64_t value) { return static_cast<double>(value); }
                extern "C" JUNO_ASM_ABI int64_t juno_d2l(double value) {
                  if (isnan(value)) return 0;
                  if (value >= 0x1.0p63) return INT64_MAX;
                  if (value <= -0x1.0p63) return INT64_MIN;
                  return static_cast<int64_t>(value);
                }
                extern "C" JUNO_ASM_ABI double juno_f2d(float value) { return static_cast<double>(value); }
                extern "C" JUNO_ASM_ABI float juno_d2f(double value) { return static_cast<float>(value); }

                """;
    }
}

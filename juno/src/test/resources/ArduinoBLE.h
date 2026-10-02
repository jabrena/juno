#pragma once

// A host-side fake of the ArduinoBLE central API the generated LEGO Powered Up shim uses, mirroring
// the real library's signatures. Besides syntax checks, it plays one advertising LEGO hub whose
// behavior tests script through the junofake namespace: every characteristic write is recorded,
// and queued notifications reach the subscribed handler on BLE.poll(), as with the real library.

#include <stdint.h>
#include <string.h>

class BLEDevice;
class BLECharacteristic;

enum BLECharacteristicEvent {
    BLESubscribed = 0,
    BLEUnsubscribed = 1,
    BLEWritten = 3,
    BLEUpdated = BLEWritten,
};

typedef void (*BLECharacteristicEventHandler)(BLEDevice device, BLECharacteristic characteristic);

namespace junofake {
constexpr int kMaxMessages = 64;
constexpr int kMaxMessageLength = 32;

struct Message {
    uint8_t bytes[kMaxMessageLength];
    int length;
};

// Scan result: LEGO company id 0x0397, button state, system type id (0x41, a City Hub).
inline uint8_t manufacturerData[] = {0x97, 0x03, 0x00, 0x41, 0x06, 0x00, 0x41, 0x00};
inline int manufacturerDataLength = sizeof(manufacturerData);
inline bool connected = false;
inline bool subscribed = false;
inline BLECharacteristicEventHandler updatedHandler = nullptr;

inline Message written[kMaxMessages];
inline int writes = 0;

inline Message pending[kMaxMessages];
inline int pendingCount = 0;
inline Message value = {};

inline void notify(const uint8_t* bytes, int length) {
    memcpy(pending[pendingCount].bytes, bytes, length);
    pending[pendingCount].length = length;
    pendingCount++;
}
}  // namespace junofake

class BLECharacteristic {
public:
    BLECharacteristic() {}
    explicit BLECharacteristic(bool valid) : valid(valid) {}
    BLECharacteristic(const BLECharacteristic& other) : valid(other.valid) {}
    // The real class has no copy assignment that keeps its reference count right; make misuse fail to compile.
    BLECharacteristic& operator=(const BLECharacteristic&) = delete;
    virtual ~BLECharacteristic() {}

    const uint8_t* value() const { return junofake::value.bytes; }
    int valueLength() const { return junofake::value.length; }

    int writeValue(const uint8_t value[], int length, bool = true) {
        junofake::Message& message = junofake::written[junofake::writes++];
        memcpy(message.bytes, value, length);
        message.length = length;
        return 1;
    }

    void setEventHandler(int event, BLECharacteristicEventHandler handler) {
        if (event == BLEUpdated) {
            junofake::updatedHandler = handler;
        }
    }

    bool subscribe() {
        junofake::subscribed = true;
        return true;
    }

    operator bool() const { return valid; }

private:
    bool valid = false;
};

class BLEDevice {
public:
    BLEDevice() {}
    explicit BLEDevice(bool valid) : valid(valid) {}
    virtual ~BLEDevice() {}

    virtual bool connected() const { return valid && junofake::connected; }

    virtual bool disconnect() {
        junofake::connected = false;
        return true;
    }

    int manufacturerDataLength() const { return junofake::manufacturerDataLength; }

    int manufacturerData(uint8_t value[], int length) const {
        if (length > junofake::manufacturerDataLength) {
            length = junofake::manufacturerDataLength;
        }
        memcpy(value, junofake::manufacturerData, length);
        return length;
    }

    bool connect() {
        junofake::connected = valid;
        return valid;
    }

    bool discoverService(const char*) { return valid; }
    virtual operator bool() const { return valid; }
    BLECharacteristic characteristic(const char*) const { return BLECharacteristic(valid); }

private:
    bool valid = false;
};

class String {
public:
    String(const char*) {}
};

class BLELocalDevice {
public:
    virtual int begin() { return 1; }

    virtual void poll() {
        for (int i = 0; i < junofake::pendingCount; i++) {
            junofake::value = junofake::pending[i];
            if (junofake::connected && junofake::subscribed && junofake::updatedHandler != nullptr) {
                junofake::updatedHandler(BLEDevice(true), BLECharacteristic(true));
            }
        }
        junofake::pendingCount = 0;
    }

    virtual int scanForUuid(String, bool = false) { return 1; }
    virtual void stopScan() {}
    virtual BLEDevice available() { return BLEDevice(true); }
};

inline BLELocalDevice BLE;

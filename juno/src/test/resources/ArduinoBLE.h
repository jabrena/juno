#pragma once

#include <stdint.h>

class BLECharacteristic {
public:
    BLECharacteristic() {}
    BLECharacteristic(const BLECharacteristic&) {}
    virtual ~BLECharacteristic() {}
    int writeValue(const uint8_t[], int, bool = true) { return 1; }
    operator bool() const { return false; }
};

class BLEDevice {
public:
    BLEDevice() {}
    virtual ~BLEDevice() {}
    virtual bool connected() const { return false; }
    virtual bool disconnect() { return true; }
    int manufacturerDataLength() const { return 0; }
    int manufacturerData(uint8_t[], int length) const { return length; }
    bool connect() { return false; }
    bool discoverService(const char*) { return false; }
    virtual operator bool() const { return false; }
    BLECharacteristic characteristic(const char*) const { return BLECharacteristic(); }
};

class String {
public:
    String(const char*) {}
};

class BLELocalDevice {
public:
    virtual int begin() { return 1; }
    virtual void poll() {}
    virtual int scanForUuid(String, bool = false) { return 1; }
    virtual void stopScan() {}
    virtual BLEDevice available() { return BLEDevice(); }
};

inline BLELocalDevice BLE;

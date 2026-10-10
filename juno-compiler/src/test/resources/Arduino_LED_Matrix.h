#pragma once

#include <stdint.h>

class ArduinoLEDMatrix {
public:
    void begin() {}
    void loadFrame(const uint32_t[3]) {}
};

/** The UNO Q (zephyr core) names its 13x8, four-word matrix class differently from the UNO R4's. */
class Arduino_LED_Matrix {
public:
    int begin() { return 1; }
    void loadFrame(const uint32_t[4]) {}
};

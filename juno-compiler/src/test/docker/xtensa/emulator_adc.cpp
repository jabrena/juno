// Linked into the emulator build of the M5Stack Cardputer only (run.sh adds it with -Wl,--wrap=adc_hal_self_calibration).
// The ESP-IDF 5 core behind the Cardputer calibrates the ADC while the app starts: adc_calc_hw_calibration_code calls
// adc_hal_self_calibration, which busy-waits for an ADC conversion-done event. Espressif's QEMU does not model the
// ESP32-S3 ADC, so that event never comes and the boot hangs before setup(). The emulator therefore skips the
// calibration; the runtime programs under test do not use the ADC, and the hardware build is unaffected.
#include <stdbool.h>
#include <stdint.h>

extern "C" uint32_t __wrap_adc_hal_self_calibration(int adc_n, int atten, bool internal_gnd) {
    (void) adc_n;
    (void) atten;
    (void) internal_gnd;
    return 0;
}

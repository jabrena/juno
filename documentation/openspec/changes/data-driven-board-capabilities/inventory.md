# MCU getter inventory (task 1.1)

Recorded from the public getters of the five removed facades at `HEAD` and their `MCU` replacements.
"Declared support" is what the compiler's `Board` capability data grants after this change; "verified" records
hardware evidence from earlier sessions. `MCU` reaches the same native providers as before.

| Getter on `MCU` | Kind | Capability (`Board` data) | Boards that support it | Facades that used to expose it |
| --- | --- | --- | --- | --- |
| `getGpio` | portable | none | every board | all five |
| `getI2c` | portable | none | every board | all five |
| `getSpi` | portable | none | shared `spi.cpp` shim over the core `SPI` library; not hardware-verified on Nano, Pico, Cardputer or ESP32-CAM | ArduinoUno |
| `getClock` | portable | none | every board | all five |
| `getBoardModel` | portable | none | every board | ArduinoUno, ArduinoNano |
| `getBoardMemory` | portable | none | every board | ArduinoUno, ArduinoNano |
| `getWifi` | capability | `ONBOARD_WIFI` | UNO R4 WiFi, UNO WiFi Rev2, UNO Q, Nano ESP32, Cardputer, ESP32-CAM, Pico 2 W | ArduinoUno, ArduinoNano, RaspberryPico2, Esp32Cam, M5StackCardputer |
| `getBluetoothLe` | capability | `BLUETOOTH_LE` | UNO R4 WiFi, UNO Q, Nano ESP32, Cardputer, ESP32-CAM, Pico 2 W | all five |
| `getMouse` | capability | `USB_HID` | UNO R4 WiFi, Nano ESP32, Cardputer, Pico 2 W | ArduinoUno, ArduinoNano, RaspberryPico2, M5StackCardputer |
| `getLedMatrix` | capability | `LED_MATRIX` | UNO R4 WiFi, UNO Q | ArduinoUno |
| `getAccelerometer` | capability | `ONBOARD_IMU` | UNO WiFi Rev2 | ArduinoUno |
| `getGyroscope` | capability | `ONBOARD_IMU` | UNO WiFi Rev2 | ArduinoUno |
| `getCamera` | capability | `CAMERA` | AI Thinker ESP32-CAM | Esp32Cam |
| `getSdCard` | capability | `ONBOARD_SD_CARD` (new) | M5Stack Cardputer (chip select GPIO12), AI Thinker ESP32-CAM (GPIO13) | M5StackCardputer (`getSd`), Esp32Cam (`getSdCard`) |
| `getDisplay` | capability | `M5_DISPLAY` | M5Stack Cardputer | M5StackCardputer |
| `getKeyboard` | capability | `M5_KEYBOARD` | M5Stack Cardputer | M5StackCardputer |
| `getAudio` | capability | `M5_AUDIO` | M5Stack Cardputer | M5StackCardputer |
| `getBattery` | capability | `M5_BATTERY` | M5Stack Cardputer | M5StackCardputer |

Notes:

- The gated getters now resolve through the return type of the `MCU` method (`CapabilityRequirements.forMethod`), so no
  facade owner name is consulted. Two getters are stricter than before: `MCU.getMouse()` and `MCU.getLedMatrix()` are now
  rejected on a board without that capability even when no Mouse or LED-matrix method is called.
- `MCU.getSdCard()` is backed by `NativeOnboardSdCard`; its chip select comes from `Board.onboardSdChipSelect()` through the
  `BOARD_ONBOARD_SD_CHIP_SELECT` intrinsic, so the board's data is the only source of the pin.
- Shield- and carrier-owned `getSdCard()` methods are not `MCU` methods and remain ungated.

## Constructor call sites that passed a facade getter (task 2.6)

Grouped by what the argument is. "Converted" means the call site now uses a convenience constructor (task 2.7); the
injectable constructor remains public for mocks and alternate topology (task 2.8).

**MCU-owned singleton plumbing (converted):**

| Constructor | Former argument | Convenience form |
| --- | --- | --- |
| `GroveBaseShield(Gpio)`, `GrovePicoShield(Gpio)`, `NanoConnectorCarrier(Gpio)` | `MCU.getGpio()` | `()` |
| `GroveTemperatureV12(Gpio)`, `GrovePirMotion(Gpio)`, `GroveAnalogGas(Gpio)`, `GroveSoundV16(Gpio)` | `MCU.getGpio()` | `()` |
| `GroveLightV12(Gpio, int, int)` | `MCU.getGpio()` | `(int, int)` |
| `GroveGpsAir530(Gpio, Clock, int)` | `MCU.getGpio()`, `MCU.getClock()` | `(int)` (already existed) |
| `RcxLink(InfraredEmitter, InfraredReceiver, Clock)` | `MCU.getClock()` | `(InfraredEmitter, InfraredReceiver)` |
| `RcxRemoteControl(InfraredReceiver, Clock)` | `MCU.getClock()` | `(InfraredReceiver)` |
| `RemotePoweredUpHub(BluetoothLe, Clock)` | `MCU.getBluetoothLe()`, `MCU.getClock()` | `()` |
| `Bmi088(I2c, Clock)` | `MCU.getI2c()`, `MCU.getClock()` | `()` |
| `PwmServoShield(I2c, int)` | `MCU.getI2c()` | `(int)` |

**Real topology or a deliberate collaborator (retained):**

- Pins, addresses, buffers, and signal pins: every `new X(..., pin, ...)`.
- A clock or bus passed from a local variable rather than `MCU` directly, e.g. `new Bmi088(MCU.getI2c(), clock)` and
  `new RemotePoweredUpHub(MCU.getBluetoothLe(), clock)` in the NanoCarrier RCX and Powered Up examples. The local `clock`
  stays injected so that one timer is shared deliberately.
- `new Atecc608(MCU.getI2c(), slots)`: the secure element's bus is explicit beside its slot table.
- Example-internal constructors that receive `MCU.getClock()` as an argument (`MailboxClient`, `ProgressView`,
  `CoverView`, `WinView`, `ChallengeView`, `MailboxConnection`): they are application seams that tests inject.
- Test-only and documentation-only arguments such as `new Point(MCU.getGpio().analogRead(0) - 1000, 4)`.

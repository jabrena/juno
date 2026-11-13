package io.github.jabrena.juno.linker;

import io.github.jabrena.juno.board.Capability;
import io.github.jabrena.juno.classfile.MethodRef;
import io.github.jabrena.juno.intrinsic.Intrinsic;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Capability requirements resolved from central metadata")
class CapabilityRequirementsTest {
    private static final String MCU = "io/github/jabrena/juno/hardware/MCU";

    private final CapabilityRequirements requirements = new CapabilityRequirements();

    @ParameterizedTest(name = "MCU.{0}() requires {1}")
    @CsvSource({
            "getWifi, ONBOARD_WIFI, Lio/github/jabrena/juno/hardware/net/Wifi;",
            "getBluetoothLe, BLUETOOTH_LE, Lio/github/jabrena/juno/hardware/net/BluetoothLe;",
            "getMouse, USB_HID, Lio/github/jabrena/juno/hardware/hid/Mouse;",
            "getLedMatrix, LED_MATRIX, Lio/github/jabrena/juno/hardware/led/LedMatrix;",
            "getAccelerometer, ONBOARD_IMU, Lio/github/jabrena/juno/robotics/Accelerometer;",
            "getGyroscope, ONBOARD_IMU, Lio/github/jabrena/juno/robotics/Gyroscope;",
            "getCamera, CAMERA, Lio/github/jabrena/juno/hardware/device/camera/Camera;",
            "getSdCard, ONBOARD_SD_CARD, Lio/github/jabrena/juno/hardware/device/sd/SdCard;",
            "getDisplay, M5_DISPLAY, Lio/github/jabrena/juno/hardware/display/Display;",
            "getKeyboard, M5_KEYBOARD, Lio/github/jabrena/juno/hardware/keyboard/Keyboard;",
            "getAudio, M5_AUDIO, Lio/github/jabrena/juno/hardware/audio/Audio;",
            "getBattery, M5_BATTERY, Lio/github/jabrena/juno/hardware/power/Battery;"
    })
    void shouldRequireTheCapabilityOfEveryCapabilityBearingGetter(String getter, Capability capability,
                                                                  String returnType) {
        // When
        var required = requirements.forMethod(new MethodRef(MCU, getter, "()" + returnType));

        // Then
        assertThat(required).contains(capability);
    }

    @ParameterizedTest(name = "MCU.{0}() is portable")
    @CsvSource({
            "getGpio, Lio/github/jabrena/juno/hardware/gpio/Gpio;",
            "getI2c, Lio/github/jabrena/juno/hardware/i2c/I2c;",
            "getSpi, Lio/github/jabrena/juno/hardware/spi/Spi;",
            "getClock, Lio/github/jabrena/juno/hardware/time/Clock;",
            "getBoardModel, Lio/github/jabrena/juno/hardware/BoardModel;",
            "getBoardMemory, Lio/github/jabrena/juno/hardware/BoardMemory;"
    })
    void shouldNotRequireCapabilityForPortableGetters(String getter, String returnType) {
        assertThat(requirements.forMethod(new MethodRef(MCU, getter, "()" + returnType))).isEmpty();
    }

    @Test
    @DisplayName("Ordinary methods on other owners are never classified by owner name")
    void shouldIgnoreMethodsOutsideMcu() {
        var elsewhere = new MethodRef("io/github/jabrena/juno/util/Elsewhere", "getWifi",
                "()Lio/github/jabrena/juno/hardware/net/Wifi;");
        assertThat(requirements.forMethod(elsewhere)).isEmpty();
    }

    @Test
    @DisplayName("The onboard SD chip-select intrinsic requires the onboard SD capability")
    void shouldRequireOnboardSdForTheChipSelectIntrinsic() {
        assertThat(requirements.forIntrinsic(Intrinsic.BOARD_ONBOARD_SD_CHIP_SELECT))
                .contains(Capability.ONBOARD_SD_CARD);
    }

    @Test
    @DisplayName("A portable intrinsic has no requirement")
    void shouldNotRequireCapabilityForPortableIntrinsics() {
        assertThat(requirements.forIntrinsic(Intrinsic.GPIO_DIGITAL_WRITE)).isEmpty();
    }
}

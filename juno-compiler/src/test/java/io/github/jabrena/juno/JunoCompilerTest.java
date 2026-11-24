package io.github.jabrena.juno;


import io.github.jabrena.juno.backend.thumb2.Thumb2AsmBackend;
import io.github.jabrena.juno.board.Board;
import io.github.jabrena.juno.intrinsic.Intrinsic;
import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.ir.IrMethod;
import io.github.jabrena.juno.ir.IrProgram;
import io.github.jabrena.juno.ir.IrTerminator;
import io.github.jabrena.juno.linker.Program;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * End-to-end (Java source -&gt; javac -&gt; classfile -&gt; link -&gt; IR -&gt; {@link Thumb2AsmBackend})
 * compiler tests. Assertions target the generated assembly's real conventions rather than the retired
 * C++ backend's textual ones: a user method is a sequential {@code juno_fnN} label (never a
 * name-mangled symbol), a hardware/runtime intrinsic is a {@code bl <function>} call, a static field is
 * {@code juno_static_<Class>_<field>_<T>} in {@code .bss}, and a string literal is {@code juno_strN} in
 * {@code .rodata}. Every assertion below was checked against the real backend output for its fixture
 * (see the ASM/backend/Thumb2AsmBackend.java class doc) before being written.
 */
@DisplayName("End-to-end compiler: Java to Thumb-2 assembly")
class JunoCompilerTest {

    @Nested
    @DisplayName("Compilation and class model")
    class CompilationAndClassModel {
        @TempDir
        Path temporaryDirectory;

        @Test
        @DisplayName("Compiles assembly and the runtime shim through the compiler API")
        void shouldCompileAssemblyAndRuntimeShimThroughCompilerApi() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.hardware.MCU;
                    import io.github.jabrena.juno.hardware.gpio.Gpio;
                    public final class Blink {
                        public static void main(String[] args) {
                            Gpio gpio = MCU.getGpio();
                            gpio.pinMode(13, MCU.getGpio().getOutput());
                            gpio.digitalWrite(13, true);
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.Blink", source);
            Path assembly = temporaryDirectory.resolve("Blink.S");
            Path shim = temporaryDirectory.resolve("BlinkShim.cpp");

            // When
            CompilationResult result = new JunoCompiler().compileTo(
                    CompilerTestSupport.classpath(temporaryDirectory), "demo.Blink", assembly, shim, false,
                    Optional.of(Board.UNO_R4_WIFI.id()));

            // Then
            assertThat(result.entryPointSymbol()).isEqualTo("juno_Blink_asm");
            assertThat(result.assembly()).contains(".global juno_Blink_asm", "bl pinMode", "bl digitalWrite");
            assertThat(assembly).hasContent(result.assembly());
            assertThat(shim).hasContent(result.runtimeShim());
            assertThat(result.report().board().fqbn()).isEqualTo(Board.UNO_R4_WIFI.fqbn());
        }

        @Test
        @DisplayName("Compiles the static PSRAM API only on an ESP32 target and emits helpers once")
        void shouldCompileStaticPsramApiOnNanoEsp32() throws Exception {
            String source = """
                    package demo;
                    import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
                    import io.github.jabrena.juno.annotations.Board;
                    import io.github.jabrena.juno.hardware.Psram;
                    @Board(ArduinoNanoEsp32Board.class)
                    public final class NanoPsram {
                        public static void main(String[] args) {
                            int handle = Psram.allocate(32);
                            byte[] bytes = new byte[4];
                            Psram.write(handle, 0, bytes, 0, bytes.length);
                            Psram.read(handle, 0, bytes, 0, bytes.length);
                            Psram.release(handle);
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.NanoPsram", source);

            CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.NanoPsram");

            assertThat(result.runtimeShim()).contains("juno_psram_allocate", "juno_psram_read", "juno_psram_write")
                    .doesNotContain("NativePsram");
            assertThat(result.runtimeShim().split("juno_psram_allocate", -1)).hasSize(2);
        }

        @Test
        @DisplayName("Rejects the static PSRAM API on a board without PSRAM")
        void shouldRejectStaticPsramApiOnCardputer() throws Exception {
            String source = """
                    package demo;
                    import io.github.jabrena.juno.annotations.Board;
                    import io.github.jabrena.juno.annotations.M5StackCardputerBoard;
                    import io.github.jabrena.juno.hardware.Psram;
                    @Board(M5StackCardputerBoard.class)
                    public final class CardputerPsram {
                        public static void main(String[] args) {
                            Psram.allocate(32);
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.CardputerPsram", source);

            assertThatThrownBy(() -> CompilerTestSupport.compileJuno(temporaryDirectory, "demo.CardputerPsram"))
                    .hasMessageContaining("Psram");
        }

        @Test
        @DisplayName("Compiles symbolic GPIO lookup for the Cardputer")
        void shouldCompileSymbolicGpioLookupForCardputer() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.annotations.M5StackCardputerBoard;
                    import io.github.jabrena.juno.hardware.MCU;
                    import io.github.jabrena.juno.hardware.gpio.Gpio;
                    @io.github.jabrena.juno.annotations.Board(M5StackCardputerBoard.class)
                    public final class CardputerGpio {
                        public static void main(String[] args) {
                            Gpio gpio = MCU.getGpio();
                            int digital = gpio.digitalPin("D2");
                            int analog = gpio.analogPin("A0");
                            gpio.pinMode(digital, gpio.getOutput());
                            gpio.digitalWrite(digital, true);
                            gpio.analogRead(analog);
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.CardputerGpio", source);

            // When
            CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.CardputerGpio");

            // Then
            assertThat(result.assembly()).contains("juno_gpio_digital_pin", "juno_gpio_analog_pin");
            assertThat(result.runtimeShim()).contains("Invalid digital pin name", "Invalid analog pin name");
            assertThat(result.report().board()).isEqualTo(Board.M5_CARDPUTER);
        }

        @Test
        @DisplayName("Compiles programs against the board-independent motor contracts")
        void shouldCompileBoardIndependentMotorContracts() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.robotics.navigation.DifferentialDrive;
                    import io.github.jabrena.juno.robotics.navigation.DifferentialDriveOdometry;
                    import io.github.jabrena.juno.robotics.navigation.DriveGeometry;
                    import io.github.jabrena.juno.robotics.navigation.HeadingHoldController;
                    import io.github.jabrena.juno.robotics.navigation.Pose;
                    import io.github.jabrena.juno.robotics.Encoder;
                    import io.github.jabrena.juno.robotics.Gyroscope;
                    import io.github.jabrena.juno.robotics.RegulatedMotor;
                    import io.github.jabrena.juno.util.Delay;
                    public final class MotorContracts {
                        static final class TestMotor implements RegulatedMotor {
                            public void setPower(int percent) { }
                            public int getPower() { return 0; }
                            public void forward() { }
                            public void backward() { }
                            public void flt() { }
                            public void stop() { }
                            public void setSpeed(int degreesPerSecond) { }
                            public void setAcceleration(int degreesPerSecondSquared) { }
                            public int speed() { return 0; }
                            public void rotate(int degrees) { }
                            public void rotate(int degrees, boolean immediateReturn) { }
                            public void rotateTo(int limitAngle) { }
                            public void rotateTo(int limitAngle, boolean immediateReturn) { }
                            public int tachoCount() { return 7; }
                            public void resetTachoCount() { }
                            public boolean isMoving() { return false; }
                            public boolean isStalled() { return false; }
                            public void waitComplete() { }
                        }
                        static final class TestEncoder implements Encoder {
                            public int getTachoCount() { return 12; }
                            public void resetTachoCount() { }
                        }
                        static final class TestGyroscope implements Gyroscope {
                            public int getAngle() { return 90; }
                            public float getAngularVelocity() { return 0f; }
                            public void recalibrateOffset() { }
                            public void reset() { }
                        }
                        public static void main(String[] args) {
                            RegulatedMotor motor = new TestMotor();
                            Encoder encoder = new TestEncoder();
                            Gyroscope gyro = new TestGyroscope();
                            DifferentialDrive drive = new DifferentialDrive(motor, motor);
                            drive.drive(motor.tachoCount(), encoder.getTachoCount());
                            Pose pose = new Pose();
                            DriveGeometry geometry = new DriveGeometry(56, 120, 360, 360);
                            DifferentialDriveOdometry odometry = new DifferentialDriveOdometry(
                                    encoder, encoder, gyro, geometry, pose);
                            odometry.update();
                            HeadingHoldController heading = new HeadingHoldController(drive, pose, 20, 30);
                            heading.captureCurrentHeading();
                            heading.holdHeading(20);
                            Delay.millis(motor.tachoCount() + encoder.getTachoCount() + pose.headingMillidegrees());
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.MotorContracts", source);

            // When
            CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.MotorContracts");

            // Then
            assertThat(result.assembly()).contains("bl delay");
        }

        @Test
        @DisplayName("Compiles programs against the board-independent sensor contracts")
        void shouldCompileBoardIndependentSensorContracts() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.robotics.Accelerometer;
                    import io.github.jabrena.juno.robotics.Gyroscope;
                    import io.github.jabrena.juno.robotics.LightDetector;
                    import io.github.jabrena.juno.robotics.SampleProvider;
                    import io.github.jabrena.juno.robotics.SensorMode;
                    import io.github.jabrena.juno.robotics.SensorModes;
                    import io.github.jabrena.juno.util.Delay;
                    public final class SensorContracts {
                        static final class Level implements SensorMode {
                            public String getName() { return "Level"; }
                            public int sampleSize() { return 1; }
                            public void fetchSample(float[] sample, int offset) { sample[offset] = 0.5f; }
                        }
                        static final class TestSensor
                                implements SensorModes, LightDetector, Gyroscope, Accelerometer {
                            private final SensorMode level = new Level();
                            public String getName() { return level.getName(); }
                            public int sampleSize() { return level.sampleSize(); }
                            public void fetchSample(float[] sample, int offset) { level.fetchSample(sample, offset); }
                            public int getModeCount() { return 1; }
                            public SensorMode getMode(int mode) { return level; }
                            public SensorMode getMode(String modeName) { return level; }
                            public void setCurrentMode(int mode) { }
                            public void setCurrentMode(String modeName) { }
                            public int getCurrentMode() { return 0; }
                            public float getLightValue() { return 40f; }
                            public float getNormalizedLightValue() { return 0.4f; }
                            public float getHigh() { return 1f; }
                            public float getLow() { return 0f; }
                            public int getAngle() { return 3; }
                            public float getAngularVelocity() { return 1.5f; }
                            public void recalibrateOffset() { }
                            public void reset() { }
                            public int getXAccel() { return 1; }
                            public int getYAccel() { return 2; }
                            public int getZAccel() { return 9; }
                        }
                        public static void main(String[] args) {
                            float[] sample = new float[2];
                            TestSensor sensor = new TestSensor();
                            SensorModes modes = sensor;
                            SampleProvider provider = modes.getMode(0);
                            provider.fetchSample(sample, 1);
                            LightDetector light = sensor;
                            Gyroscope gyro = sensor;
                            Accelerometer accelerometer = sensor;
                            float total = sample[1] + light.getLightValue()
                                    + gyro.getAngularVelocity();
                            Delay.millis((int) total + gyro.getAngle() + accelerometer.getZAccel()
                                    + modes.getModeCount());
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.SensorContracts", source);

            // When
            CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.SensorContracts");

            // Then
            assertThat(result.assembly()).contains("bl delay");
        }

        @Test
        @DisplayName("Compiles Cardputer Wi-Fi access through MCU on the Cardputer")
        void shouldCompileCardputerWifiThroughM5StackCardputer() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.annotations.Board;
                                        import io.github.jabrena.juno.hardware.MCU;
                    import io.github.jabrena.juno.hardware.net.Wifi;
                    @Board(io.github.jabrena.juno.annotations.M5StackCardputerBoard.class)
                    public final class CardputerWifi {
                        public static void main(String[] args) {
                            Wifi wifi = MCU.getWifi();
                            wifi.beginAP("JunoCardputer", "juno1234");
                            wifi.status();
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.CardputerWifi", source);

            // When
            CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.CardputerWifi");

            // Then
            assertThat(result.report().board()).isEqualTo(Board.M5_CARDPUTER);
            assertThat(result.assembly()).contains("call8 juno_wifi_begin_ap", "call8 juno_wifi_status");
            assertThat(result.runtimeShim()).contains("#include <WiFi.h>", "WiFi.softAP");
        }

        @Test
        @DisplayName("Compiles Cardputer display access through MCU on the Cardputer")
        void shouldCompileCardputerDisplayThroughM5StackCardputer() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.annotations.Board;
                                        import io.github.jabrena.juno.hardware.MCU;
                    import io.github.jabrena.juno.hardware.display.Display;
                    @Board(io.github.jabrena.juno.annotations.M5StackCardputerBoard.class)
                    public final class CardputerDisplay {
                        public static void main(String[] args) {
                            Display display = MCU.getDisplay();
                            display.fillScreen(Display.BLACK);
                            display.drawPixel(1, 2, Display.RED);
                            display.fillRect(3, 4, 5, 6, Display.BLUE);
                            display.setCursor(8, 10);
                            display.setTextColor(Display.GREEN, Display.BLACK);
                            display.setTextSize(2);
                            display.print('x');
                            display.println("Hello, Juno!");
                            display.width();
                            display.height();
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.CardputerDisplay", source);

            // When
            CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.CardputerDisplay");

            // Then
            assertThat(result.report().board()).isEqualTo(Board.M5_CARDPUTER);
            assertThat(result.assembly()).contains(
                    "call8 juno_m5_display_fill_screen",
                    "call8 juno_m5_display_draw_pixel",
                    "call8 juno_m5_display_fill_rect",
                    "call8 juno_m5_display_set_cursor",
                    "call8 juno_m5_display_set_text_color",
                    "call8 juno_m5_display_set_text_size",
                    "call8 juno_m5_display_print_char",
                    "call8 juno_m5_display_println",
                    "call8 juno_m5_display_width",
                    "call8 juno_m5_display_height");
            assertThat(result.runtimeShim()).contains(
                    "#include <M5Cardputer.h>",
                    "M5Cardputer.begin(M5.config(), keyboard)",
                    "M5Cardputer.Display.fillScreen",
                    "M5Cardputer.Display.print(static_cast<char>(",
                    "M5Cardputer.Display.println");
            // The program never calls begin(): the first drawing call starts the Cardputer itself
            assertThat(result.runtimeShim()).contains(
                    "juno_m5_display_fill_screen(int32_t color) {\n  juno_m5_begin(false);");
        }

        @Test
        @DisplayName("Compiles Cardputer speaker tone playback through MCU on the Cardputer")
        void shouldCompileCardputerSpeakerToneThroughM5StackCardputer() throws Exception {
            String source = """
                    package demo;
                    import io.github.jabrena.juno.annotations.Board;
                    import io.github.jabrena.juno.hardware.MCU;
                    import io.github.jabrena.juno.hardware.audio.Audio;
                    @Board(io.github.jabrena.juno.annotations.M5StackCardputerBoard.class)
                    public final class CardputerAudio {
                        public static void main(String[] args) {
                            Audio audio = MCU.getAudio();
                            audio.setVolume(220);
                            audio.tone(440, 250);
                            audio.stop();
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.CardputerAudio", source);

            CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.CardputerAudio");

            assertThat(result.report().board()).isEqualTo(Board.M5_CARDPUTER);
            assertThat(result.assembly()).contains("call8 juno_m5_audio_set_volume", "call8 juno_m5_audio_tone",
                    "call8 juno_m5_audio_stop");
            assertThat(result.runtimeShim()).contains("#include <M5Cardputer.h>", "M5Cardputer.Speaker.setVolume",
                    "M5Cardputer.Speaker.tone", "M5Cardputer.Speaker.stop");
        }

        @Test
        @DisplayName("Compiles Cardputer battery readings through MCU on the Cardputer")
        void shouldCompileCardputerBatteryThroughM5StackCardputer() throws Exception {
            String source = """
                    package demo;
                    import io.github.jabrena.juno.annotations.Board;
                    import io.github.jabrena.juno.hardware.MCU;
                    import io.github.jabrena.juno.hardware.power.Battery;
                    @Board(io.github.jabrena.juno.annotations.M5StackCardputerBoard.class)
                    public final class CardputerBattery {
                        public static void main(String[] args) {
                            Battery battery = MCU.getBattery();
                            battery.levelPercent();
                            battery.voltageMillivolts();
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.CardputerBattery", source);

            CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.CardputerBattery");

            assertThat(result.report().board()).isEqualTo(Board.M5_CARDPUTER);
            assertThat(result.assembly()).contains("call8 juno_m5_battery_level_percent",
                    "call8 juno_m5_battery_voltage_millivolts");
            assertThat(result.runtimeShim()).contains("#include <M5Cardputer.h>",
                    "M5Cardputer.Power.getBatteryLevel()", "M5Cardputer.Power.getBatteryVoltage()");
        }

        @Test
        @DisplayName("Compiles Cardputer USB HID mouse access through MCU on the Cardputer")
        void shouldCompileCardputerMouseThroughM5StackCardputer() throws Exception {
            String source = """
                    package demo;
                    import io.github.jabrena.juno.annotations.Board;
                    import io.github.jabrena.juno.hardware.MCU;
                    import io.github.jabrena.juno.hardware.hid.Mouse;
                    @Board(io.github.jabrena.juno.annotations.M5StackCardputerBoard.class)
                    public final class CardputerMouse {
                        public static void main(String[] args) {
                            Mouse mouse = MCU.getMouse();
                            mouse.begin();
                            mouse.move(50, -50);
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.CardputerMouse", source);

            CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.CardputerMouse");

            assertThat(result.report().board()).isEqualTo(Board.M5_CARDPUTER);
            assertThat(result.assembly()).contains("call8 juno_mouse_begin", "call8 juno_mouse_move");
            assertThat(result.runtimeShim())
                    .contains("#include <USB.h>\n#include <USBHIDMouse.h>", "static JunoUsbMouse Mouse;",
                            "extern \"C\" void juno_mouse_move(int32_t x, int32_t y)")
                    .doesNotContain("#include <Mouse.h>");
        }

        @Test
        @DisplayName("Rejects the Cardputer display on another ESP32 board")
        void shouldRejectCardputerDisplayOnAnotherEsp32Board() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
                    import io.github.jabrena.juno.annotations.Board;
                    import io.github.jabrena.juno.hardware.MCU;
                    @Board(ArduinoNanoEsp32Board.class)
                    public final class NanoDisplay {
                        public static void main(String[] args) {
                            MCU.getDisplay().fillScreen(0);
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.NanoDisplay", source);

            // When & Then
            assertThatThrownBy(() -> CompilerTestSupport.compileJuno(temporaryDirectory, "demo.NanoDisplay"))
                    .isInstanceOf(CompileException.class)
                    .hasMessageContaining("MCU.getDisplay requires @Board(")
                    .hasMessageContaining("M5StackCardputerBoard");
        }

        @Test
        @DisplayName("Compiles MCU.getDisplay on the UNO R4 WiFi without linking the Cardputer display")
        void shouldCompileMcuDisplayAsTheLedMatrixOnTheUnoR4WiFi() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
                    import io.github.jabrena.juno.annotations.Board;
                    import io.github.jabrena.juno.hardware.MCU;
                    @Board(ArduinoUnoR4WiFiBoard.class)
                    public final class R4Display {
                        public static void main(String[] args) {
                            MCU.getDisplay().fillScreen(0);
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.R4Display", source);

            // When
            Program program = CompilerTestSupport.link(temporaryDirectory, "demo.R4Display");
            CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.R4Display");

            // Then
            assertThat(result.assembly()).isNotBlank();
            assertThat(program.methods()).noneMatch(method -> method.owner().name().equals(
                    "io/github/jabrena/juno/hardware/internal/NativeDisplay"));
            assertThat(program.methods()).anyMatch(method -> method.owner().name().equals(
                    "io/github/jabrena/juno/hardware/internal/NativeLedMatrix"));
        }

        @Test
        @DisplayName("Compiles Cardputer keyboard access through MCU on the Cardputer")
        void shouldCompileCardputerKeyboardThroughM5StackCardputer() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.annotations.Board;
                                        import io.github.jabrena.juno.hardware.MCU;
                    import io.github.jabrena.juno.hardware.keyboard.Keyboard;
                    @Board(io.github.jabrena.juno.annotations.M5StackCardputerBoard.class)
                    public final class CardputerKeyboard {
                        public static void main(String[] args) {
                            Keyboard keyboard = MCU.getKeyboard();
                            keyboard.begin();
                            keyboard.update();
                            if (keyboard.isChanged() && keyboard.isPressed()) {
                                keyboard.isKeyPressed('a');
                                keyboard.keyCount();
                                keyboard.keyAt(0);
                                keyboard.isShiftPressed();
                                keyboard.isControlPressed();
                                keyboard.isAltPressed();
                                keyboard.isFnPressed();
                                keyboard.isOptionPressed();
                            }
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.CardputerKeyboard", source);

            // When
            CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.CardputerKeyboard");

            // Then
            assertThat(result.report().board()).isEqualTo(Board.M5_CARDPUTER);
            assertThat(result.assembly()).contains(
                    "call8 juno_m5_keyboard_begin",
                    "call8 juno_m5_keyboard_update",
                    "call8 juno_m5_keyboard_is_changed",
                    "call8 juno_m5_keyboard_is_pressed",
                    "call8 juno_m5_keyboard_is_key_pressed",
                    "call8 juno_m5_keyboard_key_count",
                    "call8 juno_m5_keyboard_key_at",
                    "call8 juno_m5_keyboard_is_shift_pressed",
                    "call8 juno_m5_keyboard_is_control_pressed",
                    "call8 juno_m5_keyboard_is_alt_pressed",
                    "call8 juno_m5_keyboard_is_fn_pressed",
                    "call8 juno_m5_keyboard_is_option_pressed");
            assertThat(result.runtimeShim()).contains(
                    "#include <M5Cardputer.h>",
                    "M5Cardputer.Keyboard.begin()",
                    "M5Cardputer.Keyboard.updateKeyList()",
                    "M5Cardputer.Keyboard.keysState().shift");
        }

        @Test
        @DisplayName("Rejects the Cardputer keyboard on another ESP32 board")
        void shouldRejectCardputerKeyboardOnAnotherEsp32Board() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
                    import io.github.jabrena.juno.annotations.Board;
                    import io.github.jabrena.juno.hardware.MCU;
                    @Board(ArduinoNanoEsp32Board.class)
                    public final class NanoKeyboard {
                        public static void main(String[] args) {
                            MCU.getKeyboard().begin();
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.NanoKeyboard", source);

            // When & Then
            assertThatThrownBy(() -> CompilerTestSupport.compileJuno(temporaryDirectory, "demo.NanoKeyboard"))
                    .isInstanceOf(CompileException.class)
                    .hasMessageContaining("MCU.getKeyboard requires @Board(M5StackCardputerBoard.class)");
        }

        @Test
        @DisplayName("Compiles an unannotated basic program for the requested UNO R3")
        void shouldCompileAnUnannotatedBasicProgramForTheRequestedUnoR3() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.hardware.MCU;
                    import io.github.jabrena.juno.util.Delay;
                    import io.github.jabrena.juno.hardware.gpio.Gpio;
                    public final class R3Blink {
                        public static void main(String[] args) {
                            MCU.getGpio().pinMode(MCU.getGpio().getBuiltinLed(), MCU.getGpio().getOutput());
                            MCU.getGpio().digitalWrite(MCU.getGpio().getBuiltinLed(), true);
                            Delay.millis(500);
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.R3Blink", source);

            // When
            CompilationResult result = new JunoCompiler().compile(CompilerTestSupport.classpath(temporaryDirectory),
                    "demo.R3Blink", false, Optional.of(Board.UNO_R3.id()));

            // Then
            assertThat(result.report().board()).isEqualTo(io.github.jabrena.juno.board.Board.UNO_R3);
            assertThat(result.assembly()).contains("; Generated by Juno's AVR assembly backend", "ldi r22, 13");
            assertThat(result.runtimeShim()).doesNotContain("WiFiNINA", "Arduino_LED_Matrix");
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"arduino-uno-r4-wifi", "arduino-nano-esp32", "arduino-uno-r3"})
        @DisplayName("Compiles the dup family javac emits for field post-increments and array compound assignment")
        void shouldCompileTheDupFamilyOnEveryBackend(String board) throws Exception {
            // Given: next++ as a value (dup_x1), a[i] += x (dup2), a[i]++ as a value (dup_x2), x = f = v (dup_x1)
            String source = """
                    package demo;
                    import io.github.jabrena.juno.hardware.serial.Serial;
                    public final class Dups {
                        private int next;
                        private int mirror;
                        public static void main(String[] args) {
                            Dups dups = new Dups();
                            int[] values = {1, 2, 3};
                            values[dups.next++] += 10;
                            int before = values[dups.next]++;
                            int copy = dups.mirror = before;
                            Serial.println(values[0] + values[1] + copy + dups.next);
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.Dups", source);

            // When
            CompilationResult result = new JunoCompiler().compile(CompilerTestSupport.classpath(temporaryDirectory),
                    "demo.Dups", false, Optional.of(board));

            // Then
            assertThat(result.report().board().id()).isEqualTo(board);
            assertThat(result.assembly()).isNotBlank();
        }

        @Test
        @DisplayName("Lowers the JVM-backed Delay methods directly to board timing intrinsics")
        void shouldLowerTheJvmBackedDelayMethodsToBoardTimingIntrinsics() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.util.Delay;
                    public final class TimedProgram {
                        public static void main(String[] args) {
                            Delay.millis(2);
                            Delay.micros(3);
                            Delay.seconds(0);
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.TimedProgram", source);

            // When
            CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.TimedProgram");

            // Then
            assertThat(result.assembly()).contains("bl delay", "bl delayMicroseconds");
            assertThat(result.runtimeShim()).doesNotContain("juno_thread_sleep");
        }

        @Test
        @DisplayName("Allocates a non-final class that nothing extends")
        void shouldAllocateANonFinalClassThatNothingExtends() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.hardware.MCU;
                    import io.github.jabrena.juno.hardware.gpio.Gpio;
                    public class NonFinalApp {
                        public static void main(String[] args) {
                            NonFinalApp app = new NonFinalApp();
                            app.run();
                        }
                        private void run() {
                            MCU.getGpio().pinMode(13, MCU.getGpio().getOutput());
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.NonFinalApp", source);

            // When
            CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.NonFinalApp");

            // Then
            assertThat(result.assembly()).contains("bl pinMode");
        }

        @Test
        @DisplayName("Supports inherited classes and virtual method dispatch")
        void shouldSupportInheritedClassesAndVirtualMethodDispatch() throws Exception {
            // Given
            String source = """
                    package demo;
                    public class Extended {
                        public static void main(String[] args) {
                            Extended first = new Sub();
                            Extended second = new Other();
                            if (first.run() + second.run() != 8) {
                                throw new IllegalStateException();
                            }
                        }
                        private int value;
                        Extended(int value) {
                            this.value = value;
                        }
                        int run() {
                            return value;
                        }
                    }
                    class Sub extends Extended {
                        Sub() {
                            super(2);
                        }
                        @Override
                        int run() {
                            return super.run() + 1;
                        }
                    }
                    class Other extends Extended {
                        Other() {
                            super(3);
                        }
                        @Override
                        int run() {
                            return super.run() + 2;
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.Extended", source);

            // When
            CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Extended");

            // Then
            assertThat(result.assembly()).contains("cmp r0, r1", "bl juno_fn");
        }

        @Test
        @DisplayName("Supports abstract bases with concrete virtual implementations")
        void shouldSupportAbstractBaseClasses() throws Exception {
            // Given
            String source = """
                    package demo;
                    public class AbstractBase {
                        public static void main(String[] args) {
                            Base value = new Concrete();
                            if (value.value() != 7) {
                                throw new IllegalStateException();
                            }
                        }
                        abstract static class Base {
                            abstract int value();
                        }
                        static final class Concrete extends Base {
                            @Override
                            int value() {
                                return 7;
                            }
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.AbstractBase", source);

            // When
            CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.AbstractBase");

            // Then
            assertThat(result.assembly()).isNotBlank();
        }

        @Test
        @DisplayName("Supports class inheritance on the classic UNO R3")
        void shouldSupportClassInheritanceOnClassicUnoR3() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.annotations.ArduinoUnoR3Board;
                    import io.github.jabrena.juno.annotations.Board;
                    @Board(ArduinoUnoR3Board.class)
                    public class R3Inheritance {
                        public static void main(String[] args) {
                            Base value = new Child();
                            value.run();
                        }
                        static class Base {
                            int run() {
                                return 1;
                            }
                        }
                        static class Child extends Base {
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.R3Inheritance", source);

            // When
            CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.R3Inheritance");

            // Then
            assertThat(result.assembly()).isNotBlank();
        }

        @Test
        @DisplayName("Supports an inherited interface default method")
        void shouldSupportInheritedInterfaceDefaultMethod() throws Exception {
            // Given
            String source = """
                    package demo;
                    public class DefaultInterface {
                        public static void main(String[] args) {
                            Greeting greeting = new GreetingImpl();
                            if (greeting.value() != 7) {
                                throw new IllegalStateException();
                            }
                        }
                        interface Greeting {
                            default int value() {
                                return 7;
                            }
                        }
                        static final class GreetingImpl implements Greeting {
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.DefaultInterface", source);

            // When
            Program program = CompilerTestSupport.link(temporaryDirectory, "demo.DefaultInterface");
            CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.DefaultInterface");

            // Then
            assertThat(result.assembly()).isNotBlank();
            assertThat(program.interfaceDispatches().values()).anySatisfy(dispatch ->
                    assertThat(dispatch.targets()).anySatisfy(target ->
                            assertThat(target.method().owner()).isEqualTo("demo/DefaultInterface$Greeting")));
        }

        @Test
        @DisplayName("Dispatches Display and TouchScreen defaults on a class that implements both interfaces")
        void shouldDispatchDefaultsOfTwoInterfacesOnOneClass() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.hardware.display.Display;
                    import io.github.jabrena.juno.hardware.touch.TouchScreen;
                    public class TwoInterfaces {
                        public static void main(String[] args) {
                            Canvas canvas = new Canvas();
                            Display display = canvas;
                            TouchScreen touch = canvas;
                            display.drawHorizontalLine(0, 0, 3, Display.WHITE);
                            display.fillCircle(0, 0, 1, Display.WHITE);
                            if (canvas.lit != 8 || !touch.readTouch() || touch.touchX() != 7) {
                                throw new IllegalStateException();
                            }
                        }
                        static final class Canvas implements Display, TouchScreen {
                            int lit;
                            public int width() { return 12; }
                            public int height() { return 8; }
                            public void fillScreen(int color) { }
                            public void drawPixel(int x, int y, int color) { lit = lit + 1; }
                            public void fillRect(int x, int y, int w, int h, int color) { lit = lit + w * h; }
                            public void setCursor(int x, int y) { }
                            public void setTextColor(int foreground, int background) { }
                            public void setTextSize(int size) { }
                            public void print(char character) { }
                            public void print(String text) { }
                            public void println(String text) { }
                            public boolean readTouch() { return true; }
                            public int touchX() { return 7; }
                            public int touchY() { return 3; }
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.TwoInterfaces", source);

            // When
            Program program = CompilerTestSupport.link(temporaryDirectory, "demo.TwoInterfaces");
            CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.TwoInterfaces");

            // Then
            assertThat(result.assembly()).isNotBlank();
            assertThat(program.interfaceDispatches().values()).anySatisfy(dispatch ->
                    assertThat(dispatch.targets()).anySatisfy(target ->
                            assertThat(target.method().owner()).isEqualTo("io/github/jabrena/juno/hardware/display/Display")));
        }

        @Test
        @DisplayName("Specializes Clojure deftype erased reference fields")
        void shouldSpecializeClojureDeftypeErasedReferenceFields() throws Exception {
            // Given
            CompilerTestSupport.compileJava(temporaryDirectory, "clojure.lang.IType", """
                    package clojure.lang;
                    public interface IType {
                    }
                    """);
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.ClojureState", """
                    package demo;
                    import clojure.lang.IType;
                    import java.util.concurrent.atomic.AtomicInteger;
                    import java.util.concurrent.locks.ReentrantLock;
                    public final class ClojureState implements IType {
                        private final Object lock;
                        private final Object counter;
                        ClojureState(Object lock, Object counter) {
                            this.lock = lock;
                            this.counter = counter;
                        }
                        private void increment() {
                            AtomicInteger typedCounter = (AtomicInteger) counter;
                            ReentrantLock typedLock = (ReentrantLock) lock;
                            typedCounter.incrementAndGet();
                            typedLock.lock();
                            if (typedLock.tryLock()) {
                                typedLock.unlock();
                            }
                            typedLock.unlock();
                        }
                        public static void main() {
                            new ClojureState(new ReentrantLock(), new AtomicInteger()).increment();
                        }
                    }
                    """);

            // When
            CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.ClojureState");

            // Then
            assertThat(result.assembly()).contains(
                    "bl juno_atomic_int_increment_and_get",
                    "bl juno_monitor_enter",
                    "bl juno_monitor_try_enter",
                    "bl juno_monitor_exit");
        }

        @Test
        @DisplayName("Still rejects erased reference fields on ordinary Java classes")
        void shouldStillRejectErasedReferenceFieldsOnOrdinaryJavaClasses() throws Exception {
            // Given
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.ErasedState", """
                    package demo;
                    import java.util.concurrent.locks.ReentrantLock;
                    public final class ErasedState {
                        private final Object lock;
                        ErasedState(Object lock) {
                            this.lock = lock;
                        }
                        public static void main() {
                            new ErasedState(new ReentrantLock());
                        }
                    }
                    """);

            // When & Then
            assertThatThrownBy(() -> CompilerTestSupport.compileJuno(temporaryDirectory, "demo.ErasedState"))
                    .isInstanceOf(CompileException.class)
                    .hasMessageContaining("supported scalar, array, or closed-world reference");
        }

        @Test
        @DisplayName("Lowers Gpio.digitalRead to the core function")
        void shouldLowerGpioDigitalReadToTheCoreFunction() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.hardware.MCU;
                    import io.github.jabrena.juno.hardware.gpio.Gpio;
                    public final class Bumper {
                        public static void main(String[] args) {
                            MCU.getGpio().pinMode(4, MCU.getGpio().getInputPullup());
                            if (!MCU.getGpio().digitalRead(4)) {
                                MCU.getGpio().pinMode(13, MCU.getGpio().getOutput());
                            }
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.Bumper", source);

            // When
            CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Bumper");

            // Then
            assertThat(result.assembly()).contains("bl digitalRead");
        }

        @Test
        @DisplayName("Ignores a package-info shared with Juno's own package")
        void shouldIgnorePackageInfoSharedWithJunosOwnPackage() throws Exception {
            // Given
            // juno-compiler's own target/classes already holds io/github/jabrena/juno/package-info.class.
            CompilerTestSupport.compileJava(temporaryDirectory, "io.github.jabrena.juno.package-info",
                    "/** Example programs. */\npackage io.github.jabrena.juno;\n");
            String source = """
                    package io.github.jabrena.juno;
                    import io.github.jabrena.juno.util.Delay;
                    public final class SharedPackageMain {
                        public static void main(String[] args) {
                            Delay.millis(1);
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "io.github.jabrena.juno.SharedPackageMain", source);

            // When
            CompilationResult result = CompilerTestSupport.compileJuno(
                    temporaryDirectory, "io.github.jabrena.juno.SharedPackageMain");

            // Then
            assertThat(result.assembly()).contains("bl delay");
        }

        @Test
        @DisplayName("The watchdog annotation enables the hardware watchdog with its explicit timeout")
        void shouldEnableTheHardwareWatchdogWithItsExplicitTimeout() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
                    import io.github.jabrena.juno.annotations.Board;
                    import io.github.jabrena.juno.annotations.Watchdog;
                    import io.github.jabrena.juno.util.Delay;
                    @Board(ArduinoUnoR4WiFiBoard.class)
                    @Watchdog(timeoutMillis = 3000)
                    public final class WatchdogExplicit {
                        public static void main(String[] args) {
                            Delay.millis(10);
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.WatchdogExplicit", source);
            Path assembly = temporaryDirectory.resolve("WatchdogExplicit.S");
            Path shim = temporaryDirectory.resolve("WatchdogExplicitShim.cpp");

            // When
            CompilationResult result = new JunoCompiler().compileTo(
                    CompilerTestSupport.classpath(temporaryDirectory), "demo.WatchdogExplicit", assembly, shim);

            // Then
            // The exact prologue sequence (stack-top capture, stack zeroing, then push) is covered
            // precisely by Thumb2AsmBackendTest; this just confirms the entry point is real and the
            // watchdog gets started.
            assertThat(result.assembly())
                    .contains("juno_WatchdogExplicit_asm:\n    ldr r0, =juno_gc_stack_top", "bl juno_watchdog_begin");
            assertThat(result.runtimeShim()).contains(
                    "#include <WDT.h>",
                    "WDT.refresh();",
                    "WDT.begin(3000u);",
                    "extern \"C\" void juno_watchdog_begin()",
                    "[juno-watchdog] panic: board will reset via watchdog in 3000ms",
                    "noInterrupts();\n  for (;;) {}");
        }

        @Test
        @DisplayName("The M5Stack Cardputer's ESP-IDF 5 core starts the task watchdog from a configuration structure")
        void shouldStartTheTaskWatchdogWithTheConfigurationStructureOnTheM5StackCardputer() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.annotations.Board;
                    import io.github.jabrena.juno.annotations.M5StackCardputerBoard;
                    import io.github.jabrena.juno.annotations.Watchdog;
                    import io.github.jabrena.juno.util.Delay;
                    @Board(M5StackCardputerBoard.class)
                    @Watchdog(timeoutMillis = 3000)
                    public final class WatchdogCardputer {
                        public static void main(String[] args) {
                            Delay.millis(10);
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.WatchdogCardputer", source);
            Path assembly = temporaryDirectory.resolve("WatchdogCardputer.S");
            Path shim = temporaryDirectory.resolve("WatchdogCardputerShim.cpp");

            // When
            CompilationResult result = new JunoCompiler().compileTo(
                    CompilerTestSupport.classpath(temporaryDirectory), "demo.WatchdogCardputer", assembly, shim);

            // Then
            assertThat(result.assembly()).contains("call8 juno_watchdog_begin");
            assertThat(result.runtimeShim()).contains(
                    "#include <esp_task_wdt.h>",
                    "esp_task_wdt_config_t config = {3000u, 0u, true};",
                    "if (esp_task_wdt_init(&config) == ESP_ERR_INVALID_STATE) esp_task_wdt_reconfigure(&config);",
                    "esp_task_wdt_add(nullptr);",
                    "esp_task_wdt_reset();")
                    .doesNotContain("esp_task_wdt_init(3u, true);");
        }

        @Test
        @DisplayName("The Nano ESP32's Arduino-ESP32 2.x core keeps the task watchdog timeout in seconds")
        void shouldKeepTheSecondsTaskWatchdogOnTheNanoEsp32() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
                    import io.github.jabrena.juno.annotations.Board;
                    import io.github.jabrena.juno.annotations.Watchdog;
                    import io.github.jabrena.juno.util.Delay;
                    @Board(ArduinoNanoEsp32Board.class)
                    @Watchdog(timeoutMillis = 3000)
                    public final class WatchdogNano {
                        public static void main(String[] args) {
                            Delay.millis(10);
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.WatchdogNano", source);
            Path assembly = temporaryDirectory.resolve("WatchdogNano.S");
            Path shim = temporaryDirectory.resolve("WatchdogNanoShim.cpp");

            // When
            CompilationResult result = new JunoCompiler().compileTo(
                    CompilerTestSupport.classpath(temporaryDirectory), "demo.WatchdogNano", assembly, shim);

            // Then
            assertThat(result.runtimeShim()).contains("esp_task_wdt_init(3u, true);", "esp_task_wdt_add(nullptr);")
                    .doesNotContain("esp_task_wdt_config_t");
            // The task watchdog fires from an interrupt, so the ESP32 panic halt must leave interrupts on.
            assertThat(result.runtimeShim()).contains("[[noreturn]] void juno_panic()", "for (;;) {}")
                    .doesNotContain("noInterrupts();\n  for (;;) {}");
        }


        @Test
        @DisplayName("A watchdog annotation without an explicit timeout uses its declared default")
        void shouldUseTheDeclaredDefaultWatchdogTimeoutWhenNoneIsGiven() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
                    import io.github.jabrena.juno.annotations.Board;
                    import io.github.jabrena.juno.annotations.Watchdog;
                    import io.github.jabrena.juno.util.Delay;
                    @Board(ArduinoUnoR4WiFiBoard.class)
                    @Watchdog
                    public final class WatchdogDefault {
                        public static void main(String[] args) {
                            Delay.millis(10);
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.WatchdogDefault", source);
            Path assembly = temporaryDirectory.resolve("WatchdogDefault.S");
            Path shim = temporaryDirectory.resolve("WatchdogDefaultShim.cpp");

            // When
            CompilationResult result = new JunoCompiler().compileTo(
                    CompilerTestSupport.classpath(temporaryDirectory), "demo.WatchdogDefault", assembly, shim);

            // Then
            assertThat(result.runtimeShim()).contains("WDT.begin(5000u);");
        }

        @Test
        @DisplayName("A program without a watchdog annotation emits no watchdog code at all")
        void shouldEmitNoWatchdogCodeWithoutTheWatchdogAnnotation() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.util.Delay;
                    public final class NoWatchdog {
                        public static void main(String[] args) {
                            Delay.millis(10);
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.NoWatchdog", source);
            Path assembly = temporaryDirectory.resolve("NoWatchdog.S");
            Path shim = temporaryDirectory.resolve("NoWatchdogShim.cpp");

            // When
            CompilationResult result = new JunoCompiler().compileTo(
                    CompilerTestSupport.classpath(temporaryDirectory), "demo.NoWatchdog", assembly, shim, false,
                    Optional.of(Board.UNO_R4_WIFI.id()));

            // Then
            assertThat(result.assembly()).doesNotContain("juno_watchdog_begin");
            // "watchdog" alone would also match juno_panic()'s always-present explanatory comment about
            // *why* its diagnostic print has to come before noInterrupts(); check the actual emitted
            // code/identifiers instead of that prose.
            assertThat(result.runtimeShim()).doesNotContain(
                    "#include <WDT.h>", "WDT.begin", "WDT.refresh", "juno_watchdog_begin",
                    "[juno-watchdog]");
        }

        @ParameterizedTest
        @CsvSource({"ArduinoUnoR4WiFiBoard, 0", "ArduinoPortentaH7Board, 1"})
        @DisplayName("Folds a BoardModel query through Arduino to the target board's constant")
        void shouldFoldABoardModelQueryThroughMcuToAConstant(String board, int isPortenta) throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.annotations.%1$s;
                    import io.github.jabrena.juno.annotations.Board;
                    import io.github.jabrena.juno.hardware.BoardModel;
                    import io.github.jabrena.juno.hardware.MCU;
                    import io.github.jabrena.juno.util.Delay;
                    @Board(%1$s.class)
                    public final class Which {
                        public static void main(String[] args) {
                            if (MCU.getBoardModel() == BoardModel.PORTENTA_H7) {
                                Delay.millis(111);
                            }
                        }
                    }
                    """.formatted(board);
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.Which", source);

            // When
            CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Which");

            // Then
            if (isPortenta == 1) {
                assertThat(result.assembly()).contains("#111");
            } else {
                assertThat(result.assembly()).doesNotContain("#111");
            }
        }

        @ParameterizedTest
        @CsvSource({"ArduinoUnoR4WiFiBoard, false", "ArduinoPortentaH7Board, true"})
        @DisplayName("Drops the branch its target can never take, so that side's shim features go with it")
        void shouldDropTheBranchTheTargetCannotTake(String board, boolean isPortenta) throws Exception {
            // Given: the I2C bus is used only where the board is the Portenta, as MCU.getSecretStore() uses the SE050C2 only
            // on the Portenta. The query is answered when the program is linked, so the other side is not compiled.
            String source = """
                    package demo;
                    import io.github.jabrena.juno.annotations.%1$s;
                    import io.github.jabrena.juno.annotations.Board;
                    import io.github.jabrena.juno.hardware.BoardModel;
                    import io.github.jabrena.juno.hardware.MCU;
                    import io.github.jabrena.juno.util.Delay;
                    @Board(%1$s.class)
                    public final class Which {
                        public static void main(String[] args) {
                            if (MCU.getBoardModel() == BoardModel.PORTENTA_H7) {
                                MCU.getI2c().begin();
                            } else {
                                Delay.millis(222);
                            }
                        }
                    }
                    """.formatted(board);
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.Which", source);

            // When
            CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Which");

            // Then
            if (isPortenta) {
                assertThat(result.runtimeShim()).contains("#include <Wire.h>", "juno_i2c_begin");
            } else {
                assertThat(result.runtimeShim()).doesNotContain("#include <Wire.h>", "juno_i2c_begin");
            }
        }

        @ParameterizedTest
        @CsvSource({
            "ArduinoNanoEsp32Board, juno_nvs_read, juno_se050_read",
            "ArduinoPortentaH7Board, juno_se050_read, juno_nvs_read",
            "RaspberryPico2WifiBoard, juno_pico_tz_read, juno_nvs_read",
            "ArduinoUnoR4WiFiBoard, juno_nvs_read, juno_se050_read",
            "ArduinoUnoR3Board, juno_eeprom_read, juno_nvs_read"})
        @DisplayName("MCU.getSecretStore() compiles to the one store its target board has, and is rejected on a board with none")
        void shouldCompileOnlyTheTargetBoardsSecretStore(String board, String present, String absent)
                throws Exception {
            // Given: the factory asks one board query and constructs one of two providers, or throws
            String source = """
                    package demo;
                    import io.github.jabrena.juno.annotations.%1$s;
                    import io.github.jabrena.juno.annotations.Board;
                    import io.github.jabrena.juno.hardware.MCU;
                    import io.github.jabrena.juno.hardware.security.SecretStore;
                    import io.github.jabrena.juno.hardware.serial.Serial;
                    @Board(%1$s.class)
                    public final class Secrets {
                        public static void main(String[] args) {
                            SecretStore store = MCU.getSecretStore();
                            Serial.println(store.read("wifi.ssid", new byte[63]));
                        }
                    }
                    """.formatted(board);
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.Secrets", source);

            // When
            CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Secrets");

            // Then: the provider the board does not have is neither called nor given shim code
            assertThat(result.assembly()).contains(present).doesNotContain(absent);
        }

        @ParameterizedTest
        @CsvSource({
            "'URI.create(\"http://example.com/status\")', juno_http_request_text, juno_https_request_text",
            "'URI.create(\"https://example.com/status\")', juno_https_request_text, juno_http_request_text",
            // The JDK constructor names its scheme just as URI.create does.
            "'new URI(\"http://example.com/status\")', juno_http_request_text, juno_https_request_text",
            "'new URI(\"https://example.com/status\")', juno_https_request_text, juno_http_request_text",
            // A URI the linker cannot read could be either scheme, so both requests stay.
            "'URI.create(address())', juno_http_request_text, juno_https_request_text",
            "'new URI(address())', juno_http_request_text, juno_https_request_text",
            // A scheme names its request in any case, as URI reads it.
            "'URI.create(\"HTTPS://example.com/status\")', juno_https_request_text, juno_http_request_text"})
        @DisplayName("A program written against java.net.http links only the request shim its URIs' scheme needs")
        void shouldCompileTheJdkHttpClientSyntax(String uri, String present, String other) throws Exception {
            // Given: javac resolves java.net.http to the JDK; Juno reads the board-side classes under those names
            boolean bothSchemes = uri.contains("address()");
            String source = """
                    package demo;
                    import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
                    import io.github.jabrena.juno.annotations.Board;
                    import io.github.jabrena.juno.hardware.serial.Serial;
                    import java.net.URI;
                    import java.net.http.HttpClient;
                    import java.net.http.HttpRequest;
                    import java.net.http.HttpResponse;
                    @Board(ArduinoNanoEsp32Board.class)
                    public final class JdkHttp {
                        public static void main(String[] args) throws Exception {
                            HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
                            HttpRequest request = HttpRequest.newBuilder()
                                    .uri(%s).GET().build();
                            HttpResponse<byte[]> reply = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
                            Serial.println(reply.statusCode());
                            Serial.println(reply.body().length);
                        }

                        private static String address() {
                            return "http://example.com/status";
                        }
                    }
                    """.formatted(uri);
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.JdkHttp", source);

            // When
            CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.JdkHttp");

            // Then: the request is sent through the board's own buffered request, and only for the schemes in use
            assertThat(result.assembly()).contains(present);
            if (bothSchemes) {
                assertThat(result.assembly()).contains(other);
            } else {
                assertThat(result.assembly()).doesNotContain(other);
            }
        }

        @ParameterizedTest
        @CsvSource({
            "ofString, String, 'length()', BodyHandlers_StringHandler_INSTANCE, BodyHandlers_ByteArrayHandler_INSTANCE",
            "ofByteArray, byte[], length, BodyHandlers_ByteArrayHandler_INSTANCE, BodyHandlers_StringHandler_INSTANCE"})
        @DisplayName("A java.net.http program links only the body handler it asks for")
        void shouldLinkOnlyTheBodyHandlerInUse(String handler, String bodyType, String length, String present,
                String absent) throws Exception {
            // Given: the handlers live in their own classes, so the one never asked for is never instantiated
            String source = """
                    package demo;
                    import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
                    import io.github.jabrena.juno.annotations.Board;
                    import io.github.jabrena.juno.hardware.serial.Serial;
                    import java.net.URI;
                    import java.net.http.HttpClient;
                    import java.net.http.HttpRequest;
                    import java.net.http.HttpResponse;
                    @Board(ArduinoUnoR4WiFiBoard.class)
                    public final class JdkHttpBody {
                        public static void main(String[] args) throws Exception {
                            HttpClient client = HttpClient.newHttpClient();
                            HttpRequest request = HttpRequest.newBuilder(URI.create("http://example.com/")).build();
                            HttpResponse<%2$s> reply = client.send(request, HttpResponse.BodyHandlers.%1$s());
                            %2$s body = reply.body();
                            Serial.println(body.%3$s);
                        }
                    }
                    """.formatted(handler, bodyType, length);
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.JdkHttpBody", source);

            // When
            CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.JdkHttpBody");

            // Then: a handler's static instance is emitted only when its class, and so its response code, is linked
            assertThat(result.assembly()).contains(present).doesNotContain(absent);
        }

        @Test
        @DisplayName("A java.net.http POST with headers, timeouts and redirects compiles, without the task runtime")
        void shouldCompileAJdkHttpPostWithHeadersTimeoutsAndRedirects() throws Exception {
            // Given: every request feature at once, read back through headers() and Optional
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.JdkHttpPost", """
                    package demo;
                    import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
                    import io.github.jabrena.juno.annotations.Board;
                    import io.github.jabrena.juno.hardware.serial.Serial;
                    import java.net.URI;
                    import java.net.http.HttpClient;
                    import java.net.http.HttpRequest;
                    import java.net.http.HttpResponse;
                    import java.time.Duration;
                    @Board(ArduinoUnoR4WiFiBoard.class)
                    public final class JdkHttpPost {
                        public static void main(String[] args) throws Exception {
                            HttpClient client = HttpClient.newBuilder()
                                    .followRedirects(HttpClient.Redirect.NORMAL)
                                    .connectTimeout(Duration.ofSeconds(3))
                                    .build();
                            HttpRequest request = HttpRequest.newBuilder(URI.create("http://example.com/post"))
                                    .header("Accept", "application/json")
                                    .timeout(Duration.ofMillis(4000))
                                    .POST(HttpRequest.BodyPublishers.ofString("{\\"board\\":1}"))
                                    .build();
                            HttpResponse<String> reply = client.send(request, HttpResponse.BodyHandlers.ofString());
                            Serial.println(reply.statusCode());
                            Serial.println(reply.headers().firstValue("content-type").orElse("none"));
                            Serial.println((int) reply.headers().firstValueAsLong("content-length").orElse(-1L));
                        }
                    }
                    """);

            // When
            CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.JdkHttpPost");

            // Then: Duration has its own runtime, so an HTTP timeout brings no structured-task runtime
            assertThat(result.assembly())
                    .contains("bl juno_duration_to_millis", "bl juno_http_request_text", "Too many redirects")
                    .doesNotContain("juno_https_request_text", "juno_task_scope");
        }

        @Test
        @DisplayName("A java.net.http ofString() body is received into a String sized to it, with no buffer cap")
        void shouldReceiveAnOfStringBodyIntoASizedString() throws Exception {
            // Given
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.JdkHttpText", """
                    package demo;
                    import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
                    import io.github.jabrena.juno.annotations.Board;
                    import io.github.jabrena.juno.hardware.serial.Serial;
                    import java.net.URI;
                    import java.net.http.HttpClient;
                    import java.net.http.HttpRequest;
                    import java.net.http.HttpResponse;
                    @Board(ArduinoUnoR4WiFiBoard.class)
                    public final class JdkHttpText {
                        public static void main(String[] args) throws Exception {
                            HttpClient client = HttpClient.newHttpClient();
                            HttpRequest request = HttpRequest.newBuilder(URI.create("https://example.com/")).build();
                            Serial.println(client.send(request, HttpResponse.BodyHandlers.ofString()).body().length());
                        }
                    }
                    """);

            // When
            CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.JdkHttpText");

            // Then: the body goes through the String sink, collected at most once before the String exists
            assertThat(result.assembly()).contains("bl juno_https_request_text_body");
            assertThat(result.runtimeShim()).contains("struct JunoHttpTextSink", "juno_gc_try_allocate",
                    "extern \"C\" int32_t juno_https_request_text_body(");
        }

        @Test
        @DisplayName("A java.net.http client with an SSLContext installs its trust anchors; one without links no trust code")
        void shouldInstallTheTrustAnchorsOfAnSslContext() throws Exception {
            // Given: the JDK's trust-my-CA recipe, reading the CA from the SD card
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.JdkHttpTrust", """
                    package demo;
                    import io.github.jabrena.juno.annotations.M5StackCardputerBoard;
                    import io.github.jabrena.juno.annotations.Board;
                    import io.github.jabrena.juno.hardware.MCU;
                    import io.github.jabrena.juno.hardware.serial.Serial;
                    import java.io.InputStream;
                    import java.net.URI;
                    import java.net.http.HttpClient;
                    import java.net.http.HttpRequest;
                    import java.net.http.HttpResponse;
                    import java.security.KeyStore;
                    import java.security.cert.CertificateFactory;
                    import javax.net.ssl.SSLContext;
                    import javax.net.ssl.TrustManagerFactory;
                    @Board(M5StackCardputerBoard.class)
                    public final class JdkHttpTrust {
                        public static void main(String[] args) throws Exception {
                            KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
                            store.load(null, null);
                            try (InputStream file = MCU.getSdCard().open("ca.crt")) {
                                store.setCertificateEntry("ca", CertificateFactory.getInstance("X.509")
                                        .generateCertificate(file));
                            }
                            TrustManagerFactory trust = TrustManagerFactory.getInstance(
                                    TrustManagerFactory.getDefaultAlgorithm());
                            trust.init(store);
                            SSLContext context = SSLContext.getInstance("TLS");
                            context.init(null, trust.getTrustManagers(), null);
                            HttpClient client = HttpClient.newBuilder().sslContext(context).build();
                            HttpRequest request = HttpRequest.newBuilder(URI.create("https://192.168.1.20/")).build();
                            Serial.println(client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode());
                        }
                    }
                    """);
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.JdkHttpBoardTrust", """
                    package demo;
                    import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
                    import io.github.jabrena.juno.annotations.Board;
                    import io.github.jabrena.juno.hardware.serial.Serial;
                    import java.net.URI;
                    import java.net.http.HttpClient;
                    import java.net.http.HttpRequest;
                    import java.net.http.HttpResponse;
                    @Board(ArduinoNanoEsp32Board.class)
                    public final class JdkHttpBoardTrust {
                        public static void main(String[] args) throws Exception {
                            HttpRequest request = HttpRequest.newBuilder(URI.create("https://example.com/")).build();
                            Serial.println(HttpClient.newHttpClient().send(request,
                                    HttpResponse.BodyHandlers.ofString()).statusCode());
                        }
                    }
                    """);

            // When
            CompilationResult custom = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.JdkHttpTrust");
            CompilationResult board = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.JdkHttpBoardTrust");

            // Then
            assertThat(custom.assembly()).contains("juno_http_set_trusted_certificate");
            assertThat(custom.runtimeShim()).contains("juno_runtime_trusted_ca = nullptr;\n    return 1;");
            assertThat(board.assembly()).doesNotContain("juno_http_set_trusted_certificate");
            assertThat(board.runtimeShim()).contains("setCACertBundle(juno_ca_bundle)");
            assertThat(board.runtimeShim()).contains(
                    "static bool juno_esp_tls_clock() {",
                    "configTime(0, 0, \"pool.ntp.org\", \"time.google.com\");",
                    "if (!juno_esp_tls_clock()) return 0;",
                    "return WiFiClientSecure::connect(host, port);");
        }

        @Test
        @DisplayName("A Portenta HTTPS client sets the clock and checks each certificate's dates after the handshake")
        void shouldCheckCertificateDatesOnThePortenta() throws Exception {
            // Given: the Mbed TLS date helpers are prebuilt without working dates, so the shim compares them itself
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.PortentaDates", """
                    package demo;
                    import io.github.jabrena.juno.annotations.ArduinoPortentaH7Board;
                    import io.github.jabrena.juno.annotations.Board;
                    import io.github.jabrena.juno.hardware.serial.Serial;
                    import java.net.URI;
                    import java.net.http.HttpClient;
                    import java.net.http.HttpRequest;
                    import java.net.http.HttpResponse;
                    @Board(ArduinoPortentaH7Board.class)
                    public final class PortentaDates {
                        public static void main(String[] args) throws Exception {
                            HttpRequest request = HttpRequest.newBuilder(URI.create("https://example.com/")).build();
                            Serial.println(HttpClient.newHttpClient().send(request,
                                    HttpResponse.BodyHandlers.ofString()).statusCode());
                        }
                    }
                    """, "21");

            // When
            CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.PortentaDates");

            // Then: the clock is set over SNTP, and every peer certificate is compared with it before the connection is used
            assertThat(result.runtimeShim()).contains("static bool juno_portenta_tls_clock()", "set_time(static_cast<time_t>(",
                    "gmtime_r(&nowSeconds, &now);", "bool juno_certificates_current() {",
                    "mbedtls_ssl_get_peer_cert(tls->get_ssl_context())",
                    "if (WiFiSSLClient::connect(host, port) != 1) return 0;")
                    .doesNotContain("mbedtls_x509_time_is_past(");
            // and the roots are parsed once and shared by every socket, so several sessions fit the Mbed heap at once
            assertThat(result.runtimeShim()).contains("static mbedtls_x509_crt* juno_portenta_ca_chain() {",
                    "struct JunoPortentaTlsClient : arduino::MbedSSLClient {",
                    "tls->set_ca_chain(juno_portenta_ca_chain());", "client.reset(new JunoPortentaTlsClient());",
                    "mbedtls_x509_crt_parse_der_nocopy(&juno_portenta_roots, root.der, root.length)")
                    .doesNotContain("R\"JUNO_CA(", "setCACert(", "juno_portenta_tls_busy");
        }


        @Test
        @DisplayName("A java.net.http client closes in try-with-resources, and a URI answers its parts and resolves")
        void shouldCompileTryWithResourcesAndTheUriAccessors() throws Exception {
            // Given: a long query, a resolved reference and a client that is AutoCloseable, as in the JDK since 21
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.JdkHttpClose", """
                    package demo;
                    import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
                    import io.github.jabrena.juno.annotations.Board;
                    import io.github.jabrena.juno.hardware.serial.Serial;
                    import java.net.URI;
                    import java.net.http.HttpClient;
                    import java.net.http.HttpRequest;
                    import java.net.http.HttpResponse;
                    @Board(ArduinoUnoR4WiFiBoard.class)
                    public final class JdkHttpClose {
                        public static void main(String[] args) throws Exception {
                            URI base = URI.create("http://example.com/v1/items?select=id,name,price&order=id.asc");
                            URI next = base.resolve("../v2/items?page=2#top");
                            Serial.println(next.getPath());
                            Serial.println(next.getQuery());
                            Serial.println(next.toString());
                            Serial.println(next.equals(URI.create("HTTP://EXAMPLE.com/v2/items?page=2#top")) ? 1 : 0);
                            try (HttpClient client = HttpClient.newHttpClient()) {
                                HttpRequest request = HttpRequest.newBuilder(next).build();
                                Serial.println(client.send(request, HttpResponse.BodyHandlers.ofByteArray()).statusCode());
                            }
                        }
                    }
                    """, "21");

            // When
            CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.JdkHttpClose");

            // Then: only the plain-HTTP request is linked, and the client is closed after the request
            assertThat(result.assembly()).contains("bl juno_http_request_text", "The client is shut down")
                    .doesNotContain("juno_https_request_text");
        }

        @Test
        @DisplayName("A java.net.http program that never follows redirects links no redirect code")
        void shouldLinkNoRedirectCodeWithoutFollowRedirects() throws Exception {
            // Given
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.JdkHttpPlainGet", """
                    package demo;
                    import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
                    import io.github.jabrena.juno.annotations.Board;
                    import io.github.jabrena.juno.hardware.serial.Serial;
                    import java.net.URI;
                    import java.net.http.HttpClient;
                    import java.net.http.HttpRequest;
                    import java.net.http.HttpResponse;
                    @Board(ArduinoUnoR4WiFiBoard.class)
                    public final class JdkHttpPlainGet {
                        public static void main(String[] args) throws Exception {
                            HttpClient client = HttpClient.newHttpClient();
                            HttpRequest request = HttpRequest.newBuilder(URI.create("http://example.com/")).build();
                            Serial.println(client.send(request, HttpResponse.BodyHandlers.ofByteArray()).statusCode());
                        }
                    }
                    """);

            // When
            CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.JdkHttpPlainGet");

            // Then
            assertThat(result.assembly()).contains("bl juno_http_request_text")
                    .doesNotContain("Too many redirects", "juno_duration_to_millis");
        }

        @Test
        @DisplayName("instanceof, and its pattern, compile for a final class")
        void shouldCompileInstanceOfAFinalClass() throws Exception {
            // Given
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.Shapes", """
                    package demo;
                    import io.github.jabrena.juno.hardware.serial.Serial;
                    public final class Shapes {
                        interface Shape {
                            int size();
                        }

                        static final class Circle implements Shape {
                            public int size() {
                                return 3;
                            }
                        }

                        static final class Square implements Shape {
                            public int size() {
                                return 4;
                            }
                        }

                        public static void main() {
                            Shape[] shapes = {new Circle(), new Square()};
                            for (Shape shape : shapes) {
                                if (shape instanceof Circle circle) {
                                    Serial.println(circle.size());
                                }
                                Serial.println(shape instanceof Square ? 1 : 0);
                            }
                        }
                    }
                    """, "21");

            // When
            CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Shapes");

            // Then
            assertThat(result.assembly()).isNotBlank();
        }

        @Test
        @DisplayName("instanceof of a class that is not final is rejected")
        void shouldRejectInstanceOfANonFinalClass() throws Exception {
            // Given
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.OpenShapes", """
                    package demo;
                    import io.github.jabrena.juno.hardware.serial.Serial;
                    public final class OpenShapes {
                        static class Shape {
                        }

                        static class Circle extends Shape {
                        }

                        public static void main() {
                            Shape shape = new Circle();
                            Serial.println(shape instanceof Circle ? 1 : 0);
                        }
                    }
                    """);

            // When / Then
            assertThatThrownBy(() -> CompilerTestSupport.compileJuno(temporaryDirectory, "demo.OpenShapes"))
                    .isInstanceOf(CompileException.class)
                    .hasMessageContaining("instanceof is supported only for available final classes");
        }

        @Test
        @DisplayName("Only the board-side JDK classes may return Object: a program's own method still may not")
        void shouldRejectAProgramMethodReturningObject() throws Exception {
            // Given: checkcast generates no code, so nothing would catch a wrong cast of this result
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.ObjectResult", """
                    package demo;
                    import io.github.jabrena.juno.hardware.serial.Serial;
                    public final class ObjectResult {
                        public static void main() {
                            String text = (String) pick();
                            Serial.println(text);
                        }

                        private static Object pick() {
                            return "board";
                        }
                    }
                    """);

            // When & Then
            assertThatThrownBy(() -> CompilerTestSupport.compileJuno(temporaryDirectory, "demo.ObjectResult"))
                    .isInstanceOf(CompileException.class)
                    .hasMessageContaining("supported scalar, array, or closed-world reference")
                    .hasMessageContaining("pick");
        }

        @Test
        @DisplayName("The Pico 2 W's TrustZone store brings its gateway shim, its non-secure start-up and the firmware flag")
        void shouldCompileThePico2WTrustZoneStore() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.annotations.RaspberryPico2WifiBoard;
                    import io.github.jabrena.juno.annotations.Board;
                    import io.github.jabrena.juno.hardware.MCU;
                    import io.github.jabrena.juno.hardware.security.SecretStore;
                    import io.github.jabrena.juno.hardware.serial.Serial;
                    @Board(RaspberryPico2WifiBoard.class)
                    public final class PicoSecrets {
                        public static void main(String[] args) {
                            SecretStore store = MCU.getSecretStore();
                            store.write("wifi.ssid", new byte[] {1, 2}, 2);
                            Serial.println(store.clearAll());
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.PicoSecrets", source);

            // When
            CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.PicoSecrets");

            // Then
            assertThat(result.report().usesPicoSecureFirmware()).isTrue();
            assertThat(result.fqbn()).isEqualTo("rp2040:rp2040:rpipico2w:ipbtstack=ipv4btcble");
            assertThat(result.assembly()).contains("juno_pico_tz_begin", "juno_pico_tz_write", "juno_pico_tz_clear_all");
            assertThat(result.runtimeShim()).contains("0x10002F01u", "void runtime_init_early_resets(void)",
                    "void runtime_init_bootrom_reset(void)", "pico_processor_state_is_nonsecure()",
                    "void __wrap_flash_range_erase(", "void __wrap_flash_range_program(",
                    "irq_set_exclusive_handler(IO_IRQ_BANK0_NS, juno_pico_tz_gpio_ns_irq)");
        }

        @Test
        @DisplayName("A program without the store needs no secure firmware and keeps the SDK's own start-up")
        void shouldLeaveOtherPicoProgramsAlone() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.annotations.RaspberryPico2WifiBoard;
                    import io.github.jabrena.juno.annotations.Board;
                    import io.github.jabrena.juno.hardware.serial.Serial;
                    @Board(RaspberryPico2WifiBoard.class)
                    public final class PicoPlain {
                        public static void main(String[] args) {
                            Serial.println(1);
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.PicoPlain", source);

            // When
            CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.PicoPlain");

            // Then
            assertThat(result.report().usesPicoSecureFirmware()).isFalse();
            assertThat(result.fqbn()).isEqualTo("rp2040:rp2040:rpipico2w:ipbtstack=ipv4btcble");
            assertThat(result.runtimeShim()).doesNotContain("juno_pico_tz", "runtime_init_early_resets");
        }

        @Test
        @DisplayName("The Pico 2 W's TrustZone store and Bluetooth LE compile together, BTstack's flash going through the gateway")
        void shouldCompileTheTrustZoneStoreWithBluetooth() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.annotations.RaspberryPico2WifiBoard;
                    import io.github.jabrena.juno.annotations.Board;
                    import io.github.jabrena.juno.hardware.MCU;
                    import io.github.jabrena.juno.hardware.serial.Serial;
                    @Board(RaspberryPico2WifiBoard.class)
                    public final class PicoBle {
                        public static void main(String[] args) {
                            Serial.println(MCU.getBluetoothLe().isConnected());
                            Serial.println(MCU.getSecretStore().clearAll());
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.PicoBle", source);

            // When
            CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.PicoBle");

            // Then
            assertThat(result.report().usesPicoSecureFirmware()).isTrue();
            assertThat(result.fqbn()).contains("ipbtstack=ipv4btcble");
            assertThat(result.runtimeShim()).contains("void __wrap_flash_range_erase(");
        }

        @Test
        @DisplayName("The Pico 2 W's TrustZone store runs a program on two carriers, its doorbells routed non-secure")
        void shouldRunTheTrustZoneStoreOnTwoCarriers() throws Exception {
            // Given: virtual threads give the Pico 2 W its second carrier, which the secure firmware's relay starts
            String source = """
                    package demo;
                    import io.github.jabrena.juno.annotations.RaspberryPico2WifiBoard;
                    import io.github.jabrena.juno.annotations.Board;
                    import io.github.jabrena.juno.hardware.MCU;
                    import io.github.jabrena.juno.hardware.serial.Serial;
                    @Board(RaspberryPico2WifiBoard.class)
                    public final class PicoThreads {
                        public static void main(String[] args) throws InterruptedException {
                            Thread worker = Thread.startVirtualThread(() -> Serial.println(1));
                            worker.join();
                            Serial.println(MCU.getSecretStore().clearAll());
                        }
                    }
                    """;
            CompilerTestSupport.compileJavaWithPreview(temporaryDirectory, "demo.PicoThreads", source);

            // When
            CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.PicoThreads");

            // Then
            assertThat(result.fqbn()).contains("os=freertos");
            assertThat(result.runtimeShim()).contains("juno_pico_tz_clear_all", "void __wrap_irq_set_enabled(",
                    "SIO_IRQ_BELL_NS", "rp2040.idleOtherCore();");
        }

    }

    @Nested
    @DisplayName("Optimization, reachability and object erasure")
    class OptimizationReachabilityAndErasure {
        @TempDir
        Path temporaryDirectory;

        @Test
        @DisplayName("Propagates lowered local copies before folding constant branches")
        void shouldPropagateLoweredLocalCopiesBeforeFoldingConstantBranches() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.util.Delay;
                    public final class FoldedBranch {
                        static int choose() {
                            int answer = 5;
                            if (answer == 5) return 11;
                            return 22;
                        }
                        public static void main(String[] args) {
                            Delay.millis(choose());
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.FoldedBranch", source);
            Program linked = CompilerTestSupport.link(temporaryDirectory, "demo.FoldedBranch");
            CompilationPipeline pipeline = new CompilationPipeline();

            // When
            IrProgram optimized = pipeline.optimize(pipeline.lower(linked));

            // Then
            // choose() is inlined into main; there the branch on its propagated constant folds away, leaving one
            // straight-line block whose Delay.millis receives the constant 11.
            assertThat(optimized.methods()).noneMatch(candidate -> candidate.reference().name().equals("choose"));
        }

        @Test
        @DisplayName("The folded main is one straight-line block that passes the constant 11")
        void shouldFoldMainIntoOneStraightLineBlockWithTheConstantEleven() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.util.Delay;
                    public final class FoldedBranch {
                        static int choose() {
                            int answer = 5;
                            if (answer == 5) return 11;
                            return 22;
                        }
                        public static void main(String[] args) {
                            Delay.millis(choose());
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.FoldedBranch", source);
            Program linked = CompilerTestSupport.link(temporaryDirectory, "demo.FoldedBranch");
            CompilationPipeline pipeline = new CompilationPipeline();
            IrProgram optimized = pipeline.optimize(pipeline.lower(linked));
            IrMethod main = optimized.methods().stream()
                    .filter(candidate -> candidate.reference().name().equals("main"))
                    .findFirst()
                    .orElseThrow();

            // When & Then
            assertThat(main.blocks()).hasSize(1);
            assertThat(main.blocks().getFirst().terminator()).isNotInstanceOf(IrTerminator.Branch.class);
            assertThat(main.blocks().getFirst().instructions()).anyMatch(instruction ->
                    instruction instanceof IrInstruction.Const constant && constant.value() == 11);
        }

        @Test
        @DisplayName("Compiles only the reachable methods, with branches and hardware intrinsics")
        void shouldCompileOnlyTheReachableMethodsWithBranchesAndHardwareIntrinsics() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.hardware.MCU;
                    import io.github.jabrena.juno.hardware.gpio.Gpio;
                    public final class Main {
                        public static int addTo(int limit) {
                            int value = 0;
                            for (int i = 0; i < limit; i++) value += i;
                            return value;
                        }
                        public static int unused() { return 99; }
                        public static void main(String[] args) {
                            MCU.getGpio().pinMode(13, MCU.getGpio().getOutput());
                            MCU.getGpio().digitalWrite(13, addTo(4) == 6);
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.Main", source);

            // When
            CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Main");
            String generated = result.assembly();

            // Then
            assertThat(generated).contains(".global juno_Main_asm", "bl pinMode", "bl digitalWrite");
            assertThat(result.report().reachableMethods())
                    .as("main, addTo, Gpio.board and NativeGpio's board, initializers and constructor; unused is not")
                    .isEqualTo(6);
        }

        @Test
        @DisplayName("No non-entry function remains when the small leaf is inlined")
        void shouldLeaveNoNonEntryFunctionWhenTheSmallLeafIsInlined() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.hardware.MCU;
                    import io.github.jabrena.juno.hardware.gpio.Gpio;
                    public final class Main {
                        public static int addTo(int limit) {
                            int value = 0;
                            for (int i = 0; i < limit; i++) value += i;
                            return value;
                        }
                        public static int unused() { return 99; }
                        public static void main(String[] args) {
                            MCU.getGpio().pinMode(13, MCU.getGpio().getOutput());
                            MCU.getGpio().digitalWrite(13, addTo(4) == 6);
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.Main", source);
            String generated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Main").assembly();

            // When & Then
            // unused() is unreachable from main, and addTo, a small leaf, is inlined into it, as is MCU.getGpio(): only the
            // NativeGpio static initializer remains as a non-entry function.
            assertThat(countOccurrences(generated, ".type juno_fn")).isEqualTo(1);
        }

        @Test
            @DisplayName("Erases DigitalOutput objects to pin numbers without allocating the handle")
        void shouldEraseDigitalOutputObjectsToPinNumbersWithNoHeapAllocation() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.hardware.MCU;
                    import io.github.jabrena.juno.hardware.gpio.DigitalOutput;
                    public final class ObjectStyleApi {
                        public static void main(String[] args) {
                            DigitalOutput led = DigitalOutput.of(MCU.getGpio(), "D13");
                            led.high();
                            led.low();
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.ObjectStyleApi", source);

            // When
            String generated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.ObjectStyleApi").assembly();

            // Then
            // DigitalOutput.of(gpio, "D13") resolves the name and erases the DigitalOutput handle to a pin number;
            // (r1=1 selects OUTPUT); high()/low() erase to digitalWrite(pin, 1) / digitalWrite(pin, 0) --
            // the only allocation is the board's lazily-created NativeGpio instance.
            assertThat(generated).contains("juno_gpio_digital_pin", "movs r1, #1\n    bl pinMode",
                    "movs r1, #1\n    bl digitalWrite", "movs r1, #0\n    bl digitalWrite");
            assertThat(generated).containsOnlyOnce("bl juno_alloc");
        }
    }

    @Nested
    @DisplayName("LED matrix, glyphs, touch display and sensors")
    class DisplaysAndSensors {
        @TempDir
        Path temporaryDirectory;

        @Test
        @DisplayName("Lowers the LED matrix intrinsics and keeps the driver out of programs without it")
        void shouldLowerLedMatrixIntrinsicsAndKeepTheDriverOutOfPlainPrograms() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.hardware.MCU;
                    public final class Heart {
                        public static void main(String[] args) {
                            MCU.getDisplay();
                            MCU.getDisplay().flush();
                            io.github.jabrena.juno.hardware.internal.NativeLedMatrix.INSTANCE.clear();
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.Heart", source);

            // When
            CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Heart");

            // Then
            assertThat(result.assembly()).contains(
                    "bl juno_led_matrix_begin", "bl juno_led_matrix_load_frame", "bl juno_led_matrix_clear");
            assertThat(result.runtimeShim()).contains("ArduinoLEDMatrix juno_led_matrix;",
                    "const uint32_t frame[3] = {", "int32_t word3");
        }

        @Test
        @DisplayName("A program without the LED matrix has no matrix calls or shim")
        void shouldOmitTheLedMatrixCallsAndShimFromAPlainProgram() throws Exception {
            // Given
            String plainSource = """
                    package demo;
                    import io.github.jabrena.juno.hardware.MCU;
                    import io.github.jabrena.juno.hardware.gpio.Gpio;
                    public final class Plain {
                        public static void main(String[] args) {
                            MCU.getGpio().pinMode(13, MCU.getGpio().getOutput());
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.Plain", plainSource);
            CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Plain");

            // When & Then
            // The shim only includes and defines the LED matrix driver when a program calls into it.
            assertThat(result.assembly()).doesNotContain("bl juno_led_matrix_begin", "bl juno_led_matrix_load_frame",
                    "bl juno_led_matrix_clear");
            assertThat(result.runtimeShim()).doesNotContain("Arduino_LED_Matrix", "juno_led_matrix");
        }

        @Test
        @DisplayName("Renders digits from the glyph table without a function per glyph")
        void shouldRenderDigitsFromTheGlyphTableWithoutAFunctionPerGlyph() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.hardware.MCU;
                    import io.github.jabrena.juno.hardware.display.Display;
                    public final class Digits {
                        public static void main(String[] args) {
                            Display display = MCU.getDisplay();
                            display.setTextColor(Display.WHITE, Display.BLACK);
                            display.setCursor(4, 0);
                            display.print('7');
                            display.flush();
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.Digits", source);

            // When
            CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Digits");

            // Then
            assertThat(result.assembly()).contains("bl juno_led_matrix_load_frame");
            // The glyphs are one constant table read by index, so drawing a digit reaches no per-glyph
            // code: locked at 17 (main, MCU.getDisplay, the display's print, glyph, pixel, width and flush paths,
            // charPixel, and the NativeLedMatrix static initializer); before the display moved behind Arduino
            // it was 10, where a function per glyph took 22.
            assertThat(result.report().reachableMethods()).isEqualTo(17);
        }

        @Test
        @DisplayName("The glyph table stays in flash")
        void shouldKeepTheGlyphTableInFlash() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.hardware.MCU;
                    import io.github.jabrena.juno.hardware.display.Display;
                    public final class Digits {
                        public static void main(String[] args) {
                            Display display = MCU.getDisplay();
                            display.setTextColor(Display.WHITE, Display.BLACK);
                            display.setCursor(4, 0);
                            display.print('7');
                            display.flush();
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.Digits", source);

            // When & Then
            assertThat(CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Digits").assembly())
                    .as("the table stays in flash")
                    .contains("juno_table_io_github_jabrena_juno_hardware_fonts_AsciiFont_ROWS__B:");
        }

        @Test
        @DisplayName("Printing text on the LED display reaches the ASCII glyph table")
        void shouldPrintTextThroughTheGlyphTable() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.hardware.MCU;
                    import io.github.jabrena.juno.hardware.display.Display;
                    public final class ScrollingText {
                        public static void main(String[] args) {
                            Display display = MCU.getDisplay();
                            display.setTextColor(Display.WHITE, Display.BLACK);
                            display.setCursor(0, 0);
                            display.print("Hi");
                            display.flush();
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.ScrollingText", source);

            // When
            CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.ScrollingText");

            // Then
            assertThat(result.assembly()).contains("bl juno_led_matrix_load_frame",
                    "juno_table_io_github_jabrena_juno_hardware_fonts_AsciiFont_ROWS__B:");
        }

        @Test
        @DisplayName("The TFT touch shield lowers to GPIO and parallel-bus calls without extra shim libraries")
        void shouldLowerTheTftTouchShieldToGpioWithoutExtraShimLibraries() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;
                    public final class TftDemo {
                        public static void main(String[] args) {
                            TftTouchShield tft = new TftTouchShield();
                            tft.setRotation(TftTouchShield.LANDSCAPE);
                            tft.fillScreen(TftTouchShield.BLACK);
                            tft.setTextSize(2);
                            tft.setTextColor(TftTouchShield.WHITE, TftTouchShield.BLUE);
                            tft.setCursor(10, 10);
                            tft.print("Hi ");
                            tft.print(42);
                            while (true) {
                                if (tft.readTouch()) {
                                    tft.fillRect(tft.touchX(), tft.touchY(), 3, 3,
                                            TftTouchShield.color(255, 0, 0));
                                }
                            }
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.TftDemo", source);

            // When
            CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.TftDemo");

            // Then
            // The driver is Java over Gpio/Delay for control and touch, and ParallelBus for the display's bytes.
            assertThat(result.assembly()).contains("bl pinMode", "bl digitalWrite", "bl analogRead",
                    "bl juno_parallel_bus_begin", "bl juno_parallel_bus_write", "bl juno_parallel_bus_repeat16");
            assertThat(result.runtimeShim()).contains("juno_parallel_bus_repeat16").doesNotContain(
                    "#include <SdFat.h>", "#include <WiFiS3.h>", "#include <Mouse.h>");
        }

        @Test
        @DisplayName("The Grove DHT22 driver lowers to GPIO and timing intrinsics")
        void shouldLowerTheGroveDht22DriverToGpioAndTimingIntrinsics() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.hardware.MCU;
                    import io.github.jabrena.juno.hardware.device.grove.GroveBaseShield;
                    import io.github.jabrena.juno.hardware.device.grove.sensors.GroveDht22;
                    public final class GroveDht22Demo {
                        public static void main(String[] args) {
                            GroveBaseShield shield = new GroveBaseShield();
                            int[] reading = new int[GroveDht22.READING_LENGTH];
                            int[] frame = new int[GroveDht22.FRAME_LENGTH];
                            new GroveDht22().read(shield.getD4(), reading, reading.length, frame, frame.length);
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.GroveDht22Demo", source);

            // When
            CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.GroveDht22Demo");

            // Then
            assertThat(result.assembly()).contains("bl pinMode", "bl digitalWrite", "bl digitalRead",
                    "bl micros", "bl delay", "bl delayMicroseconds");
            assertThat(result.runtimeShim()).doesNotContain("#include <DHT.h>", "#include <Wire.h>");
        }

        @Test
        @DisplayName("The Grove GPS Air530 driver lowers through GPIO and timing intrinsics")
        void shouldLowerTheGroveGpsAir530DriverToGpioAndTimingIntrinsics() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.hardware.MCU;
                    import io.github.jabrena.juno.hardware.device.grove.GroveBaseShield;
                    import io.github.jabrena.juno.hardware.device.grove.sensors.GroveGpsAir530;
                    import io.github.jabrena.juno.hardware.serial.Serial;
                    public final class GroveGpsAir530Demo {
                        public static void main(String[] args) {
                            GroveBaseShield shield = new GroveBaseShield();
                            GroveGpsAir530 gps = new GroveGpsAir530(shield.getD2());
                            byte[] sentence = new byte[GroveGpsAir530.SENTENCE_CAPACITY];
                            int[] position = new int[GroveGpsAir530.POSITION_LENGTH];
                            gps.begin();
                            if (gps.readPosition(sentence, sentence.length, position, position.length, 1000)) {
                                Serial.println(position[GroveGpsAir530.LATITUDE_MICRODEGREES]);
                            }
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.GroveGpsAir530Demo", source);

            // When
            CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.GroveGpsAir530Demo");

            // Then
            assertThat(result.assembly()).contains("bl pinMode", "bl digitalRead", "bl micros", "bl delayMicroseconds");
            assertThat(result.runtimeShim()).doesNotContain("#include <SoftwareSerial.h>");
        }

        @Test
        @DisplayName("The Grove PIR and analog gas drivers lower to GPIO, analog and I2C calls")
        void shouldLowerTheGrovePirAndAnalogGasDrivers() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.hardware.MCU;
                    import io.github.jabrena.juno.hardware.device.grove.sensors.GroveAnalogGas;
                    import io.github.jabrena.juno.hardware.device.grove.GroveBaseShield;
                    import io.github.jabrena.juno.hardware.device.grove.sensors.GrovePirMotion;
                    public final class GroveSensorsDemo {
                        public static void main(String[] args) {
                            GroveBaseShield shield = new GroveBaseShield();
                            GrovePirMotion pir = new GrovePirMotion();
                            GroveAnalogGas gasSensor = new GroveAnalogGas();
                            pir.begin(shield.getD3());
                            int gas = gasSensor.rawValue(shield.getA0());
                            boolean motion = pir.motionDetected(shield.getD3());
                            if (motion && gas > 100) {
                                io.github.jabrena.juno.hardware.serial.Serial.println(gas);
                            }
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.GroveSensorsDemo", source);

            // When
            CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.GroveSensorsDemo");

            // Then
            assertThat(result.assembly()).contains("bl pinMode", "bl digitalRead", "bl analogRead");
            assertThat(result.runtimeShim()).doesNotContain("#include <Wire.h>", "#include <DHT.h>");
        }

        @Test
        @DisplayName("The Grove sensor batch drivers lower to GPIO and timing intrinsics")
        void shouldLowerTheGroveSensorBatchDriversToGpioAndTimingIntrinsics() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.hardware.device.grove.GroveBaseShield;
                    import io.github.jabrena.juno.hardware.device.grove.sensors.GroveDht11;
                    import io.github.jabrena.juno.hardware.MCU;
                    import io.github.jabrena.juno.hardware.device.grove.sensors.GroveLightV12;
                    import io.github.jabrena.juno.hardware.device.grove.sensors.GroveSoundV16;
                    import io.github.jabrena.juno.hardware.device.grove.sensors.GroveTemperatureV12;
                    public final class GroveSensorBatchDemo {
                        public static void main(String[] args) {
                            GroveBaseShield shield = new GroveBaseShield();
                            int[] reading = new int[GroveDht11.READING_LENGTH];
                            int[] frame = new int[GroveDht11.FRAME_LENGTH];
                            boolean valid = new GroveDht11().read(shield.getD4(), reading, reading.length,
                                    frame, frame.length);
                            GroveTemperatureV12 thermistor = new GroveTemperatureV12();
                            int temperature = thermistor.rawValue(shield.getA0());
                            float celsius = thermistor.celsiusFromRaw(temperature);
                            int sound = new GroveSoundV16().rawValue(shield.getA1());
                            float light = new GroveLightV12(shield.getA2(), GroveLightV12.ADC_10_BIT).getLightValue();
                            if (valid && temperature + sound + light > 0) {
                                io.github.jabrena.juno.hardware.serial.Serial.println(celsius);
                            }
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.GroveSensorBatchDemo", source);

            // When
            CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.GroveSensorBatchDemo");

            // Then
            assertThat(result.assembly()).contains("bl pinMode", "bl digitalWrite", "bl digitalRead",
                    "bl analogRead", "bl micros", "bl delay", "bl delayMicroseconds");
            assertThat(result.runtimeShim()).doesNotContain("#include <DHT.h>", "#include <Wire.h>");
        }

        @Test
        @DisplayName("The Grove light sensor reads as a LightDetector bound to its socket")
        void shouldReadTheGroveLightSensorAsALightDetector() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.hardware.MCU;
                    import io.github.jabrena.juno.hardware.device.grove.GroveBaseShield;
                    import io.github.jabrena.juno.hardware.device.grove.sensors.GroveLightV12;
                    import io.github.jabrena.juno.hardware.serial.Serial;
                    import io.github.jabrena.juno.robotics.LightDetector;
                    public final class GroveLightDetectorDemo {
                        public static void main(String[] args) {
                            LightDetector light = new GroveLightV12(new GroveBaseShield().getA2(), GroveLightV12.ADC_10_BIT);
                            Serial.println((int) light.getLightValue());
                            Serial.println(light.getNormalizedLightValue() > light.getLow());
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.GroveLightDetectorDemo", source);

            // When
            CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory,
                    "demo.GroveLightDetectorDemo");

            // Then
            assertThat(result.assembly()).contains("bl analogRead");
        }

        @Test
        @DisplayName("The Grove robotics and SSD1315 adapters lower through injected board transports")
        void shouldLowerTheGroveRoboticsAndOledAdapters() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.hardware.MCU;
                    import io.github.jabrena.juno.hardware.device.grove.GroveBaseShield;
                    import io.github.jabrena.juno.hardware.device.grove.display.GroveOledSsd1315;
                    import io.github.jabrena.juno.hardware.device.grove.sensors.GroveUltrasonicRanger;
                    import io.github.jabrena.juno.hardware.device.imu.Bmi088;
                    import io.github.jabrena.juno.hardware.device.motor.L298nMotor;
                    import io.github.jabrena.juno.hardware.gpio.Gpio;
                    import io.github.jabrena.juno.hardware.i2c.I2c;
                    import io.github.jabrena.juno.hardware.time.Clock;
                    public final class GroveRoboticsDemo {
                        public static void main(String[] args) {
                            GroveBaseShield shield = new GroveBaseShield();
                            Gpio gpio = MCU.getGpio();
                            Clock clock = MCU.getClock();
                            I2c i2c = MCU.getI2c();
                            L298nMotor motor = new L298nMotor(gpio, 3, 4, 5);
                            motor.setPower(50);
                            motor.forward();
                            GroveUltrasonicRanger ranger = new GroveUltrasonicRanger(gpio, clock, shield.getD2());
                            float range = ranger.getRange();
                            Bmi088 imu = new Bmi088(i2c, clock);
                            if (range >= 0f) {
                                imu.getAngularVelocity();
                            }
                            byte[] framebuffer = new byte[GroveOledSsd1315.FRAMEBUFFER_SIZE];
                            GroveOledSsd1315 oled = new GroveOledSsd1315(i2c, framebuffer);
                            oled.drawPixel(0, 0, GroveOledSsd1315.WHITE);
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.GroveRoboticsDemo", source);

            // When
            CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.GroveRoboticsDemo");

            // Then
            // The ranger's echo is timed by the core's pulseIn, not by polling digitalRead against micros.
            assertThat(result.assembly()).contains("bl pinMode", "bl digitalWrite", "bl juno_pulse_in", "bl analogWrite",
                    "bl juno_i2c_begin", "bl juno_i2c_write_register", "bl juno_i2c_read_register");
            assertThat(result.runtimeShim()).contains("#include <Wire.h>", "juno_pulse_in").doesNotContain("#include <DHT.h>");
        }

        @Test
        @DisplayName("Raw I2C write and read lower to the Wire shims with a caller-owned byte buffer")
        void shouldLowerRawI2cTransfers() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.hardware.MCU;
                    import io.github.jabrena.juno.hardware.i2c.I2c;
                    public final class RawI2cDemo {
                        public static void main(String[] args) {
                            I2c i2c = MCU.getI2c();
                            i2c.begin();
                            byte[] frame = new byte[8];
                            int sent = i2c.write(0x60, frame, frame.length);
                            int received = i2c.read(0x60, frame, 4);
                            if (sent < 0 || received < 0) {
                                return;
                            }
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.RawI2cDemo", source);

            // When
            CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.RawI2cDemo");

            // Then
            assertThat(result.assembly()).contains("bl juno_i2c_write", "bl juno_i2c_read");
            assertThat(result.runtimeShim()).contains("int32_t juno_i2c_write(int32_t address, int8_t* data, int32_t length)",
                    "int32_t juno_i2c_read(int32_t address, int8_t* buffer, int32_t length)");
        }

        @Test
        @DisplayName("SPI clock and block transfer lower to the SPI shims with a caller-owned byte buffer")
        void shouldLowerSpiClockAndBlockTransfer() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.hardware.MCU;
                    import io.github.jabrena.juno.hardware.spi.Spi;
                    public final class SpiBlockDemo {
                        public static void main(String[] args) {
                            Spi spi = MCU.getSpi();
                            spi.begin();
                            spi.setClock(32000000);
                            byte[] row = new byte[64];
                            row[0] = (byte) 0xF8;
                            spi.transfer(row, row.length);
                            spi.write(row, row.length);
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.SpiBlockDemo", source);

            // When
            CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.SpiBlockDemo");

            // Then
            assertThat(result.assembly()).contains("bl juno_spi_begin", "bl juno_spi_set_clock", "bl juno_spi_transfer_buffer",
                    "bl juno_spi_write_buffer");
            assertThat(result.runtimeShim()).contains("void juno_spi_set_clock(int32_t frequency)",
                    "SPI.beginTransaction(SPISettings(", "void juno_spi_transfer_buffer(int8_t* data, int32_t length)",
                    "SPI.transfer(data, static_cast<size_t>(length))",
                    "void juno_spi_write_buffer(int8_t* data, int32_t length)", "memcpy(chunk, data + offset")
                    .doesNotContain("LL_SPI_TransmitData8");
        }

        @Test
        @DisplayName("Portenta H7 feeds an SPI write straight into the H7's SPI FIFO, with the core's transfer as fallback")
        void shouldFeedPortentaSpiWritesToTheFifo() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.annotations.Board;
                    import io.github.jabrena.juno.hardware.MCU;
                    import io.github.jabrena.juno.hardware.spi.Spi;
                    @Board(io.github.jabrena.juno.annotations.ArduinoPortentaH7Board.class)
                    public final class PortentaSpiWriteDemo {
                        public static void main(String[] args) {
                            Spi spi = MCU.getSpi();
                            spi.begin();
                            byte[] row = new byte[64];
                            spi.write(row, row.length);
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.PortentaSpiWriteDemo", source);

            // When
            CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.PortentaSpiWriteDemo");

            // Then
            assertThat(result.assembly()).contains("bl juno_spi_write_buffer");
            assertThat(result.runtimeShim()).contains("#include \"stm32h7xx_ll_spi.h\"", "LL_SPI_TransmitData8",
                    "juno_spi_write_chunked(data, length)");
        }

        @Test
        @DisplayName("Portenta H7 accepts the raw pin names its Hat Carrier needs, up to three digits")
        void shouldResolvePortentaRawPinNames() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.annotations.Board;
                    import io.github.jabrena.juno.hardware.MCU;
                    import io.github.jabrena.juno.hardware.gpio.Gpio;
                    @Board(io.github.jabrena.juno.annotations.ArduinoPortentaH7Board.class)
                    public final class PortentaRawPinDemo {
                        public static void main(String[] args) {
                            Gpio gpio = MCU.getGpio();
                            int dataCommand = gpio.digitalPin("GPIO161");
                            gpio.pinMode(dataCommand, gpio.getOutput());
                            gpio.digitalWrite(dataCommand, true);
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.PortentaRawPinDemo", source);

            // When
            CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.PortentaRawPinDemo");

            // Then
            assertThat(result.runtimeShim()).contains("juno_gpio_additional_digital_pins[] = {\n159, 160, 161, -1}",
                    "if (digit == 3 ||");
        }
    }

    @Nested
    @DisplayName("Serial output, numbers and runtime strings")
    class SerialAndRuntimeStrings {
        @TempDir
        Path temporaryDirectory;

        @Test
        @DisplayName("Compiles a Cardputer Hello World through Serial")
        void shouldCompileM5CardputerHelloWorldToUsbSerial() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.annotations.Board;
                                        import io.github.jabrena.juno.hardware.serial.BaudRate;
                    import io.github.jabrena.juno.hardware.serial.Serial;
                    @Board(io.github.jabrena.juno.annotations.M5StackCardputerBoard.class)
                    public final class M5HelloWorld {
                        public static void main(String[] args) {
                            Serial.begin(BaudRate.BAUD_115200);
                            Serial.println("Hello, World!");
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.M5HelloWorld", source);

            // When
            CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.M5HelloWorld");

            // Then
            assertThat(result.report().board()).isEqualTo(Board.M5_CARDPUTER);
            assertThat(result.report().board().fqbn())
                    .isEqualTo("m5stack:esp32:m5stack_cardputer:USBMode=default,CDCOnBoot=cdc,UploadMode=cdc");
            assertThat(result.assembly()).contains("call8 juno_serial_begin", "call8 juno_serial_println_str",
                    "Hello, World!");
            assertThat(result.runtimeShim())
                    .contains("Serial.begin", "Serial.println", "defined(BOARD_HAS_PIN_REMAP)", "#else\n  return pin;");
            assertThat(result.runtimeShim()).doesNotContain("#include <M5Cardputer.h>");
        }

        @Test
        @DisplayName("Lowers the serial intrinsics for begin, print and println")
        void shouldLowerTheSerialIntrinsics() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.hardware.serial.BaudRate;
                    import io.github.jabrena.juno.hardware.serial.Serial;
                    public final class Counter {
                        public static void main(String[] args) {
                            Serial.begin(BaudRate.BAUD_9600);
                            Serial.print(1);
                            Serial.println(2);
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.Counter", source);

            // When
            String generated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Counter").assembly();

            // Then
            assertThat(generated).contains("bl juno_serial_begin", "bl juno_serial_print\n", "bl juno_serial_println\n");
        }

        @Test
        @DisplayName("Lowers boolean serial output as text")
        void shouldLowerBooleanSerialOutputAsText() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.hardware.MCU;
                    import io.github.jabrena.juno.hardware.time.Clock;
                    import io.github.jabrena.juno.hardware.serial.Serial;
                    public final class BooleanSerial {
                        public static void main(String[] args) {
                            boolean value = MCU.getClock().millis() > 0;
                            Serial.print(value);
                            Serial.println(!value);
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.BooleanSerial", source);

            // When
            String generated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.BooleanSerial").assembly();

            // Then
            assertThat(generated).contains(
                    ".asciz \"true\"", ".asciz \"false\"",
                    "bl juno_serial_print_str", "bl juno_serial_println_str");
        }

        @Test
        @DisplayName("Lowers wide and floating-point serial intrinsics")
        void shouldLowerWideAndFloatingPointSerialIntrinsics() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.hardware.serial.Serial;
                    public final class NumericSerial {
                        public static void main(String[] args) {
                            Serial.print(Long.MIN_VALUE);
                            Serial.println(Long.MAX_VALUE);
                            Serial.print(Float.MIN_VALUE);
                            Serial.println(Float.MAX_VALUE);
                            Serial.print(Double.MIN_VALUE);
                            Serial.println(Double.MAX_VALUE);
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.NumericSerial", source);

            // When
            CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.NumericSerial");

            // Then
            assertThat(result.assembly()).contains(
                    "bl juno_serial_print_long", "bl juno_serial_println_long",
                    "bl juno_serial_print_float", "bl juno_serial_println_float",
                    "bl juno_serial_print_double", "bl juno_serial_println_double");
            assertThat(result.runtimeShim()).contains(
                    "extern \"C\" void juno_serial_print_long(int64_t value)",
                    "extern \"C\" JUNO_ASM_ABI void juno_serial_print_float(float value)",
                    "extern \"C\" JUNO_ASM_ABI void juno_serial_print_double(double value)");
        }

        @Test
        @DisplayName("Lowers a custom serial baud rate with a movw and movt immediate pair")
        void shouldLowerACustomSerialBaudRateWithAMovwMovtPair() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.hardware.serial.Serial;
                    public final class CustomBaudRate {
                        public static void main(String[] args) {
                            Serial.begin(74880);
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.CustomBaudRate", source);

            // When
            String generated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.CustomBaudRate").assembly();

            // Then
            assertThat(generated).contains("bl juno_serial_begin");
            // 74880 = 0x124C0: movw/movt immediate-load pair, not a bipush/sipush-sized literal.
            assertThat(generated).contains("movw r0, #9344", "movt r0, #1");
        }

        @Test
        @DisplayName("Lowers the random-number intrinsics")
        void shouldLowerTheRandomNumberIntrinsics() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.util.Random;
                    public final class Dice {
                        public static void main(String[] args) {
                            Random.seed(42);
                            int zeroBased = Random.nextInt(100);
                            int die = Random.nextInt(1, 7);
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.Dice", source);

            // When
            CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Dice");

            // Then
            assertThat(result.assembly()).contains(
                    "bl juno_random_seed", "bl juno_random_next_bound", "bl juno_random_next_range");
            assertThat(result.runtimeShim()).contains(
                    "extern \"C\" void juno_random_seed(int32_t seed)",
                    "extern \"C\" int32_t juno_random_next_bound(int32_t bound)",
                    "extern \"C\" int32_t juno_random_next_range(int32_t origin, int32_t bound)");
        }

        @Test
        @DisplayName("Lowers serial string literals to rodata symbols")
        void shouldLowerSerialStringLiteralsToRodataSymbols() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.hardware.serial.BaudRate;
                    import io.github.jabrena.juno.hardware.serial.Serial;
                    public final class Greeting {
                        public static void main(String[] args) {
                            Serial.begin(BaudRate.BAUD_9600);
                            Serial.print("hello");
                            String message = "world";
                            Serial.println(message);
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.Greeting", source);

            // When
            String generated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Greeting").assembly();

            // Then
            assertThat(generated).contains(".asciz \"hello\"", ".asciz \"world\"",
                    "bl juno_serial_print_str", "bl juno_serial_println_str");
        }

        @Test
        @DisplayName("Supports runtime strings across method parameters and returns")
        void shouldSupportRuntimeStringsAcrossMethodParametersAndReturns() throws Exception {
            // Given
            String source = """
                    package demo;
                    public final class RuntimeText {
                        static String format(int value) {
                            return String.valueOf(value);
                        }
                        static int inspect(String value) {
                            if (value == null) return 0;
                            return value.length() + value.charAt(0);
                        }
                        public static void main(String[] args) {
                            int dynamic = inspect(format(-12));
                            int literal = inspect("ok");
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.RuntimeText", source);

            // When
            CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.RuntimeText");

            // Then
            assertThat(result.assembly()).contains(".asciz \"ok\"",
                    "bl juno_string_value_of_int", "bl juno_string_length", "bl juno_string_char_at");
            assertThat(result.runtimeShim()).contains("static int32_t juno_string_copy(const char* text, uint32_t length)")
                    .doesNotContain("juno_string_slots");
        }

        @Test
        @DisplayName("Lowers String.valueOf a double with a typed argument")
        void shouldLowerStringValueOfDoubleWithATypedArgument() throws Exception {
            // Given
            String source = """
                    package demo;
                    public final class RuntimeDecimal {
                        static String format(double value) {
                            return String.valueOf(value);
                        }
                        public static void main(String[] args) {
                            String text = format(21.2);
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.RuntimeDecimal", source);

            // When
            CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.RuntimeDecimal");

            // Then
            assertThat(result.assembly()).contains("bl juno_string_value_of_double");
            assertThat(result.runtimeShim()).contains("extern \"C\" JUNO_ASM_ABI int32_t juno_string_value_of_double(double value)");
        }

        @Test
        @DisplayName("Lowers the string concat factory for strings, booleans and integers")
        void shouldLowerTheStringConcatFactoryForStringsBooleansAndIntegers() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.hardware.MCU;
                    import io.github.jabrena.juno.hardware.time.Clock;
                    import io.github.jabrena.juno.hardware.serial.Serial;
                    public final class RuntimeConcat {
                        public static void main(String[] args) {
                            String state = MCU.getClock().millis() > 0 ? "running" : "stopped";
                            boolean active = MCU.getClock().millis() > 0;
                            int count = MCU.getClock().millis();
                            String text = state + ":" + active + ":" + count;
                            Serial.println(text);
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.RuntimeConcat", source);

            // When
            CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.RuntimeConcat");

            // Then
            assertThat(result.assembly()).contains(
                    "bl juno_string_concat_new_sized",
                    "bl juno_string_concat_append_string",
                    "bl juno_string_concat_append_boolean",
                    "bl juno_string_concat_append_int",
                    "bl juno_serial_println_str");
            assertThat(result.runtimeShim()).contains(
                    "extern \"C\" int32_t juno_string_concat_new_sized(int32_t length)",
                    "extern \"C\" void juno_string_concat_append_boolean(int32_t handle, int32_t value)");
        }

        @Test
        @DisplayName("Even a short concatenation is measured and built in an arena String of its own")
        void shouldBuildAShortConcatenationInItsOwnArenaString() throws Exception {
            // Given: "n=" and an int, at most 13 bytes
            String source = """
                    package demo;
                    import io.github.jabrena.juno.hardware.MCU;
                    import io.github.jabrena.juno.hardware.serial.Serial;
                    public final class ShortConcat {
                        public static void main(String[] args) {
                            Serial.println("n=" + MCU.getClock().millis());
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.ShortConcat", source);

            // When
            CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.ShortConcat");

            // Then
            assertThat(result.assembly()).contains("bl juno_string_concat_measure_int", "bl juno_string_concat_new_sized");
            assertThat(result.runtimeShim()).doesNotContain("juno_string_slots");
        }

        @Test
        @DisplayName("A concatenation with a String argument is measured, then built in an exact arena block")
        void shouldMeasureAConcatenationWithAStringArgument() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.hardware.MCU;
                    import io.github.jabrena.juno.hardware.serial.Serial;
                    public final class LongConcat {
                        static String host() {
                            return MCU.getClock().millis() > 0 ? "192.168.1.10" : "10.0.0.1";
                        }
                        public static void main(String[] args) {
                            long id = MCU.getClock().millis();
                            Serial.println("https://" + host() + "/api/v1/mailbox/" + MCU.getClock().millis() + "/" + id);
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.LongConcat", source);

            // When
            CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.LongConcat");

            // Then
            assertThat(result.assembly()).contains("bl juno_string_concat_measure_string",
                    "bl juno_string_concat_measure_int", "bl juno_string_concat_measure_long",
                    "bl juno_string_concat_new_sized", "bl juno_string_concat_append_string");
            assertThat(result.runtimeShim()).contains(
                    "extern \"C\" int32_t juno_string_concat_new_sized(int32_t length)",
                    "extern \"C\" int32_t juno_string_concat_measure_string(int32_t length, const char* value)");
        }

        @Test
        @DisplayName("Lowers the string concat factory for wide and floating-point values")
        void shouldLowerTheStringConcatFactoryForWideAndFloatingPointValues() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.hardware.MCU;
                    import io.github.jabrena.juno.hardware.time.Clock;
                    import io.github.jabrena.juno.hardware.serial.Serial;
                    public final class NumericConcat {
                        public static void main(String[] args) {
                            String optional = MCU.getClock().millis() > 0 ? null : "ok";
                            char marker = '!';
                            long count = 7L;
                            float ratio = 1.5f;
                            double precise = 2.5;
                            String text = optional + ":" + marker + ":" + count + ":" + ratio + ":" + precise;
                            Serial.println(text);
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.NumericConcat", source);

            // When
            CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.NumericConcat");

            // Then
            assertThat(result.assembly()).contains(
                    "bl juno_string_concat_append_char",
                    "bl juno_string_concat_append_long",
                    "bl juno_string_concat_append_float",
                    "bl juno_string_concat_append_double");
            assertThat(result.runtimeShim()).contains(
                    "value != nullptr ? value : \"null\"",
                    "extern \"C\" void juno_string_concat_append_long(int32_t handle, int64_t value)",
                    "extern \"C\" JUNO_ASM_ABI void juno_string_concat_append_float(int32_t handle, float value)",
                    "extern \"C\" JUNO_ASM_ABI void juno_string_concat_append_double(int32_t handle, double value)");
        }

        @Test
        @DisplayName("Decodes string-concat factory constant placeholders")
        void shouldDecodeStringConcatFactoryConstantPlaceholders() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.hardware.MCU;
                    import io.github.jabrena.juno.hardware.time.Clock;
                    import io.github.jabrena.juno.hardware.serial.Serial;
                    public final class ConcatRecipeConstant {
                        public static void main(String[] args) {
                            int value = MCU.getClock().millis();
                            String text = "prefix\\1" + value;
                            Serial.println(text);
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.ConcatRecipeConstant", source);

            // When
            CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.ConcatRecipeConstant");

            // Then
            assertThat(result.assembly()).contains(
                    "bl juno_string_concat_append_string",
                    "bl juno_string_concat_append_int",
                    "bl juno_serial_println_str");
        }

        @Test
        @DisplayName("Prints a runtime serial string argument and keeps literals direct")
        void shouldPrintARuntimeSerialStringArgumentAndKeepLiteralsDirect() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.hardware.MCU;
                    import io.github.jabrena.juno.hardware.time.Clock;
                    import io.github.jabrena.juno.hardware.serial.BaudRate;
                    import io.github.jabrena.juno.hardware.serial.Serial;
                    public final class DynamicGreeting {
                        public static void main(String[] args) {
                            Serial.begin(BaudRate.BAUD_9600);
                            String message = MCU.getClock().millis() > 0 ? "yes" : "no";
                            Serial.println(message);
                            Serial.println("done");
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.DynamicGreeting", source);

            // When
            String assembly = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.DynamicGreeting").assembly();

            // Then
            // The literal still loads its .asciz address right before the call.
            assertThat(assembly).contains("ldr r0, =juno_str2\n    bl juno_serial_println_str");
            assertThat(countOccurrences(assembly, "bl juno_serial_println_str")).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("Networking")
    class Networking {
        @TempDir
        Path temporaryDirectory;

        @Test
        @DisplayName("Lowers Wi-Fi intrinsics with literal credentials")
        void shouldLowerWifiIntrinsicsWithLiteralCredentials() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.hardware.MCU;
                    import io.github.jabrena.juno.hardware.net.Wifi;
                    public final class WifiConnect {
                        public static void main(String[] args) {
                            MCU.getWifi().begin("TestNetwork-SSID", "test-password-123");
                            int status = MCU.getWifi().status();
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.WifiConnect", source);

            // When
            CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.WifiConnect");

            // Then
            assertThat(result.assembly()).contains(".asciz \"TestNetwork-SSID\"", ".asciz \"test-password-123\"",
                    "bl juno_wifi_begin", "bl juno_wifi_status");
            assertThat(result.runtimeShim()).contains("#include <WiFiS3.h>",
                    "extern \"C\" void juno_wifi_begin(const char* ssid, const char* password)",
                    "WiFi.begin(ssid, password);");
        }

        @Test
        @DisplayName("Lowers portable UDP intrinsics for the UNO R4 WiFi")
        void shouldLowerPortableUdpIntrinsicsForUnoR4Wifi() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
                    import io.github.jabrena.juno.annotations.Board;
                    import java.io.IOException;
                    import java.net.DatagramPacket;
                    import java.net.DatagramSocket;
                    import java.net.InetAddress;
                    @Board(ArduinoUnoR4WiFiBoard.class)
                    public final class PortableUdp {
                        public static void main(String[] args) {
                            byte[] payload = new byte[16];
                            try {
                                DatagramSocket socket = new DatagramSocket(5000);
                                InetAddress broadcast = InetAddress.getByAddress(
                                        new byte[] {(byte) 255, (byte) 255, (byte) 255, (byte) 255});
                                socket.send(new DatagramPacket(payload, 4, broadcast, 5000));
                                socket.setSoTimeout(10);
                                socket.receive(new DatagramPacket(payload, payload.length));
                                socket.close();
                            } catch (IOException e) {
                                payload[0] = 0;
                            }
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.PortableUdp", source);
            List<Path> classpath = CompilerTestSupport.classpath(temporaryDirectory);
            JunoCompiler compiler = new JunoCompiler();

            // When
            CompilationResult r4 = compiler.compile(classpath, "demo.PortableUdp", false,
                    Optional.of(Board.UNO_R4_WIFI.id()));

            // Then
            assertThat(r4.assembly()).contains("bl juno_datagram_socket_new", "bl juno_inet_address_get_by_address",
                    "bl juno_datagram_packet_new_send", "bl juno_datagram_socket_send",
                    "bl juno_datagram_socket_set_so_timeout", "bl juno_datagram_socket_receive",
                    "bl juno_datagram_socket_close");
            assertThat(r4.runtimeShim()).contains("#include <WiFiS3.h>", "static WiFiUDP juno_udp;",
                    "IPAddress(255, 255, 255, 255)", "juno_udp.beginMulticast(");
        }

        @Test
        @DisplayName("Lowers the legacy Kasa HS100 and HS110 requests for the UNO R4 WiFi")
        void shouldLowerLegacyKasaHs100AndHs110RequestsForUnoR4Wifi() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
                    import io.github.jabrena.juno.annotations.Board;
                    import io.github.jabrena.juno.hardware.device.kasa.KasaSmartPlug;
                    import java.io.IOException;
                    import java.net.DatagramPacket;
                    import java.net.DatagramSocket;
                    import java.net.InetAddress;
                    @Board(ArduinoUnoR4WiFiBoard.class)
                    public final class KasaPlug {
                        public static void main(String[] args) {
                            byte[] request = new byte[KasaSmartPlug.REQUEST_BUFFER_SIZE];
                            byte[] response = new byte[KasaSmartPlug.DEFAULT_RESPONSE_BUFFER_SIZE];
                            try {
                                DatagramSocket socket = KasaSmartPlug.openDiscovery();
                                DatagramPacket requestPacket = KasaSmartPlug.discoveryRequest(request);
                                DatagramPacket replyPacket = new DatagramPacket(response, response.length);
                                KasaSmartPlug.broadcastDiscovery(socket, requestPacket);
                                KasaSmartPlug.receiveDiscovery(socket, replyPacket);
                                InetAddress device = replyPacket.getAddress();
                                KasaSmartPlug.getSystemInfo(device, request, response, response.length);
                                KasaSmartPlug.turnOn(device, request, response, response.length);
                                KasaSmartPlug.turnOff(device, request, response, response.length);
                                socket.close();
                            } catch (IOException e) {
                                response[0] = 0;
                            }
                        }
                    }
                    """;
                        CompilerTestSupport.compileJava(temporaryDirectory, "demo.KasaPlug", source);
            List<Path> classpath = CompilerTestSupport.classpath(temporaryDirectory);
            JunoCompiler compiler = new JunoCompiler();

            // When
            CompilationResult r4 = compiler.compile(classpath, "demo.KasaPlug", false,
                    Optional.of(Board.UNO_R4_WIFI.id()));

            // Then
            assertThat(r4.assembly()).contains("bl juno_datagram_socket_new", "bl juno_datagram_socket_send",
                    "bl juno_datagram_socket_receive", "bl juno_socket_new", "bl juno_socket_output_stream_write",
                    "bl juno_socket_stream_read", "bl juno_socket_close");
            assertThat(r4.runtimeShim()).contains("#include <WiFiS3.h>", "using JunoTcpClient = WiFiClient;",
                    "extern \"C\" int32_t juno_tcp_open(")
                    .doesNotContain("juno_kasa");
        }

        @Test
        @DisplayName("Rejects board credentials from a compile-time environment variable")
        void shouldRejectWifiCredentialsFromACompileTimeEnvironmentVariable() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.hardware.MCU;
                    import io.github.jabrena.juno.hardware.net.Wifi;
                    public final class WifiConnectFromEnv {
                        public static void main(String[] args) {
                            MCU.getWifi().begin(System.getenv("PATH"), "password");
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.WifiConnectFromEnv", source);

            // When
            Throwable failure = catchThrowable(
                    () -> CompilerTestSupport.compileJuno(temporaryDirectory, "demo.WifiConnectFromEnv"));

            // Then
            assertThat(failure)
                    .isInstanceOf(CompileException.class)
                    .hasMessageContaining("System.getenv")
                    .hasMessageContaining("SecretStore")
                    .hasMessageContaining("firmware");
        }
    }

    @Nested
    @DisplayName("SD card storage and files")
    class SdCardStorageAndFiles {
        @TempDir
        Path temporaryDirectory;

        @Test
        @DisplayName("Lowers the Cardputer's built-in SD card through MCU on the Cardputer")
        void shouldLowerTheCardputerSdCardThroughM5StackCardputer() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.annotations.Board;
                                        import io.github.jabrena.juno.hardware.MCU;
                    import io.github.jabrena.juno.hardware.device.sd.SdCard;
                    @Board(io.github.jabrena.juno.annotations.M5StackCardputerBoard.class)
                    public final class CardputerSd {
                        private static final SdCard SD = MCU.getSdCard();
                        public static void main(String[] args) {
                            if (SD.begin()) SD.exists("settings.txt");
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.CardputerSd", source);

            // When
            CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.CardputerSd");

            // Then
            assertThat(result.report().board()).isEqualTo(Board.M5_CARDPUTER);
            assertThat(result.assembly()).contains("call8 juno_sd_begin", "call8 juno_sd_exists");
            assertThat(result.runtimeShim()).contains("juno_sd.begin(juno_nano_pin(chipSelectPin))");
        }

        @Test
        @DisplayName("Lowers SD card files, properties and runtime Wi-Fi credentials")
        void shouldLowerSdCardFilesPropertiesAndRuntimeWifiCredentials() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.hardware.MCU;
                    import io.github.jabrena.juno.hardware.net.Wifi;
                    import io.github.jabrena.juno.hardware.device.sd.DataLoggerShield;
                    import io.github.jabrena.juno.hardware.device.sd.SdCard;
                    import java.io.IOException;
                    import java.io.InputStream;
                    import java.util.Properties;
                    public final class SdConfiguration {
                        private static final SdCard SD_CARD = new DataLoggerShield().getSdCard();
                        public static void main(String[] args) throws IOException {
                            if (!SD_CARD.begin() || !SD_CARD.exists("application.properties")) return;
                            InputStream file = SD_CARD.open("application.properties");
                            if (file == null) return;
                            if (file.available() > 0) {
                                Properties properties = new Properties();
                                properties.load(file);
                                file.close();
                                if (properties.size() > 0) {
                                    String ssid = properties.getProperty("wifi.ssid");
                                    String password = properties.getProperty("wifi.password", "");
                                    if (ssid != null) MCU.getWifi().begin(ssid, password);
                                }
                            } else {
                                file.read();
                                file.close();
                            }
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.SdConfiguration", source);

            // When
            CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.SdConfiguration");

            // Then
            assertThat(result.assembly()).contains(
                    "bl juno_sd_begin", "bl juno_sd_exists", "bl juno_sd_open",
                    "bl juno_sd_file_available", "bl juno_sd_file_read",
                    "bl juno_sd_file_close", "bl juno_properties_new", "bl juno_properties_load",
                    "bl juno_properties_get", "bl juno_properties_get_default",
                    "bl juno_properties_size", "bl juno_wifi_begin");
            assertThat(result.runtimeShim()).contains(
                    "#include <SdFat.h>", "JUNO_PROPERTIES_MAX_ENTRIES = 16",
                    "JUNO_SD_BEGIN_ATTEMPTS = 3", "juno_io_wait(JUNO_SD_BEGIN_RETRY_DELAY_MS)", "JunoIoGuard guard(JUNO_IO_STORAGE);",
                    "extern \"C\" int32_t juno_properties_new()",
                    "extern \"C\" void juno_properties_load(int32_t propertiesHandle, int32_t fileHandle)");
            assertThat(result.report().runtimeRisks().estimatedArenaBytes())
                    .isGreaterThanOrEqualTo(RuntimeLimits.PROPERTIES_STORAGE_BYTES);
        }

        @Test
        @DisplayName("Lowers buffered SD input stream reads")
        void shouldLowerBufferedSdInputStreamReads() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.hardware.device.sd.DataLoggerShield;
                    import io.github.jabrena.juno.hardware.device.sd.SdCard;
                    import java.io.IOException;
                    import java.io.InputStream;
                    public final class BufferedSdRead {
                        private static final SdCard SD_CARD = new DataLoggerShield().getSdCard();
                        public static void main(String[] args) throws IOException {
                            if (!SD_CARD.begin()) return;
                            InputStream file = SD_CARD.open("payload.bin");
                            if (file == null) return;
                            byte[] buffer = new byte[128];
                            file.read(buffer, 0, buffer.length);
                            file.close();
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.BufferedSdRead", source);

            // When
            CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.BufferedSdRead");

            // Then
            assertThat(result.assembly()).contains("bl juno_sd_file_read_bytes", "bl juno_sd_file_close");
            assertThat(result.runtimeShim()).contains(
                    "extern \"C\" int32_t juno_sd_file_read_bytes(",
                    "juno_sd_file(handle)->read(buffer + offset");
        }

        /**
         * {@code SdCard.appendLine}'s path must stay a compile-time literal like every other SD path, but its
         * {@code line} argument must accept a runtime value built from {@code StringBuilder#toString()} —
         * a fixed path with a computed line is exactly the shape a high-score log needs.
         */
        @Test
        @DisplayName("Append line accepts a runtime line built from a StringBuilder")
        void shouldAppendALineWithARuntimeLineBuiltFromAStringBuilder() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.hardware.MCU;
                    import io.github.jabrena.juno.hardware.time.Clock;
                    import io.github.jabrena.juno.hardware.device.sd.DataLoggerShield;
                    import io.github.jabrena.juno.hardware.device.sd.SdCard;
                    public final class HighScoreLog {
                        private static final SdCard SD_CARD = new DataLoggerShield().getSdCard();
                        public static void main(String[] args) {
                            SD_CARD.begin();
                            StringBuilder line = new StringBuilder(16);
                            line.append("JAB-");
                            String score = String.valueOf(MCU.getClock().millis());
                            for (int i = 0; i < score.length(); i++) {
                                line.append(score.charAt(i));
                            }
                            SD_CARD.appendLine("tempest-high-scores.txt", line.toString());
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.HighScoreLog", source);

            // When
            CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.HighScoreLog");

            // Then
            assertThat(result.assembly()).contains(".asciz \"tempest-high-scores.txt\"");
            // The runtime line from toString() must flow into append's 2nd argument register (r1) right
            // after the toString() call computes it, while the path loads its literal address into r1 —
            // never as a .asciz body literal of its own.
            assertThat(result.assembly()).containsPattern(
                    "(?s)bl juno_string_builder_to_string\\n.*\\n\\s*ldr r0, =juno_str\\d+\\n"
                            + "\\s*ldr r1, \\[sp, #\\d+]\\n\\s*bl juno_sd_file_append_line\\n");
            assertThat(result.runtimeShim()).contains(
                    "extern \"C\" int32_t juno_sd_file_append_line(const char* path, const char* line)");
        }

        /**
         * The exemption above is scoped to exactly {@code appendLine}'s {@code line} parameter — its
         * {@code path} parameter must still be rejected when it isn't a compile-time literal, proving
         * {@code requiresLiteralStringArgument} didn't loosen the whole method.
         */
        @Test
        @DisplayName("Still rejects a non-literal SD card append-line path")
        void shouldStillRejectANonLiteralSdCardAppendLinePath() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.hardware.MCU;
                    import io.github.jabrena.juno.hardware.time.Clock;
                    import io.github.jabrena.juno.hardware.device.sd.DataLoggerShield;
                    import io.github.jabrena.juno.hardware.device.sd.SdCard;
                    public final class DynamicHighScorePath {
                        private static final SdCard SD_CARD = new DataLoggerShield().getSdCard();
                        public static void main(String[] args) {
                            SD_CARD.begin();
                            String path = MCU.getClock().millis() > 0 ? "a.txt" : "b.txt";
                            SD_CARD.appendLine(path, "JAB-5000");
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.DynamicHighScorePath", source);

            // When & Then
            assertThatThrownBy(() -> CompilerTestSupport.compileJuno(temporaryDirectory, "demo.DynamicHighScorePath"))
                    .isInstanceOf(CompileException.class);
        }

        @Test
        @DisplayName("Lowers SD card append-text without adding a newline")
        void shouldLowerSdCardAppendTextWithoutANewline() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.hardware.device.sd.DataLoggerShield;
                    import io.github.jabrena.juno.hardware.device.sd.SdCard;
                    public final class SaveState {
                        private static final SdCard SD_CARD = new DataLoggerShield().getSdCard();
                        public static void main(String[] args) {
                            SD_CARD.begin();
                            SD_CARD.remove("state.txt");
                            SD_CARD.appendText("state.txt", "score=");
                            SD_CARD.appendText("state.txt", String.valueOf(42));
                            SD_CARD.appendText("state.txt", "\\n");
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.SaveState", source);

            // When
            CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.SaveState");

            // Then
            assertThat(result.assembly()).contains("bl juno_sd_remove", "bl juno_sd_file_append_text",
                    ".asciz \"state.txt\"");
            assertThat(result.runtimeShim()).contains(
                    "extern \"C\" int32_t juno_sd_file_append_text(const char* path, const char* text)")
                    .doesNotContain("O_TRUNC");
            // appendText writes with print(), appendLine with println(): only the latter adds a newline.
            assertThat(result.runtimeShim()).containsPattern(
                    "(?s)juno_sd_file_append_text\\(.*?file\\.print\\(text\\);.*?juno_sd_remove");
            assertThat(result.runtimeShim()).containsPattern(
                    "(?s)juno_sd_file_append_line\\(.*?file\\.println\\(line\\);");
        }

        @Test
        @DisplayName("Still rejects a non-literal SD card append-text path")
        void shouldStillRejectANonLiteralSdCardAppendTextPath() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.hardware.MCU;
                    import io.github.jabrena.juno.hardware.time.Clock;
                    import io.github.jabrena.juno.hardware.device.sd.DataLoggerShield;
                    import io.github.jabrena.juno.hardware.device.sd.SdCard;
                    public final class DynamicStatePath {
                        private static final SdCard SD_CARD = new DataLoggerShield().getSdCard();
                        public static void main(String[] args) {
                            SD_CARD.begin();
                            String path = MCU.getClock().millis() > 0 ? "a.txt" : "b.txt";
                            SD_CARD.appendText(path, "x");
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.DynamicStatePath", source);

            // When & Then
            assertThatThrownBy(() -> CompilerTestSupport.compileJuno(temporaryDirectory, "demo.DynamicStatePath"))
                    .isInstanceOf(CompileException.class);
        }

        @Test
        @DisplayName("Lowers SD card size")
        void shouldLowerSdCardSize() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.hardware.device.sd.DataLoggerShield;
                    import io.github.jabrena.juno.hardware.device.sd.SdCard;
                    import io.github.jabrena.juno.hardware.serial.Serial;
                    public final class LogSize {
                        private static final SdCard SD_CARD = new DataLoggerShield().getSdCard();
                        public static void main(String[] args) {
                            SD_CARD.begin();
                            if (SD_CARD.size("log.txt") > 1024) {
                                SD_CARD.remove("log.txt");
                            }
                            Serial.println(SD_CARD.size("log.txt"));
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.LogSize", source);

            // When
            CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.LogSize");

            // Then
            assertThat(result.assembly()).contains("bl juno_sd_size", ".asciz \"log.txt\"");
            assertThat(result.runtimeShim()).contains("extern \"C\" int32_t juno_sd_size(const char* path)",
                    "if (!file) return -1;", "file.size()").doesNotContain("juno_sd_free_kib");
        }

        @Test
        @DisplayName("Lowers SD card remove")
        void shouldLowerSdCardRemove() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.hardware.device.sd.DataLoggerShield;
                    import io.github.jabrena.juno.hardware.device.sd.SdCard;
                    public final class DeleteHighScoreLog {
                        private static final SdCard SD_CARD = new DataLoggerShield().getSdCard();
                        public static void main(String[] args) {
                            SD_CARD.begin();
                            SD_CARD.remove("tempest-high-scores.txt");
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.DeleteHighScoreLog", source);

            // When
            CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.DeleteHighScoreLog");

            // Then
            assertThat(result.assembly()).contains("bl juno_sd_remove", ".asciz \"tempest-high-scores.txt\"");
            assertThat(result.runtimeShim()).contains("extern \"C\" int32_t juno_sd_remove(const char* path)");
        }

        @Test
        @DisplayName("Lowers folder creation and folder file operations with runtime names")
        void shouldLowerFolderCreationAndFolderFileOperations() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.hardware.device.sd.DataLoggerShield;
                    import io.github.jabrena.juno.hardware.device.sd.SdCard;
                    import java.io.InputStream;
                    public final class MailboxStorage {
                        private static final SdCard SD_CARD = new DataLoggerShield().getSdCard();
                        public static void main(String[] args) throws Exception {
                            SD_CARD.begin();
                            String directory = "mailbox/20261025";
                            String fileStem = "5500e840-e29b-41d4-a716-446655440000";
                            byte[] entry = new byte[48];
                            SD_CARD.createDirectory(directory);
                            SD_CARD.list("/mailbox", 0, entry, entry.length);
                            SD_CARD.removeDirectory(directory);
                            SD_CARD.appendText(directory, fileStem, "message");
                            InputStream file = SD_CARD.open(directory, fileStem);
                            if (file != null) file.close();
                            SD_CARD.remove(directory, fileStem);
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.MailboxStorage", source);

            // When
            CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.MailboxStorage");

            // Then
            assertThat(result.assembly()).contains(
                    "bl juno_sd_create_directory", "bl juno_sd_file_append_file_text",
                    "bl juno_sd_list", "bl juno_sd_remove_directory", "bl juno_sd_open_file", "bl juno_sd_remove_file");
            assertThat(result.runtimeShim()).contains(
                    "extern \"C\" int32_t juno_sd_create_directory(const char* path)",
                    "static constexpr int32_t JUNO_SD_LIST_CURSOR_COUNT = 2",
                    "static JunoSdListCursor* juno_sd_list_cursor(const char* directory, int32_t index)",
                    "extern \"C\" int32_t juno_sd_remove_directory(const char* path)",
                    "extern \"C\" int32_t juno_sd_file_append_file_text(const char* directory, const char* fileStem, const char* text)",
                    "extern \"C\" int32_t juno_sd_open_file(const char* directory, const char* fileStem)",
                    "extern \"C\" int32_t juno_sd_remove_file(const char* directory, const char* fileStem)",
                    "memcpy(target + directoryLength + 1u + stemLength, \".txt\", 5u)");
        }

        /**
         * {@code java.io.RandomAccessFile} reads an SD card file at any offset, the way a WAD directory is walked: seek,
         * fill a local buffer, skip, and every failure surfaces as the JDK's own exception in the program's handlers.
         */
        @Test
        @DisplayName("Lowers RandomAccessFile reads with the JDK exception types")
        void shouldLowerRandomAccessFileReadsWithJdkExceptions() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.hardware.device.sd.DataLoggerShield;
                    import io.github.jabrena.juno.hardware.device.sd.SdCard;
                    import io.github.jabrena.juno.hardware.serial.Serial;
                    import java.io.EOFException;
                    import java.io.FileNotFoundException;
                    import java.io.IOException;
                    import java.io.RandomAccessFile;
                    public final class WadHeader {
                        private static final SdCard SD_CARD = new DataLoggerShield().getSdCard();
                        public static void main(String[] args) {
                            SD_CARD.begin();
                            // Declared before the try: javac reuses a try block's local slots for its catch variables.
                            byte[] header = new byte[12];
                            try (RandomAccessFile wad = new RandomAccessFile("DOOM1.WAD", "r")) {
                                wad.readFully(header);
                                wad.seek(wad.length() - 16);
                                int read = wad.read(header, 4, 8);
                                wad.skipBytes(4);
                                Serial.println(read + wad.read() + (int) wad.getFilePointer() + header[0]);
                            } catch (FileNotFoundException missing) {
                                Serial.println(missing.getMessage());
                            } catch (EOFException truncated) {
                                Serial.println("truncated");
                            } catch (IOException failed) {
                                Serial.println(failed.getMessage());
                            }
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.WadHeader", source);

            // When
            CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.WadHeader");

            // Then
            assertThat(result.assembly()).contains(".asciz \"DOOM1.WAD\"", "bl juno_raf_open", "bl juno_raf_read_fully",
                    "bl juno_raf_length", "bl juno_raf_seek", "bl juno_raf_read_bytes", "bl juno_raf_skip_bytes",
                    "bl juno_raf_read", "bl juno_raf_get_file_pointer", "bl juno_raf_close", "bl juno_throw_pending");
            // readFully(byte[]) reads the whole local buffer: offset 0 and its compile-time length 12 as the length.
            assertThat(result.assembly()).containsPattern("(?s)movs? r3, #0\\n.*?bl juno_raf_read_fully");
            assertThat(result.runtimeShim()).contains("#include <SdFat.h>",
                    "extern \"C\" int32_t juno_raf_open(const char* path)",
                    "juno_raf_raise(JUNO_RAF_FILE_NOT_FOUND, path);", "juno_raf_raise(JUNO_RAF_EOF, nullptr);",
                    "extern \"C\" int64_t juno_raf_length(int32_t handle)",
                    "\"java.io.EOFException\"", "\"java.io.FileNotFoundException\"", "\"java.io.IOException\"");
        }

        /** An open file is passed to helper methods, the way a WAD reader splits the work per lump. */
        @Test
        @DisplayName("Passes a RandomAccessFile to a helper method")
        void shouldPassARandomAccessFileToAHelperMethod() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.hardware.serial.Serial;
                    import java.io.IOException;
                    import java.io.RandomAccessFile;
                    public final class WadLumps {
                        public static void main(String[] args) {
                            byte[] entry = new byte[16];
                            try (RandomAccessFile wad = new RandomAccessFile("DOOM1.WAD", "r")) {
                                Serial.println(directoryOffset(wad, entry));
                            } catch (IOException failed) {
                                Serial.println("no WAD");
                            }
                        }

                        private static int directoryOffset(RandomAccessFile wad, byte[] buffer) throws IOException {
                            wad.seek(8);
                            wad.readFully(buffer, 0, 4);
                            return (buffer[0] & 0xFF) | (buffer[1] & 0xFF) << 8 | (buffer[2] & 0xFF) << 16 | buffer[3] << 24;
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.WadLumps", source);

            // When
            CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.WadLumps");

            // Then
            // The helper's IOException reaches main's handler through the caller's pending-exception poll.
            assertThat(result.assembly()).contains("bl juno_raf_seek", "bl juno_raf_read_fully",
                    "bl juno_throw_pending");
        }

        @Test
        @DisplayName("Rejects a writable RandomAccessFile")
        void shouldRejectAWritableRandomAccessFile() throws Exception {
            // Given
            String source = """
                    package demo;
                    import java.io.IOException;
                    import java.io.RandomAccessFile;
                    public final class WritableWad {
                        public static void main(String[] args) throws IOException {
                            RandomAccessFile wad = new RandomAccessFile("DOOM1.WAD", "rw");
                            wad.close();
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.WritableWad", source);

            // When & Then
            assertThatThrownBy(() -> CompilerTestSupport.compileJuno(temporaryDirectory, "demo.WritableWad"))
                    .isInstanceOf(CompileException.class)
                    .hasMessageContaining("RandomAccessFile mode \"rw\" is not supported");
        }

        /** {@code read(byte[])} takes its length from the array, which only a local constant-size array has here. */
        @Test
        @DisplayName("Rejects a whole-buffer read into an array of unknown length")
        void shouldRejectAWholeBufferReadIntoAnArrayOfUnknownLength() throws Exception {
            // Given
            String source = """
                    package demo;
                    import java.io.IOException;
                    import java.io.RandomAccessFile;
                    public final class FieldBuffer {
                        private static byte[] buffer;
                        public static void main(String[] args) throws IOException {
                            byte[] local = new byte[16];
                            buffer = local;
                            RandomAccessFile wad = new RandomAccessFile("DOOM1.WAD", "r");
                            wad.read(buffer);
                            wad.close();
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.FieldBuffer", source);

            // When & Then
            assertThatThrownBy(() -> CompilerTestSupport.compileJuno(temporaryDirectory, "demo.FieldBuffer"))
                    .isInstanceOf(CompileException.class)
                    .hasMessageContaining("pass (buffer, offset, length)");
        }
    }

    @Nested
    @DisplayName("Memory capacity, flash footprint and environment")
    class MemoryCapacityAndEnvironment {
        @TempDir
        Path temporaryDirectory;

        @Test
        @DisplayName("Lowers the arena capacity")
        void shouldLowerTheArenaCapacity() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.hardware.MCU;
                    import io.github.jabrena.juno.hardware.serial.Serial;
                    public final class ArenaRoom {
                        public static void main(String[] args) {
                            Serial.println(MCU.getBoardMemory().arenaCapacityBytes() - MCU.getBoardMemory().arenaUsedBytes());
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.ArenaRoom", source);

            // When
            CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.ArenaRoom");

            // Then
            assertThat(result.assembly()).contains("bl juno_memory_arena_capacity", "bl juno_memory_arena_used");
            assertThat(result.runtimeShim()).contains("return static_cast<int32_t>(sizeof(juno_arena));");
        }

        @Test
        @DisplayName("Lowers the flash footprint from the core's linker layout")
        void shouldLowerTheFlashFootprintFromTheCoresLinkerLayout() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.hardware.MCU;
                    import io.github.jabrena.juno.hardware.serial.Serial;
                    public final class FlashRoom {
                        public static void main(String[] args) {
                            Serial.println(MCU.getBoardMemory().flashCapacityBytes() - MCU.getBoardMemory().flashUsedBytes());
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.FlashRoom", source);

            // When
            CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.FlashRoom");

            // Then
            assertThat(result.assembly()).contains("bl juno_memory_flash_capacity", "bl juno_memory_flash_used");
            assertThat(result.runtimeShim())
                    .contains("__etext - __ROM_Start", "return 262144;")
                    .doesNotContain("juno_memory_arena_used");
        }

        @Test
        @DisplayName("Lowers the SD card capacity only when the program asks for it")
        void shouldLowerSdCardCapacityOnlyWhenTheProgramAsksForIt() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.hardware.device.sd.DataLoggerShield;
                    import io.github.jabrena.juno.hardware.device.sd.SdCard;
                    import io.github.jabrena.juno.hardware.serial.Serial;
                    public final class SdRoom {
                        private static final SdCard SD_CARD = new DataLoggerShield().getSdCard();
                        public static void main(String[] args) {
                            SD_CARD.begin();
                            Serial.println(SD_CARD.totalKiB());
                            Serial.println(SD_CARD.freeKiB());
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.SdRoom", source);

            // When
            CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.SdRoom");

            // Then
            assertThat(result.assembly()).contains("bl juno_sd_total_kib", "bl juno_sd_free_kib");
            assertThat(result.runtimeShim()).contains(
                    "extern \"C\" int32_t juno_sd_free_kib()", "juno_io_offload(juno_sd_free_kib_job, &result);");
            assertThat(result.runtimeShim()).doesNotContain("juno_memory_flash_used");
        }

        @Test
        @DisplayName("Rejects an unset compile-time environment variable")
        void shouldRejectAnUnsetCompileTimeEnvironmentVariable() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.hardware.MCU;
                    import io.github.jabrena.juno.hardware.net.Wifi;
                    public final class WifiConnectFromMissingEnv {
                        public static void main(String[] args) {
                            MCU.getWifi().begin(System.getenv("JUNO_TEST_WIFI_SSID_NOT_SET"), "password");
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.WifiConnectFromMissingEnv", source);
            assertThat(System.getenv("JUNO_TEST_WIFI_SSID_NOT_SET"))
                    .as("test environment must not define JUNO_TEST_WIFI_SSID_NOT_SET")
                    .isNull();

            // When & Then
            assertThatThrownBy(() -> CompilerTestSupport.compileJuno(temporaryDirectory, "demo.WifiConnectFromMissingEnv"))
                    .isInstanceOf(CompileException.class);
        }
    }

    @Nested
    @DisplayName("HTTP and HTTPS requests")
    class HttpAndHttps {
        @TempDir
        Path temporaryDirectory;

    }

    @Nested
    @DisplayName("Email, HTTP server and string responses")
    class EmailAndHttpServer {
        @TempDir
        Path temporaryDirectory;

        @Test
        @DisplayName("Lowers the SMTP and POP3 email intrinsics")
        void shouldLowerEmailIntrinsics() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.hardware.net.email.Pop3Client;
                    import io.github.jabrena.juno.hardware.net.email.Smtp;
                    public final class EmailDemo {
                        public static void main(String[] args) {
                            int sent = Smtp.send("mail.example.com", 587, "alerts@example.com", "secret",
                                    "alerts@example.com", "me@example.com", "Arduino alert", "Alarm activated");
                            int sentTls = Smtp.sendTls("mail.example.com", 465, "alerts@example.com", "secret",
                                    "alerts@example.com", "me@example.com", "Arduino alert", "Alarm activated");

                            int count = Pop3Client.messageCount("mail.example.com", 995, "me@example.com", "secret");

                            byte[] headers = new byte[Pop3Client.DEFAULT_HEADERS_BUFFER_SIZE];
                            byte[] body = new byte[Pop3Client.DEFAULT_BODY_BUFFER_SIZE];
                            int[] status = new int[1];
                            int bodyLength = Pop3Client.readLatest("mail.example.com", 995, "me@example.com", "secret",
                                    headers, headers.length, body, body.length, status);

                            byte[] subject = new byte[16];
                            int subjectLength = Pop3Client.readSubject("mail.example.com", 995, "me@example.com", "secret",
                                    1, subject, subject.length);
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.EmailDemo", source);

            // When
            CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.EmailDemo");

            // Then
            assertThat(result.assembly()).contains(
                    "bl juno_smtp_send", "bl juno_smtp_send_tls", "bl juno_pop3_message_count", "bl juno_pop3_read_latest",
                    "bl juno_pop3_read_subject");
            assertThat(result.runtimeShim()).contains(
                    "#include <WiFiS3.h>", "#include <WiFiSSLClient.h>", "#include <ESP_SSLClient.h>",
                    "extern \"C\" int32_t juno_smtp_send", "extern \"C\" int32_t juno_smtp_send_tls",
                    "extern \"C\" int32_t juno_pop3_message_count",
                    "extern \"C\" int32_t juno_pop3_read_latest", "extern \"C\" int32_t juno_pop3_read_subject",
                    "client.print(\"STARTTLS\\r\\n\")", "client.connectSSL()");
        }

        @Test
        @DisplayName("Checks the STARTTLS server's certificate chain and dates on the Nano ESP32")
        void shouldVerifyTheStarttlsCertificateChainOnTheNanoEsp32() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
                    import io.github.jabrena.juno.annotations.Board;
                    import io.github.jabrena.juno.hardware.net.email.Smtp;
                    @Board(ArduinoNanoEsp32Board.class)
                    public final class NanoStarttls {
                        public static void main(String[] args) {
                            int sent = Smtp.send("mail.example.com", 587, "alerts@example.com", "secret",
                                    "alerts@example.com", "me@example.com", "Arduino alert", "Alarm activated");
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.NanoStarttls", source);

            // When
            CompilationResult result = new JunoCompiler().compileTo(
                    CompilerTestSupport.classpath(temporaryDirectory), "demo.NanoStarttls",
                    temporaryDirectory.resolve("NanoStarttls.S"), temporaryDirectory.resolve("NanoStarttlsShim.cpp"));

            // Then
            assertThat(result.runtimeShim()).contains(
                    "static const char juno_smtp_ca[]", "-----BEGIN CERTIFICATE-----",
                    "static bool juno_esp_tls_clock() {",
                    "if (!juno_esp_tls_clock()) return -13;",
                    "client.setCACert(juno_smtp_ca);")
                    .doesNotContain("client.setInsecure();");
        }

        @Test
        @DisplayName("Lowers the HTTP server intrinsics and string equality")
        void shouldLowerHttpServerIntrinsicsAndStringEquals() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.hardware.internal.NativeHttpServer;
                    public final class HttpServerDemo {
                        public static void main(String[] args) {
                            NativeHttpServer.begin(80);
                            byte[] body = new byte[128];
                            int bodyLength = NativeHttpServer.accept(body, body.length);
                            if (bodyLength >= 0) {
                                String method = NativeHttpServer.method();
                                String path = NativeHttpServer.path();
                                if (NativeHttpServer.contentLength() > body.length) {
                                    NativeHttpServer.respond(413, "application/json", "{}");
                                } else if (method.equals("GET") && path.equals("/status")) {
                                    NativeHttpServer.respond(200, "application/json", "{\\"ok\\":true}");
                                } else {
                                    StringBuilder json = new StringBuilder(32);
                                    json.append('{').append('}');
                                    NativeHttpServer.respond(404, "application/json", json.toString());
                                }
                            }
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.HttpServerDemo", source);

            // When
            CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.HttpServerDemo");

            // Then
            assertThat(result.assembly()).contains(".asciz \"GET\"", ".asciz \"/status\"",
                    ".asciz \"application/json\"", ".asciz \"{\\\"ok\\\":true}\"",
                    "bl juno_http_server_begin", "bl juno_http_server_accept", "bl juno_http_server_method",
                    "bl juno_http_server_path", "bl juno_http_server_content_length", "bl juno_string_equals",
                    "bl juno_http_server_respond");
            assertThat(result.runtimeShim()).contains("#include <WiFiS3.h>", "#include <new>",
                    "extern \"C\" void juno_http_server_begin(int32_t port)",
                    "extern \"C\" int32_t juno_http_server_accept(",
                    "extern \"C\" int32_t juno_http_server_method()",
                    "extern \"C\" int32_t juno_http_server_path()",
                    "extern \"C\" int32_t juno_http_server_content_length()",
                    "if (hasContentLength) juno_http_server_content_length_value = contentLength;",
                    "extern \"C\" void juno_http_server_respond(",
                    "extern \"C\" int32_t juno_string_equals(int32_t a, int32_t b)",
                    "return static_cast<int32_t>(reinterpret_cast<intptr_t>(juno_http_server_path_buf));");
            // A long path (e.g. /api/v1/mailbox/<uuid>) must come back whole, not null.
            assertThat(result.runtimeShim()).doesNotContain("juno_http_server_pool_copy");
            // A server that never reads a header gets neither the header buffer nor a leftover hook.
            assertThat(result.runtimeShim()).doesNotContain("juno_http_server_headers", "${JUNO_HTTP_SERVER_HEADER");
        }

        @Test
        @DisplayName("Lowers NativeHttpServer.header to a case-insensitive lookup in the captured request headers")
        void shouldLowerHttpServerHeaderLookup() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.hardware.internal.NativeHttpServer;
                    public final class HttpServerHeaderDemo {
                        public static void main(String[] args) {
                            NativeHttpServer.begin(80);
                            byte[] body = new byte[64];
                            byte[] key = new byte[32];
                            if (NativeHttpServer.accept(body, body.length) >= 0) {
                                int keyLength = NativeHttpServer.header("Mailbox-API-Key", key, key.length);
                                NativeHttpServer.respond(keyLength < 0 ? 401 : 200, "application/json", "{}");
                            }
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.HttpServerHeaderDemo", source);

            // When
            CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.HttpServerHeaderDemo");

            // Then
            assertThat(result.assembly()).contains("bl juno_http_server_header", ".asciz \"Mailbox-API-Key\"");
            assertThat(result.runtimeShim()).contains(
                    "static char juno_http_server_headers_buf[384];",
                    "  juno_http_server_headers_length = 0;",
                    "juno_http_server_headers_buf[juno_http_server_headers_length++] = c;",
                    "extern \"C\" int32_t juno_http_server_header(const char* name, uint8_t* out, int32_t outLength)",
                    "lines[lineEnd] != '\\n'")
                    .doesNotContain("${JUNO_HTTP_SERVER_HEADER");
        }

        @Test
        @DisplayName("Rejects NativeHttpServer.beginTls on the UNO R4 WiFi, which has no TLS server")
        void shouldRejectHttpServerBeginTlsOnTheUnoR4Wifi() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.hardware.internal.NativeHttpServer;
                    public final class HttpsServerOnR4 {
                        public static void main(String[] args) {
                            byte[] certificate = new byte[64];
                            byte[] key = new byte[64];
                            NativeHttpServer.beginTls(443, certificate, 64, key, 64);
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.HttpsServerOnR4", source);

            // When & Then
            assertThatThrownBy(() -> CompilerTestSupport.compileJuno(temporaryDirectory, "demo.HttpsServerOnR4"))
                    .isInstanceOf(CompileException.class)
                    .hasMessageContaining("UNO R4 WiFi cannot run a TLS server session");
        }

        /**
         * {@code NativeHttpServer.respond(int, String, String)}'s {@code body} is the one intrinsic {@code
         * String} parameter in all of Juno exempt from the compile-time-literal requirement (see
         * {@link io.github.jabrena.juno.intrinsic.IntrinsicRegistry#requiresLiteralStringArgument}) —
         * a runtime value built from {@code StringBuilder#toString()} must flow into it exactly like a
         * literal would, as a plain {@code const char*} argument rather than a {@code .asciz} literal.
         */
        @Test
        @DisplayName("Lowers an HTTP server response with a runtime string body")
        void shouldLowerHttpServerRespondWithARuntimeStringBody() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.hardware.MCU;
                    import io.github.jabrena.juno.hardware.time.Clock;
                    import io.github.jabrena.juno.hardware.internal.NativeHttpServer;
                    public final class HttpServerRuntimeBody {
                        public static void main(String[] args) {
                            NativeHttpServer.begin(80);
                            StringBuilder json = new StringBuilder(32);
                            json.append("{\\"uptime_ms\\":");
                            String uptime = String.valueOf(MCU.getClock().millis());
                            for (int i = 0; i < uptime.length(); i++) {
                                json.append(uptime.charAt(i));
                            }
                            json.append('}');
                            NativeHttpServer.respond(200, "application/json", json.toString());
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.HttpServerRuntimeBody", source);

            // When
            CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.HttpServerRuntimeBody");

            // Then
            // The runtime String from toString() must flow into respond's 3rd argument register (r2),
            // right after the toString() call itself computes it — never as a .asciz body literal.
            assertThat(result.assembly()).containsPattern(
                    "(?s)bl juno_string_builder_to_string.*ldr r2, \\[sp, #\\d+]\\n\\s*bl juno_http_server_respond\\n");
        }

        /**
         * The exemption above is scoped to exactly one parameter of one intrinsic — {@code
         * contentType} (the same method's other {@code String} parameter) must still be a literal,
         * proving {@code requiresLiteralStringArgument} didn't accidentally loosen the whole method.
         */
        @Test
        @DisplayName("Still rejects a non-literal HTTP server content type")
        void shouldStillRejectANonLiteralHttpServerContentType() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.hardware.MCU;
                    import io.github.jabrena.juno.hardware.time.Clock;
                    import io.github.jabrena.juno.hardware.internal.NativeHttpServer;
                    public final class HttpServerDynamicContentType {
                        public static void main(String[] args) {
                            NativeHttpServer.begin(80);
                            String contentType = MCU.getClock().millis() > 0 ? "application/json" : "text/plain";
                            NativeHttpServer.respond(200, contentType, "{}");
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.HttpServerDynamicContentType", source);

            // When & Then
            assertThatThrownBy(() -> CompilerTestSupport.compileJuno(temporaryDirectory, "demo.HttpServerDynamicContentType"))
                    .isInstanceOf(CompileException.class);
        }
    }

    @Nested
    @DisplayName("StringBuilder and JSON")
    class StringBuilderAndJson {
        @TempDir
        Path temporaryDirectory;

        @Test
        @DisplayName("Lowers StringBuilder construction, append and toString")
        void shouldLowerStringBuilderConstructionAppendAndToString() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.hardware.serial.Serial;
                    public final class StringBuilderDemo {
                        public static void main(String[] args) {
                            StringBuilder builder = new StringBuilder(8);
                            builder.append('1');
                            builder.append("!!");
                            String text = builder.toString();
                            Serial.println(text.length());
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.StringBuilderDemo", source);

            // When
            CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.StringBuilderDemo");

            // Then
            // Construction: real allocation (juno_string_builder_new), never a discarded placeholder.
            assertThat(result.assembly()).contains("bl juno_string_builder_new", "bl juno_string_builder_append_char",
                    "bl juno_string_builder_append_string", "bl juno_string_builder_to_string", "bl juno_string_length");
            assertThat(result.runtimeShim()).contains(
                    "extern \"C\" int32_t juno_string_builder_new(int32_t capacity)",
                    "static uint8_t* juno_string_builder_buffer(int32_t handle)",
                    // The runtime-string helpers are linked with the builder, though toString() allocates by itself.
                    "static int32_t juno_string_copy(const char* text, uint32_t length)");
        }

        @Test
        @DisplayName("StringBuilder.append(String) takes a runtime String, and still passes a literal by address")
        void shouldAppendARuntimeStringToAStringBuilder() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.hardware.MCU;
                    import io.github.jabrena.juno.hardware.serial.Serial;
                    public final class RuntimeAppend {
                        public static void main(String[] args) {
                            String host = MCU.getClock().millis() > 0 ? "192.168.1.10" : "10.0.0.1";
                            StringBuilder builder = new StringBuilder(64);
                            builder.append("https://");
                            builder.append(host);
                            builder.append(String.valueOf(MCU.getClock().millis()));
                            Serial.println(builder.toString());
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.RuntimeAppend", source);

            // When
            CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.RuntimeAppend");

            // Then: the literal is still a .asciz address; the runtime values are passed as String handles
            assertThat(result.assembly()).containsPattern("ldr r1, =juno_str\\d+\\s+bl juno_string_builder_append_string");
            assertThat(result.assembly().split("bl juno_string_builder_append_string", -1)).hasSize(4);
            assertThat(result.runtimeShim()).contains("text != nullptr ? text : \"null\"");
        }

        @Test
        @DisplayName("Compiles JSON parsing from Java rather than a shim scanner")
        void shouldCompileJsonFromJavaRatherThanAShimScanner() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.hardware.net.http.Json;
                    import io.github.jabrena.juno.hardware.net.http.JsonValue;
                    public final class JsonDemo {
                        public static void main(String[] args) {
                            byte[] buffer = new byte[64];
                            byte[] name = new byte[32];
                            JsonValue root = Json.parse(buffer, buffer.length);
                            int valueType = root.get("items").get(0).get("value").type();
                            int temperature = root.get("data").get("sensor").get("temp").asInt();
                            long sequence = root.get("sequence").asLong();
                            double precise = root.get("precise").asDouble();
                            boolean ok = root.get("ok").asBoolean();
                            int nameLength = root.get("name").copyString(name);
                            int itemCount = root.get("items").size();
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.JsonDemo", source);

            // When
            CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.JsonDemo");

            // Then
            assertThat(result.assembly()).as("JSON parsing alone needs no WiFi").doesNotContain("WiFiS3.h");
            assertThat(result.assembly()).contains(".asciz \"sensor\"", "bl juno_fn")
                    .doesNotContain("bl juno_json");
            assertThat(result.runtimeShim()).doesNotContain("juno_json", "juno_string_from_bytes");
        }

        @Test
        @DisplayName("Lowers a JSON value's text from a buffer through the string-from-bytes intrinsic")
        void shouldLowerJsonGetStringValueThroughTheStringFromBytesIntrinsic() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.hardware.net.http.Json;
                    public final class JsonStringValueDemo {
                        public static void main(String[] args) {
                            byte[] buffer = new byte[64];
                            String temperature = Json.parse(buffer, buffer.length).get("temp").toString();
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.JsonStringValueDemo", source);

            // When
            CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.JsonStringValueDemo");

            // Then
            assertThat(result.assembly()).contains("bl juno_string_from_bytes");
            assertThat(result.runtimeShim()).contains("extern \"C\" int32_t juno_string_from_bytes(",
                    "juno_string_copy(reinterpret_cast<const char*>(bytes + start)");
        }

        /** {@code JsonValue.asString()} is unreachable here, so its native helper is left out. */
        @Test
        @DisplayName("Omits the string-from-bytes helper when only other JSON methods are used")
        void shouldOmitStringFromBytesHelperWhenOnlyOtherJsonMethodsAreUsed() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.hardware.net.http.Json;
                    public final class JsonIntOnlyDemo {
                        public static void main(String[] args) {
                            byte[] buffer = new byte[64];
                            int temperature = Json.parse(buffer, buffer.length).get("temp").asInt();
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.JsonIntOnlyDemo", source);

            // When
            CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.JsonIntOnlyDemo");

            // Then
            assertThat(result.assembly()).doesNotContain("juno_string_from_bytes");
            assertThat(result.runtimeShim()).doesNotContain("juno_string_from_bytes");
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource({"ArduinoUnoR3Board, UNO R3"})
        @DisplayName("JSON is rejected below the UNO R4 WiFi, the smallest board that runs it")
        void shouldRejectJsonBelowTheUnoR4Wifi(String annotation, String displayName) throws Exception {
            // Given
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.JsonSmallBoard", """
                    package demo;
                    import io.github.jabrena.juno.annotations.%s;
                    import io.github.jabrena.juno.annotations.Board;
                    import io.github.jabrena.juno.hardware.net.http.Json;
                    import io.github.jabrena.juno.hardware.serial.Serial;
                    @Board(%s.class)
                    public final class JsonSmallBoard {
                        public static void main(String[] args) {
                            Serial.println(Json.parse("{\\"n\\":1}").get("n").asInt());
                        }
                    }
                    """.formatted(annotation, annotation));

            // When / Then
            assertThatThrownBy(() -> CompilerTestSupport.compileJuno(temporaryDirectory, "demo.JsonSmallBoard"))
                    .isInstanceOf(CompileException.class)
                    .hasMessageContaining(displayName + " has too little memory for JSON support; the UNO R4 WiFi is"
                            + " the smallest board that runs it");
        }

        @Test
        @DisplayName("JSON compiles for the UNO R4 WiFi")
        void shouldCompileJsonForTheUnoR4Wifi() throws Exception {
            // Given
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.JsonR4", """
                    package demo;
                    import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
                    import io.github.jabrena.juno.annotations.Board;
                    import io.github.jabrena.juno.hardware.net.http.Json;
                    import io.github.jabrena.juno.hardware.net.http.JsonWriter;
                    import io.github.jabrena.juno.hardware.serial.Serial;
                    @Board(ArduinoUnoR4WiFiBoard.class)
                    public final class JsonR4 {
                        public static void main(String[] args) {
                            byte[] body = new byte[32];
                            JsonWriter writer = new JsonWriter(body).beginObject().name("n").value(1).endObject();
                            Serial.println(Json.parse(body, writer.length()).get("n").asInt());
                        }
                    }
                    """);

            // When
            CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.JsonR4");

            // Then
            assertThat(result.assembly()).contains("bl juno_fn");
        }

        @Test
        @DisplayName("Omits the HTTP and JSON helpers when they are unused")
        void shouldOmitHttpAndJsonHelpersWhenUnused() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.util.Delay;
                    public final class NoNetworking {
                        public static void main(String[] args) {
                            Delay.millis(10);
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.NoNetworking", source);

            // When
            CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.NoNetworking");

            // Then
            assertThat(result.assembly()).doesNotContain("bl juno_http", "bl juno_fn");
            assertThat(result.runtimeShim()).doesNotContain("juno_http_request", "WiFiS3.h");
            assertThat(result.runtimeShim()).doesNotContain("juno_smtp_send", "juno_pop3_message_count", "ESP_SSLClient.h");
        }
    }

    @Nested
    @DisplayName("LEGO and HID peripherals")
    class LegoAndHid {
        @TempDir
        Path temporaryDirectory;

        @Test
        @DisplayName("Lowers the BLE radio intrinsics for a LEGO hub and omits the header when unused")
        void shouldLowerLegoPoweredUpIntrinsicsAndOmitUnusedHeader() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
                    import io.github.jabrena.juno.annotations.Board;
                    import io.github.jabrena.juno.hardware.MCU;
                    import io.github.jabrena.juno.remote.lego.poweredup.RemotePoweredUpHub;
                    import io.github.jabrena.juno.remote.lego.poweredup.HubColor;
                    import io.github.jabrena.juno.remote.lego.poweredup.BuiltInSensor;
                    import io.github.jabrena.juno.remote.lego.poweredup.PoweredUpPort;
                    @Board(ArduinoUnoR4WiFiBoard.class)
                    public final class Train {
                        public static void main(String[] args) {
                            RemotePoweredUpHub hub = new RemotePoweredUpHub();
                            if (hub.connect(10000) && hub.hubType() == RemotePoweredUpHub.TYPE_CITY_HUB) {
                                hub.setLedColor(HubColor.GREEN);
                                hub.getMotor(PoweredUpPort.A).drive(-50);
                                hub.getMotor(PoweredUpPort.A).stop();
                                hub.enableSensor(PoweredUpPort.B, RemotePoweredUpHub.MODE_MOTOR_POSITION);
                                hub.setLedRgb(hub.readSensor(PoweredUpPort.B), 0, 0);
                            }
                            if (hub.isConnected()) {
                                hub.disconnect();
                            }
                            hub.switchOff();
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.Train", source);

            // When
            CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Train");

            // Then
            assertThat(result.assembly()).contains("bl juno_ble_connect", "bl juno_ble_is_connected",
                    "bl juno_ble_write", "bl juno_ble_read", "bl juno_ble_disconnect",
                    "\"00001623-1212-efde-1623-785feabcd123\"");
            assertThat(result.runtimeShim()).contains("#include <ArduinoBLE.h>",
                    "extern \"C\" int32_t juno_ble_connect(const char* serviceUuid");

            String plainSource = """
                    package demo;
                    import io.github.jabrena.juno.hardware.MCU;
                    import io.github.jabrena.juno.hardware.gpio.Gpio;
                    public final class PlainBle {
                        public static void main(String[] args) {
                            MCU.getGpio().pinMode(13, MCU.getGpio().getOutput());
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.PlainBle", plainSource);

            CompilationResult plainResult = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.PlainBle");
            assertThat(plainResult.runtimeShim()).doesNotContain("ArduinoBLE.h", "juno_ble_");
        }

        @Test
        @DisplayName("Lowers the Mouse intrinsics and omits the header when unused")
        void shouldLowerMouseIntrinsicsAndOmitUnusedHeader() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.hardware.MCU;
                    import io.github.jabrena.juno.hardware.hid.Mouse;
                    public final class Wiggle {
                        public static void main(String[] args) {
                            Mouse mouse = MCU.getMouse();
                            mouse.begin();
                            mouse.move(50, -50);
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.Wiggle", source);

            // When
            CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Wiggle");

            // Then
            assertThat(result.assembly()).contains("bl juno_mouse_begin", "bl juno_mouse_move");
            assertThat(result.runtimeShim()).contains("#include <Mouse.h>");

            String plainSource = """
                    package demo;
                    import io.github.jabrena.juno.hardware.MCU;
                    import io.github.jabrena.juno.hardware.gpio.Gpio;
                    public final class Plain {
                        public static void main(String[] args) {
                            MCU.getGpio().pinMode(13, MCU.getGpio().getOutput());
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.Plain", plainSource);

            CompilationResult plainResult = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Plain");
            assertThat(plainResult.runtimeShim()).doesNotContain("Mouse.h", "juno_mouse_begin", "juno_parallel_bus");
        }

    }

    @Nested
    @DisplayName("Arrays, statics and constants")
    class ArraysStaticsAndConstants {
        @TempDir
        Path temporaryDirectory;

        @Test
        @DisplayName("Supports reference arrays")
        void shouldSupportReferenceArrays() throws Exception {
            // Given
            String source = """
                    package demo;
                    public final class Objects {
                        public static void main(String[] args) { Object[] x = new Object[3]; }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.Objects", source);

            // When
            String generated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Objects").assembly();

            // Then
            // 4-byte length word + 3 references * 4 bytes = 16, 4-byte aligned: the arena allocation for the array itself.
            assertThat(generated).contains("movs r0, #16\n    movs r1, #4\n    bl juno_alloc");
        }

        @Test
        @DisplayName("Needs no special handling for integer overflow, unlike the retired C++ backend")
        void shouldNeedNoSpecialHandlingForIntegerOverflowUnlikeTheRetiredCppBackend() throws Exception {
            // Given
            // The retired C++ backend had to route every int op through unsigned-cast helpers
            // (juno_imul/juno_ineg) to force wraparound instead of C++ signed-overflow UB. ARM registers
            // are already 32-bit two's complement, so the ASM backend emits the native instructions
            // directly -- no helper call, no special casing, overflow "just happens" correctly.
            String source = """
                    package demo;
                    import io.github.jabrena.juno.hardware.MCU;
                    public final class MathProgram {
                        static int calculate(int a, int b) { return -((a + b) * (a - b)); }
                        public static void main() {
                            io.github.jabrena.juno.util.Delay.millis(calculate(MCU.getGpio().analogRead(0),
                                    MCU.getGpio().analogRead(1)));
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.MathProgram", source);

            // When
            String generated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.MathProgram").assembly();

            // Then
            assertThat(generated).contains("add r0, r0, r1", "sub r0, r0, r1", "mul r0, r0, r1", "rsb r0, r0, #0");
        }

        @Test
        @DisplayName("Returns a report alongside the generated assembly")
        void shouldReturnAReportAlongsideTheGeneratedAssembly() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.hardware.MCU;
                    import io.github.jabrena.juno.hardware.gpio.Gpio;
                    import io.github.jabrena.juno.util.Delay;
                    public final class Reported {
                        static int addTo(int limit) {
                            int value = 0;
                            for (int i = 0; i < limit; i++) value += i;
                            return value;
                        }
                        public static void main(String[] args) {
                            MCU.getGpio().pinMode(13, MCU.getGpio().getOutput());
                            Delay.millis(addTo(4));
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.Reported", source);

            // When
            CompilationResult result = new JunoCompiler().compile(
                    new CompilationRequest(CompilerTestSupport.classpath(temporaryDirectory), "demo.Reported", false,
                            Optional.of(Board.UNO_R4_WIFI.id())));

            // Then
            // addTo is a small leaf (its loop included) and is inlined into main: only the NativeGpio static initializer
            // remains as a separate function.
            assertThat(result.assembly()).contains(".global juno_Reported_asm", "bl pinMode");
            assertThat(countOccurrences(result.assembly(), ".type juno_fn")).isEqualTo(1);
            assertThat(result.report().entryPoint().displayName()).isEqualTo("demo.Reported.main([Ljava/lang/String;)V");
            assertThat(result.report().reachableMethods())
                    .as("main, addTo, Gpio.board and NativeGpio's board, initializers and constructor, before inlining")
                    .isEqualTo(6);
            assertThat(result.report().irBlocks()).as("addTo's loop needs more than one block per method").isGreaterThan(2);
            assertThat(result.report().intrinsics()).isEqualTo(Set.of(Intrinsic.GPIO_PIN_MODE,
                    Intrinsic.GPIO_OUTPUT_MODE, Intrinsic.DELAY_MILLIS));
            assertThat(result.report().runtimeRisks().arenaCapacityBytes()).isEqualTo(8192);
            assertThat(result.report().runtimeRisks().estimatedMaxStackBytes()).isPositive();
        }

        @Test
        @DisplayName("Inlines a same-class static final int constant")
        void shouldInlineASameClassStaticFinalIntConstant() throws Exception {
            // Given
            // static final int fields initialized with a constant expression are compile-time constants
            // per JLS 4.12.4: javac inlines the literal at every use, same class or not, so this never
            // reaches the linker/backend as a getstatic - it's just an iconst/bipush/sipush like any
            // other literal. This mirrors juno-examples' Blink.java (`private static final int LED = 13`),
            // already flashed and verified on hardware; this test just locks the behavior in.
            String source = """
                    package demo;
                    public final class SameClassConstant {
                        private static final int LIMIT = 4;
                        static int addTo() {
                            int value = 0;
                            for (int i = 0; i < LIMIT; i++) value += i;
                            return value;
                        }
                        public static void main(String[] args) {
                            addTo();
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.SameClassConstant", source);

            // When
            String generated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.SameClassConstant").assembly();

            // Then
            assertThat(generated).contains(".global juno_SameClassConstant_asm");
            // javac must inline the constant: no static field (and hence no .bss slot) for it at all.
            // .bss itself is no longer a signal here: juno_gc_stack_top always gets one for the
            // conservative GC's stack scan, regardless of whether the program has static fields.
            assertThat(generated).doesNotContain("juno_static_");
            // The inlined constant is the loop test's immediate operand.
            assertThat(generated).contains("cmp r0, #4");
        }

        @Test
        @DisplayName("Inlines a cross-class static final int constant")
        void shouldInlineACrossClassStaticFinalIntConstant() throws Exception {
            // Given
            String declaringSource = """
                    package demo;
                    public final class Limits {
                        static final int MAX = 4;
                    }
                    """;
            String usingSource = """
                    package demo;
                    public final class CrossClassConstant {
                        static int addTo() {
                            int value = 0;
                            for (int i = 0; i < Limits.MAX; i++) value += i;
                            return value;
                        }
                        public static void main(String[] args) {
                            addTo();
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.Limits", declaringSource);
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.CrossClassConstant", usingSource);

            // When
            String generated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.CrossClassConstant").assembly();

            // Then
            assertThat(generated).contains(".global juno_CrossClassConstant_asm");
            // .bss itself is no longer a signal here: juno_gc_stack_top always gets one for the
            // conservative GC's stack scan, regardless of whether the program has static fields.
            assertThat(generated).doesNotContain("juno_static_");
            // The inlined constant is the loop test's immediate operand.
            assertThat(generated).contains("cmp r0, #4");
        }

        @Test
        @DisplayName("Supports default-initialized mutable static int and float fields")
        void shouldSupportDefaultInitializedMutableStaticIntAndFloatFields() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.util.Delay;
                    public final class MutableStatic {
                        static int counter;
                        static float scale;
                        public static void main(String[] args) {
                            counter = counter + 1;
                            scale = 2.5f;
                            Delay.millis((int) (scale * (float) counter));
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.MutableStatic", source);

            // When
            String generated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.MutableStatic").assembly();

            // Then
            assertThat(generated).contains("juno_static_demo_MutableStatic_counter_I:", "juno_static_demo_MutableStatic_scale_F:",
                    "ldr r0, =juno_static_demo_MutableStatic_counter_I", "ldr r1, =juno_static_demo_MutableStatic_scale_F");
        }

        @Test
        @DisplayName("Executes a reachable static field initializer before main")
        void shouldExecuteAReachableStaticFieldInitializerBeforeMain() throws Exception {
            // Given
            String source = """
                    package demo;
                    public final class ReadOnlyStatic {
                        static final int NOT_A_CONSTANT_EXPRESSION = compute();
                        static int compute() { return 5; }
                        public static void main(String[] args) {
                            int c = NOT_A_CONSTANT_EXPRESSION;
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.ReadOnlyStatic", source);

            // When
            String generated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.ReadOnlyStatic").assembly();

            // Then
            assertThat(generated).contains("juno_static_demo_ReadOnlyStatic_NOT_A_CONSTANT_EXPRESSION_I:");
            // The class initializer (juno_fn0) must run before the entry point's own first block.
            assertThat(generated.indexOf("bl juno_fn0"))
                    .isLessThan(generated.indexOf(".Ljuno_ReadOnlyStatic_asmblock0"));
        }

        @Test
        @DisplayName("Panics on a constant index past a known length and checks a variable index with one unsigned compare")
        void shouldPanicOnAConstantIndexPastAKnownLengthAndCheckAVariableOneWithOneUnsignedCompare() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.hardware.MCU;
                    import io.github.jabrena.juno.hardware.gpio.Gpio;
                    public final class ArrayEdges {
                        public static void main(String[] args) {
                            int[] pins = new int[3];
                            int index = MCU.getGpio().analogRead(0);
                            pins[index] = 1;
                            pins[3] = 2;
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.ArrayEdges", source);

            // When
            String generated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.ArrayEdges").assembly();

            // Then
            // The variable index: one unsigned compare catches both a negative index and one past the end.
            assertThat(generated).contains("cmp r0, #3\n    blo .Lbcok");
            // The constant index 3 into an int[3] can never be in range: it panics unconditionally.
            assertThat(countOccurrences(generated, "bl juno_panic")).isEqualTo(2);
        }

        @Test
        @DisplayName("Supports a local array with bounds-checked access and a constant-folded length")
        void shouldSupportALocalArrayWithBoundsCheckedAccessAndAConstantFoldedLength() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.hardware.MCU;
                    import io.github.jabrena.juno.hardware.gpio.Gpio;
                    public final class ArrayDemo {
                        static int sum(int[] values, int count) {
                            int total = 0;
                            for (int i = 0; i < count; i++) total += values[i];
                            return total;
                        }
                        public static void main(String[] args) {
                            int[] pins = new int[3];
                            pins[0] = 2;
                            pins[1] = 3;
                            pins[2] = 4;
                            int total = sum(pins, pins.length);
                            MCU.getGpio().pinMode(total, MCU.getGpio().getOutput());
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.ArrayDemo", source);

            // When
            String generated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.ArrayDemo").assembly();

            // Then
            // Length word + 3 ints * 4 bytes, 4-byte aligned: the local array must use arena storage.
            assertThat(generated).contains("movs r0, #16\n    movs r1, #4\n    bl juno_alloc");
            // The 3 writes into the 3-element local array use constant indices 0-2, which the compiler proves in range:
            // their bounds checks are decided at compile time and emit nothing; the only check left is sum's, which
            // compares its parameter's index against the stored length word.
            assertThat(countOccurrences(generated, "bl juno_panic")).isZero();
            assertThat(countOccurrences(generated, "bl juno_array_check")).isEqualTo(1);
            // sum's int[] parameter is a raw pointer (r0) with the explicit count as a second register (r1).
            assertThat(generated).contains("bl juno_fn1");
            // pins.length either folds to a compile-time constant or compileJuno throws (see the negative
            // test below); reaching this point at all already proves it resolved successfully.
        }

        @Test
        @DisplayName("Stores the length of multi-dimensional and compiler-created arrays in front of their elements")
        void shouldStoreTheLengthOfMultiDimensionalAndCompilerCreatedArraysInFrontOfTheirElements() throws Exception {
            // Given
            String source = """
                    package demo;
                    public final class StoredLengths {
                        enum Color { RED, GREEN, BLUE }
                        static int rows(int[][] grid) {
                            return grid.length + grid[0].length;
                        }
                        public static void main(String[] args) {
                            int total = rows(new int[2][3]) + Color.values().length;
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.StoredLengths", source);

            // When
            String generated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.StoredLengths").assembly();

            // Then
            // The outer array (2 rows) and each row (3 ints) carry a length word: 4 + 2 * 4 = 12 and 4 + 3 * 4 = 16.
            assertThat(generated).contains("movs r0, #12\n    movs r1, #4\n    bl juno_alloc\n    movs r1, #2\n"
                    + "    str r1, [r0]\n    adds r0, r0, #4");
            assertThat(generated).contains("movs r0, #16\n    movs r1, #4\n    bl juno_alloc\n    movs r1, #3\n"
                    + "    str r1, [r0]\n    adds r0, r0, #4");
            assertThat(generated).as("the loaded length word sits right in front of the elements")
                    .contains("ldr r0, [r0, #-4]");
        }

        @Test
        @DisplayName("Rejects a non-constant array length")
        void shouldRejectANonConstantArrayLength() throws Exception {
            // Given
            String source = """
                    package demo;
                    public final class NonConstLen {
                        public static void main(String[] args) {
                            int n = 5;
                            int[] arr = new int[n];
                            arr[0] = 1;
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.NonConstLen", source);

            // When & Then
            assertThatThrownBy(() -> CompilerTestSupport.compileJuno(temporaryDirectory, "demo.NonConstLen"))
                    .isInstanceOf(CompileException.class)
                    .hasMessageContaining("array length must be a compile-time constant");
        }

        @Test
        @DisplayName("Reads the stored length of an array received as a parameter")
        void shouldReadTheStoredLengthOfAnArrayReceivedAsAParameter() throws Exception {
            // Given
            // A parameter's array could have come from any caller with any length, so .length is one load of the
            // length word stored in front of its elements, and element accesses are checked against it.
            String source = """
                    package demo;
                    public final class LengthOnParam {
                        static int firstLength(int[] values) {
                            return values.length + values[0];
                        }
                        public static void main(String[] args) {
                            int[] a = new int[2];
                            firstLength(a);
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.LengthOnParam", source);

            // When
            String generated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.LengthOnParam").assembly();

            // Then
            assertThat(generated).contains("ldr r0, [r0, #-4]");
            assertThat(generated).as("values[0] is checked against the stored length by one shared helper")
                    .contains("    bl juno_array_check\n", "juno_array_check:\n    ldr r12, [r0, #-4]\n    cmp r1, r12\n");
            assertThat(countOccurrences(generated, "juno_array_check:")).isEqualTo(1);
        }

        @Test
        @DisplayName("Checks a reassigned array local against its stored length")
        void shouldCheckAReassignedArrayLocalAgainstItsStoredLength() throws Exception {
            // Given
            // arr is astore'd twice, so it is not "effectively final" and its length is not tracked: both stores
            // still compile, checked against the length stored with the array instead of a constant.
            String source = """
                    package demo;
                    public final class Reassigned {
                        public static void main(String[] args) {
                            int[] arr = new int[2];
                            arr[0] = 1;
                            arr = new int[3];
                            arr[0] = 2;
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.Reassigned", source);

            // When
            String generated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Reassigned").assembly();

            // Then
            assertThat(generated).as("a reassigned local is checked against its stored length, not a constant")
                    .contains("bl juno_array_check");
            // both writes must still compile, as raw pointer stores through a scaled index.
            assertThat(countOccurrences(generated, "lsls r1, r1, #2")).isEqualTo(2);
        }

        @Test
        @DisplayName("Supports byte, char and short arrays with their native storage width")
        void shouldSupportByteCharAndShortArraysWithTheirNativeStorageWidth() throws Exception {
            // Given
            String source = """
                    package demo;
                    public final class ElementTypes {
                        static int sumBytes(byte[] values, int count) {
                            int total = 0;
                            for (int i = 0; i < count; i++) total += values[i];
                            return total;
                        }
                        public static void main(String[] args) {
                            byte[] buf = new byte[4];
                            buf[0] = 10;
                            char[] chars = new char[3];
                            chars[0] = 'a';
                            short[] shorts = new short[2];
                            shorts[0] = 1000;
                            int total = sumBytes(buf, buf.length);
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.ElementTypes", source);

            // When
            String generated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.ElementTypes").assembly();

            // Then
            assertThat(generated).as("byte[4] must use 1-byte-aligned storage and a byte store")
                    .contains("movs r0, #8\n    movs r1, #1\n    bl juno_alloc", "strb r2, [r0, r1]");
            assertThat(generated).as("char[3] must use 2-byte-aligned storage (4 + 3 * 2 = 10) and a halfword store")
                    .contains("movs r0, #10\n    movs r1, #2\n    bl juno_alloc", "strh r2, [r0, r1]");
            assertThat(generated).as("short[2] must use 2-byte-aligned storage (4 + 2 * 2 = 8)")
                    .contains("movs r0, #8\n    movs r1, #2\n    bl juno_alloc");
            assertThat(generated).as("sumBytes's byte[] parameter must be read with a sign-extending byte load")
                    .contains("ldrsb r2, [r0, r1]");
        }

        @Test
        @DisplayName("Supports float arrays across calls and forwarded returns")
        void shouldSupportFloatArraysAcrossCallsAndForwardedReturns() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.util.Delay;
                    public final class FloatArrays {
                        static float sum(float[] values, int count) {
                            float total = 0.0f;
                            for (int i = 0; i < count; i++) total += values[i];
                            return total;
                        }
                        static float[] identity(float[] values) {
                            return values;
                        }
                        public static void main(String[] args) {
                            float[] samples = new float[3];
                            samples[0] = 1.25f;
                            samples[1] = 2.5f;
                            samples[2] = -0.75f;
                            float[] forwarded = identity(samples);
                            Delay.millis((int) sum(forwarded, samples.length));
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.FloatArrays", source);

            // When
            String assembly = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.FloatArrays").assembly();

            // Then
            // Four-byte elements: index scaled by 4, one word moved.
            assertThat(assembly).contains("lsls r1, r1, #2", "ldr r2, [r0, r1]", "str r2, [r0, r1]");
        }

        @Test
        @DisplayName("Supports long arrays across calls and forwarded returns")
        void shouldSupportLongArraysAcrossCallsAndForwardedReturns() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.util.Delay;
                    public final class LongArray {
                        static long[] identity(long[] values) {
                            return values;
                        }
                        public static void main(String[] args) {
                            long[] xs = new long[3];
                            xs[0] = 5L;
                            xs[1] = -2L;
                            long value = identity(xs)[0] + xs[1];
                            Delay.millis((int) value);
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.LongArray", source);

            // When
            String assembly = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.LongArray").assembly();

            // Then
            // Eight-byte elements: index scaled by 8, both words moved.
            assertThat(assembly).contains("lsls r1, r1, #3", "ldr r3, [r0, #4]", "str r3, [r0, #4]");
        }

        @Test
        @DisplayName("Supports forwarding a received array parameter as a return value")
        void shouldSupportForwardingAReceivedArrayParameterAsAReturnValue() throws Exception {
            // Given
            String source = """
                    package demo;
                    public final class ReturnForward {
                        static int[] pick(boolean useA, int[] a, int[] b) {
                            if (useA) {
                                return a;
                            }
                            return b;
                        }
                        public static void main(String[] args) {
                            int[] x = new int[2];
                            int[] y = new int[2];
                            int[] chosen = pick(true, x, y);
                            chosen[1] = 99;
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.ReturnForward", source);

            // When
            String generated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.ReturnForward").assembly();

            // Then
            // pick() returns one of two received arrays; it is a small leaf and is inlined into main.
            assertThat(countOccurrences(generated, "bl juno_alloc")).isEqualTo(2);
            assertThat(generated).doesNotContain("bl juno_fn");
        }

        @Test
        @DisplayName("Supports returning a locally allocated arena array")
        void shouldSupportReturningALocallyAllocatedArenaArray() throws Exception {
            // Given
            String source = """
                    package demo;
                    public final class ReturnLocal {
                        static int[] makeArray() {
                            int[] local = new int[3];
                            local[0] = 5;
                            return local;
                        }
                        public static void main(String[] args) {
                            int[] arr = makeArray();
                            arr[0] = 1;
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.ReturnLocal", source);

            // When
            String generated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.ReturnLocal").assembly();

            // Then
            assertThat(generated).contains("bl juno_fn1", "movs r0, #16\n    movs r1, #4\n    bl juno_alloc");
        }

        @Test
        @DisplayName("Supports returning an array after a branch merge")
        void shouldSupportReturningAnArrayAfterABranchMerge() throws Exception {
            // Given
            String source = """
                    package demo;
                    public final class ReturnTernary {
                        static int[] pick(boolean useA, int[] a, int[] b) {
                            return useA ? a : b;
                        }
                        public static void main(String[] args) {
                            int[] x = new int[2];
                            int[] y = new int[2];
                            pick(true, x, y);
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.ReturnTernary", source);

            // When
            String generated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.ReturnTernary").assembly();

            // Then
            // pick() merges two array references in a branch; it is a small leaf and is inlined into main.
            assertThat(countOccurrences(generated, "bl juno_alloc")).isEqualTo(2);
            assertThat(generated).doesNotContain("bl juno_fn");
        }
    }

    @Nested
    @DisplayName("Numeric types and java.lang.Math")
    class NumericTypesAndMath {
        @TempDir
        Path temporaryDirectory;

        @Test
        @DisplayName("Supports long locals with arithmetic, shifts, bitwise operations and comparisons")
        void shouldSupportLongLocalsWithArithmeticShiftsBitwiseAndComparisons() throws Exception {
            // Given
            String source = """
                    package demo;
                    public final class LongMath {
                        public static void main(String[] args) {
                            long acc = 0L;
                            for (long i = 0; i < 10; i++) {
                                acc += i;
                            }
                            long a = 123456789012L;
                            long b = -987654321098L;
                            long sum = a + b;
                            long diff = a - b;
                            long prod = a * 3L;
                            long quot = a / 7L;
                            long rem = a % 7L;
                            long neg = -a;
                            long shiftedLeft = a << 3;
                            long shiftedRight = b >> 2;
                            long shiftedUnsigned = b >>> 2;
                            long anded = a & b;
                            long ored = a | b;
                            long xored = a ^ b;
                            int fromLong = (int) a;
                            long fromInt = fromLong;
                            boolean less = a < b;
                            if (less && sum != diff) {
                                acc = acc + 1;
                            }
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.LongMath", source);

            // When
            String generated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.LongMath").assembly();

            // Then
            assertThat(generated).contains(".global juno_LongMath_asm",
                    "bl juno_ladd", "bl juno_lsub", "bl juno_lmul", "bl juno_ldiv", "bl juno_lrem", "bl juno_lneg",
                    "bl juno_lshl", "bl juno_lshr", "bl juno_lushr", "bl juno_land", "bl juno_lor", "bl juno_lxor",
                    "bl juno_lcmp");
        }

        @Test
        @DisplayName("Supports long method parameters, returns, fields and float conversions")
        void shouldSupportLongMethodParametersReturnsFieldsAndFloatConversions() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.util.Delay;
                    public final class LongParam {
                        static long saved;
                        static long identity(long x) {
                            return x;
                        }
                        public static void main(String[] args) {
                            long r = identity(5L);
                            float asFloat = (float) r;
                            saved = (long) asFloat;
                            Delay.millis((int) saved);
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.LongParam", source);

            // When
            CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.LongParam");

            // Then
            assertThat(result.assembly()).contains("juno_static_demo_LongParam_saved_J:", "bl juno_fn1",
                    "bl juno_l2f", "bl juno_f2l");
            assertThat(result.runtimeShim()).contains(
                    "extern \"C\" JUNO_ASM_ABI float juno_l2f(int64_t value)", "extern \"C\" JUNO_ASM_ABI int64_t juno_f2l(float value)");
        }

        @Test
        @DisplayName("Supports float locals with arithmetic, comparisons, conversions and loops")
        void shouldSupportFloatLocalsArithmeticComparisonsConversionsAndLoops() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.hardware.MCU;
                    import io.github.jabrena.juno.util.Delay;
                    import io.github.jabrena.juno.hardware.gpio.Gpio;
                    public final class FloatMath {
                        public static void main(String[] args) {
                            float total = 0.0f;
                            for (int i = 0; i < 4; i++) {
                                total += 0.75f;
                            }
                            float adjusted = -(total * 2.0f - 1.0f) / 2.0f;
                            float remainder = adjusted % 1.25f;
                            int narrowed = (int) remainder;
                            float widened = (float) narrowed;
                            float nan = 0.0f / 0.0f;
                            boolean ordered = widened < total && !(nan >= total);
                            MCU.getGpio().digitalWrite(13, ordered);
                            Delay.millis(narrowed);
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.FloatMath", source);

            // When
            CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.FloatMath");

            // Then
            assertThat(result.assembly()).contains("bl juno_fadd", "bl juno_fmul", "bl juno_fsub", "bl juno_fdiv",
                    "bl juno_frem", "bl juno_f2i", "bl juno_fcmp",
                    // unary float negation is a native sign-bit flip, not a shim call.
                    "eor r0, r0, #0x80000000");
            assertThat(result.runtimeShim()).contains("extern \"C\" JUNO_ASM_ABI int32_t juno_fcmp(",
                    // The board core builds the shim hard-float; assembly passes floats in core registers.
                    "#define JUNO_ASM_ABI __attribute__((pcs(\"aapcs\")))");
        }

        @Test
        @DisplayName("Lowers java.lang.Math to usage-gated shim helpers")
        void shouldLowerJavaLangMathToUsageGatedShimHelpers() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.hardware.MCU;
                    import io.github.jabrena.juno.hardware.time.Clock;
                    import io.github.jabrena.juno.hardware.serial.Serial;
                    public final class Geometry {
                        public static void main(String[] args) {
                            int x = MCU.getClock().millis();
                            double angle = Math.toRadians(x);
                            double length = Math.hypot(Math.cos(angle), Math.sin(angle));
                            long rounded = Math.round(length * 100.0);
                            int clamped = Math.clamp(rounded, 0, 255);
                            Serial.println(Math.max(Math.abs(x), clamped) + Math.floorMod(x, 7));
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.Geometry", source, "21");

            // When
            CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Geometry");

            // Then
            assertThat(result.assembly()).contains("bl juno_math_to_radians", "bl juno_math_cos", "bl juno_math_sin",
                    "bl juno_math_hypot", "bl juno_math_round_double", "bl juno_math_clamp_int",
                    "bl juno_math_abs_int", "bl juno_math_max_int", "bl juno_math_floor_mod_int");
            assertThat(result.runtimeShim()).contains("#include <math.h>",
                    "extern \"C\" JUNO_ASM_ABI double juno_math_hypot(double x, double y) { return hypot(x, y); }",
                    // clamp(long, int, int): the long occupies r0:r1, the int bounds r2 and r3.
                    "extern \"C\" JUNO_ASM_ABI int32_t juno_math_clamp_int(int64_t value, int32_t lo, int32_t hi)");
            // Only reached overloads are emitted.
            assertThat(result.runtimeShim()).doesNotContain("juno_math_pow", "juno_math_abs_long", "juno_math_tan(");
        }

        @Test
        @DisplayName("Aligns long Math arguments to an even register pair")
        void shouldAlignLongMathArgumentsToAnEvenRegisterPair() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.hardware.MCU;
                    import io.github.jabrena.juno.hardware.time.Clock;
                    import io.github.jabrena.juno.hardware.serial.Serial;
                    public final class WideClamp {
                        public static void main(String[] args) {
                            long value = MCU.getClock().millis();
                            Serial.println(Math.clamp(value, -10L, 10L));
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.WideClamp", source, "21");

            // When
            String assembly = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.WideClamp").assembly();

            // Then
            // Six argument words: value in r0:r1, lo in r2:r3, hi spilled to an 8-byte stack area.
            int call = assembly.indexOf("bl juno_math_clamp_long");
            assertThat(call).isPositive();
            assertThat(assembly.substring(0, call)).contains("sub sp, sp, #8");
        }

        @Test
        @DisplayName("Supports float method parameters and return values")
        void shouldSupportFloatMethodParametersAndReturnValues() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.util.Delay;
                    public final class FloatMethods {
                        static float mix(float left, int scale, float right) {
                            return left * (float) scale + right;
                        }
                        public static void main(String[] args) {
                            float result = mix(1.25f, 2, 0.5f);
                            Delay.millis((int) result);
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.FloatMethods", source);

            // When
            String generated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.FloatMethods").assembly();

            // Then
            // mix's (float, int, float) body is inlined into main; its float arithmetic still runs through the runtime.
            assertThat(generated).contains("bl juno_i2f", "bl juno_fmul", "bl juno_fadd").doesNotContain("bl juno_fn");
        }

        @Test
        @DisplayName("Supports double locals, calls, fields and conversions")
        void shouldSupportDoubleLocalsCallsFieldsAndConversions() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.util.Delay;
                    public final class DoubleMath {
                        static double last;
                        static double mix(double left, int scale, double right) {
                            return left * (double) scale + right;
                        }
                        public static void main(String[] args) {
                            double value = mix(1.25, 2, 0.5);
                            double remainder = -value % 1.25;
                            double nan = 0.0 / 0.0;
                            float narrowedFloat = (float) remainder;
                            int narrowedInt = (int) remainder;
                            long narrowedLong = (long) remainder;
                            double widenedInt = (double) narrowedInt;
                            double widenedFloat = (double) narrowedFloat;
                            double widenedLong = (double) narrowedLong;
                            last = remainder + widenedInt + widenedFloat + widenedLong;
                            Delay.millis(narrowedInt + (nan < value ? 1 : 0));
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.DoubleMath", source);

            // When
            CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.DoubleMath");

            // Then
            assertThat(result.assembly()).contains("juno_static_demo_DoubleMath_last_D:", "bl juno_fn1",
                    "bl juno_dmul", "bl juno_dadd", "bl juno_drem", "bl juno_d2f", "bl juno_d2i", "bl juno_d2l",
                    "bl juno_i2d", "bl juno_l2d", "bl juno_f2d");
            assertThat(result.runtimeShim()).contains(
                    "extern \"C\" JUNO_ASM_ABI double juno_drem(double a, double b) { return fmod(a, b); }");
            // A JVM local is one 4-byte slot; doubles use the two consecutive slots the JVMS reserves.
            // This guards the compact layout through the complete javac -> IR -> Thumb-2 path.
            assertThat(result.metrics().maximumFixedFrameBytes()).isLessThan(512);
        }

        @Test
        @DisplayName("Supports double arrays across calls and forwarded returns")
        void shouldSupportDoubleArraysAcrossCallsAndForwardedReturns() throws Exception {
            // Given
            String source = """
                    package demo;
                    public final class DoubleArray {
                        static double[] identity(double[] values) {
                            return values;
                        }
                        public static void main(String[] args) {
                            double[] inputs = new double[2];
                            inputs[0] = 1.25;
                            double[] values = identity(inputs);
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.DoubleArray", source);

            // When
            String assembly = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.DoubleArray").assembly();

            // Then
            assertThat(assembly).contains("lsls r1, r1, #3", "str r3, [r0, #4]");
        }
    }

    @Nested
    @DisplayName("Enums and records")
    class EnumsAndRecords {
        @TempDir
        Path temporaryDirectory;

        @Test
        @DisplayName("Supports enum constants as ordinal ints")
        void shouldSupportEnumConstantsAsOrdinalInts() throws Exception {
            // Given
            // Scoped deliberately to ordinal-int representation: an enum constant is never actually
            // constructed (no heap, no objects), it is just its 0-based declaration-order ordinal, a plain
            // int32_t. getstatic on a recognized enum constant resolves directly to that literal; ==/!=
            // (if_acmpeq/if_acmpne) then works for free, since equal ordinals are equal ints.
            String enumSource = """
                    package demo;
                    public enum Direction {
                        NORTH, SOUTH, EAST, WEST;
                    }
                    """;
            String usingSource = """
                    package demo;
                    public final class UsesEnum {
                        static int classify(Direction d) {
                            if (d == Direction.NORTH) {
                                return 0;
                            }
                            return -1;
                        }
                        public static void main(String[] args) {
                            Direction d = Direction.SOUTH;
                            boolean isNorth = d == Direction.NORTH;
                            boolean isSouth = d != Direction.NORTH;
                            int code = classify(Direction.NORTH);
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.Direction", enumSource);
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.UsesEnum", usingSource);

            // When
            String generated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.UsesEnum").assembly();

            // Then
            assertThat(generated).contains(".global juno_UsesEnum_asm");
            // an enum constant never allocates: no getstatic-equivalent static field/bss slot for Direction.
            // .bss itself is no longer a signal here: juno_gc_stack_top always gets one for the
            // conservative GC's stack scan, regardless of whether the program has static fields.
            assertThat(generated).doesNotContain("juno_static_");
            // classify(Direction.NORTH) receives the ordinal 0 as a plain int; it is a small leaf, inlined into main.
            assertThat(generated).doesNotContain("bl juno_fn");
        }

        @Test
        @DisplayName("Rejects reading an enum static field its static initializer assigns, which would read as null")
        void shouldRejectReadingAnEnumStaticFieldItsInitializerAssigns() throws Exception {
            // Given: the enum's <clinit> never runs on the board, so ALL would stay null and a read would fault
            String enumSource = """
                    package demo;
                    public enum Phase {
                        IDLE, RUNNING, DONE;
                        private static final Phase[] ALL = {IDLE, RUNNING, DONE};
                        static Phase of(int code) {
                            return code >= 0 && code < ALL.length ? ALL[code] : IDLE;
                        }
                    }
                    """;
            String usingSource = """
                    package demo;
                    public final class UsesPhaseTable {
                        public static void main() {
                            Phase phase = Phase.of(1);
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.Phase", enumSource);
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.UsesPhaseTable", usingSource);

            // When & Then
            assertThatThrownBy(() -> CompilerTestSupport.compileJuno(temporaryDirectory, "demo.UsesPhaseTable"))
                    .isInstanceOf(CompileException.class)
                    .hasMessageContaining("Enum static fields initialized in the enum are not supported")
                    .hasMessageContaining("demo.Phase.ALL");
        }

        @Test
        @DisplayName("Still reads an enum static field that methods assign, since it needs no static initializer")
        void shouldReadAnEnumStaticFieldThatMethodsAssign() throws Exception {
            // Given
            String enumSource = """
                    package demo;
                    public enum Counter {
                        ONLY;
                        private static int count;
                        static int next() {
                            count = count + 1;
                            return count;
                        }
                    }
                    """;
            String usingSource = """
                    package demo;
                    public final class UsesCounter {
                        public static void main() {
                            int first = Counter.next();
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.Counter", enumSource);
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.UsesCounter", usingSource);

            // When
            String generated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.UsesCounter").assembly();

            // Then
            assertThat(generated).contains("juno_static_demo_Counter_count");
        }

        @Test
        @DisplayName("Supports an integer value associated with each enum constant")
        void shouldSupportAnIntegerValueAssociatedWithEachEnumConstant() throws Exception {
            // Given
            String enumSource = """
                    package demo;
                    public enum Speed {
                        NORMAL(9600), FAST(115200);
                        private final int bitsPerSecond;
                        Speed(int bitsPerSecond) {
                            this.bitsPerSecond = bitsPerSecond;
                        }
                        public int bitsPerSecond() {
                            return bitsPerSecond;
                        }
                    }
                    """;
            String usingSource = """
                    package demo;
                    import io.github.jabrena.juno.util.Delay;
                    public final class UsesEnumValue {
                        public static void main(String[] args) {
                            Speed speed = Speed.FAST;
                            Delay.millis(speed.bitsPerSecond());
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.Speed", enumSource);
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.UsesEnumValue", usingSource);

            // When
            String generated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.UsesEnumValue").assembly();

            // Then
            // Each enum constant's associated value becomes one word in a constant-folded int[] table
            // (NORMAL=9600, FAST=115200), read via a bounds-checked array load, not a heap object.
            assertThat(generated).contains(".word 9600, 115200", "ldr r0, =juno_int_array0", "bl delay")
                    .doesNotContain("bl juno_alloc");
        }

        @Test
        @DisplayName("Supports simple records as locals with accessor reads")
        void shouldSupportSimpleRecordsAsLocalsWithAccessorReads() throws Exception {
            // Given
            String recordSource = """
                    package demo;
                    public record Point(int x, int y) {
                    }
                    """;
            String usingSource = """
                    package demo;
                    public final class UsesPoint {
                        static Point kept;
                        public static void main(String[] args) {
                            Point p = new Point(3, 4);
                            kept = p;  // escapes: stays a heap object with this layout
                            int total = p.x() + p.y();
                            io.github.jabrena.juno.util.Delay.millis(total);  // read, so the loads stay
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.Point", recordSource);
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.UsesPoint", usingSource);

            // When
            String generated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.UsesPoint").assembly();

            // Then
            assertThat(generated).contains(".global juno_UsesPoint_asm",
                    // Point(x, y): a 2-field, 8-byte, 4-byte-aligned arena object, x and y at offsets 0 and 4.
                    "movs r0, #8\n    movs r1, #4\n    bl juno_alloc",
                    "str r1, [r0, #0]", "str r1, [r0, #4]",
                    "ldr r1, [r0, #0]", "ldr r1, [r0, #4]");
        }

        @Test
        @DisplayName("Supports a record as a method parameter and return type")
        void shouldSupportARecordAsAMethodParameterAndReturnType() throws Exception {
            // Given
            String recordSource = """
                    package demo;
                    public record Point(int x, int y) {
                    }
                    """;
            String usingSource = """
                    package demo;
                    public final class TakesPoint {
                        static Point echo(Point p) {
                            return p;
                        }
                        static int sum(Point p) {
                            return p.x() + p.y();
                        }
                        public static void main(String[] args) {
                            sum(echo(new Point(1, 2)));
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.Point", recordSource);
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.TakesPoint", usingSource);

            // When
            String generated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.TakesPoint").assembly();

            // Then
            // echo, sum, the accessors and the canonical constructor are all small leaves and are inlined; the record
            // then never leaves main, so it is scalar-replaced: no allocation, and sum folds to a constant.
            assertThat(generated).doesNotContain("bl juno_alloc", "bl juno_fn");
        }

        @Test
        @DisplayName("Supports a record with a compact constructor")
        void shouldSupportARecordWithACompactConstructor() throws Exception {
            // Given
            String recordSource = """
                    package demo;
                    public record Point(int x, int y) {
                        public Point {
                            if (x < 0) x = -x;
                        }
                    }
                    """;
            String usingSource = """
                    package demo;
                    import io.github.jabrena.juno.hardware.MCU;
                    public final class UsesCompactPoint {
                        public static void main(String[] args) {
                            Point p = new Point(MCU.getGpio().analogRead(0) - 1000, 4);
                            int x = p.x();
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.Point", recordSource);
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.UsesCompactPoint", usingSource);

            // When
            String generated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.UsesCompactPoint").assembly();

            // Then
            // Native two's-complement negation for the compact constructor's `x = -x`, same as any other
            // int negation (see the integer-overflow test above) -- no special-casing for record bodies.
            assertThat(generated).contains("rsb r0, r0, #0");
        }

        @Test
        @DisplayName("Supports a record with a custom accessor override")
        void shouldSupportARecordWithACustomAccessorOverride() throws Exception {
            // Given
            String recordSource = """
                    package demo;
                    public record Point(int x, int y) {
                        @Override
                        public int x() {
                            return x * 2;
                        }
                    }
                    """;
            String usingSource = """
                    package demo;
                    public final class UsesCustomAccessor {
                        static Point kept;
                        public static void main(String[] args) {
                            Point p = new Point(3, 4);
                            kept = p;  // escapes: stays a heap object with this layout
                            io.github.jabrena.juno.util.Delay.millis(p.x());
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.Point", recordSource);
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.UsesCustomAccessor", usingSource);

            // When
            String generated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.UsesCustomAccessor").assembly();

            // Then
            // The override's x * 2 runs: a multiplication by a power of two becomes a shift.
            assertThat(generated).contains("lsl r0, r0, #1");
        }

        @Test
        @DisplayName("Supports a record with an array component")
        void shouldSupportARecordWithAnArrayComponent() throws Exception {
            // Given
            String recordSource = """
                    package demo;
                    public record Labeled(int[] data, int value) {
                    }
                    """;
            String usingSource = """
                    package demo;
                    public final class UsesLabeled {
                        static Labeled kept;
                        public static void main(String[] args) {
                            int[] data = new int[2];
                            Labeled l = new Labeled(data, 3);
                            kept = l;  // escapes: stays a heap object with this layout
                            int v = l.value();
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.Labeled", recordSource);
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.UsesLabeled", usingSource);

            // When
            String generated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.UsesLabeled").assembly();

            // Then
            // Labeled(int[] data, int value): a 2-field, 8-byte record whose first field is a pointer.
            assertThat(generated).contains("movs r0, #8\n    movs r1, #4\n    bl juno_alloc",
                    "str r1, [r0, #0]", "str r1, [r0, #4]");
        }

        @Test
        @DisplayName("Supports a record with String components")
        void shouldSupportARecordWithStringComponents() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.hardware.MCU;
                    import io.github.jabrena.juno.hardware.net.Wifi;
                    public final class UsesCredentials {
                        static Credentials kept;
                        private record Credentials(String ssid, String password) {
                        }
                        public static void main(String[] args) {
                            String runtimeSsid = String.valueOf(42);
                            Credentials credentials = new Credentials(runtimeSsid, "secret");
                            kept = credentials;  // escapes: stays a heap object with this layout
                            MCU.getWifi().begin(credentials.ssid(), credentials.password());
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.UsesCredentials", source);

            // When
            String generated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.UsesCredentials").assembly();

            // Then
            assertThat(generated).contains("movs r0, #8\n    movs r1, #4\n    bl juno_alloc",
                    "str r1, [r0, #0]", "str r1, [r0, #4]",
                    "ldr r1, [r0, #0]", "ldr r1, [r0, #4]", "bl juno_wifi_begin");
        }

        @Test
        @DisplayName("Supports generated record toString hashCode and equals")
        void shouldSupportGeneratedRecordObjectMethods() throws Exception {
            // Given
            String source = """
                    package demo;
                    public record Point(int x, String label) {
                    }
                    """;
            String usingSource = """
                    package demo;
                    public final class UsesGeneratedObjectMethods {
                        public static void main(String[] args) {
                            Point first = new Point(3, "east");
                            Point second = new Point(3, "east");
                            String text = first.toString();
                            int hash = first.hashCode();
                            boolean equal = first.equals(second);
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.Point", source);
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.UsesGeneratedObjectMethods", usingSource);

            // When
            String generated = CompilerTestSupport.compileJuno(temporaryDirectory,
                    "demo.UsesGeneratedObjectMethods").assembly();

            // Then
            assertThat(generated).contains(".global juno_UsesGeneratedObjectMethods_asm");
        }

        @Test
        @DisplayName("Rejects record identity calls through Object-typed references")
        void shouldRejectGeneratedRecordObjectMethodsThroughObjectReferences() throws Exception {
            // Given
            String source = """
                    package demo;
                    public record Point(int x) {
                        public static void main(String[] args) {
                            Object value = new Point(3);
                            String text = value.toString();
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.Point", source);

            // When & Then
            assertThatThrownBy(() -> CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Point"))
                    .isInstanceOf(CompileException.class)
                    .hasMessageContaining("Reachable class not found: java.lang.Object");
        }
    }

    @Nested
    @DisplayName("Objects, interfaces and lambdas")
    class ObjectsInterfacesAndLambdas {
        @TempDir
        Path temporaryDirectory;

        @Test
        @DisplayName("Supports constructing a final object with mutable fields and instance calls")
        void shouldSupportConstructingAFinalObjectWithMutableFieldsAndInstanceCalls() throws Exception {
            // Given
            String source = """
                    package demo;
                    public final class NewsObject {
                        static NewsObject kept;
                        private int value;
                        NewsObject(int value) { this.value = value; }
                        int add(int amount) { value += amount; return value; }
                        public static void main(String[] args) {
                            NewsObject value = new NewsObject(3);
                            kept = value;  // escapes: stays a heap object with this layout
                            int result = value.add(4);
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.NewsObject", source);

            // When
            String generated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.NewsObject").assembly();

            // Then
            // A single mutable int field: 4 bytes, 4-byte aligned. The constructor and add(amount) are small leaves and
            // are inlined: the field at offset 0 is written with 3, then read and written back by the addition.
            assertThat(generated).contains("movs r0, #4\n    movs r1, #4\n    bl juno_alloc", "str r1, [r0, #0]")
                    .doesNotContain("bl juno_fn");
        }

        @Test
        @DisplayName("Resolves an interface call directly when only one implementation is reachable")
        void shouldResolveAnInterfaceCallDirectlyWhenOnlyOneImplementationIsReachable() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.util.Delay;
                    public final class DirectInterface {
                        static Operation kept;
                        interface Operation { int apply(int value); }
                        static final class Increment implements Operation {
                            private int amount;
                            Increment(int amount) { this.amount = amount; }
                            public int apply(int value) { return value + amount; }
                        }
                        static final class Unused implements Operation {
                            public int apply(int value) { return value * 100; }
                        }
                        public static void main(String[] args) {
                            Operation operation = new Increment(3);
                            kept = operation;  // escapes: stays a heap object with this layout
                            Delay.millis(operation.apply(4));
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.DirectInterface", source);

            // When
            Program linked = CompilerTestSupport.link(temporaryDirectory, "demo.DirectInterface");
            IrProgram lowered = new CompilationPipeline().lower(linked);

            // Then
            assertThat(lowered.objectTypeIds()).isEmpty();
            assertThat(lowered.methods()).flatExtracting(method -> method.blocks()).flatExtracting(block -> block.instructions())
                    .noneMatch(IrInstruction.InterfaceCall.class::isInstance)
                    .anyMatch(instruction -> instruction instanceof IrInstruction.Call call
                            && call.method().owner().equals("demo/DirectInterface$Increment")
                            && call.method().name().equals("apply"));

            String assembly = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.DirectInterface").assembly();
            assertThat(assembly).contains("movs r0, #4\n    movs r1, #4\n    bl juno_alloc",
                    "str r1, [r0, #0]", "ldr r1, [r0, #0]")
                    .doesNotContain(".LinterfaceNext", "ldr r0, [r0, #0]\n    movs r1, #1\n    cmp r0, r1");
        }

        @Test
        @DisplayName("Inlines an injected Gpio's calls down to the GPIO intrinsics")
        void shouldInlineAnInjectedGpiosCallsDownToTheIntrinsics() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.hardware.MCU;
                    import io.github.jabrena.juno.hardware.gpio.Gpio;
                    public final class PortBlink {
                        private final Gpio gpio;
                        PortBlink(Gpio gpio) { this.gpio = gpio; }
                        void light(int pin) { gpio.pinMode(pin, gpio.getOutput()); gpio.digitalWrite(pin, true); }
                        public static void main(String[] args) {
                            new PortBlink(MCU.getGpio()).light(13);
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.PortBlink", source);

            // When
            Program linked = CompilerTestSupport.link(temporaryDirectory, "demo.PortBlink");
            IrProgram lowered = new CompilationPipeline().lower(linked);
            String assembly = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.PortBlink").assembly();

            // Then
            // Gpio has one reachable implementation, NativeGpio, whose methods are the intrinsics.
            assertThat(lowered.methods()).flatExtracting(method -> method.blocks()).flatExtracting(block -> block.instructions())
                    .noneMatch(IrInstruction.InterfaceCall.class::isInstance);
            assertThat(assembly).contains("bl pinMode", "bl digitalWrite").doesNotContain(".LinterfaceNext");
            // The interface calls are the intrinsics themselves, so light() is cheap enough to inline into main: only the
            // NativeGpio static initializer remains as a non-entry function.
            assertThat(countOccurrences(assembly, ".type juno_fn")).isEqualTo(2);
        }

        @Test
        @DisplayName("Lowers a non-capturing lambda to a function reference")
        void shouldLowerANonCapturingLambdaToAFunctionReference() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.util.Delay;
                    public final class NonCapturingLambda {
                        interface Operation { int apply(int value); }
                        public static void main(String[] args) {
                            Operation increment = value -> value + 1;
                            Delay.millis(increment.apply(4));
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.NonCapturingLambda", source);

            // When
            CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.NonCapturingLambda");

            // Then
            assertThat(result.report().reachableMethods()).isEqualTo(2);
            assertThat(result.assembly())
                    .contains("bl juno_fn1", "add r0, r0, #1")
                    .doesNotContain("bl juno_alloc");
        }

        @Test
        @DisplayName("Stores captured lambda values in a closure object")
        void shouldStoreCapturedLambdaValuesInAClosureObject() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.util.Delay;
                    public final class CapturingLambda {
                        interface Operation { int apply(int value); }
                        public static void main(String[] args) {
                            int amount = 3;
                            Operation add = value -> value + amount;
                            Delay.millis(add.apply(4));
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.CapturingLambda", source);

            // When
            String assembly = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.CapturingLambda").assembly();

            // Then
            assertThat(assembly).contains(
                    "movs r0, #8\n    movs r1, #4\n    bl juno_alloc",
                    "str r1, [r0, #4]",
                    "ldr r0, [r12, #4]",
                    "bl juno_fn1");
        }

        @Test
        @DisplayName("Lowers static, bound, unbound and constructor method references")
        void shouldLowerStaticBoundUnboundAndConstructorMethodReferences() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.util.Delay;
                    public final class MethodReferences {
                        interface Unary { int apply(int value); }
                        interface Binary { int apply(Adder adder, int value); }
                        interface Factory { Adder create(int amount); }
                        static final class Adder {
                            private int amount;
                            Adder(int amount) { this.amount = amount; }
                            int add(int value) { return value + amount; }
                        }
                        static int twice(int value) { return value * 2; }
                        static int run(Unary operation, int value) { return operation.apply(value); }
                        public static void main(String[] args) {
                            Unary twice = MethodReferences::twice;
                            Adder three = new Adder(3);
                            Unary addThree = three::add;
                            Binary unbound = Adder::add;
                            Factory factory = Adder::new;
                            Adder four = factory.create(4);
                            Delay.millis(run(twice, 2) + run(addThree, 2)
                                    + unbound.apply(four, 2));
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.MethodReferences", source);
            Program linked = CompilerTestSupport.link(temporaryDirectory, "demo.MethodReferences");
            IrProgram lowered = new CompilationPipeline().lower(linked);

            // When
            assertThat(lowered.objectTypeIds().keySet())
                    .anyMatch(name -> name.contains("$$JunoLambda$"));

            // Then
            String assembly = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.MethodReferences").assembly();
            assertThat(assembly)
                    .contains("juno_lambda_0:", ".LinterfaceDone", "ldr r0, [r12, #4]", "bl juno_alloc")
                    .doesNotContain("unresolved method");
        }

        @Test
        @DisplayName("Rejects unsupported generated record component types explicitly")
        void shouldRejectUnsupportedGeneratedRecordComponentTypesExplicitly() throws Exception {
            // Given
            String source = """
                    package demo;
                    public record StringConcatDynamic(int[] value) {
                        public static void main(String[] args) {
                            StringConcatDynamic value = new StringConcatDynamic(new int[] {3});
                            String text = value.toString();
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.StringConcatDynamic", source);

            // When & Then
            assertThatThrownBy(() -> CompilerTestSupport.compileJuno(temporaryDirectory, "demo.StringConcatDynamic"))
                    .isInstanceOf(CompileException.class)
                    .hasMessageContaining("demo.StringConcatDynamic.toString")
                    .hasMessageContaining("component value")
                    .hasMessageContaining("[I")
                    .hasMessageContaining("toString");
        }

        @Test
        @DisplayName("Supports a standard Runnable lambda without linking the JDK implementation")
        void shouldSupportAStandardRunnableLambdaWithoutLinkingTheJdkImplementation() throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.util.Delay;
                    public final class RunnableLambda {
                        public static void main(String[] args) {
                            Runnable task = () -> Delay.millis(1);
                            task.run();
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.RunnableLambda", source);

            // When
            String assembly = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.RunnableLambda").assembly();

            // Then
            assertThat(assembly).contains("juno_lambda_0:", "bl juno_fn1", "bl delay")
                    .doesNotContain("bl juno_alloc");
        }

        @Test
        @DisplayName("Dispatches an interface call by a deterministic object type id when several implementations are reachable")
        void shouldDispatchAnInterfaceCallByDeterministicObjectTypeIdWhenMultipleImplementationsAreReachable()
                throws Exception {
            // Given
            String source = """
                    package demo;
                    import io.github.jabrena.juno.hardware.MCU;
                    import io.github.jabrena.juno.util.Delay;
                    import io.github.jabrena.juno.hardware.gpio.Gpio;
                    public final class PolymorphicInterface {
                        interface Operation { int apply(int value); }
                        static final class Adder implements Operation {
                            private int operand;
                            Adder(int operand) { this.operand = operand; }
                            public int apply(int value) { return value + operand; }
                        }
                        static final class Multiplier implements Operation {
                            private int operand;
                            Multiplier(int operand) { this.operand = operand; }
                            public int apply(int value) { return value * operand; }
                        }
                        static Operation choose(boolean add) {
                            return add ? new Adder(2) : new Multiplier(3);
                        }
                        static int run(Operation operation, int value) {
                            return operation.apply(value);
                        }
                        public static void main(String[] args) {
                            Delay.millis(run(choose(MCU.getGpio().analogRead(0) > 0), 5));
                        }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.PolymorphicInterface", source);
            Program linked = CompilerTestSupport.link(temporaryDirectory, "demo.PolymorphicInterface");
            IrProgram lowered = new CompilationPipeline().lower(linked);

            // When
            assertThat(lowered.objectTypeIds())
                    .containsEntry("demo/PolymorphicInterface$Adder", 1)
                    .containsEntry("demo/PolymorphicInterface$Multiplier", 2);
            IrInstruction.InterfaceCall dispatch = lowered.methods().stream()
                    .flatMap(method -> method.blocks().stream())
                    .flatMap(block -> block.instructions().stream())
                    .filter(IrInstruction.InterfaceCall.class::isInstance)
                    .map(IrInstruction.InterfaceCall.class::cast)
                    .findFirst().orElseThrow();

            // Then
            assertThat(dispatch.targets()).extracting(target -> target.method().owner())
                    .containsExactly("demo/PolymorphicInterface$Adder", "demo/PolymorphicInterface$Multiplier");

            String assembly = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.PolymorphicInterface").assembly();
            assertThat(assembly).contains(
                    // One header word plus one field word; concrete field access skips the type id.
                    "movs r0, #8\n    movs r1, #4\n    bl juno_alloc",
                    "str r1, [r0, #0]", "str r1, [r0, #4]", "ldr r1, [r0, #4]",
                    // The interface receiver's header is compared with the deterministic ids 1 and 2.
                    "ldr r0, [r0, #0]", "movs r1, #1\n    cmp r0, r1",
                    "movs r1, #2\n    cmp r0, r1", ".LinterfaceDone");
        }

        @Test
        @DisplayName("Rejects an interface call with no reachable implementation")
        void shouldRejectAnInterfaceCallWithNoReachableImplementation() throws Exception {
            // Given
            String source = """
                    package demo;
                    public final class MissingInterfaceTarget {
                        interface Operation { int apply(int value); }
                        static int run(Operation operation) { return operation.apply(1); }
                        public static void main(String[] args) { run(null); }
                    }
                    """;
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.MissingInterfaceTarget", source);

            // When & Then
            assertThatThrownBy(() -> CompilerTestSupport.compileJuno(temporaryDirectory, "demo.MissingInterfaceTarget"))
                    .isInstanceOf(CompileException.class)
                    .hasMessageContaining("no reachable implementation")
                    .hasMessageContaining("demo.MissingInterfaceTarget$Operation.apply(I)I");
        }
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(needle, index)) != -1) {
            count++;
            index += needle.length();
        }
        return count;
    }
}

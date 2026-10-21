package io.github.jabrena.juno.linker;

import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.board.Board;
import io.github.jabrena.juno.board.Capability;
import io.github.jabrena.juno.bytecode.Instruction;
import io.github.jabrena.juno.classfile.MethodRef;
import io.github.jabrena.juno.intrinsic.Intrinsic;
import io.github.jabrena.juno.intrinsic.IntrinsicRegistry;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Rejects programs that use two shields wired to the same pins, which cannot be stacked: on the UNO R3, whose network
 * is the Arduino WiFi Shield, the LCD Keypad Shield's data and backlight lines (D4, D7, D10) are the WiFi Shield's SD
 * chip select, handshake line and Wi-Fi chip select. The other Wi-Fi boards reach their radio without those pins.
 */
final class ShieldPinConflicts {
    private static final String LCD_KEYPAD_SHIELD = "io/github/jabrena/juno/hardware/device/lcd/LcdKeypadShield";
    private static final String WIFI_SHIELD = "io/github/jabrena/juno/hardware/device/wifi/ArduinoWifiShield";
    private static final Set<Capability> WIFI_SHIELD_CAPABILITIES = EnumSet.of(Capability.WIFI,
            Capability.HTTP_CLIENT);
    private static final int INVOKEVIRTUAL = 182;
    private static final int INVOKESTATIC = 184;

    private final IntrinsicRegistry intrinsicRegistry;
    private final Map<Intrinsic, Capability> requiredCapabilities;

    ShieldPinConflicts(IntrinsicRegistry intrinsicRegistry, Map<Intrinsic, Capability> requiredCapabilities) {
        this.intrinsicRegistry = intrinsicRegistry;
        this.requiredCapabilities = requiredCapabilities;
    }

    void validate(Board board, List<LinkedMethod> methods) {
        if (board != Board.UNO_R3) {
            return;
        }
        Optional<LinkedMethod> lcd = methods.stream()
                .filter(method -> method.method().reference().owner().equals(LCD_KEYPAD_SHIELD))
                .findFirst();
        if (lcd.isEmpty()) {
            return;
        }
        for (LinkedMethod method : methods) {
            for (Instruction instruction : method.instructions()) {
                int opcode = instruction.opcode();
                if (opcode < INVOKEVIRTUAL || opcode > INVOKESTATIC) {
                    continue;
                }
                MethodRef called = method.owner().constantPool().methodRef(instruction.operandA());
                if (needsWifiShield(called)) {
                    throw new CompileException("LcdKeypadShield cannot be used with the Arduino WiFi Shield on "
                            + board.displayName() + ": both shields are wired to D4, D7 and D10 (the LCD's data and "
                            + "backlight lines are the WiFi Shield's SD chip select, handshake and Wi-Fi chip select), "
                            + "so they cannot be stacked. " + called.displayName() + " (used from "
                            + method.method().reference().displayName() + ") needs the WiFi Shield, and "
                            + lcd.get().method().reference().displayName() + " the LCD Keypad Shield");
                }
            }
        }
    }

    /** Whether {@code called} is the WiFi Shield itself or a network intrinsic that only the shield provides. */
    private boolean needsWifiShield(MethodRef called) {
        if (called.owner().equals(WIFI_SHIELD)) {
            return true;
        }
        Optional<Capability> capability = intrinsicRegistry.resolve(called).map(requiredCapabilities::get);
        return capability.isPresent() && WIFI_SHIELD_CAPABILITIES.contains(capability.get());
    }
}

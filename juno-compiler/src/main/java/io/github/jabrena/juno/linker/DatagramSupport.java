package io.github.jabrena.juno.linker;

import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.classfile.MethodRef;
import io.github.jabrena.juno.intrinsic.DatagramMethods;
import io.github.jabrena.juno.intrinsic.IntrinsicRegistry;

/** Closed-world plumbing for {@code InetAddress}, {@code DatagramPacket} and {@code DatagramSocket} handles. */
public final class DatagramSupport {
    private final IntrinsicRegistry intrinsicRegistry = new IntrinsicRegistry();

    public boolean isDatagramType(String descriptor) {
        return isInetAddressType(descriptor) || isDatagramPacketType(descriptor) || isDatagramSocketType(descriptor);
    }

    public boolean isInetAddressType(String descriptor) {
        return descriptor.equals("L" + DatagramMethods.INET_ADDRESS + ";");
    }

    public boolean isDatagramPacketType(String descriptor) {
        return descriptor.equals("L" + DatagramMethods.DATAGRAM_PACKET + ";");
    }

    public boolean isDatagramSocketType(String descriptor) {
        return descriptor.equals("L" + DatagramMethods.DATAGRAM_SOCKET + ";");
    }

    public boolean isDatagramOwner(String owner) {
        return owner.equals(DatagramMethods.INET_ADDRESS) || owner.equals(DatagramMethods.DATAGRAM_PACKET)
                || owner.equals(DatagramMethods.DATAGRAM_SOCKET);
    }

    /** Whether {@code called} is a constructor this support lowers to a runtime handle. */
    public boolean isConstruction(MethodRef called) {
        return called.name().equals("<init>") && isDatagramOwner(called.owner())
                && intrinsicRegistry.isIntrinsic(called);
    }

    public void validateCall(MethodRef called) {
        if (isDatagramOwner(called.owner()) && !intrinsicRegistry.isIntrinsic(called)) {
            throw new CompileException("Juno's java.net datagram subset does not support: " + called.displayName());
        }
    }
}

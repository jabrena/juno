package io.github.jabrena.juno.maven.fixture;

/** A program that starts a thread, so on the Portenta H7 with two carriers it builds a second image for the M4. */
public final class PortentaThreadProgram {
    private static int result;

    private PortentaThreadProgram() {
    }

    public static void main(String[] args) throws InterruptedException {
        Thread.startVirtualThread(() -> result = 42).join();
    }
}

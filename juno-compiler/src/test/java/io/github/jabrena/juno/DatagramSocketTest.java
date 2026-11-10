package io.github.jabrena.juno;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The bounded {@code java.net} datagram subset ({@code InetAddress}, {@code DatagramPacket}, {@code DatagramSocket}). */
@DisplayName("Bounded java.net datagram subset")
class DatagramSocketTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    @DisplayName("Lowers a datagram round trip to the UDP shim")
    void shouldLowerADatagramRoundTripToTheUdpShim() throws Exception {
        // Given
        CompilationResult result = compile("""
                public static void main() throws java.io.IOException {
                    byte[] buffer = new byte[16];
                    java.net.DatagramSocket socket = new java.net.DatagramSocket(4210);
                    socket.setSoTimeout(500);
                    java.net.DatagramPacket incoming = new java.net.DatagramPacket(buffer, buffer.length);
                    socket.receive(incoming);
                    java.net.InetAddress peer = java.net.InetAddress.getByAddress(new byte[] {10, 0, 0, 2});
                    java.net.DatagramPacket outgoing = new java.net.DatagramPacket(buffer, 4, peer, 4210);
                    socket.send(outgoing);
                    int length = incoming.getLength();
                    int port = incoming.getPort();
                    socket.close();
                }
                """);

        // When & Then
        assertThat(result.assembly()).contains(
                "bl juno_datagram_socket_new", "bl juno_datagram_socket_set_so_timeout",
                "bl juno_datagram_packet_new_receive", "bl juno_datagram_socket_receive",
                "bl juno_inet_address_get_by_address", "bl juno_datagram_packet_new_send",
                "bl juno_datagram_socket_send", "bl juno_datagram_packet_get_length",
                "bl juno_datagram_packet_get_port", "bl juno_datagram_socket_close");
    }

    @Test
    @DisplayName("The runtime shim carries the datagram helpers, the UDP transport they sit on, and the JDK exceptions")
    void shouldCarryTheDatagramHelpersAndTheJdkExceptionsInTheShim() throws Exception {
        // Given
        CompilationResult result = compile("""
                public static void main() throws java.io.IOException {
                    byte[] buffer = new byte[16];
                    java.net.DatagramSocket socket = new java.net.DatagramSocket(4210);
                    java.net.DatagramPacket incoming = new java.net.DatagramPacket(buffer, buffer.length);
                    socket.receive(incoming);
                    socket.close();
                }
                """);

        // When & Then
        assertThat(result.runtimeShim()).contains(
                "juno_datagram_socket_receive", "juno_datagram_packet_new_receive", "juno_udp_receive",
                "juno_udp_listen", "juno_udp_stop", "juno_throw_raise");
    }

    @Test
    @DisplayName("Compiles the juno-api Syslog logger onto the same datagram shims, with no intrinsics of its own")
    void shouldCompileTheSyslogLoggerOntoTheDatagramShims() throws Exception {
        // Given
        CompilationResult result = compile("""
                public static void main() throws java.io.IOException {
                    java.net.InetAddress everyone = java.net.InetAddress.getByAddress(
                            new byte[] {(byte) 255, (byte) 255, (byte) 255, (byte) 255});
                    io.github.jabrena.juno.hardware.net.udp.Syslog syslog = new io.github.jabrena.juno.hardware.net.udp.Syslog(
                            new java.net.DatagramSocket(5515), everyone,
                            io.github.jabrena.juno.hardware.net.udp.Syslog.DEFAULT_PORT, "board", "app", new byte[64]);
                    syslog.info("ready");
                    for (int i = 0; i < 3; i++) {
                        syslog.begin(io.github.jabrena.juno.hardware.net.udp.Syslog.DEBUG).text("i=").number(i).send();
                    }
                }
                """);

        // When & Then
        assertThat(result.assembly()).contains(
                "bl juno_datagram_socket_new", "bl juno_inet_address_get_by_address",
                "bl juno_datagram_packet_new_send", "bl juno_datagram_packet_set_length",
                "bl juno_datagram_socket_send");
    }

    @Test
    @DisplayName("Rejects an InetAddress lookup by name, which the subset does not support")
    void shouldRejectAnInetAddressLookupByName() {
        // Given
        String members = """
                public static void main() throws java.io.IOException {
                    java.net.InetAddress.getByName("example.com");
                }
                """;

        // When & Then
        assertThatThrownBy(() -> compile(members))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("java.net datagram subset does not support");
    }

    @Test
    @DisplayName("Lowers a socket-only program's stream read straight to the socket, without the SD runtime")
    void shouldLowerASocketOnlyStreamReadStraightToTheSocket() throws Exception {
        // Given
        CompilationResult result = compile("""
                public static void main() throws java.io.IOException {
                    java.net.InetAddress peer = java.net.InetAddress.getByAddress(new byte[] {10, 0, 0, 2});
                    java.net.Socket socket = new java.net.Socket(peer, 7007);
                    socket.setSoTimeout(1000);
                    byte[] line = new byte[4];
                    socket.getOutputStream().write(line, 0, line.length);
                    int read = socket.getInputStream().read(line, 0, line.length);
                    socket.close();
                }
                """);

        // When & Then
        assertThat(result.assembly()).contains(
                "bl juno_socket_new", "bl juno_socket_set_so_timeout", "bl juno_socket_output_stream_write",
                "bl juno_socket_stream_read", "bl juno_socket_close");
        assertThat(result.runtimeShim()).contains("juno_socket_stream_read", "juno_tcp_read", "juno_tcp_open")
                .doesNotContain("juno_sd_files");
    }

    private CompilationResult compile(String members) throws Exception {
        String source = """
                package demo;
                public final class Tasks {
                %s
                }
                """.formatted(members);
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Tasks", source, "25");
        return CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Tasks");
    }
}

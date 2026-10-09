package io.github.jabrena.juno.backend;

import io.github.jabrena.juno.RuntimeConfig;
import io.github.jabrena.juno.board.ArduinoCore;

/**
 * The UNO Q's {@code arduino:zephyr} core: it provides {@code yield()} itself but inlines
 * {@code delay()}/{@code delayMicroseconds()}, so they are reached through shim wrappers.
 */
record ZephyrCoreRuntime() implements CoreRuntime {

    @Override
    public String delayMillisFunction() {
        return "juno_delay";
    }

    @Override
    public String delayMicrosFunction() {
        return "juno_delay_microseconds";
    }

    /**
     * PH10, {@code LED3}'s red channel — the first entry of the UNO Q board overlay's
     * {@code builtin-led-gpios} and the pin {@code LED_BUILTIN} resolves to in the Zephyr core; D13
     * on this board's header has no LED wired to it. Position within {@code digital-pin-gpios} in
     * {@code arduino_uno_q_stm32u585xx.overlay}, hardware-confirmed 2026-09-30.
     */
    @Override
    public int builtinLedPin() {
        return 50;
    }

    /** {@code Arduino_LED_Matrix.h} of the zephyr core declares {@code Arduino_LED_Matrix}, 13x8. */
    @Override
    public String ledMatrixType() {
        return "Arduino_LED_Matrix";
    }

    @Override
    public int ledMatrixColumns() {
        return 13;
    }

    @Override
    public String wifiIncludes(boolean udp) {
        // UNO Q networking belongs to its Linux MPU. Arduino_RouterBridge carries UDP and other
        // network operations between the Zephyr sketch on the MCU and arduino-router on Linux.
        return "#include <Arduino_RouterBridge.h>\n";
    }

    @Override
    public String wifiHelpers() {
        return """

                extern "C" void juno_wifi_begin(const char* ssid, const char* password) {
                  // UNO Q Wi-Fi is configured by Linux (App Lab/nmcli), not by the MCU sketch.
                  static_cast<void>(ssid);
                  static_cast<void>(password);
                  Bridge.begin();
                }

                extern "C" void juno_wifi_begin_ap(const char* ssid, const char* password) {
                  // The MCU cannot create an access point: the hotspot belongs to Linux
                  // (nmcli device wifi hotspot ssid <ssid> password <password>). Only start the bridge.
                  static_cast<void>(ssid);
                  static_cast<void>(password);
                  Bridge.begin();
                }

                extern "C" int32_t juno_wifi_status() {
                  return Bridge ? 3 : 0;
                }

                extern "C" void juno_wifi_local_ip(int32_t* octets) {
                  // arduino-router 0.4.x does not expose the Linux interface address over RPC.
                  octets[0] = 0;
                  octets[1] = 0;
                  octets[2] = 0;
                  octets[3] = 0;
                }
                """;
    }

    @Override
    public String httpServerTransport() {
        // The listener lives in arduino-router on Linux (tcp/listen, tcp/accept). BridgeTCPServer
        // keeps returning the same connection from accept() until disconnect() is called, so
        // release() must run once the response has been sent.
        return """

                using JunoHttpClient = BridgeTCPClient<512>;
                alignas(BridgeTCPServer<512>) static unsigned char juno_http_server_storage[sizeof(BridgeTCPServer<512>)];
                static BridgeTCPServer<512>* juno_http_server_instance = nullptr;
                static JunoHttpClient juno_http_server_client(Bridge);

                extern "C" void juno_http_server_begin(int32_t port) {
                  juno_http_server_instance = new (juno_http_server_storage)
                      BridgeTCPServer<512>(Bridge, IPAddress(0, 0, 0, 0), static_cast<uint16_t>(port));
                  juno_http_server_instance->begin();
                }

                static bool juno_http_server_next_client() {
                  if (juno_http_server_instance == nullptr) return false;
                  JunoHttpClient client = juno_http_server_instance->accept();
                  if (!client.connected()) return false;
                  juno_http_server_client = client;
                  return true;
                }

                static void juno_http_server_release() {
                  juno_http_server_instance->disconnect();
                }
                """;
    }

    @Override
    public String udpDeclaration() {
        // Keep the bridge buffer bounded; Juno's UDP payloads are caller-sized and streamed over RPC.
        return "static BridgeUDP<512> juno_udp(Bridge);";
    }

    @Override
    public String httpsInclude() {
        return "";
    }

    @Override
    public String httpsHelpers() {
        return """

                class JunoBridgeSSLClient : public BridgeTCPClient<512> {
                public:
                  JunoBridgeSSLClient() : BridgeTCPClient<512>(Bridge) {
                    begin();
                  }

                  int connect(const char* host, uint16_t port) {
                    // BridgeTCPClient uses 0 for success; Arduino Client uses non-zero.
                    return connectSSL(host, port, "") == 0 ? 1 : 0;
                  }
                };
                """;
    }

    @Override
    public String httpsClientDeclaration() {
        return "JunoBridgeSSLClient client;";
    }

    @Override
    public String yieldFunction() {
        return """
                // The Zephyr core provides yield() itself (the generated assembly's `bl yield` reaches
                // it directly) but inlines delay() and delayMicroseconds(), so the assembly calls them
                // through these wrappers instead.
                extern "C" void juno_delay(uint32_t ms) {
                  delay(ms);
                }

                extern "C" void juno_delay_microseconds(uint32_t us) {
                  delayMicroseconds(us);
                }

                """;
    }

    @Override
    public String threadIncludes() {
        return "#include <zephyr/kernel.h>\n";
    }

    @Override
    public String threadPort(RuntimeConfig config) {
        return ThreadRuntime.zephyrPort(config.maxThreads(), config.threadStackBytes(ArduinoCore.ZEPHYR.defaultThreadStackBytes()));
    }
}

class JunoBridgeTcpClient : public BridgeTCPClient<1024> {
 public:
  // `juno_tcp_client` below is a static global, constructed before setup()/main() run; calling
  // begin() there would start the Bridge RPC handshake before Zephyr and the Linux side are
  // ready and hang the board at boot. begin() (which inits the mutex and starts Bridge on first
  // use) is deferred to stop()/connect() instead, both only ever called from juno_tcp_open after
  // main() is running.
  JunoBridgeTcpClient() : BridgeTCPClient<1024>(Bridge) {
  }

  void stop() {
    begin();
    BridgeTCPClient<1024>::stop();
  }

  int connect(IPAddress address, uint16_t port) {
    begin();
    // BridgeTCPClient uses zero for a successful connection; Arduino Client uses non-zero.
    return BridgeTCPClient<1024>::connect(address, port) == 0 ? 1 : 0;
  }
};

using JunoTcpClient = JunoBridgeTcpClient;

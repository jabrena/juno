
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

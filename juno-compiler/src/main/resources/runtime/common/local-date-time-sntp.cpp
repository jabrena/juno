${JUNO_SNTP_UDP_DECLARATION}
static constexpr uint16_t JUNO_SNTP_LOCAL_PORT = 2390u;
static constexpr uint32_t JUNO_SNTP_TIMEOUT_MILLIS = 2000u;
static constexpr uint32_t JUNO_SNTP_UNIX_OFFSET = 2208988800ul;

// One SNTP (RFC 4330) exchange: a 48-byte client request, then the server's transmit timestamp, seconds since 1900.
static bool juno_local_date_time_fetch(JunoLocalDateTime* target) {
  if (juno_wifi_status() != 3) return false;
  if (juno_sntp_udp.begin(JUNO_SNTP_LOCAL_PORT) == 0) return false;
  uint8_t packet[48];
  memset(packet, 0, sizeof(packet));
  packet[0] = 0x1B;  // leap indicator 0, version 3, mode 3 (client)
  uint32_t seconds = 0;
  if (juno_sntp_udp.beginPacket(${JUNO_SNTP_HOST}, static_cast<uint16_t>(${JUNO_SNTP_PORT})) == 1
      && juno_sntp_udp.write(packet, sizeof(packet)) == sizeof(packet)
      && juno_sntp_udp.endPacket() == 1) {
    uint32_t start = static_cast<uint32_t>(millis());
    while (static_cast<uint32_t>(millis()) - start < JUNO_SNTP_TIMEOUT_MILLIS) {
      if (juno_sntp_udp.parsePacket() >= static_cast<int>(sizeof(packet))) {
        juno_sntp_udp.read(packet, sizeof(packet));
        // A server reply (mode 4) with a non-zero stratum; stratum 0 is a "kiss-o'-death" refusal.
        if ((packet[0] & 0x07) == 4 && packet[1] != 0) {
          seconds = (static_cast<uint32_t>(packet[40]) << 24) | (static_cast<uint32_t>(packet[41]) << 16)
              | (static_cast<uint32_t>(packet[42]) << 8) | static_cast<uint32_t>(packet[43]);
        }
        break;
      }
      delay(10);
    }
  }
  juno_sntp_udp.stop();
  if (seconds <= JUNO_SNTP_UNIX_OFFSET) return false;
  uint32_t unixSeconds = seconds - JUNO_SNTP_UNIX_OFFSET;
  uint32_t days = unixSeconds / 86400u;
  JunoLocalDateTime value = {JUNO_LOCAL_DATE_TIME_MAGIC, 1970, 1, 1, 0, 0, 0};
  // Whole years first: juno_local_date_time_advance steps day by day.
  while (true) {
    uint32_t yearDays = juno_local_date_time_leap_year(value.year) ? 366u : 365u;
    if (days < yearDays) break;
    days -= yearDays;
    value.year++;
  }
  *target = juno_local_date_time_advance(value, days * 86400u + unixSeconds % 86400u);
  return true;
}


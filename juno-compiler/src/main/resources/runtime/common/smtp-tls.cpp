extern "C" int32_t juno_smtp_send_tls(const char* host, int32_t port,
                                       const char* username, const char* password,
                                       const char* from, const char* to,
                                       const char* subject, const char* body) {
  ${JUNO_TLS_CLIENT_DECLARATION}
  if (!client.connect(host, static_cast<uint16_t>(port))) return -1;
  unsigned long deadline = millis() + 15000;

  if (juno_smtp_read_reply(client, deadline) != 220) { client.stop(); return -2; }

  client.print("EHLO juno\r\n");
  if (juno_smtp_read_reply(client, deadline) != 250) { client.stop(); return -3; }

  client.print("AUTH LOGIN\r\n");
  if (juno_smtp_read_reply(client, deadline) != 334) { client.stop(); return -4; }

  char encoded[196];
  if (juno_base64_encode(username, static_cast<int32_t>(strlen(username)), encoded, sizeof(encoded)) < 0) {
    client.stop();
    return -10;
  }
  client.print(encoded);
  client.print("\r\n");
  if (juno_smtp_read_reply(client, deadline) != 334) { client.stop(); return -4; }

  if (juno_base64_encode(password, static_cast<int32_t>(strlen(password)), encoded, sizeof(encoded)) < 0) {
    client.stop();
    return -10;
  }
  client.print(encoded);
  client.print("\r\n");
  if (juno_smtp_read_reply(client, deadline) != 235) { client.stop(); return -5; }

  client.print("MAIL FROM:<");
  client.print(from);
  client.print(">\r\n");
  if (juno_smtp_read_reply(client, deadline) != 250) { client.stop(); return -6; }

  client.print("RCPT TO:<");
  client.print(to);
  client.print(">\r\n");
  if (juno_smtp_read_reply(client, deadline) != 250) { client.stop(); return -7; }

  client.print("DATA\r\n");
  if (juno_smtp_read_reply(client, deadline) != 354) { client.stop(); return -8; }

  client.print("From: "); client.print(from); client.print("\r\n");
  client.print("To: "); client.print(to); client.print("\r\n");
  client.print("Subject: "); client.print(subject); client.print("\r\n\r\n");
  client.print(body);
  client.print("\r\n.\r\n");
  if (juno_smtp_read_reply(client, deadline) != 250) { client.stop(); return -9; }

  client.print("QUIT\r\n");
  client.stop();
  return 0;
}


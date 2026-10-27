# Juno Mailbox certificates and deployment

The mailbox example has two applications:

- `io.github.jabrena.juno.usecases.mailbox.server.Mailbox` runs the HTTPS server on an Arduino UNO Q.
- `io.github.jabrena.juno.usecases.mailbox.client.MailboxCardputer` runs the HTTPS client on an M5Stack Cardputer.

The server reads its PEM certificate and private key from the root of its SD card. The Cardputer embeds the private CA
certificate at compile time, so the two applications can use a private certificate authority on a local network.

Keep the CA private key, server private key, and Wi-Fi credentials outside the repository.

## Create a local CA and server certificate

Choose the UNO Q address first. The address must be stable, must be the same address shown by the UNO Q mailbox, and
must appear in the server certificate's `subjectAltName`.

Run the following with OpenSSL. Set `TLS_DIR` to a directory outside this repository:

```bash
export JUNO_Q_IP=192.168.1.47
export TLS_DIR=/path/outside/juno/mailbox-tls
mkdir -p "$TLS_DIR"

# Development CA: keep this key private.
openssl genrsa -out "$TLS_DIR/mailbox-ca.key" 4096
openssl req -x509 -new -sha256 -days 825 \
  -key "$TLS_DIR/mailbox-ca.key" \
  -out "$TLS_DIR/mailbox-ca.crt" \
  -subj "/CN=Juno Mailbox Development CA"

# The UNO Q uses this key and certificate. P-256 keeps the PEM files within the board's bounded buffers.
openssl ecparam -name prime256v1 -genkey -noout \
  -out "$TLS_DIR/server.key"
openssl req -new -sha256 \
  -key "$TLS_DIR/server.key" \
  -out "$TLS_DIR/server.csr" \
  -subj "/CN=${JUNO_Q_IP}"
cat > "$TLS_DIR/server-ext.cnf" <<EOF
subjectAltName = IP:${JUNO_Q_IP}
extendedKeyUsage = serverAuth
EOF
openssl x509 -req -sha256 -days 825 \
  -in "$TLS_DIR/server.csr" \
  -CA "$TLS_DIR/mailbox-ca.crt" \
  -CAkey "$TLS_DIR/mailbox-ca.key" \
  -CAcreateserial \
  -out "$TLS_DIR/server.crt" \
  -extfile "$TLS_DIR/server-ext.cnf"

openssl verify -CAfile "$TLS_DIR/mailbox-ca.crt" "$TLS_DIR/server.crt"
openssl x509 -in "$TLS_DIR/server.crt" -noout -subject -issuer -ext subjectAltName
```

Copy these files to the UNO Q SD card's root:

```text
server.crt
server.key
```

The client trusts the CA, not the server private key. `JUNO_TLS_CA` must contain the PEM text itself, not a filename:

```bash
export JUNO_TLS_CA="$(cat "$TLS_DIR/mailbox-ca.crt")"
```

## Prepare the SD cards

The UNO Q server needs `server.crt` and `server.key` in the SD card root. It may also contain
`application.properties`; the server only checks that this file is readable.

The Cardputer needs an `application.properties` file in the SD card root:

```properties
wifi.ssid=your-network-name
wifi.password=your-network-password
```

The Cardputer starts with API key `1234567890`. Change it from the Settings screen; the new value is stored in
`api-key.txt`. Enter the UNO Q address and the same API key in the client UI.

## Build and upload the UNO Q server

Run `juno:verify` first to compile and validate the generated sketch without touching hardware:

```bash
JUNO_Q_IP="$JUNO_Q_IP" ./mvnw -f juno-examples/pom.xml compile juno:verify \
  -Djuno.main=io.github.jabrena.juno.usecases.mailbox.server.Mailbox \
  -Djuno.board=arduino-uno-q
```

With the UNO Q connected and its SD card inserted, upload it with `juno:upload`:

```bash
JUNO_Q_IP="$JUNO_Q_IP" ./mvnw -f juno-examples/pom.xml compile juno:upload \
  -Djuno.main=io.github.jabrena.juno.usecases.mailbox.server.Mailbox \
  -Djuno.board=arduino-uno-q
```

Then open the serial monitor. The server waits briefly before its first output, so leave the monitor attached long
enough to see the startup result:

```bash
./mvnw -f juno-examples/pom.xml juno:monitor \
  -Djuno.board=arduino-uno-q \
  -Djuno.baudRate=115200
```

If automatic board detection finds more than one board, add `-Djuno.port=<PORT>` to both goals.

## Build and upload the Cardputer client

`JUNO_TLS_CA` is consumed while the Cardputer sketch is generated, so keep it exported for both verification and
upload. The Cardputer example needs a 12 KiB arena:

```bash
export JUNO_TLS_CA="$(cat "$TLS_DIR/mailbox-ca.crt")"

./mvnw -f juno-examples/pom.xml compile juno:verify \
  -Djuno.main=io.github.jabrena.juno.usecases.mailbox.client.MailboxCardputer \
  -Djuno.board=m5stack-cardputer \
  -Djuno.Xmx=12k

./mvnw -f juno-examples/pom.xml compile juno:upload \
  -Djuno.main=io.github.jabrena.juno.usecases.mailbox.client.MailboxCardputer \
  -Djuno.board=m5stack-cardputer \
  -Djuno.Xmx=12k

./mvnw -f juno-examples/pom.xml juno:monitor \
  -Djuno.board=m5stack-cardputer \
  -Djuno.baudRate=115200
```

Use `juno:monitor` after every upload and check that Wi-Fi connects, the mailbox is discovered, and HTTPS requests
return successful status codes.

## Test the API from a workstation

The Bruno collection in [`bruno/mailbox`](bruno/mailbox) exercises the same HTTPS API. Configure its host and API
key, and import or trust `mailbox-ca.crt` in Bruno before running the requests. The OpenAPI contract is in
[`Mailbox.openapi.yaml`](Mailbox.openapi.yaml).

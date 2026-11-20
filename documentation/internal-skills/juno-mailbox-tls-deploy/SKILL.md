---
name: juno-mailbox-tls-deploy
description: Deploy the Juno Mailbox HTTPS server on a Portenta H7 (Hat Carrier + MHS-3.5 TFT) and the HTTPS client on an M5 Cardputer, including local CA trust, board uploads, and serial verification.
---

# Juno Mailbox TLS deployment

Use this skill only for an explicitly authorized physical deployment of the Mailbox example. It covers the local-network case where a Portenta H7 in a Portenta Hat Carrier with the MHS-3.5 TFT touch HAT serves HTTPS and an M5 Cardputer sends HTTPS requests.

## Trust model

- The Portenta reads the leaf certificate and private key at startup from `server.crt` and `server.key` (PEM) in the root of the Hat Carrier's microSD card. The firmware image holds no key. Never copy the private key to the M5, print it, commit it, or send it through the mailbox.
- The Portenta joins Wi-Fi with `wifi.ssid` and `wifi.password` from its SE050C2 secret store, written once with `SecretProvisioner`. Never put Wi-Fi credentials on the SD card or in the build.
- The M5 receives only the CA certificate, as `mailbox-ca.crt` in the root of its SD card, and trusts nothing else for the mailbox; the default Mozilla bundle does not trust a local self-signed CA.
- The leaf certificate SAN must contain the address the M5 uses, normally `IP:192.168.1.47`. Give the Portenta a fixed DHCP lease, because a certificate for another address fails TLS identity verification.
- Do not use `setInsecure()` for the deployment. It hides trust failures by disabling server identity validation.

## Deployment workflow

The full certificate and card layout is in `juno-examples/src/main/java/io/github/jabrena/juno/usecases/mailbox/docs/README.md`.

1. Confirm the connected boards with `./mvnw -f juno-examples/pom.xml juno:boards`; use Maven `juno:flash` and `juno:monitor` with `-Djuno.port=<serial port>`, never raw `arduino-cli`.
2. Flash the Portenta once with `-Djuno.board=arduino-portenta-h7` and monitor it at 115200 baud. A healthy startup prints `IP: <address>` after Wi-Fi joins, and the cover's IP row shows the same address. Use that address as `MAILBOX_IP`. If Wi-Fi does not join, check the secret store with `SecretWifiStatus` before going further.
3. Generate a temporary CA and a leaf certificate with `IP:$MAILBOX_IP` in `subjectAltName`; P-256 keeps the PEM files small. Keep the directory outside the repository and retain it only as long as needed. Copy `server.crt` and `server.key` to the root of the Hat Carrier's microSD card.
4. Copy the CA certificate to the Cardputer's SD card as `mailbox-ca.crt`, with its `application.properties` Wi-Fi settings, and build it with `-Djuno.Xmx=12k` (and `-Djuno.gcLog=true` while diagnosing). The serial output contains `juno-gc` collection lines and `juno-gc OOM` if an allocation fails.
5. Upload the Cardputer on its recognized serial port, then run `juno:monitor` at 115200 baud. A healthy startup prints Wi-Fi connection and IP without `juno-gc OOM` or a panic.
6. Reset the Portenta with the card inserted and monitor it again; healthy startup prints `IP: <MAILBOX_IP>` and `Mailbox listening on HTTPS port 443`.
7. Replace `mailbox-ca.crt` on the M5's card after any CA change. The M5 must be monitored after upload because `juno:flash` resets it and its settings screen requires the mailbox API key and server IP again.

## Safety and evidence

- Do not call mailbox HTTP endpoints directly while diagnosing deployment; use `.claude/hooks/mailbox.sh` for mailbox calls.
- Do not report HTTPS end-to-end success from the Portenta's startup alone. Verify a successful M5 HTTPS request only after the M5 trusts the CA and its serial output shows the request result.
- Record only certificate metadata and serial status, never PEM contents or private-key material.

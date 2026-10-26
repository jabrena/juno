---
name: juno-mailbox-tls-deploy
description: Deploy the Juno Mailbox HTTPS server on UNO Q and the HTTPS client on an M5 Cardputer, including local CA trust, board uploads, and serial verification.
---

# Juno Mailbox TLS deployment

Use this skill only for an explicitly authorized physical deployment of the Mailbox example. It covers the local-network case where UNO Q serves HTTPS and M5 Cardputer sends HTTPS requests.

## Trust model

- The Q receives the leaf certificate and private key through build-time `JUNO_TLS_CERT` and `JUNO_TLS_KEY` environment variables. Never copy the private key to M5, print it, commit it, or send it through the mailbox.
- The M5 receives only the CA certificate through build-time `JUNO_TLS_CA`. The ESP32 runtime must emit `WiFiClientSecure::setCACert(...)` for this custom CA; the default Mozilla bundle does not trust a local self-signed CA.
- The Q certificate SAN must contain the address the M5 uses, normally `IP:192.168.1.47`. A certificate for another hostname or IP still fails TLS identity verification.
- Do not use `setInsecure()` for the deployment. It hides trust failures by disabling server identity validation.

## Deployment workflow

1. Confirm the connected boards with `./mvnw -f juno-examples/pom.xml juno:boards`; use Maven `juno:upload` and `juno:monitor`, never raw `arduino-cli`. Take the Q's address from its `[network]` port line and export it once, so the certificate, the TFT and the M5 all use the same value:
   ```bash
   export JUNO_Q_IP="$(./mvnw -f juno-examples/pom.xml juno:boards 2>&1 \
     | sed -n 's/.*INFO\]  *\([0-9.]*\) \[network\] - Arduino UNO Q.*/\1/p' | head -1)"
   ```
   If it is empty, the Q is not reachable on the network yet; ask the user for the address instead of scanning.
2. Generate a temporary CA and a Q leaf certificate with `IP:$JUNO_Q_IP` in `subjectAltName`. Keep the directory outside the repository and retain it only as long as needed.
3. Build the Cardputer with `JUNO_TLS_CA="$(<ca.crt)"`, `-Djuno.Xmx=10240`, `-Djuno.gcLog=true`, and its 8 MB/3 MB-app FQBN when the Mozilla bundle exceeds the default 4 MB application partition. The generated serial output contains `juno-gc` collection lines and `juno-gc OOM` if an allocation fails:
   `m5stack:esp32:m5stack_cardputer:FlashSize=8M,PartitionScheme=default_8MB`.
4. Upload the Cardputer on its recognized serial port, then run `juno:monitor` at 115200 baud. A healthy startup prints Wi-Fi connection and IP without `juno-gc OOM` or a panic.
5. Build and upload Q with `JUNO_Q_IP` (the cover's IP row; the build fails without it), `JUNO_TLS_CERT` and `JUNO_TLS_KEY`, `-Djuno.Xmx=12288`, `-Djuno.maxThreads=2`, `-Djuno.gcLog=true`, board `arduino-uno-q`, and its serial port. Monitor it at 115200 baud; healthy startup prints `IP: <JUNO_Q_IP>` and `Mailbox listening on HTTPS port 443`, and the cover's second row shows the address.
6. Rebuild the M5 after any CA or Q certificate change. The M5 must be monitored after upload because `juno:upload` resets it and its settings screen requires the mailbox API key and server IP again.

## Safety and evidence

- Do not call mailbox HTTP endpoints directly while diagnosing deployment; use `.claude/hooks/mailbox.sh` for mailbox calls.
- Do not report HTTPS end-to-end success from Q startup alone. Verify a successful M5 HTTPS request only after the M5 trusts the CA and its serial output shows the request result.
- Record only certificate metadata and serial status, never PEM contents or private-key material.

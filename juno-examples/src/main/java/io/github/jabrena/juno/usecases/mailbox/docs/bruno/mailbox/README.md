# Juno Mailbox Bruno collection

This collection consumes the mailbox API described by
[`Mailbox.openapi.yaml`](../../Mailbox.openapi.yaml).

Set the UNO Q host in `environments/local.yml`, trust its build-time TLS certificate in Bruno, then run the collection:

```bash
npx --yes @usebruno/cli run --env local
```

Every mailbox request sends the board's API key in a `Mailbox-API-Key` header, from the `apiKey` environment
variable. It holds the board's starting key, `1234567890`; once the key is changed on the board's Security screen
(the gear on the cover, then SECURITY), pass the new one. `GET /actuator/info` needs no key.

To override the host or the key without editing the environment file:

```bash
npx --yes @usebruno/cli run --env local --env-var host=192.168.1.42 --env-var apiKey=24680135
```

The UNO Q gets its network from Linux, so the address shown on the TFT is set at build time with `JUNO_Q_IP`, the
Q's `[network]` port in `juno:boards`; the build requires it:

```bash
JUNO_Q_IP=192.168.1.47
```

The collection contains requests for listing, creating, retrieving, and deleting messages, plus `GET /actuator/info`,
which identifies the service. The list request returns only
message UUIDs; use one returned by the create or list request in the retrieve and delete requests.

## CLI commands

Run these commands from this `mailbox` collection directory. Set `host` to the board address when needed.

```bash
# Identify the service: {"info":"Juno Mailbox"}
npx --yes @usebruno/cli run "Actuator/Identify the service.yml" --env local

# List all messages
npx --yes @usebruno/cli run "Mailbox/List mailbox messages.yml" --env local

# Create a message; the board generates the UUID and createAt values
npx --yes @usebruno/cli run "Mailbox/Add a message to the mailbox.yml" --env local

# Retrieve one message
npx --yes @usebruno/cli run "Mailbox/Retrieve one mailbox message.yml" --env local \
  --env-var uuid=6f0a6a4b-36b8-4dc9-bc67-8f4a3bfb2d61

# Delete one message
npx --yes @usebruno/cli run "Mailbox/Delete one mailbox message.yml" --env local \
  --env-var uuid=6f0a6a4b-36b8-4dc9-bc67-8f4a3bfb2d61
```

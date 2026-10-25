# Juno Mailbox Bruno collection

This collection consumes the mailbox API described by
[`Mailbox.openapi.yaml`](../../Mailbox.openapi.yaml).

Set the board host in `environments/local.yml`, then run the collection with Bruno CLI:

```bash
npx --yes @usebruno/cli run --env local
```

To override the host without editing the environment file:

```bash
npx --yes @usebruno/cli run --env local --env-var host=192.168.1.42
```

The mailbox loads Wi-Fi credentials from `application.properties` at the root of the SD card:

```properties
wifi.ssid=your-network
wifi.password=your-password
```

The collection contains requests for listing, creating, retrieving, and deleting messages. The list request returns only
message UUIDs; use one returned by the create or list request in the retrieve and delete requests.

## CLI commands

Run these commands from this `mailbox` collection directory. Set `host` to the board address when needed.

```bash
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

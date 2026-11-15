#!/bin/sh
# Sends a server certificate and its private key to a Portenta H7 running PortentaHttpsServer, over USB serial.
# Run it after the board prints READY. Usage:
#   scripts/portenta-https-send-pem.sh <serial-port> <certificate.pem> <key.pem>
# The key is only written to the serial port; it never reaches the firmware or the repository.
set -eu

if [ "$#" -ne 3 ]; then
    echo "usage: $0 <serial-port> <certificate.pem> <key.pem>" >&2
    exit 2
fi

port=$1
certificate=$2
key=$3

stty -f "$port" 115200 raw -echo

# One line at a time, with a short pause, so the board's serial buffer never overflows.
send_block() {
    while IFS= read -r line || [ -n "$line" ]; do
        printf '%s\r\n' "$line"
        sleep 0.02
    done < "$1"
    printf 'END\r\n'
    sleep 0.2
}

send_block "$certificate" > "$port"
send_block "$key" > "$port"
echo "sent certificate and key to $port"

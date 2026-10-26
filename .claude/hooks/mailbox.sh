#!/bin/bash
# Talks to the Juno Mailbox example (juno-examples/.../usecases/mailbox) over its REST API.
#
#   mailbox.sh host [IP]          print the configured board host, or save IP as the host
#   mailbox.sh api-key [KEY]      print the API key sent to the board, or save KEY as the key
#   mailbox.sh notify             Stop hook: post the last reply as a tweet-sized CLAUDE: message
#   mailbox.sh send TITLE BODY    post one message
#   mailbox.sh poll               print pending messages whose title contains AGENT or agent (any case), one JSON per line
#   mailbox.sh delete UUID        delete one message
#
# The host comes from $MAILBOX_HOST, else from .claude/mailbox/host (git-ignored). With no host, every
# command except `host` and `api-key` exits 0 silently, so the hook is harmless while the board is off.
# Every mailbox request carries the board's API key in a Mailbox-API-Key header: $MAILBOX_API_KEY, else
# .claude/mailbox/api-key (git-ignored), else the board's starting key 1234567890.
# The board keeps four messages and the UNO Q hangs on non-ASCII bodies, so bodies are ASCII-only, at
# most 280 characters, and `notify` deletes earlier CLAUDE: messages to leave room for AGENT tasks.
set -uo pipefail

ROOT="${CLAUDE_PROJECT_DIR:-$(cd "$(dirname "$0")/../.." && pwd)}"
STATE="$ROOT/.claude/mailbox"
HOST_FILE="$STATE/host"
API_KEY_FILE="$STATE/api-key"
DEFAULT_API_KEY="1234567890"
IDLE_FLAG="$STATE/idle"
TEXT="$ROOT/.claude/hooks/MailboxText.java"
JBANG="$(command -v jbang || echo "$HOME/.sdkman/candidates/jbang/current/bin/jbang")"
NOTIFY_PREFIX="CLAUDE:"
TASK_MARKER="AGENT"
TWEET_MAX=280
CURL=(curl --silent --show-error --connect-timeout 3 --max-time 8)

host() {
  if [ -n "${MAILBOX_HOST:-}" ]; then
    echo "$MAILBOX_HOST"
  elif [ -s "$HOST_FILE" ]; then
    tr -d '[:space:]' < "$HOST_FILE"
  fi
}

api_key() {
  if [ -n "${MAILBOX_API_KEY:-}" ]; then
    echo "$MAILBOX_API_KEY"
  elif [ -s "$API_KEY_FILE" ]; then
    tr -d '[:space:]' < "$API_KEY_FILE"
  else
    echo "$DEFAULT_API_KEY"
  fi
}

# curl for one mailbox request, with the API key header.
mailbox_curl() {
  "${CURL[@]}" -H "Mailbox-API-Key: $(api_key)" "$@"
}

base_url() {
  echo "http://$(host)/api/v1/mailbox/"
}

uuids() {
  mailbox_curl "$(base_url)" | jq -r '.[].uuid'
}

message() {
  mailbox_curl "$(base_url)$1"
}

delete() {
  mailbox_curl -o /dev/null -w '%{http_code}\n' -X DELETE "$(base_url)$1"
}

# Prints stdin as ASCII without markdown, on one line, cut to TWEET_MAX characters.
tweet() {
  "$JBANG" --quiet "$TEXT" tweet "$TWEET_MAX"
}

send() {
  local title body
  title="$(printf '%s' "$1" | tweet | cut -c1-64)"
  body="$(printf '%s' "$2" | tweet)"
  [ -n "$body" ] || return 0
  jq -cn --arg title "$title" --arg body "$body" '{title: $title, body: $body}' |
    mailbox_curl -o /dev/null -w '%{http_code}\n' -X POST -H 'Content-Type: application/json' \
      --data-binary @- "$(base_url)"
}

# Picks the summary from a reply: a "TL;DR:" line when there is one, else the first paragraph.
summary() {
  "$JBANG" --quiet "$TEXT" summary
}

last_reply() {
  local input="$1" reply transcript
  reply="$(jq -r '.last_assistant_message // empty' <<< "$input")"
  if [ -z "$reply" ]; then
    transcript="$(jq -r '.transcript_path // empty' <<< "$input")"
    [ -f "$transcript" ] && reply="$(jq -rs '
      [.[] | select(.type == "assistant") | .message.content[]? | select(.type == "text") | .text]
      | last // empty' "$transcript")"
  fi
  printf '%s' "$reply"
}

notify() {
  local input reply uuid
  input="$(cat)"
  [ "$(jq -r '.stop_hook_active // false' <<< "$input")" = "true" ] && return 0
  if [ -e "$IDLE_FLAG" ]; then
    rm -f "$IDLE_FLAG"
    return 0
  fi
  reply="$(last_reply "$input")"
  [ -n "$reply" ] || return 0
  for uuid in $(uuids 2>/dev/null); do
    if message "$uuid" | jq -e --arg p "$NOTIFY_PREFIX" '.title | startswith($p)' > /dev/null 2>&1; then
      delete "$uuid" > /dev/null
    fi
  done
  send "$NOTIFY_PREFIX $(basename "$ROOT") task done" "$(printf '%s' "$reply" | summary)" > /dev/null
}

poll() {
  local uuid found=0 json
  mkdir -p "$STATE"
  for uuid in $(uuids 2>/dev/null); do
    json="$(message "$uuid" 2>/dev/null)"
    if jq -e --arg m "$TASK_MARKER" '.title | ascii_downcase | contains($m | ascii_downcase)' > /dev/null 2>&1 <<< "$json"; then
      jq -c . <<< "$json"
      found=1
    fi
  done
  if [ "$found" = 1 ]; then
    rm -f "$IDLE_FLAG"
  else
    touch "$IDLE_FLAG"
  fi
}

command="${1:-}"
shift || true
if [ "$command" = "host" ]; then
  if [ -n "${1:-}" ]; then
    mkdir -p "$STATE"
    echo "$1" > "$HOST_FILE"
  fi
  host
  exit 0
fi
if [ "$command" = "api-key" ]; then
  if [ -n "${1:-}" ]; then
    mkdir -p "$STATE"
    echo "$1" > "$API_KEY_FILE"
  fi
  api_key
  exit 0
fi
if [ -z "$(host)" ]; then
  [ "$command" = "poll" ] && mkdir -p "$STATE" && touch "$IDLE_FLAG"
  exit 0
fi
case "$command" in
  notify) notify ;;
  send) send "$1" "$2" ;;
  poll) poll ;;
  delete) delete "$1" ;;
  *) sed -n '2,8p' "$0" >&2; exit 2 ;;
esac
exit 0

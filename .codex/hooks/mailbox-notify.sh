#!/bin/bash
# Codex `notify` hook: posts the turn's last reply to the Juno Mailbox, like Claude's Stop hook does.
#
# Codex runs this after each turn with one JSON argument ({"type":"agent-turn-complete",
# "last-assistant-message":...}); .claude/hooks/mailbox.sh notify reads Claude's Stop-hook JSON on stdin, so this
# only reshapes the one into the other. The host, the TL;DR summary and the four-message limit stay in mailbox.sh.
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
EVENT="${1:-}"

[ "$(jq -r '.type // empty' <<< "$EVENT" 2>/dev/null)" = "agent-turn-complete" ] || exit 0
jq -c '{last_assistant_message: (."last-assistant-message" // "")}' <<< "$EVENT" |
  CLAUDE_PROJECT_DIR="$ROOT" "$ROOT/.claude/hooks/mailbox.sh" notify
exit 0

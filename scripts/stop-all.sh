#!/usr/bin/env bash
# Stops everything started by scripts/start-all.sh.
cd "$(dirname "$0")/.."
for pidfile in logs/*.pid; do
  [ -e "$pidfile" ] || continue
  name="$(basename "$pidfile" .pid)"
  pid="$(cat "$pidfile")"
  if kill "$pid" 2>/dev/null; then echo "stopped $name ($pid)"; fi
  rm -f "$pidfile"
done

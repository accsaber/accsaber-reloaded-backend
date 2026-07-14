#!/usr/bin/env bash
set -euo pipefail

CONTAINER="${CONTAINER:-accsaber-postgres}"
IMAGE="${IMAGE:-postgres:18-alpine}"
DB="${POSTGRES_DB:-accsaber}"
DB_USER="${POSTGRES_USER:-accsaber}"
OUT_DIR="${OUT_DIR:-$HOME/accsaber-backups}"
COMPRESS="${COMPRESS:-zstd:3}"
KEEP_DAILY="${KEEP_DAILY:-2}"
KEEP_WEEKLY="${KEEP_WEEKLY:-1}"
WEEKLY_DOW="${WEEKLY_DOW:-1}"
MIN_FREE_MB="${MIN_FREE_MB:-6000}"

DAILY_DIR="$OUT_DIR/daily"
WEEKLY_DIR="$OUT_DIR/weekly"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
NAME="accsaber-$STAMP.dump"
DUMP="$DAILY_DIR/$NAME"
PARTIAL="$DUMP.partial"
GLOBALS="$DAILY_DIR/accsaber-globals-$STAMP.sql"

log() { echo "[$(date -u +%H:%M:%S)] $*"; }

trap 'rm -f "$PARTIAL"' EXIT

mkdir -p "$DAILY_DIR" "$WEEKLY_DIR"

if ! docker inspect -f '{{.State.Running}}' "$CONTAINER" 2>/dev/null | grep -q true; then
  log "error: container '$CONTAINER' is not running"
  exit 1
fi

FREE_MB="$(df -Pm "$OUT_DIR" | awk 'NR==2 {print $4}')"
if (( FREE_MB < MIN_FREE_MB )); then
  log "error: ${FREE_MB}MB free on $OUT_DIR, need at least ${MIN_FREE_MB}MB"
  log "free space or lower KEEP_DAILY before retrying"
  exit 1
fi

log "dumping $DB from $CONTAINER (compress=$COMPRESS)"
docker exec "$CONTAINER" pg_dump -U "$DB_USER" -d "$DB" \
  --format=custom --compress="$COMPRESS" > "$PARTIAL"

log "verifying archive"
docker run --rm -v "$OUT_DIR:/backup:ro" "$IMAGE" \
  pg_restore --list "/backup/daily/$(basename "$PARTIAL")" > /dev/null

mv "$PARTIAL" "$DUMP"

log "dumping cluster globals"
docker exec "$CONTAINER" pg_dumpall -U "$DB_USER" --globals-only > "$GLOBALS"

if [[ "$(date -u +%u)" == "$WEEKLY_DOW" ]]; then
  ln -f "$DUMP" "$WEEKLY_DIR/$NAME"
  log "promoted to weekly"
fi

prune() {
  local dir="$1" pattern="$2" keep="$3" f
  ls -1t "$dir"/$pattern 2>/dev/null | tail -n "+$((keep + 1))" | while read -r f; do
    log "pruning $(basename "$f")"
    rm -f "$f"
  done
}

prune "$DAILY_DIR" "accsaber-2*.dump" "$KEEP_DAILY"
prune "$DAILY_DIR" "accsaber-globals-*.sql" "$KEEP_DAILY"
prune "$WEEKLY_DIR" "accsaber-2*.dump" "$KEEP_WEEKLY"

log "backup ok: $DUMP ($(du -h "$DUMP" | cut -f1))"
log "retained: $(ls -1 "$DAILY_DIR"/accsaber-2*.dump 2>/dev/null | wc -l) daily, $(ls -1 "$WEEKLY_DIR"/accsaber-2*.dump 2>/dev/null | wc -l) weekly, $(du -sh "$OUT_DIR" | cut -f1) on disk"

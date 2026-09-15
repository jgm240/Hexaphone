# Shared helpers, sourced by the numbered scripts.

set -euo pipefail

if [ -z "${HEXAPHONE_SRC:-}" ]; then
  echo "error: HEXAPHONE_SRC is not set." >&2
  echo "  export HEXAPHONE_SRC=/Volumes/HexaphoneSrc" >&2
  exit 1
fi

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DOCKER_IMAGE="hexaphone-builder"

docker_run() {
  docker run --rm -i --platform linux/amd64 \
    -v "$HEXAPHONE_SRC:/src" \
    -v "$PROJECT_ROOT:/hexaphone:ro" \
    -w /src \
    "$DOCKER_IMAGE" \
    bash -lc "$1"
}

# Copies our git-tracked overlay trees (branding, the Performance app) into
# the synced source tree. Safe to call repeatedly.
sync_overlay_trees() {
  mkdir -p "$HEXAPHONE_SRC/vendor/hexaphone" "$HEXAPHONE_SRC/packages/hexaphone"
  rsync -a --delete "$PROJECT_ROOT/vendor/hexaphone/" "$HEXAPHONE_SRC/vendor/hexaphone/"
  rsync -a --delete "$PROJECT_ROOT/packages/hexaphone/" "$HEXAPHONE_SRC/packages/hexaphone/"
}

#!/usr/bin/env bash
# repo sync (long-running — hours depending on bandwidth). Run this with
# nohup/in background and tail the log; it will get disconnected if your
# machine sleeps.
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

JOBS="${SYNC_JOBS:-2}"
MAX_ATTEMPTS="${SYNC_MAX_ATTEMPTS:-100}"

# The legacy (Python 2.7) repo tool has a known select()/EINTR race under
# threaded fetches that can kill the whole sync mid-run. repo sync is safe
# to resume (already-fetched projects are cached), so just retry on failure
# rather than requiring a human to notice and rerun it.
attempt=1
until "$PROJECT_ROOT/scripts/patch-repo-eintr.sh" && docker_run "repo sync -c -j$JOBS --force-sync --no-clone-bundle"; do
  if [ "$attempt" -ge "$MAX_ATTEMPTS" ]; then
    echo "repo sync failed $attempt times, giving up." >&2
    exit 1
  fi
  echo "repo sync attempt $attempt failed, retrying ($((attempt+1))/$MAX_ATTEMPTS) in 10s..." >&2
  attempt=$((attempt+1))
  sleep 10
done

echo "sync complete. Fetching LFS content repo sync can't smudge itself..."
"$PROJECT_ROOT/scripts/fetch-webview-lfs.sh"

echo "Patching device tree (kernel Soong config bridge)..."
"$PROJECT_ROOT/scripts/patch-device-tree.sh"

echo "Copying vendor/hexaphone and packages/hexaphone into source..."
sync_overlay_trees
echo "done."

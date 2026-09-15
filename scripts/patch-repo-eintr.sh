#!/usr/bin/env bash
# The legacy (pre-Python-3.5) repo tool doesn't retry select() on EINTR
# (that only became automatic with PEP 475 in Python 3.5+). Under Docker's
# Rosetta-emulated amd64 on Apple Silicon, signals interrupt select() often
# enough to reliably crash multi-threaded repo sync. Patch it to retry.
#
# Idempotent, and safe to call before every sync attempt in case repo
# self-updates its own checkout mid-run and clobbers the patch.
set -euo pipefail

F="${HEXAPHONE_SRC:?}/.repo/repo/platform_utils.py"
[ -f "$F" ] || { echo "no platform_utils.py yet, skipping patch"; exit 0; }

python3 - "$F" <<'EOF'
import sys
path = sys.argv[1]
with open(path) as f:
    content = f.read()

if "except select.error as e:" in content:
    print("already patched")
    sys.exit(0)

old = """  def select(self):
    ready_streams, _, _ = select.select(self.streams, [], [])
    return ready_streams"""

new = """  def select(self):
    while True:
      try:
        ready_streams, _, _ = select.select(self.streams, [], [])
        return ready_streams
      except select.error as e:
        if e.args[0] == errno.EINTR:
          continue
        raise"""

if old not in content:
    print("pattern not found, repo internals may have changed - skipping")
    sys.exit(0)

content = content.replace(old, new, 1)
if "import errno" not in content:
    content = content.replace("import select", "import errno\nimport select", 1)

with open(path, "w") as f:
    f.write(content)
print("patched")
EOF

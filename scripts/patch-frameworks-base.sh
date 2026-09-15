#!/usr/bin/env bash
# frameworks/base/core/java/android/net/StaticIpConfiguration.java (part of
# followmsi's forked frameworks/base) predates an upstream API refactor:
# it exposes dnsServers/domains as public fields, but the stock (unforked)
# frameworks/opt/net/wifi/service code — specifically
# WifiConfigurationUtil.java — calls .getDnsServers()/.getDomains() getter
# methods that don't exist here, so wifi-service fails to compile with
# "cannot find symbol". A real version-skew bug in the community port, hit
# on the first real build attempt (63% in, kernel build was already
# running in parallel).
#
# Fix: add the missing getters as thin wrappers over the existing public
# fields — additive only, doesn't touch the fields themselves in case
# other code in this tree still accesses them directly. Idempotent, safe
# to call before every build in case frameworks/base gets re-synced from
# scratch.
set -euo pipefail

: "${HEXAPHONE_SRC:?}"

F="$HEXAPHONE_SRC/frameworks/base/core/java/android/net/StaticIpConfiguration.java"
MARKER="public ArrayList<InetAddress> getDnsServers()"

[ -f "$F" ] || { echo "no StaticIpConfiguration.java yet, skipping"; exit 0; }

if grep -qF "$MARKER" "$F"; then
  echo "StaticIpConfiguration.java already patched."
  exit 0
fi

python3 - "$F" << 'EOF'
import sys

path = sys.argv[1]
with open(path) as f:
    content = f.read()

old = """    public String domains;

    public StaticIpConfiguration() {"""

new = """    public String domains;

    // Hexaphone: this fork predates the upstream getter-based API that
    // frameworks/opt/net/wifi/service (stock, unforked) expects — it still
    // calls .getDnsServers()/.getDomains() rather than reading the public
    // fields directly. Added as plain accessors over the existing fields
    // rather than converting the fields to private, to avoid breaking any
    // other code in this tree that still accesses them directly.
    public ArrayList<InetAddress> getDnsServers() {
        return dnsServers;
    }

    public String getDomains() {
        return domains;
    }

    public StaticIpConfiguration() {"""

if old not in content:
    print("error: expected anchor text not found, file may have changed", file=sys.stderr)
    sys.exit(1)

with open(path, "w") as f:
    f.write(content.replace(old, new, 1))
EOF

echo "StaticIpConfiguration.java patched."

#!/usr/bin/env bash
# repo init against the LineageOS 16.0 manifest, then layer our local
# manifest (followmsi's unofficial manta port: device + kernel + several
# forked frameworks/hardware repos) on top.
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

docker_run "
  git config --global user.email hexaphone@localhost &&
  git config --global user.name Hexaphone &&
  git config --global color.ui false &&
  repo init -u https://github.com/LineageOS/android.git -b lineage-16.0 --depth=${HEXAPHONE_DEPTH:-1}
"

mkdir -p "$HEXAPHONE_SRC/.repo/local_manifests"
cp "$PROJECT_ROOT/local_manifests/manta.xml" "$HEXAPHONE_SRC/.repo/local_manifests/manta.xml"

echo "repo initialized at $HEXAPHONE_SRC, local manifest for manta installed."

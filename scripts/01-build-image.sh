#!/usr/bin/env bash
# Build the Ubuntu 16.04 container image used for repo init/sync/build.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
docker build --platform linux/amd64 -t hexaphone-builder docker/

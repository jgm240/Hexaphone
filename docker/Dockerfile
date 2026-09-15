# Build environment matching what LineageOS 16.0 (Android 9 / lineage-16.0)
# expects: Ubuntu 16.04 (Xenial) with OpenJDK 8. Newer host toolchains break
# this era of the AOSP build system in subtle ways, so we pin the container
# rather than fight the host.

FROM --platform=linux/amd64 ubuntu:16.04

ENV DEBIAN_FRONTEND=noninteractive \
    LANG=C.UTF-8 \
    USE_CCACHE=1 \
    CCACHE_DIR=/src/.ccache

RUN apt-get update && apt-get install -y --no-install-recommends \
    openjdk-8-jdk \
    git-core gnupg flex bison gperf build-essential \
    zip curl zlib1g-dev gcc-multilib g++-multilib \
    libc6-dev-i386 lib32ncurses5-dev x11proto-core-dev \
    libx11-dev lib32z1-dev libgl1-mesa-dev libxml2-utils \
    xsltproc unzip fontconfig bc rsync ccache \
    python python3 \
    schedtool squashfs-tools pngcrush imagemagick \
    lib32readline-dev lib32z-dev liblz4-tool \
    libssl-dev libncurses5 libncurses5-dev libncursesw5-dev \
    ca-certificates wget \
    && rm -rf /var/lib/apt/lists/*

# NOTE: external/chromium-webview's prebuilt webview.apk files are stored
# via git-lfs, which repo sync can't smudge on its own (leaves LFS pointer
# stubs). We deliberately do NOT install the git-lfs binary here — it was
# tried and its Go TLS client fails hard against review.lineageos.org under
# this Docker/Rosetta setup ("tls: bad record MAC", 100% reproducible)
# even though plain curl to the same host works fine. Instead,
# scripts/fetch-webview-lfs.sh hits the LFS batch API with curl directly
# and downloads the object itself, bypassing git-lfs entirely.

# repo tool — pinned to a pre-2020 release. Google's current repo launcher
# requires Python 3.6+ (f-strings) to even bootstrap; Ubuntu 16.04 only has
# Python 3.5, and Python 3.5 wasn't around when LineageOS 14.1 was actually
# built anyway (it used repo on Python 2.7).
RUN curl -fsSL https://raw.githubusercontent.com/GerritCodeReview/git-repo/v1.13.9.4/repo -o /usr/local/bin/repo \
    && chmod a+x /usr/local/bin/repo

RUN update-alternatives --set java /usr/lib/jvm/java-8-openjdk-amd64/bin/java || true

WORKDIR /src

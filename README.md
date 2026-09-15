# Hexaphone

A rebranded LineageOS 16.0 (Android 9 / Pie) build for the Google Nexus 10
(`manta`), based on an unofficial community port — LineageOS itself never
shipped past 14.1 for this device (see "Why LineageOS 16.0" below).

This repo does **not** contain the Android source tree (~100GB+). It contains
everything needed to fetch and build it: the Docker build environment, repo
manifest additions, and the `vendor/hexaphone` branding/customization overlay
that turns the base ROM into Hexaphone.

## Known issues (inherited from the upstream unofficial port)

This port ([followmsi](https://github.com/followmsi), XDA) is what makes
Android 9 possible on this hardware at all, but it comes with real gaps we
haven't tried to fix:

- **Camera is broken** — HAL1 is broken, HAL2 isn't supported. The Camera2
  app is removed from the build for exactly this reason.
- **Hardware composer partly broken** — reports of invisible icons,
  wallpaper glitches, some overlay issues.
- **Encryption bootloops** — do not enable disk encryption.
- **NFC unreliable** — may not work correctly, reported to drain battery.
  NFC hardware support is removed from the build.

## Known issues (introduced by getting this to actually build)

Hitting these fixing/building Hexaphone itself — none are followmsi's fault,
they're version-skew between the forked and stock parts of this tree. See
`scripts/patch-*.sh` for the ones that were fixed at the source.

- **No AOT-compiled boot image** — `dex2oatd` crashes natively
  (`ClassLinker::InitWithoutImage`, a core ART bootstrap crash, not a
  normal verifier rejection) trying to precompile `boot.art`. Root cause
  not isolated — it crashes before per-class verification even starts,
  so it needs real bisection across the boot classpath to locate.
  Built with `WITH_DEXPREOPT=false` instead (see `scripts/04-build.sh`):
  the device falls back to on-device dex2oat/interpretation at first
  boot. Slower first boot, no other known functional loss.
Fixed at the source (not known issues, just documenting what was wrong):
`StaticIpConfiguration` was missing getter methods the stock WiFi code
expected (`patch-frameworks-base.sh`); `MmsService`'s `IMms` Stub
implementation expected a multi-user API the forked AIDL interface
predates (`patch-mms-service.sh`); and Telecom
(`packages/services/Telecomm`) had two separate mismatches — a missing
`StatusHints.validateAccountIconUserBoundary()` method, and 6
`ITelecomService.Stub` methods returning `ParceledListSlice<T>` where the
forked AIDL interface still expects plain `List<T>` (`patch-telecom.sh`).
Tried removing Telecom via the debloat list first (manta has no cellular
modem, so it looked like dead weight) — turned out `PRODUCT_PACKAGES`
filter-out doesn't actually drop it, something else in the tree still
pulls it in as a build dependency regardless. Patched it properly instead
since debloating wasn't actually an option here.

## Layout

- `docker/` — Ubuntu 16.04 container matching the toolchain this era of
  AOSP expects (OpenJDK 8, old host tool assumptions). Native macOS builds
  aren't supported.
- `local_manifests/manta.xml` — the followmsi unofficial-port manifest
  (device tree, kernel, and several forked frameworks/hardware repos),
  layered on top of the upstream LineageOS 16.0 manifest.
- `vendor/hexaphone/` — brand identity, product makefile, debloat list,
  overlay resources, boot animation, plus the root-side support for the
  Performance app (init service, script, sepolicy). This is what makes it
  "Hexaphone" instead of stock LineageOS.
- `packages/hexaphone/Performance/` — the Performance system app (ZRAM /
  swap file control).
- `patches/` — kernel and device-tree notes/patches for `manta`, applied
  after their respective source is synced.
- `scripts/` — orchestration: repo init, sync, breakfast + build.

## Status

| Piece | State |
|---|---|
| Rebrand (name/props) | Scaffolded, needs source tree to finish wiring in |
| Boot animation + wallpaper | Done — generated hexagon mark identity, see `vendor/hexaphone/art/README.md`. Boot anim wired via `PRODUCT_COPY_FILES`, wallpaper via a `default_wallpaper` framework overlay |
| Debloat list | Template in `vendor/hexaphone/hexaphone.mk`, needs review against synced package set |
| Performance app (ZRAM/swap UI) | App + root-side script written (`packages/hexaphone/Performance`); sepolicy is a draft needing a real boot+logcat pass; device tree needs a 2-line patch (`patches/device/README.md`); kernel needs zram configs confirmed (`patches/kernel/README.md`) |
| Kernel governor/battery tuning | Documented target in `patches/kernel/README.md`, patch pending kernel checkout |
| Theme (dark mode + hexagon icons) | Done — `config_defaultNightMode`=2 and a hexagon `config_icon_mask`, both verified against the real synced source, not guessed. Settings/SystemUI were already dark-themed in this tree. QS-tile hexagons and notification-icon-circle reshaping deliberately skipped — see "Deliberately not done" in `vendor/hexaphone/art/README.md` for why. Performance's icon rebuilt as a real adaptive icon so it isn't the one square icon on a hexagon ROM |
| Bundled apps | DuckDuckGo (default browser), F-Droid, Aurora Store — all F-Droid builds, presigned, hash-pinned fetch in `scripts/fetch-prebuilt-apks.sh`, wired into `04-build.sh`. F-Droid + Aurora Store matter more than usual here since this ROM has no GApps/Play Store at all |
| Source sync | **Done** (shallow clone, ~50GB) — see `logs/sync.log`. Not yet built. |

## Build environment

Source is a **shallow clone** (`--depth=1`, no full git history) — much
faster to sync and smaller on disk. The only downside is the build's
`ro.build.version.incremental` (a commit count) is cosmetic/inaccurate;
nothing else depends on full history. Set `HEXAPHONE_DEPTH` before
`02-repo-init.sh` to override.

Source + build output must live on a volume with **250GB+ free**
(source ~100GB, build output/ccache ~100-150GB more). Set `HEXAPHONE_SRC` to
that path before running any script:

```bash
export HEXAPHONE_SRC=/Volumes/<your-volume>/hexaphone-src
```

## Build steps

```bash
scripts/01-build-image.sh   # build the Docker build environment once
scripts/02-repo-init.sh     # repo init against LineageOS 16.0 manifest
scripts/03-sync.sh          # repo sync (long — hours)
scripts/04-build.sh         # breakfast hexaphone_manta && mka bacon
```

Output zip lands in `$HEXAPHONE_SRC/out/target/product/manta/`.

## Why LineageOS 16.0 / Android 9

LineageOS itself never shipped past 14.1 (Android 7.1.2) for manta — the
device's 32-bit-only hardware and unmaintained GPU/kernel blobs never made
the jump to Project Treble in LineageOS 15.1+. XDA developer followmsi
built and maintained an unofficial LineageOS 16.0 port anyway, forking the
device tree, kernel, and several framework/hardware repos to bridge that
gap (see `local_manifests/manta.xml` for the exact forks/revisions used).
It's not Treble — we're building this the pre-Treble way, same as 14.1,
just with a newer Android version on top of forked components.

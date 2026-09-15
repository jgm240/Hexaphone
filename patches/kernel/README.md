# Kernel tuning — manta (Exynos 5250, 3.4 kernel)

Not yet applied — needs `kernel/samsung/manta` checked out first (pulled by
`local_manifests/manta.xml` during sync — this is now followmsi's fork,
branch `followmsi-pie`, still a 3.4 tree with Android 9-era patches on top,
not the stock LineageOS 14.1 kernel). Once it's there, turn each item
below into an actual patch in this directory and reference it from a
`BOARD_KERNEL_CMDLINE` / defconfig change in the device tree, or apply
directly against `arch/arm/configs/manta_defconfig`.

Planned changes:

1. **CPUFreq governor defaults** — `manta_defconfig` sets the default
   governor and `cpufreq_interactive` tunables
   (`hispeed_freq`, `above_hispeed_delay`, `min_sample_time`). Nexus 10's
   stock tuning is conservative for battery life at the cost of UI
   responsiveness; retune `hispeed_freq` down a notch and shorten
   `above_hispeed_delay` for snappier UI, without changing max frequency
   (thermal headroom on this SoC/chassis is limited — no overclock).

2. **I/O scheduler** — switch the default block scheduler for the internal
   eMMC from CFQ to `noop` or `sio` (deadline-family), which measurably
   helps on flash storage with no seek penalty; CFQ's fairness logic is
   pure overhead here.

3. **Low memory killer** — 2GB RAM is tight for a 2017-era Android build
   with modern apps. Consider adjusting `lowmemorykiller` thresholds so
   background tabs/apps survive longer before being killed — complements
   the Performance app's ZRAM/swap rather than replacing it.

Each of these needs verification against the actual current defconfig
contents once synced — this file just records intent, not a working diff.

## Required for the Performance app (ZRAM/swap)

The Performance app (`packages/hexaphone/Performance`) needs these enabled
in `arch/arm/configs/manta_defconfig` — check whether they already are
(many CM14.1-era Exynos kernels shipped zram already) and add whatever's
missing:

- `CONFIG_ZRAM=y` (or `=m`, but `=y` is simpler — no module-loading to
  coordinate with the boot-time init script)
- `CONFIG_ZRAM_DEFAULT_COMP` / compression backends: `CONFIG_CRYPTO_LZO=y`
  at minimum; `CONFIG_CRYPTO_LZ4=y` if available on this kernel (see the
  note in `Performance/res/values/arrays.xml` about not offering zstd —
  too new for a 3.4 kernel)
- `CONFIG_SWAP=y` (needed for both zram-as-swap and the plain swap file)

Also confirm the block device shows up as `/dev/block/zram0` and the sysfs
controls as `/sys/block/zram0/{disksize,comp_algorithm,reset}` —
`hexaphone-zram.sh` assumes the mainline zram driver's node names, which
should hold for a 3.4 kernel but is worth checking once booted.

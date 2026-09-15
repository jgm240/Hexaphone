#!/system/bin/sh
# Applies Hexaphone's ZRAM/swap configuration at boot. Runs as root via the
# hexaphone_zram init service (vendor/hexaphone/rootdir/etc/init/hexaphone.rc),
# reading the persist.sys.hexaphone.* properties the Performance app writes.
#
# NOTE: sysfs paths below (/sys/block/zram0/...) match the mainline zram
# driver; verify against manta's actual 3.4 kernel zram driver once synced —
# older/vendor-forked zram drivers sometimes use slightly different node
# names (e.g. no comp_algorithm node if only one codec is compiled in).

ZRAM_DEV=/dev/block/zram0
ZRAM_SYS=/sys/block/zram0
SWAPFILE=/data/hexaphone_swapfile

zram_enabled=$(getprop persist.sys.hexaphone.zram.enabled 0)
zram_algo=$(getprop persist.sys.hexaphone.zram.algo lz4)
zram_size_mb=$(getprop persist.sys.hexaphone.zram.size_mb 512)

swap_enabled=$(getprop persist.sys.hexaphone.swap.enabled 0)
swap_size_mb=$(getprop persist.sys.hexaphone.swap.size_mb 512)

if [ "$zram_enabled" = "1" ]; then
    # Reset first in case it's already initialized from a previous boot
    # stage (some kernels bring zram up with a default config).
    swapoff "$ZRAM_DEV" 2>/dev/null
    echo 1 > "$ZRAM_SYS/reset" 2>/dev/null

    if [ -f "$ZRAM_SYS/comp_algorithm" ]; then
        echo "$zram_algo" > "$ZRAM_SYS/comp_algorithm"
    fi
    echo "${zram_size_mb}M" > "$ZRAM_SYS/disksize"

    mkswap "$ZRAM_DEV"
    swapon "$ZRAM_DEV"
fi

if [ "$swap_enabled" = "1" ]; then
    current_size_mb=0
    if [ -f "$SWAPFILE" ]; then
        current_size_bytes=$(stat -c %s "$SWAPFILE" 2>/dev/null || echo 0)
        current_size_mb=$((current_size_bytes / 1024 / 1024))
    fi

    if [ "$current_size_mb" != "$swap_size_mb" ]; then
        swapoff "$SWAPFILE" 2>/dev/null
        rm -f "$SWAPFILE"
        dd if=/dev/zero of="$SWAPFILE" bs=1m count="$swap_size_mb"
        chmod 600 "$SWAPFILE"
        mkswap "$SWAPFILE"
    fi

    swapon "$SWAPFILE"
fi

exit 0

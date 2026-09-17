#!/system/bin/sh
# Sets SELinux enforcement based on sys.hexaphone.selinux_permissive's
# value ("1" -> permissive, anything else -> enforcing). A true toggle
# now (System Tweaks' Quick Settings tile needs to be able to turn
# enforcement back on without a reboot, not just one-shot to permissive)
# -- though setenforce 0 still resets to enforcing on its own at the next
# reboot regardless of whether anything ever calls this with "0" first.
# Runs in its own dedicated, `permissive`-marked domain (see
# vendor/hexaphone/sepolicy/hexaphone.te for why a plain `allow` rule for
# this isn't possible on this tree at all) whose only job, ever, is this.

value=$(getprop sys.hexaphone.selinux_permissive)
if [ "$value" = "1" ]; then
    setenforce 0
    log -p w -t hexaphone_selinux_permissive "SELinux set to permissive"
else
    setenforce 1
    log -p i -t hexaphone_selinux_permissive "SELinux set to enforcing"
fi

exit 0

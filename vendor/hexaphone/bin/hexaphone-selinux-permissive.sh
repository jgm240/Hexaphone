#!/system/bin/sh
# Sets SELinux to permissive mode. Resets to enforcing at the next reboot
# on its own -- this script never re-enables enforcement itself, there's
# nothing to undo. Runs in its own dedicated, `permissive`-marked domain
# (see vendor/hexaphone/sepolicy/hexaphone.te for why a plain `allow` rule
# for this isn't possible on this tree at all) whose only job, ever, is
# this one line.

setenforce 0
log -p w -t hexaphone_selinux_permissive "SELinux set to permissive (resets at next reboot)"

exit 0

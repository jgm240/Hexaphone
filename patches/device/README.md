# device/samsung/manta patches needed for the Performance app

Not yet applied — needs `device/samsung/manta` checked out first. Two small,
required changes once it's there:

1. **Import the Hexaphone init script.** `vendor/hexaphone/rootdir/etc/init/hexaphone.rc`
   gets copied to `system/etc/init/hexaphone.rc` at build time (see
   `vendor/hexaphone/hexaphone.mk`), but Android 7.x's init doesn't
   auto-import every `.rc` file in that directory (that's a Treble/8.0+
   behavior) — add an explicit line to the device's `init.manta.rc` (or
   wherever the device tree's root init script is):

   ```
   import /system/etc/init/hexaphone.rc
   ```

2. **Register the sepolicy directory.** Add to `BoardConfig.mk`:

   ```
   BOARD_SEPOLICY_DIRS += vendor/hexaphone/sepolicy
   ```

   `vendor/hexaphone/sepolicy/hexaphone.te` and `property_contexts` are a
   draft (see the comment at the top of `hexaphone.te`) — they need a real
   build+boot+logcat iteration pass, not just merging in blind.

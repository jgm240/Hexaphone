# Hexaphone product definition for manta (Nexus 10), on top of the
# followmsi unofficial LineageOS 16.0 (Android 9) port.
#
# Mirrors followmsi's device/samsung/manta/lineage_manta.mk inherit chain
# (vendor/lineage/config/common_mini_tablet_wifionly.mk +
# device/samsung/manta/full_manta.mk — verify this still matches once
# synced, in case the fork's structure differs from what we saw on GitHub),
# then overrides identity so it builds and reports as "Hexaphone" instead
# of LineageOS.

$(call inherit-product, vendor/lineage/config/common_mini_tablet_wifionly.mk)
$(call inherit-product, device/samsung/manta/full_manta.mk)
$(call inherit-product, vendor/hexaphone/config/common.mk)
include vendor/hexaphone/hexaphone.mk

PRODUCT_NAME := hexaphone_manta
PRODUCT_DEVICE := manta
PRODUCT_BRAND := hexaphone
PRODUCT_MODEL := Hexaphone Tablet
PRODUCT_MANUFACTURER := Samsung

# followmsi's lineage_manta.mk also spoofs the build fingerprint to a real
# Nexus 10 (mantaray) retail signature via PRODUCT_BUILD_PROP_OVERRIDES +
# BUILD_FINGERPRINT, presumably for GApps/device-attestation compatibility.
# Deliberately not carried over here — it fights the Hexaphone identity and
# isn't needed unless something (SafetyNet, a specific app) turns out to
# require it. Revisit if GApps compatibility becomes a goal.

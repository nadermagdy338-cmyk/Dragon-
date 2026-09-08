# BoardConfig.mk for MaxManager ROM integration
# ==============================================
# Add this to your device BoardConfig.mk or create a new one

# Include MaxManager in system image
PRODUCT_PACKAGES += \
    MaxManager \
    maxmanager_daemon \
    libmaxmanager_native

# SELinux policy
BOARD_SEPOLICY_DIRS += device/[vendor]/[device]/sepolicy

# Init scripts
PRODUCT_COPY_FILES += \
    device/[vendor]/[device]/maxmanager.rc:$(TARGET_COPY_OUT_VENDOR)/etc/init/maxmanager.rc

# Permissions
PRODUCT_COPY_FILES += \
    device/[vendor]/[device]/permissions/nd.max.xml:$(TARGET_COPY_OUT_SYSTEM)/etc/permissions/nd.max.xml

# Override default properties
PRODUCT_PROPERTY_OVERRIDES += \
    maxmanager.enable=1 \
    maxmanager.verbose=0
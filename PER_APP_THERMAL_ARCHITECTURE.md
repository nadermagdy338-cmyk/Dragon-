# Per-App Thermal Ownership

This change moves Xiaomi `thermal_message/sconfig` ownership out of the Kotlin
companion and into `sys.maxmanager-service` (ArchDaemon).

## Runtime model

```text
AppMonitor.kt
    |
    | app_status / foreground package
    v
sys.maxmanager-service
    |
    +-- GameConfig: gpu_profile / thermal_profile / cpu_governor /
    |               gpu_governor / gpu_max_freq
    |
    +-- per-app thermal ownership
            |
            +-- save current sconfig
            +-- sconfig = 6 while a custom CPU/GPU override is active
            +-- reassert only when vendor userspace changes it
            +-- restore saved mode when the override ends
```

Mode `6` is not itself a temperature profile. It is the Xiaomi no-limits policy
selection used to stop `mi_thermald` from replacing a custom CPU/GPU limit. The
actual per-app performance profile remains the GPU/CPU configuration already
stored for that package.

## Important ownership rules

- Only the privileged daemon writes `sconfig`.
- The Kotlin companion no longer writes `sconfig`.
- The legacy `xiaomi-extras.sh` background writer is no longer launched because
  it could continuously overwrite the daemon's ownership with mode 11/27/0.
- If the node is missing, the feature is a no-op.
- If `sconfig` is already `6`, the daemon does not claim restoration ownership
  because it did not change the value.
- The previous mode is recorded in a small marker file before changing the
  kernel node, allowing recovery after a daemon crash.
- The marker is removed after successful restoration.
- The daemon restores ownership when it receives SIGTERM/SIGINT.
- Ownership is active only while the screen is effectively awake and a tracked
  foreground package has a non-default CPU/GPU override.

## Why this fixes the old design

The old path split ownership across `AppMonitor.kt`, `VendorThermalUtil.kt`,
and a second shell loop. Those writers could race each other and `mi_thermald`.
The new path has one privileged writer and one state machine, while the Java
side remains responsible for app-specific GPU/CPU writes that already exist in
`PerAppKernelUtil`.

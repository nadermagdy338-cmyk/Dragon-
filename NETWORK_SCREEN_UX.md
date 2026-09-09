# Network & Scheduler UX redesign

The Network/Scheduler screen now follows the Max Manager product language instead of using a page-local theme or a decorative fixed-color hero.

- Uses the application's MaterialTheme directly.
- Compact top app bar; no oversized solid-color header.
- Live overview summarizes available TCP controls, enabled switches, and the current congestion algorithm.
- Network, Scheduler, UClamp, Advanced and Kernel controls are visually separated by task, not by implementation detail.
- Device-dependent controls remain conditional on real kernel node availability.
- Raw tunables remain editable through the existing real sysfs/procfs ViewModel operations.
- No fake charts, fake values, or decorative controls were added.

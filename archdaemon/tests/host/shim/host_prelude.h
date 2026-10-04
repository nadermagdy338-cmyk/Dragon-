/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 *
 * Forced-include prelude for the host parity build (`-include host_prelude.h`).
 *
 * Android's toolchain pulls certain headers in transitively (bionic's
 * <sys/system_properties.h> and friends bring in <sys/time.h>). Host glibc does
 * not. Rather than edit production sources for a test-only portability gap,
 * the harness forces this prelude into every translation unit, so the daemon
 * sources stay byte-identical to what ships.
 */
#ifndef MAXMANAGER_HOST_PRELUDE_H
#define MAXMANAGER_HOST_PRELUDE_H

#include <sys/time.h>
#include <sys/types.h>

#endif

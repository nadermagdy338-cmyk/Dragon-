/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 *
 * Host stand-in for bionic's <sys/system_properties.h>.
 *
 * The daemon is compiled for Android, but a large part of it is pure decision
 * logic (property whitelisting, renderer/resolution branching, string parsing)
 * that never touches a real device node. This shim lets the REAL translation
 * units be compiled and executed on a desktop so that logic can be measured
 * without a phone. It is a test fixture, not shipped code, and it is not an
 * attempt to emulate Android — only the handful of symbols the audited units
 * actually call.
 *
 * `host_prop_*` are test-only helpers; nothing in production references them.
 */
#ifndef MAXMANAGER_HOST_SYSTEM_PROPERTIES_H
#define MAXMANAGER_HOST_SYSTEM_PROPERTIES_H

#include <stdint.h>

#define PROP_VALUE_MAX 92
#define PROP_NAME_MAX 32

typedef struct prop_info prop_info;
typedef void (*prop_read_callback)(void* cookie, const char* name, const char* value, uint32_t serial);

int __system_property_get(const char* name, char* value);
int __system_property_set(const char* name, const char* value);
void __system_property_read_callback(const prop_info* pi, prop_read_callback callback, void* cookie);
int __system_property_foreach(void (*callback)(const prop_info* pi, void* cookie), void* cookie);

/* ── test-only helpers (not part of the Android contract) ── */
void host_prop_reset(void);
void host_prop_set(const char* name, const char* value);
int host_prop_count(void);

#endif

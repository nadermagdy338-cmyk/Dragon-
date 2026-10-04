/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * بديلٌ مصغَّر لـ`cutils/ashmem.h` — لأنّ `libcutils` ليست في الـNDK.
 *
 * و`libfmq` يستعمل الثلاثة في `MessageQueueBase.h` (سطر ٨٠٣-٨٠٤): إنشاءُ منطقة، وضبطُ
 * صلاحيّتها، وقراءةُ حجمها. والتنفيذ هنا على `ASharedMemory_*` من الـNDK
 * (`<android/sharedmem.h>`، منذ API 26) — وهي **الطبقة نفسها** التي كانت `ashmem_*`
 * غلافًا عليها في AOSP.
 *
 * ودلالة العائد محفوظة حرفيًّا: `0` نجاح و`-1` فشل في `set_prot`، ووصفٌ (`fd`) أو سالب
 * في `create_region`، وحجمٌ بالبايت أو سالب في `get_size` — لأنّ `libfmq` يقارن بها.
 */
#ifndef MAXFX_COMPAT_CUTILS_ASHMEM_H
#define MAXFX_COMPAT_CUTILS_ASHMEM_H

#include <stddef.h>
#include <sys/types.h>

#include <android/sharedmem.h>

#ifdef __cplusplus
extern "C" {
#endif

static inline int ashmem_create_region(const char* name, size_t size) {
    return ASharedMemory_create(name == NULL ? "maxfx-fmq" : name, size);
}

static inline int ashmem_set_prot_region(int fd, int prot) {
    return ASharedMemory_setProt(fd, prot) == 0 ? 0 : -1;
}

static inline int ashmem_get_size_region(int fd) {
    return (int)ASharedMemory_getSize(fd);
}

#ifdef __cplusplus
}
#endif

#endif /* MAXFX_COMPAT_CUTILS_ASHMEM_H */

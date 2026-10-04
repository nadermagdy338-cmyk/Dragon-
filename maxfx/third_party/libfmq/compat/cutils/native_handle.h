/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * بديلٌ مصغَّر لـ`cutils/native_handle.h` — والبنية والدوالّ من AOSP
 * (`system/core/libcutils/include/cutils/native_handle.h` و`native_handle.c`) لأنّ
 * `libcutils` ليست في الـNDK.
 *
 * **ولماذا نسخةٌ خاصّة بنا:** `native_handle_create/close/delete` **دوالّ** في `libcutils`
 * لا ماكروات؛ ورأسٌ يُوجد بلا مكتبةٍ يُنتج خطأ ربطٍ لا خطأ ترجمة — أي فشلًا في CI برسالة
 * غامضة. فالتنفيذ عندنا (`native_handle_shim.cpp`) يجعل المكتبة مكتفيةً ذاتيًّا.
 *
 * والبنية **مطابقة بايتًا ببايت**: `{int version; int numFds; int numInts; int data[0];}`
 * بترتيب حقولها — وهي تُمرَّر داخل `NativeHandle` عبر binder إلى عمليّةٍ أخرى، فأي اختلافٍ
 * في التخطيط عطبٌ صامت.
 */
#ifndef MAXFX_COMPAT_CUTILS_NATIVE_HANDLE_H
#define MAXFX_COMPAT_CUTILS_NATIVE_HANDLE_H

#include <sys/cdefs.h>

__BEGIN_DECLS

typedef struct native_handle {
    int version; /* sizeof(struct native_handle) */
    int numFds;
    int numInts;
    int data[0];
} native_handle_t;

typedef const native_handle_t* buffer_handle_t;

#define NATIVE_HANDLE_MAX_FDS 1024
#define NATIVE_HANDLE_MAX_INTS 1024
#define NATIVE_HANDLE_VERSION 12

/* `count` = عدد الـ`int` المخصّصة بعد الحقول الأربعة. */
#define native_handle_num_ints(count) (count)

static inline int native_handle_init_internal(native_handle_t* h, int numFds, int numInts) {
    h->version = NATIVE_HANDLE_VERSION;
    h->numFds = numFds;
    h->numInts = numInts;
    return 0;
}

native_handle_t* native_handle_create(int numFds, int numInts);
native_handle_t* native_handle_init(char* storage, int numFds, int numInts);
int native_handle_delete(native_handle_t* h);
int native_handle_close(const native_handle_t* h);

__END_DECLS

#endif /* MAXFX_COMPAT_CUTILS_NATIVE_HANDLE_H */

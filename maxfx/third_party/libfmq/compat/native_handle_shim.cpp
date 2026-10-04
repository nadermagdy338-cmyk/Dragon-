/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * تنفيذ دوالّ `native_handle` الثلاث التي يحتاجها `libfmq` — منقولةٌ بنصّ سلوكها من
 * `system/core/libcutils/native_handle.c` (Apache-2.0، بنسبة الفضل).
 *
 * **والسلوك المنقول حرفيًّا:** `native_handle_close` تُغلق كل وصفٍ ثمّ تُصفّره؛ وهذه
 * **إغلاق مزدوج** يصير عند استدعائها مرّتين، ولهذا يُصفَّر `data[i]` بعد الإغلاق
 * (`-1` في الأصل) فلا يُغلق وصفٌ مُغلَق. و`libfmq` يستدعي `close` ثمّ `delete` — ولو
 * لم نُصفّر، لأُغلق كل وصفٍ مرّةً ثانيةً.
 */
#include <cutils/native_handle.h>

#include <stdlib.h>
#include <string.h>
#include <unistd.h>

native_handle_t* native_handle_create(int numFds, int numInts) {
    if (numFds < 0 || numInts < 0 || numFds > NATIVE_HANDLE_MAX_FDS ||
        numInts > NATIVE_HANDLE_MAX_INTS) {
        return NULL;
    }

    size_t size = sizeof(native_handle_t) + (size_t)(numFds + numInts) * sizeof(int);
    native_handle_t* h = (native_handle_t*)malloc(size);
    if (h == NULL) return NULL;

    h->version = sizeof(native_handle_t);
    h->numFds = numFds;
    h->numInts = numInts;
    for (int i = 0; i < numFds + numInts; i++) {
        h->data[i] = -1;
    }
    return h;
}

native_handle_t* native_handle_init(char* storage, int numFds, int numInts) {
    if (storage == NULL || numFds < 0 || numInts < 0) {
        return NULL;
    }
    native_handle_t* h = (native_handle_t*)storage;
    h->version = sizeof(native_handle_t);
    h->numFds = numFds;
    h->numInts = numInts;
    return h;
}

int native_handle_close(const native_handle_t* h) {
    if (h == NULL) return 0;
    const int numFds = h->numFds;
    for (int i = 0; i < numFds; i++) {
        int fd = h->data[i];
        if (fd >= 0) {
            /* الإغلاق هنا بلا فحص النتيجة: الوصف قد يكون مُغلقًا سابقًا، والصفّ التالي
             * هو الحرس الحقيقيّ. */
            close(fd);
            ((native_handle_t*)h)->data[i] = -1;
        }
    }
    return 0;
}

int native_handle_delete(native_handle_t* h) {
    if (h == NULL) return 0;
    free(h);
    return 0;
}

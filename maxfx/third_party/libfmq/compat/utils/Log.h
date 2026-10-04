/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * بديلٌ مصغَّر لـ`utils/Log.h` — لأنّ `libutils` ليست في الـNDK.
 *
 * و`libfmq` يستعمل `ALOGE`/`ALOGW`/`ALOGV`، و`android_errorWriteLog` في
 * `FmqInternal.cpp`. والوسم `LOG_TAG` يُعرَّف في الملفّ المُضمِّن قبل هذا الرأس — ويُحترم.
 */
#ifndef MAXFX_COMPAT_UTILS_LOG_H
#define MAXFX_COMPAT_UTILS_LOG_H

#include <android/log.h>
#include <stdarg.h>

#ifndef LOG_TAG
#define LOG_TAG "maxfx"
#endif

#define ALOG(priority, ...) __android_log_print((priority), LOG_TAG, __VA_ARGS__)
#define ALOGE(...) ALOG(ANDROID_LOG_ERROR, __VA_ARGS__)
#define ALOGW(...) ALOG(ANDROID_LOG_WARN, __VA_ARGS__)
#define ALOGI(...) ALOG(ANDROID_LOG_INFO, __VA_ARGS__)
#define ALOGD(...) ALOG(ANDROID_LOG_DEBUG, __VA_ARGS__)
#define ALOGV(...) ALOG(ANDROID_LOG_VERBOSE, __VA_ARGS__)

#define ALOG_ASSERT(cond, ...) \
    do {                       \
        if (!(cond)) {         \
            ALOGE(__VA_ARGS__); \
        }                      \
    } while (0)

/* في AOSP تُبلِّغ هذه الدالة نظام التحقّق من الكتابة الفائضة؛ وهنا تُسجَّل فقط. */
static inline void android_errorWriteLog(int tag, const char* info) {
    __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "errorWriteLog(%d): %s", tag,
                        info == nullptr ? "(null)" : info);
}

static inline void android_errorWriteWithInfoLog(int tag, const char* subTag, int32_t uid,
                                                 const char* info) {
    __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "errorWriteLog(%d/%s uid=%d): %s", tag,
                        subTag == nullptr ? "(null)" : subTag, (int)uid,
                        info == nullptr ? "(null)" : info);
}

#endif /* MAXFX_COMPAT_UTILS_LOG_H */

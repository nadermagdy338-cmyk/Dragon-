/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * بديلٌ مصغَّر لـ`utils/SystemClock.h` — لأنّ `libutils` ليست في الـNDK.
 *
 * و`libfmq` يستعمل `elapsedRealtimeNano()` لحساب المهل في `wait`، والقيمة يجب أن تكون
 * **مُتزايدة رتيبة** (`CLOCK_MONOTONIC`) لا ساعة الحائط — فساعةٌ تُعاد ضبطها أثناء الانتظار
 * تُنتج مهلةً سالبة. وهذا هو تعريف AOSP نفسه في `system/core/libutils/SystemClock.cpp`.
 */
#ifndef MAXFX_COMPAT_UTILS_SYSTEM_CLOCK_H
#define MAXFX_COMPAT_UTILS_SYSTEM_CLOCK_H

#include <stdint.h>
#include <time.h>

namespace android {

static inline int64_t elapsedRealtimeNano() {
    struct timespec ts;
    if (clock_gettime(CLOCK_MONOTONIC, &ts) != 0) return 0;
    return (int64_t)ts.tv_sec * 1000000000LL + (int64_t)ts.tv_nsec;
}

static inline int64_t elapsedRealtime() { return elapsedRealtimeNano() / 1000000LL; }

static inline int64_t uptimeMillis() { return elapsedRealtime(); }

}  // namespace android

#endif /* MAXFX_COMPAT_UTILS_SYSTEM_CLOCK_H */

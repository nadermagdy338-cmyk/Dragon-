/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * حاضنة **تصريف** لسطح `compat/android-base/logging.h` — وليست جزءًا من المكتبة.
 *
 * ⚠️ لا تُضاف إلى `LOCAL_SRC_FILES` في `jni/Android.mk`: لا تُبنى ولا تُربط، بل تُصرَّف
 * (`-fsyntax-only`) في CI قبل `ndk-build` بثوانٍ.
 *
 * ── لماذا وُجدت ──
 *
 * بديل `logging.h` يُقلّد سطح `libbase` بقدر ما تستعمله `libfmq` — وسطحٌ ناقص فيه لا يُكشف
 * إلا **داخل** بناء المكتبة، بعد توليد الرؤوس ودقائق من الترجمة. ووقع فعلًا: التشغيل
 * `37030025157` سقط بـ`error: use of undeclared identifier 'ERROR'` في `FmqInternal.cpp`،
 * وهو عطبُ سطحٍ لا عطبُ منطق — يُمسك بتصريف سطرٍ واحد.
 *
 * والمحاكاة هنا **حرفيّة في موضع الاستعمال**: `namespace android::hardware::details` بالضبط
 * كما في `FmqInternal.cpp`، وفيه `LOG(ERROR)` بالتسمية غير المؤهَّلة — وهي النقطة التي لا
 * تمرّ فيها الشدّة بـ`android::base` بالتسمية المباشرة.
 */
#define LOG_TAG "FMQ_surface_test"

#include <android-base/logging.h>

#include <utils/Log.h>

#include <string>

namespace android {
namespace hardware {
namespace details {

/* مأخوذة حرفيًّا من `third_party/libfmq/src/FmqInternal.cpp` */
void check(bool exp) {
    CHECK(exp);
}

void check(bool exp, const char* message) {
    CHECK(exp) << message;
}

void logError(const std::string& message) {
    LOG(ERROR) << message;
}

/* والصيغة المؤهَّلة يجب أن تعمل أيضًا — وهذا نصّ تعليق AOSP على SEVERITY_LAMBDA. */
void logQualified(const std::string& message) {
    LOG(::android::base::WARNING) << message;
    LOG(INFO) << "unqualified";
}

/* `ALOG*` من بديل `utils/Log.h` — تُستعمل في `errorWriteLog` هناك. */
void logAndroid(const char* text) {
    ALOGE("%s", text);
    ALOGW("warn");
    ALOGI("info");
}

}  // namespace details
}  // namespace hardware
}  // namespace android

/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * بديلٌ مصغَّر لـ`android-base/logging.h` — لأنّ `libbase` ليست في الـNDK.
 *
 * ويُوفّر ما تستعمله `libfmq` فقط: `LOG(SEVERITY) << …` كنهرٍ، و`CHECK(expr) << …` الذي
 * يُسقط العملية عند الكذب، و`LOG_TAG` يأتي من الملفّ المُضمِّن (**قبل** تضمين هذا الرأس)
 * فيُحترم كما في الأصل.
 *
 * ── ولماذا الأسماء (`ERROR` · `CHECK` …) لا تكفي وحدها ──
 *
 * `LOG(severity)` يُنادى بالتسمية **غير المؤهَّلة** عند موضع الاستعمال:
 *
 *     namespace android { namespace hardware { namespace details {
 *     void logError(const std::string& message) { LOG(ERROR) << message; }
 *
 * وبحثُ الاسم غير المؤهَّل يصعد من `details` إلى `hardware` إلى `android` إلى العالم —
 * **ولا يمرّ بـ`android::base`**، فالشدّة لا تُرى من هناك. وهذا ليس افتراضًا: قِيس في
 * التشغيل `37030025157`:
 *     third_party/libfmq/src/FmqInternal.cpp:34:9: error: use of undeclared identifier 'ERROR'
 *
 * وحلُّ AOSP ليس تصديرًا عالميًّا للشدّات، بل **ميكانيزمٌ داخل الماكرو نفسه**: دالّة
 * `SEVERITY_LAMBDA` التي تُدخل الأسماء بـ`using` داخل جسم lambda ثم تُعيد الوسيط — فيقبل
 * `LOG` الاسم المؤهَّل وغير المؤهَّل معًا (‏`include/android-base/logging.h` سطر ١٧٥).
 * وهذا الرأس يُطابق الميكانيزم لا يُقلّده بالتخمين — وترتيب `LogSeverity` هو ترتيب AOSP
 * نفسه، فلا تُقرأ شدّةٌ على أنّها أخرى.
 */
#ifndef MAXFX_COMPAT_ANDROID_BASE_LOGGING_H
#define MAXFX_COMPAT_ANDROID_BASE_LOGGING_H

#include <android/log.h>
#include <stdlib.h>

#include <sstream>
#include <string>

/* تُعرَّف فقط إن لم يُعرّفها المُضمِّن — فلا نُبطل وسمًا مقصودًا. */
#ifndef LOG_TAG
#define LOG_TAG "maxfx"
#endif

namespace android {
namespace base {

/* ترتيب AOSP الحرفيّ (‏`include/android-base/logging.h`): لا يُعاد ترتيبه. */
enum LogSeverity {
    VERBOSE,
    DEBUG,
    INFO,
    WARNING,
    ERROR,
    FATAL_WITHOUT_ABORT,
    FATAL,
};

namespace logging_internal {

inline int severityToPriority(LogSeverity severity) {
    if (severity == VERBOSE) return ANDROID_LOG_VERBOSE;
    if (severity == DEBUG) return ANDROID_LOG_DEBUG;
    if (severity == INFO) return ANDROID_LOG_INFO;
    if (severity == WARNING) return ANDROID_LOG_WARN;
    if (severity == ERROR) return ANDROID_LOG_ERROR;
    return ANDROID_LOG_FATAL;  /* FATAL_WITHOUT_ABORT وFATAL */
}

/* نهرٌ يُفرِّغ عند نهاية العبارة — فيبقى `LOG(X) << a << b` سطرًا واحدًا في السجل. */
class LogMessage {
  public:
    LogMessage(LogSeverity severity, const char* tag)
        : severity_(severity), tag_(tag), fatal_(severity == FATAL) {}

    LogMessage(const LogMessage&) = delete;
    LogMessage& operator=(const LogMessage&) = delete;

    template <typename T>
    LogMessage& operator<<(const T& value) {
        stream_ << value;
        return *this;
    }

    ~LogMessage() {
        const std::string text = stream_.str();
        if (!text.empty() || fatal_) {
            __android_log_write(severityToPriority(severity_), tag_, text.c_str());
        }
        if (fatal_) {
            abort();
        }
    }

  private:
    LogSeverity severity_;
    const char* tag_;
    bool fatal_;
    std::ostringstream stream_;
};

/* `CHECK(expr) << …`: الرسالة تُكتب عند الفشل وحده، والإسقاط في مُدمِّر النهر. */
class CheckMessage {
  public:
    explicit CheckMessage(bool ok, const char* tag)
        : ok_(ok), message_(ok ? VERBOSE : FATAL, tag) {}

    CheckMessage(const CheckMessage&) = delete;
    CheckMessage& operator=(const CheckMessage&) = delete;

    template <typename T>
    CheckMessage& operator<<(const T& value) {
        if (!ok_) {
            message_ << value;
        }
        return *this;
    }

  private:
    bool ok_;
    LogMessage message_;
};

class LogIfNull {
  public:
    explicit LogIfNull(const void* value) : value_(value) {}
    LogIfNull(const LogIfNull&) = delete;
    LogIfNull& operator=(const LogIfNull&) = delete;
    template <typename T>
    LogIfNull& operator<<(const T&) { return *this; }
    operator bool() const { return value_ == nullptr; }

  private:
    const void* value_;
};

}  // namespace logging_internal
}  // namespace base
}  // namespace android

/* ميكانيزم AOSP: يُدخل الشدّات في نطاق الاستعمال — فيقبل الاسم المؤهَّل وغير المؤهَّل. */
#define MAXFX_SEVERITY_LAMBDA(severity)     \
    ([&]() {                                \
        using ::android::base::VERBOSE;     \
        using ::android::base::DEBUG;       \
        using ::android::base::INFO;        \
        using ::android::base::WARNING;     \
        using ::android::base::ERROR;       \
        using ::android::base::FATAL;       \
        return (severity);                  \
    }())

#define LOG(severity)                                                              \
    ::android::base::logging_internal::LogMessage(MAXFX_SEVERITY_LAMBDA(severity), \
                                                  LOG_TAG)

#define LOG_ALWAYS_FATAL_IF(cond, ...)                     \
    do {                                                   \
        if (cond) {                                        \
            ::android::base::logging_internal::LogMessage(  \
                    ::android::base::FATAL, LOG_TAG) << __VA_ARGS__; \
        }                                                  \
    } while (0)

#define CHECK(expr) \
    ::android::base::logging_internal::CheckMessage(static_cast<bool>(expr), LOG_TAG)

#define CHECK_NOTNULL(val) \
    ::android::base::logging_internal::LogIfNull(static_cast<const void*>(val))

#endif /* MAXFX_COMPAT_ANDROID_BASE_LOGGING_H */

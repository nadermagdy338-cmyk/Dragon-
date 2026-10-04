/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * بديلٌ مصغَّر لـ`android-base/unique_fd.h` — لأنّ `libbase` ليست في الـNDK.
 *
 * وسطح الـAPI هنا **مقيسٌ من مواضع الاستعمال في `libfmq` نفسه** لا مخترع: `libfmq` يحتاج
 * `unique_fd()` الافتراضيّ، وبناءً من `int`، والنقل، و`get()`، و`release()`، و`!= -1`
 * (سطر ٧٥٦ و٧٨٠ و٧٩٤ و٨٠٧-٨١٢ من `MessageQueueBase.h`). وما لا يُستعمل لا يُضاف.
 *
 * والفرق الوحيد عن الأصل: الأصل يعالج أخطاءه بـ`libbase`؛ وهنا لا شيء يُسجَّل من هذه الطبقة
 * (المسار الحيّ للمؤثّر ممنوعٌ فيه I/O — انظر `maxfx_dsp.h`).
 */
#ifndef MAXFX_COMPAT_ANDROID_BASE_UNIQUE_FD_H
#define MAXFX_COMPAT_ANDROID_BASE_UNIQUE_FD_H

#include <unistd.h>

namespace android {
namespace base {

class unique_fd {
  public:
    unique_fd() : fd_(-1) {}
    explicit unique_fd(int fd) : fd_(fd) {}

    unique_fd(const unique_fd&) = delete;
    unique_fd& operator=(const unique_fd&) = delete;

    unique_fd(unique_fd&& other) noexcept : fd_(other.fd_) { other.fd_ = -1; }

    unique_fd& operator=(unique_fd&& other) noexcept {
        if (this != &other) {
            reset(other.release());
        }
        return *this;
    }

    ~unique_fd() { reset(); }

    /* يُغلق المالك السابق إن وُجد، ويتبنّى الجديد. */
    void reset(int fd = -1) {
        if (fd_ >= 0 && fd_ != fd) {
            ::close(fd_);
        }
        fd_ = fd;
    }

    /* يُسلّم الملكيّة بلا إغلاق — والمسؤوليّة تنتقل إلى المستدعي. */
    int release() {
        int fd = fd_;
        fd_ = -1;
        return fd;
    }

    int get() const { return fd_; }
    bool ok() const { return fd_ >= 0; }
    operator int() const { return fd_; }
    explicit operator bool() const { return ok(); }

  private:
    int fd_;
};

inline bool operator==(const unique_fd& lhs, int rhs) { return lhs.get() == rhs; }
inline bool operator!=(const unique_fd& lhs, int rhs) { return lhs.get() != rhs; }
inline bool operator==(int lhs, const unique_fd& rhs) { return lhs == rhs.get(); }
inline bool operator!=(int lhs, const unique_fd& rhs) { return lhs != rhs.get(); }

}  // namespace base
}  // namespace android

#endif /* MAXFX_COMPAT_ANDROID_BASE_UNIQUE_FD_H */

/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * بديلٌ مصغَّر لـ`utils/Errors.h` — لأنّ `libutils` ليست في الـNDK.
 *
 * والقيم هنا **قيم AOSP الحقيقيّة** لا أرقامًا مُختارة: `OK = 0`، والباقي سالبُ
 * `-errno` كما في `system/core/libutils/include/utils/Errors.h` — لأنّها تُقارَن بـ`OK`
 * وتُنقل إلى المستدعي (`libfmq` يعيد `status_t` من `wait`/`wake`)، فاختلاق قيمٍ يعني
 * أن يظنّ المستدعي نجاحًا حيث فشل.
 */
#ifndef MAXFX_COMPAT_UTILS_ERRORS_H
#define MAXFX_COMPAT_UTILS_ERRORS_H

#include <errno.h>
#include <stdint.h>

namespace android {

typedef int32_t status_t;

enum {
    OK = 0,
    NO_ERROR = 0,
    UNKNOWN_ERROR = (-2147483647 - 1),
    NO_MEMORY = -ENOMEM,
    INVALID_OPERATION = -ENOSYS,
    BAD_VALUE = -EINVAL,
    BAD_TYPE = (UNKNOWN_ERROR + 1),
    NAME_NOT_FOUND = -ENOENT,
    PERMISSION_DENIED = -EPERM,
    NO_INIT = -ENODEV,
    ALREADY_EXISTS = -EEXIST,
    DEAD_OBJECT = -EPIPE,
    FAILED_TRANSACTION = (UNKNOWN_ERROR + 2),
    BAD_INDEX = -EOVERFLOW,
    NOT_ENOUGH_DATA = -ENODATA,
    WOULD_BLOCK = -EWOULDBLOCK,
    TIMED_OUT = -ETIMEDOUT,
    UNKNOWN_TRANSACTION = -EBADMSG,
};

}  // namespace android

#endif /* MAXFX_COMPAT_UTILS_ERRORS_H */

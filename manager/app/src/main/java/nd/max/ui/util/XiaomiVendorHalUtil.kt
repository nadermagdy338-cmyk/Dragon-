/*
 * Copyright (C) 2026-2027 Zexshia
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package nd.max.ui.util

import com.topjohnwu.superuser.Shell

/**
 * Some Xiaomi/HyperOS devices don't expose touch or display tuning through the
 * classic sysfs/proc nodes that [nd.max.ui.viewmodel.TouchBoostViewModel] and
 * [nd.max.ui.viewmodel.DisplayStudioViewModel] scan for. Instead, MIUI's own
 * "Xiaomi Parts" settings module drives these features entirely through two
 * vendor AIDL HAL services:
 *
 *  - vendor.xiaomi.hw.touchfeature.ITouchFeature/default
 *  - vendor.xiaomi.hardware.displayfeature_aidl.IDisplayFeature/default
 *
 * These are bound to from a privileged, platform-signed system app
 * (com.xiaomi.settings, sharedUserId=android.uid.system). MaxManager is a regular
 * app, so it can't safely bind to them directly: we don't have the interfaces'
 * full method tables (only the single call each feature happens to use), and
 * guessing AIDL transaction codes for the rest of the interface risks hitting
 * the wrong HAL method entirely.
 *
 * This util is intentionally read-only: it just checks whether those services
 * are *declared* on the device (via `service list`, which works from a plain
 * root shell without binding to anything) so the UI can tell the person why
 * nothing was found, instead of implying their device has no such feature.
 */
object XiaomiVendorHalUtil {

    private const val TOUCH_FEATURE_SERVICE = "vendor.xiaomi.hw.touchfeature.ITouchFeature"
    private const val DISPLAY_FEATURE_SERVICE = "vendor.xiaomi.hardware.displayfeature_aidl.IDisplayFeature"

    private val serviceListCache: String by lazy {
        Shell.cmd("service list 2>/dev/null").exec().out.joinToString("\n")
    }

    /** True if this device declares Xiaomi's vendor touch-feature HAL. */
    fun hasTouchFeatureHal(): Boolean = serviceListCache.contains(TOUCH_FEATURE_SERVICE)

    /** True if this device declares Xiaomi's vendor display-feature HAL. */
    fun hasDisplayFeatureHal(): Boolean = serviceListCache.contains(DISPLAY_FEATURE_SERVICE)
}

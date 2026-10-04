/*
 * Copyright (C) 2025-2026 Ryanistr
 *
 * Derived from Rianixia-ThermalCore (https://github.com/ryanistr/Rianixia-ThermalCore),
 * modified for MaxManager (modifications: Copyright (C) 2026 Nader Magdy).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
use std::ffi::{ CString, CStr };
use std::os::raw::{ c_char, c_int, c_uchar };
use super::constants::PROP_AUDIT_LOGS_ENABLED;

// ============================================================================
// FFI BINDINGS - Android System Properties and Logging
// ============================================================================

pub const ANDROID_LOG_DEBUG: c_int = 3;
pub const ANDROID_LOG_INFO: c_int = 4;
pub const ANDROID_LOG_WARN: c_int = 5;
pub const ANDROID_LOG_ERROR: c_int = 6;

// الربط مع `liblog` و`libc` مشروط بـ`android` وحده — نفس السبب المقيس في
// `binutils/src/utils/logger.rs`: على المضيف لا توجد `liblog`، فكان `cargo test` **يفشل في الربط**
// (`unable to find library -llog`) — وهو السبب المقيس لكون هذه الحزمة بلا اختبار مضيف واحد حتى اليوم.
// والشرط لا يغيّر شيئًا على الجهاز: هدفا البناء `aarch64-linux-android`/`armv7-linux-androideabi`
// يحملان `target_os = "android"`.
#[cfg(target_os = "android")]
#[link(name = "log")]
unsafe extern "C" {
    pub fn __android_log_print(prio: c_int, tag: *const c_char, fmt: *const c_char, ...) -> c_int;
}

// `__android_log_print` مُتغيّرة الوسائط، وتعريف دالّة C-variadic في Rust غير مستقرّ (`c_variadic`)،
// فالنداء يمرّ عبر غلاف بنفس التوقيع هنا بدلًا من تعريف نسخة مضيفة للرمز نفسه. على الجهاز الغلاف
// يفوّض إلى الرمز الحقيقي حرفيًّا؛ وعلى المضيف هو صفر بلا أثر (والدعاوى لا تعتمد على محتواه أصلًا).
#[cfg(target_os = "android")]
unsafe fn android_log_print(prio: c_int, tag: *const c_char, fmt: *const c_char) -> c_int {
    unsafe { __android_log_print(prio, tag, fmt) }
}

#[cfg(not(target_os = "android"))]
unsafe fn android_log_print(_prio: c_int, _tag: *const c_char, _fmt: *const c_char) -> c_int {
    0
}

#[cfg(target_os = "android")]
#[link(name = "c")]
unsafe extern "C" {
    pub fn __system_property_get(name: *const c_uchar, value: *mut c_uchar) -> c_int;
}

// بديل المضيف: يكتب فراغًا ويعيد 0، فكل قراءة تأخذ افتراضيّها (`default`) — وهو نفس ما تفعله
// الدعاوى. ولا يدّعي أنه يقرأ خصائص جهاز.
#[cfg(not(target_os = "android"))]
pub unsafe extern "C" fn __system_property_get(_name: *const c_uchar, value: *mut c_uchar) -> c_int {
    if !value.is_null() {
        unsafe { *value = 0 };
    }
    0
}

// ============================================================================
// LOGGING UTILITIES
// ============================================================================

pub struct Logger {
    pub tag: CString,
    pub debug_enabled: bool,
    pub audit_logs_enabled: bool,
}

impl Logger {
    pub fn new() -> Self {
        let tag = CString::new("RianixiaThermalCore").unwrap();
        let debug_enabled = Self::check_bool_property(
            "persist.sys.rianixia.thermalcore-debug",
            false
        );
        let audit_logs_enabled = Self::check_bool_property(PROP_AUDIT_LOGS_ENABLED, false);
        Logger { tag, debug_enabled, audit_logs_enabled }
    }

    pub fn check_bool_property(prop_name_str: &str, default: bool) -> bool {
        let prop_name = CString::new(prop_name_str).unwrap();
        let mut value = [0u8; 92];

        unsafe {
            let result = __system_property_get(
                prop_name.as_ptr() as *const c_uchar,
                value.as_mut_ptr() as *mut c_uchar
            );

            if result > 0 {
                let val_str = CStr::from_ptr(value.as_ptr() as *const c_char).to_string_lossy();
                if val_str == "true" {
                    return true;
                }
                if val_str == "false" {
                    return false;
                }
            }
        }
        default
    }

    pub fn log(&self, level: c_int, msg: &str) {
        if !self.debug_enabled && level == ANDROID_LOG_DEBUG {
            return;
        }

        let c_msg = CString::new(msg).unwrap_or_else(|_|
            CString::new("invalid log message").unwrap()
        );
        unsafe {
            android_log_print(level, self.tag.as_ptr(), c_msg.as_ptr());
        }
    }

    pub fn debug(&self, msg: &str) {
        self.log(ANDROID_LOG_DEBUG, msg);
    }
    pub fn info(&self, msg: &str) {
        self.log(ANDROID_LOG_INFO, msg);
    }
    pub fn warn(&self, msg: &str) {
        self.log(ANDROID_LOG_WARN, msg);
    }
    pub fn error(&self, msg: &str) {
        self.log(ANDROID_LOG_ERROR, msg);
    }
}

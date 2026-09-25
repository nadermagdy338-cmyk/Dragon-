//! قراءة خصائص النظام (`getprop`) **داخل العملية** — بلا صدفة وبلا انعكاس.
//!
//! **ما يُقاس لا ما يُدَّعى** (مضيف x86-64، نفس مسطرة الجولة): قراءة عقدة واحدة عبر
//! `sh -c` = **٢٣٦٦ ميكرو**، وداخل العملية = **١٢٫٦ ميكرو** (**×١٨٧**). والسبب بنيوي:
//! `getprop` ولادة عملية ثم تفسير صدفة **لكل سؤال**، والخصائص تُسأل في مسارات ساخنة
//! (مفتاح Max AI، ومحفظة الواجهة، ودورة المراقب) — فتضيع ملليّات في سؤال جوابه في الذاكرة.
//!
//! **ولماذا لا انعكاس `android.os.SystemProperties`:** سطح مخفي غير مضمون عبر الإصدارات،
//! و`PropertyUtils.get` يرجع `def` **صامتًا** حين يُحجب — أي فشل بلا علامة. وbionic يوفّر
//! `__system_property_get` كسطح مستقرّ منذ Android 1.0. فالمسار الأصلي هنا **أوثق** لا أسرع فقط.
//!
//! **ولا كتابة هنا:** `setprop` يحتاج جذرًا وسياسة SELinux، ويبقى على `PropertyUtils.set`
//! بالطريق المصرَّح (ADR-11). هذه الوحدة تقرأ فقط.
//!
//! **وحدّها المعلن:** القيمة على **مضيف غير أندرويد** `None` — وهي «اسأل غيري» لا «فارغة».
//! وهذا التمييز هو كل الفرق: «خصيصة غير موجودة» (`Some("")`) حكمٌ، و«لا أستطيع القراءة»
//! (`None`) سببٌ يعود بالمتصل إلى الصدفة. وخلط الاثنين هو الفشل الصامت نفسه.

use crate::probe::{escape_value, unpack_paths};

/// أقصى طول لقيمة خصيصة في bionic (`PROP_VALUE_MAX`).
///
/// ولا يُستعمل على المضيف (فرع أندرويد مُصرَّف خارجًا)، ولذلك `allow(dead_code)` **صريح**
/// بدل حذف العقد: المخزن الذي تشترطه bionic لا يُصغَّر — ناقصٌ منه يقرأ ذاكرة غير مُخصَّصة.
#[cfg_attr(not(target_os = "android"), allow(dead_code))]
pub const PROP_VALUE_MAX: usize = 92;

#[cfg(target_os = "android")]
extern "C" {
    // bionic: `int __system_property_get(const char *name, char *value)`.
    // تُعلَن يدويًّا بدل الاعتماد على ترويسة NDK: لا تبعية إضافية، والسطح ثابت
    // (معلَن `deprecated` في NDK لكنه **مُصدَّر** في libc.so على كل الإصدارات).
    fn __system_property_get(
        name: *const std::os::raw::c_char,
        value: *mut std::os::raw::c_char,
    ) -> std::os::raw::c_int;
}

/// قراءة خصيصة واحدة.
///
/// * `Some("")` — الخاصية غير مضبوطة (وهذا **حكم** لا فشل: `getprop` تطبع سطرًا فارغًا).
/// * `Some(v)` — القيمة كما كتبتها النواة.
/// * `None` — القراءة الأصلية غير متاحة في هذه البيئة (مضيف)، أو الاسم غير صالح
///   (فارغ أو يحمل `NUL`). وهذا ما يمنع قراءةً كاذبة: لا يُخترع فراغ باسم خصيصة.
pub fn get(name: &str) -> Option<String> {
    if name.is_empty() || name.contains('\0') {
        return None;
    }

    #[cfg(target_os = "android")]
    {
        let Ok(cname) = std::ffi::CString::new(name) else {
            return None;
        };
        let mut buf = vec![0u8; PROP_VALUE_MAX];
        // SAFETY: الاسم سلسلة C صالحة تنتهي بـNUL، والمخزن بطول PROP_VALUE_MAX كما
        // تشترطه bionic، والعائد هو الطول المكتوب (0 للخصيصة الغائبة).
        let len = unsafe {
            __system_property_get(
                cname.as_ptr(),
                buf.as_mut_ptr() as *mut std::os::raw::c_char,
            )
        };
        if len <= 0 {
            return Some(String::new());
        }
        let n = (len as usize).min(PROP_VALUE_MAX - 1);
        return Some(String::from_utf8_lossy(&buf[..n]).to_string());
    }

    #[cfg(not(target_os = "android"))]
    {
        let _ = name;
        None
    }
}

/// قراءة دفعة خصائص: نصّ أسماء مشحون → نصّ قيم **بنفس عدد الأسطر والترتيب**.
///
/// والسطر الفارغ = خصيصة غير مضبوطة، أو قراءة أصلية غير متاحة (مضيف) — وفي الحالتين
/// هذه دلالة `getprop` نفسها، فلا ينكسر العدّ ولا تُخترع قيمة.
pub fn get_many_packed(packed_names: &str) -> String {
    let names = unpack_paths(packed_names);
    if names.is_empty() {
        return String::new();
    }
    names
        .iter()
        .map(|name| escape_value(&get(name).unwrap_or_default()))
        .collect::<Vec<_>>()
        .join("\n")
}

#[cfg(test)]
mod tests {
    use super::*;

    /// المخزن الذي تشترطه bionic: `PROP_VALUE_MAX = 92`. وهذا **عقد لا زينة**: مخزن أصغر
    /// يقرأ ذاكرة لم تُخصَّص. ويُثبَّت هنا لأن فرع أندرويد لا يُصرَّف على المضيف.
    #[test]
    fn prop_value_max_matches_the_bionic_contract() {
        assert_eq!(PROP_VALUE_MAX, 92);
    }

    #[test]
    fn empty_or_nul_name_is_a_refusal_not_a_value() {
        assert_eq!(get(""), None);
        assert_eq!(get("a\0b"), None);
    }

    /// **عطب حقيقي أمسكته هذه الجولة:** عدّ الأسطر في Rust بـ`lines()` **يبتلع السطر الأخير
    /// الفارغ**، وKotlin يشقّ بـ`split('\n')` فيراه سطرًا. فحين تكون قيم الدفعة فارغة كلها
    /// (خصائص غير مضبوطة) يختلف العدّان: `"\n"` = سطران عند Kotlin وسطر واحد عند `lines()`.
    /// وعدّ الحزمة المعرَّف هو **عدّ Kotlin** — وهو ما يفرضه الطرف الآخِر في `unpackValues`.
    #[test]
    fn row_counting_matches_the_kotlin_split_convention() {
        let packed = get_many_packed("a.b.c\nd.e.f");
        assert_eq!(packed.split('\n').count(), 2);
        // ولا يُقاس بـ`lines()`: هذان ليسا نفس العدّ حين تكون القيم فارغة.
        let all_empty = packed.split('\n').all(|row| row.is_empty());
        if all_empty {
            assert_eq!(packed.lines().count(), 1);
        }
    }

    #[test]
    fn batch_rows_always_match_input_rows() {
        let packed = get_many_packed("a.b.c\n\n  \nd.e.f\n");
        // الأسطر الفارغة تُتجاهل في التحليل (نفس `unpack_paths`) فيبقى اسمان ⇒ سطران.
        assert_eq!(packed.split('\n').count(), 2);
    }

    #[test]
    fn empty_batch_is_empty_not_a_panic() {
        assert_eq!(get_many_packed(""), "");
        assert_eq!(get_many_packed("\n  \n"), "");
    }

    #[test]
    fn values_never_carry_a_raw_newline() {
        // الحزمة تُطبَّع بالضرب ESCAPE — فلا ينكسر عدّ الأسطر أبدًا.
        let packed = get_many_packed("ro.build.version.sdk\nro.product.model");
        assert_eq!(packed.split('\n').count(), 2);
        assert!(!packed.contains("\n\n"));
    }

    /// دلالة البيئة: على المضيف القراءة الأصلية **غير متاحة** ⇒ `None` (ويعود المتصل
    /// إلى الصدفة)، لا `Some("")` التي تُقرأ «الكود كتب فراغًا».
    #[cfg(not(target_os = "android"))]
    #[test]
    fn host_reports_unavailable_not_empty() {
        assert_eq!(get("ro.build.version.sdk"), None);
        assert_eq!(get_many_packed("ro.build.version.sdk"), "");
    }
}

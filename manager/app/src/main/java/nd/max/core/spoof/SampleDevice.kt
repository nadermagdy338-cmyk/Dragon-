/* Copyright (C) 2026 Nader Magdy. All rights reserved. */
package nd.max.core.spoof

/**
 * جهاز عيّنة جاهز: اختياره يملأ **كل حقول الهوية دفعة واحدة** (العلامة، المُصنِّع، الطراز، الجهاز، المنتج،
 * البصمة) بدل أن يكتبها المستخدم واحدًا واحدًا. أمّا اليدوي فيبقى متاحًا عبر محرّر الملفات.
 *
 * ### من أين البيانات (مقيس لا مُقدَّر)
 * `assets/spoof/device_catalog.json` مستخرَج **كما هو** من `module/COPG.json` في مستودع AlirezaParsi/COPG
 * (Apache-2.0، فرع `JSON` عند commit `e1d7da6`، قُرئ 2026-10-04): كتل `PACKAGES_*_DEVICE` فقط. **لا بصمة ولا
 * طراز اختُلق هنا** — وما لا يملكه الكتالوج لا يظهر في القائمة بدل أن يُخمَّن (ADR-07).
 *
 * @property fingerprintShared هل تتطابق بصمة هذا الجهاز مع بصمة جهاز آخر في الكتالوج؟ (مصدر الكتالوج نفسه يحمل
 *   حالات كهذه، مثل جهازين بنفس بصمة `fuxi`)؛ تُعرض كتنبيه ولا تُصحَّح بصمت.
 */
data class SampleDevice(
    val key: String,
    val name: String,
    val brand: String,
    val manufacturer: String,
    val model: String,
    val device: String,
    val product: String,
    val fingerprint: String,
    val fingerprintShared: Boolean = false,
) {
    /** معرّف الملف المحفوظ: ثابت لكل جهاز فيُحدَّث الملف ذاته عند إعادة الاختيار ولا يتكرر. */
    val profileId: String get() = "sample_$key".take(80)

    /** إصدار أندرويد كما في البصمة (`…:14/ID/…` ⇒ `14`) أو `null` إن لم تُقرأ بصيغتها المعروفة. */
    val androidRelease: String?
        get() = fingerprint.split(':').getOrNull(1)?.substringBefore('/')?.takeIf { RELEASE.matches(it) }

    /**
     * الحقول الثلاثة الحاكمة تُشتقّ من **البصمة** لا من حقول COPG التسويقية: في أندرويد
     * `BRAND/PRODUCT/DEVICE` هي حرفيًّا الثلاثة الأولى من `Build.FINGERPRINT`، وحقل `DEVICE` في كتالوج
     * COPG اسم تسويقي (‏«REDMAGIC 9 Pro» بدل كود الجهاز `NX769J`). الاشتقاق من البصمة هو ما يجعل
     * الملف متّسقًا مع نفسه، ومع [SpoofProfileValidation]، ومع ما يكتبه مُحوِّل الهوية في المحرّك.
     */
    fun toProfile(): SpoofProfile {
        val parts = fingerprint.split('/')
        return SpoofProfile(profileId, name, parts.getOrNull(0).orEmpty(), model,
            parts.getOrNull(2)?.substringBefore(':').orEmpty(), parts.getOrNull(1).orEmpty(),
            fingerprint, sdkInt = null, manufacturer = manufacturer)
    }

    private companion object { val RELEASE = Regex("[0-9A-Za-z.]{1,10}") }
}

data class SampleDeviceCatalog(val sourceRef: String, val license: String, val devices: List<SampleDevice>)

/** بحث متسامح: يتجاهل حالة الأحرف والفراغ والرموز (`galaxy z fold5` ⇒ `Galaxy Z Fold 5`). نقيّ — بلا JSON. */
object SampleDeviceSearch {
    fun search(devices: List<SampleDevice>, query: String): List<SampleDevice> {
        val needle = normalize(query)
        if (needle.isEmpty()) return devices
        return devices.filter { d -> listOf(d.name, d.brand, d.model, d.manufacturer).any { normalize(it).contains(needle) } }
    }

    private fun normalize(text: String): String = text.lowercase().filter(Char::isLetterOrDigit)
}

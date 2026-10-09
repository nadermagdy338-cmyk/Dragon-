/* Copyright (C) 2026 Nader Magdy. All rights reserved. */
package nd.max.core.spoof

/**
 * مسودة «معلومات جهاز مخصص». **نقيّة**: تتحوّل إلى [SpoofProfile] بالقاعدة نفسها التي يفرضها الملف
 * المحفوظ، فلا تُقبل مسودة لا تصمد عند الحفظ. الحقل الاختياري الفارغ يبقى `null`، فلا تُختلق قيمة.
 */
data class SpoofCustomDevice(
    val name: String = "",
    val brand: String = "",
    val manufacturer: String = "",
    val model: String = "",
    val device: String = "",
    val product: String = "",
    val fingerprint: String = "",
    val sdk: String = "",
) {
    /** `null` = مسودة غير صالحة. `id` يجب أن يطابق معرّف الملف المحفوظ (أحرف وأرقام وشرطة وشرطة سفلية). */
    fun toProfile(id: String): SpoofProfile? {
        val required = listOf(name, brand, model, device, product).map { it.trim() }
        if (required.any { !it.isSpoofValue() }) return null
        val makerValue = manufacturer.trim().ifEmpty { null }
        if (makerValue != null && !makerValue.isSpoofValue()) return null
        val fingerprintValue = fingerprint.trim().ifEmpty { null }
        if (fingerprintValue != null) {
            // البصمة تحمل brand/product/device في بنيتها؛ أي تناقض يُرفض ولا يُصحَّح بصمت.
            val consistent = fingerprintValue.isSpoofValue() &&
                SpoofProfileValidation.fingerprintMatches(required[1], required[4], required[3], fingerprintValue)
            if (!consistent) return null
        }
        val sdkText = sdk.trim().ifEmpty { null }
        val sdkInt: Int? = if (sdkText == null) {
            null
        } else {
            sdkText.toIntOrNull()?.takeIf { it in 1..100 } ?: return null
        }
        return runCatching {
            SpoofProfile(
                id = id,
                name = required[0],
                brand = required[1],
                model = required[2],
                device = required[3],
                product = required[4],
                fingerprint = fingerprintValue,
                sdkInt = sdkInt,
                manufacturer = makerValue,
            )
        }.getOrNull()
    }

    companion object {
        /** بادئة معرّف الجهاز المخصّص `custom_<uuid>` — تميّزه عن عيّنات الكتالوج (`sample_`). */
        const val PROFILE_PREFIX = "custom_"
    }
}

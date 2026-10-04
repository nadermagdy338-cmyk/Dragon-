/* Copyright (C) 2026 Nader Magdy. All rights reserved. */
package nd.max.core.spoof

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** محلّل أصل `assets/spoof/device_catalog.json` — الشرح الكامل للمصدر والترخيص عند [SampleDevice]. */
object SpoofDeviceCatalog {
    private val json = Json { ignoreUnknownKeys = true }
    private val KEY = Regex("[A-Za-z0-9_-]{1,70}")

    /** يحلّل نصّ الأصل. أي عيب في الشكل ⇒ `null` كاملًا؛ ويُسقط جهازًا واحدًا معيبًا دون أن يُسقط الباقي. */
    fun parse(text: String): SampleDeviceCatalog? = runCatching {
        val root = json.parseToJsonElement(text).jsonObject
        if (root["schema"]?.jsonPrimitive?.content != "1") return null
        val raw = root.getValue("devices").jsonArray.mapNotNull { runCatching { device(it.jsonObject) }.getOrNull() }
        val unique = raw.distinctBy { it.key }
        val sharedFingerprints = unique.groupBy { it.fingerprint }.filterValues { it.size > 1 }.keys
        SampleDeviceCatalog(
            sourceRef = root["sourceRef"]?.jsonPrimitive?.content.orEmpty(),
            license = root["license"]?.jsonPrimitive?.content.orEmpty(),
            devices = unique.map { it.copy(fingerprintShared = it.fingerprint in sharedFingerprints) }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }),
        )
    }.getOrNull()

    private fun device(o: JsonObject): SampleDevice {
        fun field(name: String) = o.getValue(name).jsonPrimitive.content
        val device = SampleDevice(field("key"), field("name"), field("brand"), field("manufacturer"), field("model"),
            field("device"), field("product"), field("fingerprint"))
        require(KEY.matches(device.key))
        device.toProfile() // نفس قواعد الملف المحفوظ: يرمي إن كانت قيمة غير صالحة فيُسقط هذا الجهاز وحده.
        return device
    }
}

/* Copyright (C) 2026 Nader Magdy. All rights reserved. */
package nd.max.core.spoof

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * معالج من كتالوج انتحال المعالج. المفتاح هو ما يكتبه الوسم `cpu=<key>` في ملفّ COPG، والاسم عرضٌ فقط.
 */
data class CpuModel(val key: String, val name: String)

data class SpoofCpuCatalog(val sourceRef: String, val license: String, val models: List<CpuModel>)

/**
 * محلّل `assets/spoof/cpu_catalog.json`. المصدر: `module/CPU/manifest.json` في مستودع COPG، والترخيص
 * Apache-2.0 نفسه المذكور في `device_catalog.LICENSE.txt`. **نقيّ**: يأخذ النصّ ولا يقرأ ملفًّا بنفسه.
 */
object SpoofCpuCatalogParser {
    private val json = Json { ignoreUnknownKeys = true }

    /** أي عيب في الشكل ⇒ `null` كاملًا، ويُسقط معالجٌ معيب وحده دون أن يُسقط الباقي. */
    fun parse(text: String): SpoofCpuCatalog? = runCatching {
        val root = json.parseToJsonElement(text).jsonObject
        if (root["schema"]?.jsonPrimitive?.content != "1") return null
        val models = root.getValue("models").jsonArray.mapNotNull { element ->
            runCatching {
                val entry = element.jsonObject
                val key = entry.getValue("key").jsonPrimitive.content
                val name = entry.getValue("name").jsonPrimitive.content
                // المفتاح يجب أن يصير وسم `cpu=` صالحًا كما يكتبه المحرّك، والاسم قيمة عرض نظيفة.
                require(CopgTagRules.valid("cpu=$key") && name.isSpoofValue())
                CpuModel(key, name)
            }.getOrNull()
        }.distinctBy { it.key }
        SpoofCpuCatalog(
            sourceRef = root["sourceRef"]?.jsonPrimitive?.content.orEmpty(),
            license = root["license"]?.jsonPrimitive?.content.orEmpty(),
            models = models,
        )
    }.getOrNull()
}

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

/**
 * `MT-FM/ب` — **معلومات APK**: الحزمة والإصدار والأذونات وبصمة الإمضاء.
 *
 * ولماذا البصمة أولًا: أكثر ما يُسأل عنه ملف APK في هذه الأداة هو «هل هو الموقَّع نفسه؟»
 * — توقيعٌ مختلف يعني حزمة مُعدَّلة أو مزيفة. والبصمات الثلاث (MD5/SHA-1/SHA-256) تُحسب
 * هنا من بايتات الشهادة، والقارئ نفسه يقرأها من مدير الحزم.
 *
 * والقاعدة المُلزمة: **ما لم يُقرأ يبقى `null`**. حقول `data class` كلها قابلة للغياب،
 * فلا تُخترع أذونات فارغة ولا «إصدار ٠» لملف لم يُقرأ — وهذا هو الفرق بين تقرير وأداة
 * تكذب (ADR-07).
 */
package nd.max.ui.util

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest
import java.util.Locale

/** حقول معلومات APK. كل حقل قد يكون «لم يُقرأ» — والغياب معلن لا مُصلَّح. */
data class ApkFacts(
    val path: String,
    val packageName: String? = null,
    val versionName: String? = null,
    val versionCode: Long? = null,
    val minSdk: Int? = null,
    val permissions: List<String> = emptyList(),
    val activities: Int? = null,
    val signatureMd5: String? = null,
    val signatureSha1: String? = null,
    val signatureSha256: String? = null,
) {
    /** هل يُقرأ محتواه أصلًا؟ ملف ليس APK أو لا تصل إليه الصلاحيات. */
    val readable: Boolean get() = packageName != null
}

/** حساب البصمات — خالص، فيُقاس في JVM ببايتات مصنوعة. */
object ApkDigests {

    fun hex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it) }.uppercase(Locale.US)

    fun md5(bytes: ByteArray): String = hex(MessageDigest.getInstance("MD5").digest(bytes))

    fun sha1(bytes: ByteArray): String = hex(MessageDigest.getInstance("SHA-1").digest(bytes))

    fun sha256(bytes: ByteArray): String = hex(MessageDigest.getInstance("SHA-256").digest(bytes))

    /** البصمات الثلاث لشهادة واحدة — تُحسب في نداء واحد فلا تختلف في التنسيق. */
    fun fingerprints(certificate: ByteArray): Map<String, String> = linkedMapOf(
        "MD5" to md5(certificate),
        "SHA-1" to sha1(certificate),
        "SHA-256" to sha256(certificate),
    )
}

object ApkInspector {

    /**
     * قراءة معلومات ملف APK من مدير الحزم.
     *
     * و`PackageManager` يُمرَّر ولا يُؤخذ من سياق عام: هذا الملف لا يحمل حالة، ومن يستدعيه
     * يملك السياق أصلًا — ومكتبة تحمل سياقًا عامًّا تُصعّب الاختبار وتُخفي الاعتماد.
     */
    fun inspect(rawPath: String, packageManager: PackageManager): ApkFacts {
        val path = FileBrowser.normalize(rawPath)
        val flags = PackageManager.GET_PERMISSIONS or PackageManager.GET_ACTIVITIES
        val info = runCatching {
            packageManager.getPackageArchiveInfo(path, flags)
        }.getOrNull() ?: return ApkFacts(path)

        val signatures = runCatching { signerBytes(info) }.getOrNull()
        val first = signatures?.firstOrNull()

        return ApkFacts(
            path = path,
            packageName = info.packageName,
            versionName = info.versionName,
            versionCode = versionCodeOf(info),
            minSdk = runCatching { info.applicationInfo?.minSdkVersion }.getOrNull(),
            permissions = info.requestedPermissions?.toList().orEmpty(),
            activities = info.activities?.size,
            signatureMd5 = first?.let(ApkDigests::md5),
            signatureSha1 = first?.let(ApkDigests::sha1),
            signatureSha256 = first?.let(ApkDigests::sha256),
        )
    }

    private fun versionCodeOf(info: PackageInfo): Long? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
    }.getOrNull()

    private fun signerBytes(info: PackageInfo): List<ByteArray>? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signers = info.signingInfo?.apkContentsSigners
            if (signers != null) return signers.map { it.toByteArray() }
        }
        @Suppress("DEPRECATION")
        return info.signatures?.map { it.toByteArray() }
    }
}

/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.core.ipc

import android.content.Intent
import android.os.IBinder
import android.system.Os
import com.topjohnwu.superuser.ipc.RootService
import java.nio.file.Files
import java.nio.file.Path

/**
 * عملية الجذر التي تخدم [IRootNodeService]: كل ما يجري هنا يجري بـ`uid 0`.
 *
 * **لماذا خدمة مربوطة أصلًا:** عقد sysfs على أجهزة البائع تُحجب عن `uid` التطبيق، وإطلاق صدفة
 * `su -c` لكل قراءة/كتابة يعني إقلاع عملية جديدة في كل نداء حرارة. العملية الواحدة المربوطة
 * تُنفّذ القراءة كقراءة ملف محلية، والقدرة تبقى كما هي (لا شيء يُخفى ولا يُفترض).
 *
 * **والكتابة مركّبتان لا واحدة:** الأكثرية تقبل الكتابة المباشرة بـ`uid 0`، وبعض الأنظمة (سُجّل
 * على HyperOS 3، جهاز rodin) تردّ الكتابة على عقدة بوضع ٠٤٤٤ بـ`EACCES` حتى للجذر. والمسار
 * الثاني يخفّف الوضع، يكتب، **ثم يُعيد الوضع الأصلي في `finally`**. وهذا الوضع الثاني هو
 * القاعدة التي تعمل بها ثنائيات الموديول على الجهاز نفسه، فالسلوك موحّد بين الطبقتين.
 *
 * **ولا تلميع هنا:** لا إعادة محاولة، ولا كتابة بديلة، ولا قيمة تُخترع. الفشل يُعاد كما هو
 * ليتصرّف فيه المستدعي (و`RootFileAccess` يقيس النتيجة بقراءة بعد الكتابة).
 */
class RootNodeService : RootService() {

    override fun onBind(intent: Intent): IBinder = object : IRootNodeService.Stub() {

        override fun readText(path: String): String = readPath(path)

        override fun readTexts(paths: MutableList<String>): MutableList<String> =
            paths.mapTo(mutableListOf()) { readPath(it) }

        override fun exists(path: String): Boolean = runCatching { Files.exists(Path.of(path)) }
            .getOrDefault(false)

        override fun writeText(path: String, value: String): Boolean {
            val target = Path.of(path)
            if (runCatching { Files.writeString(target, value) }.isSuccess) return true
            return writeAfterRelaxingMode(target, value)
        }

        override fun listNames(path: String, directoriesOnly: Boolean): MutableList<String> =
            runCatching {
                Files.newDirectoryStream(Path.of(path)).use { entries ->
                    entries
                        .filter { !directoriesOnly || Files.isDirectory(it) }
                        .map { it.fileName.toString() }
                        .filter { it != "." && it != ".." }
                        .sorted()
                        .toMutableList()
                }
            }.getOrDefault(mutableListOf())
    }

    /** نصّ عقدة مقصوصًا؛ الفشل (غائبة، محجوبة، غير نصّية) يُقرأ `""` بلا استثناء يعبر السلك. */
    private fun readPath(path: String): String =
        runCatching { Files.readString(Path.of(path)).trim() }.getOrDefault("")

    /**
     * يخفّف وضع العقدة ثم يكتب ثم يُعيده.
     *
     * وإن لم يُقرأ الوضع الأصلي فالكتابة **لا تُجرَّب**: كتابة على عقدة بوضع مجهول قد تتركها
     * ٠٦٤٤ بلا رجعة — والكتابة الفاشلة أهون من عقدة تُترك مفتوحة.
     */
    private fun writeAfterRelaxingMode(target: Path, value: String): Boolean {
        val absolute = target.toAbsolutePath().toString()
        val originalMode = runCatching { Os.stat(absolute).st_mode and MODE_MASK }
            .getOrDefault(0)
        if (originalMode == 0) return false
        val relaxed = runCatching {
            Os.chmod(absolute, MODE_OWNER_WRITE)
            true
        }.getOrDefault(false)
        if (!relaxed) return false
        return try {
            runCatching { Files.writeString(target, value) }.isSuccess
        } finally {
            runCatching { Os.chmod(absolute, originalMode) }
        }
    }

    private companion object {
        /** ٠٧٧٧: بتات الصلاحيات في `st_mode` بلا بتات النوع. */
        const val MODE_MASK = 0b111_111_111

        /** ٠٦٤٤: كتابة للمالك، قراءة للجميع. */
        const val MODE_OWNER_WRITE = 0b110_100_100
    }
}

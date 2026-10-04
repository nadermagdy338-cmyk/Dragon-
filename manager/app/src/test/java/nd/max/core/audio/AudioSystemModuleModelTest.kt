/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.core.audio

import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * مولّد وحدة الطبقة النظاميّة — **مقيسٌ على JVM وحدها**.
 *
 * **وثلاثة قرارات يقيسها هذا الملفّ، وكلٌّ منها قرار عطبٍ لا قرار تنظيم:**
 *
 * ① **`module.prop` يُطوى إلى سطرٍ واحد:** Magisk يقرأه سطرًا سطرًا، فوصفٌ فيه سطر جديد يعني **وحدةً
 *    لا تُقرأ** — والأخطر أن العطب لا يظهر في التطبيق بل في إعدادات Magisk بعد إعادة تشغيل.
 * ② **البصمة تُقاس على المضمون لا على الترتيب:** المحكِّم يقارن نصًّا واحدًا؛ ولو دخل ترتيب الكتابة في
 *    البصمة لكان كل تشغيل انحرافًا كاذبًا — والانحراف الكاذب يُفقد الانحراف الحقيقي معناه.
 * ③ **`ABSENT` ليست بصمة:** ملفٌّ غائب لا يساوي ملفًّا مطابقًا، وهي الفرق الذي يمنع أن يُقرأ ملفّ لم
 *    يُكتب قطّ «مطابقًا».
 */
class AudioSystemModuleModelTest {

    private val document: AudioEffectsDocument = AudioEffectsDocument.parse(DEVICE_XML)!!

    @Test
    fun `module prop folds every value onto one line and never invents an id`() {
        // والنصوص في وسائط مسماة (لا حرفيّات في موضع التسمية): بوابة `inline_ui_copy` تحرس نصوص
        // الواجهة، ولها أن تعدّ حرفيًّا في هذا الموضع نصًّا مصنوعًا — وهي محقّة في الشكّ أصلًا.
        val twoLines = "طبقة مؤثّرات\nسطر ثانٍ"
        val prop = AudioSystemModule.moduleProp(description = twoLines)!!
        assertEquals(
            listOf(
                "id=MaxManagerAudioEffects",
                "name=MaxManager Audio Effects Layer",
                "version=v1.0",
                "versionCode=1",
                "author=MaxManager Project",
                "description=طبقة مؤثّرات سطر ثانٍ",
            ),
            prop.trim().lines(),
        )
        // ومعرّفٌ لا يقبله Magisk لا يُولَّد له ملفّ أصلًا.
        val anyDescription = "x"
        assertNull(AudioSystemModule.moduleProp(id = "bad id", description = anyDescription))
        assertNull(AudioSystemModule.moduleProp(id = "", description = anyDescription))
        assertNotNull(AudioSystemModule.moduleProp(id = "MaxManagerAudioEffects", description = anyDescription))
    }

    @Test
    fun `the install scripts do the least they can`() {
        val customize = AudioSystemModule.customizeScript("system/vendor/etc/audio_effects.xml")
        assertTrue(customize.startsWith("#!/system/bin/sh"))
        assertTrue(customize.contains("set_perm_recursive \"\$MODPATH/system\" 0 0 0755 0755"))
        // والوسم يُصلَح للملفّ بعينه: الافتراضيّ (`set_perm_recursive`) يوسم كل شيء `system_file`،
        // وملفّ `/vendor` يُوسم `vendor_configs_file`.
        assertTrue(
            customize.contains(
                "set_perm \"\$MODPATH/system/vendor/etc/audio_effects.xml\" 0 0 0644 u:object_r:vendor_configs_file:s0",
            ),
        )
        assertFalse(customize.contains("cp "))
        assertFalse(customize.contains("mv "))
    }

    @Test
    fun `post-fs-data fixes ownership, mode and label before anything is mounted`() {
        val vendor = AudioSystemModule.postFsDataScript("system/vendor/etc/audio_effects.xml")
        assertTrue(vendor.startsWith("#!/system/bin/sh"))
        assertTrue(vendor.contains("MODDIR=\${0%/*}"))
        assertTrue(vendor.contains("TARGET=\"\$MODDIR/system/vendor/etc/audio_effects.xml\""))
        assertTrue(vendor.contains("chown 0:0"))
        // **و`chmod 0644` هو السطر الذي يمنع «نجحتْ ولا يقرأها أحد»:** الملفّ يُكتب من التطبيق بصلاحية
        // 0600، و`audioserver` لا يقرأ 0600 مملوكًا للجذر.
        assertTrue(vendor.contains("chmod 0644"))
        assertTrue(vendor.contains("chcon \"u:object_r:vendor_configs_file:s0\""))
        // وفشل الضبط لا يُسقِط الإقلاع: بلا جذر لا معنى للوحدة، وبها لا يجوز أن تُفشل `post-fs-data`.
        assertTrue(vendor.contains("|| true"))
        // وملفّ لا يوجد يُخرج بهدوء (بعد إلغاء التثبيت مثلًا).
        assertTrue(vendor.contains("|| exit 0"))

        assertTrue(AudioSystemModule.postFsDataScript("system/odm/etc/audio_effects.xml").contains("vendor_configs_file"))
        assertTrue(AudioSystemModule.postFsDataScript("system/etc/audio_effects.xml").contains("u:object_r:system_file:s0"))
        assertTrue(AudioSystemModule.postFsDataScript("system/product/etc/audio_effects.xml").contains("system_file"))
    }

    @Test
    fun `the module is four files and the overlay sits where Magisk mounts it`() {
        val files = AudioSystemModule.files(document, "/vendor/etc/audio_effects.xml", "طبقة مؤثّرات")!!
        assertEquals(
            listOf("module.prop", "system/vendor/etc/audio_effects.xml", "customize.sh", "post-fs-data.sh"),
            files.map { it.relativePath },
        )
        assertEquals(
            document.toXml(),
            files.first { it.relativePath == "system/vendor/etc/audio_effects.xml" }.content,
        )
        assertEquals(
            "/data/adb/modules/MaxManagerAudioEffects/system/vendor/etc/audio_effects.xml",
            AudioSystemModule.overlayAbsolutePath("/vendor/etc/audio_effects.xml"),
        )
    }

    @Test
    fun `a path that would not be mounted produces no module at all`() {
        assertNull(AudioSystemModule.files(document, "/data/adb/audio_effects.xml", "طبقة"))
        assertNull(AudioSystemModule.files(document, "/vendor/../etc/audio_effects.xml", "طبقة"))
        assertNull(AudioSystemModule.files(document, "vendor/etc/audio_effects.xml", "طبقة"))
        assertNull(AudioSystemModule.files(document, "/vendor/etc/audio_effect.xml", "طبقة"))
        assertNull(AudioSystemModule.overlayRelativePath("/data/etc/audio_effects.xml"))
        assertNull(AudioSystemModule.overlayAbsolutePath("/data/etc/audio_effects.xml"))
    }

    @Test
    fun `a missing module on disk is absent and a broken description still writes a module`() {
        // الوصف ذو السطرين يُطوى (لا يفشل) — والاختبار أعلاه يحرس الحالة التي تُفشل: المعرّف.
        val files = AudioSystemModule.files(document, "/system/etc/audio_effects.xml", "سطر\nثانٍ")!!
        assertTrue(files.first { it.relativePath == "module.prop" }.content.contains("description=سطر ثانٍ"))
        assertEquals(AudioSystemModule.ABSENT, AudioSystemModule.signatureOf(emptyList()))
    }

    @Test
    fun `the signature is the content, not the order of writing`() {
        val prop = "module.prop" to "id=MaxManagerAudioEffects\n"
        val overlay = "vendor/etc/audio_effects.xml" to "<audio_effects/>"
        assertEquals(
            AudioSystemModule.signature(listOf(prop, overlay)),
            AudioSystemModule.signature(listOf(overlay, prop)),
        )
        assertTrue(AudioSystemModule.signature(listOf(prop, overlay)).matches(Regex("^[0-9a-f]{64}$")))
        assertNotEquals(
            AudioSystemModule.signature(listOf(prop, overlay)),
            AudioSystemModule.signature(listOf(prop, "vendor/etc/audio_effects.xml" to "<audio_effects version=\"2.0\"/>")),
        )
    }

    @Test
    fun `a missing file is absent, and absent never equals a match`() {
        val files = AudioSystemModule.files(document, "/system/etc/audio_effects.xml", "طبقة")!!
        assertNotEquals(AudioSystemModule.ABSENT, AudioSystemModule.signatureOf(files))
        assertEquals(AudioSystemModule.ABSENT, AudioSystemModule.signature(emptyList()))
        assertEquals(AudioSystemModule.ABSENT, AudioSystemModule.signature(listOf("module.prop" to null)))
        assertEquals(
            AudioSystemModule.ABSENT,
            AudioSystemModule.signature(listOf("module.prop" to "id=x\n", "overlay" to null)),
        )
        // وحذف ملفٍّ من الوحدة تغيّر البصمة: الوحدة ليست «مطابقة» إن نقص بعضها.
        assertNotEquals(
            AudioSystemModule.signatureOf(files),
            AudioSystemModule.signatureOf(files.dropLast(1)),
        )
    }

    // ──────────────── ‏تكملة ٢٤٠: **شحن `libmaxfx.so`** — الفجوة المقيسة «صفر سطرٍ ينسخ مكتبة» ────────────────

    @Test
    fun `the library goes to the directory the factory searches, not next to the config`() {
        // **المقيس من AOSP:** `EffectConfig::resolveLibrary` يبحث عن الاسم المجرَّد في مجلّدات
        // `kEffectLibPath` (+ apex)، ولوق جهاز المالك يقول أين وجدها: `/vendor/lib64/soundfx//lib<name>.so`.
        assertEquals(
            "/vendor/lib64/soundfx/libmaxfx.so",
            AudioEffectsPaths.libraryDevicePath("arm64-v8a", "libmaxfx.so"),
        )
        assertEquals(
            "/vendor/lib/soundfx/libmaxfx.so",
            AudioEffectsPaths.libraryDevicePath("armeabi-v7a", "libmaxfx.so"),
        )
        // ومسارٌ غير معروف العمود لا يُخترع له مسار.
        assertNull(AudioEffectsPaths.libraryDevicePath("mips", "libmaxfx.so"))
        assertNull(AudioEffectsPaths.libraryDevicePath("arm64-v8a", ""))
        // **واسم واحد لا مسار:** سمة `path` في `<library>` اسم ملفٍ مجرَّد، فمسارٌ فيه `/` يُرفض.
        assertNull(AudioEffectsPaths.libraryDevicePath("arm64-v8a", "soundfx/libmaxfx.so"))
    }

    @Test
    fun `the library overlay lands under system and cannot escape the module root`() {
        assertEquals(
            "system/vendor/lib64/soundfx/libmaxfx.so",
            AudioEffectsPaths.libraryModuleRelativePath("/vendor/lib64/soundfx/libmaxfx.so"),
        )
        // وقسم النظام لا يُكرَّر (نفس قاعدة ملفّ التهيئة).
        assertEquals(
            "system/lib64/soundfx/libmaxfx.so",
            AudioEffectsPaths.libraryModuleRelativePath("/system/lib64/soundfx/libmaxfx.so"),
        )
        // **وما لا يُركّب لا يُوعد:** `..` أو جزء فارغ أو قسم غير معروف أو غير `.so`.
        assertNull(AudioEffectsPaths.libraryModuleRelativePath("/vendor/../lib64/soundfx/libmaxfx.so"))
        assertNull(AudioEffectsPaths.libraryModuleRelativePath("/vendor/lib64//libmaxfx.so"))
        assertNull(AudioEffectsPaths.libraryModuleRelativePath("/data/local/tmp/libmaxfx.so"))
        assertNull(AudioEffectsPaths.libraryModuleRelativePath("vendor/lib64/soundfx/libmaxfx.so"))
        assertNull(AudioEffectsPaths.libraryModuleRelativePath("/vendor/lib64/soundfx/libmaxfx.so.bak"))
    }

    @Test
    fun `the library label is not the config label`() {
        // **ووسمٌ مغايرٌ مقيس:** مكتبة `/vendor/lib64` تُوسم `vendor_file`، وملفّ `/vendor/etc` يُوسم
        // `vendor_configs_file` — وخلطُهما يمنع `audioserver` من تحميل المكتبة (`avc denied`).
        assertEquals(
            AudioEffectsPaths.VENDOR_FILE_LABEL,
            AudioEffectsPaths.libraryLabel("system/vendor/lib64/soundfx/libmaxfx.so"),
        )
        assertEquals(
            AudioEffectsPaths.SYSTEM_FILE_LABEL,
            AudioEffectsPaths.libraryLabel("system/lib64/soundfx/libmaxfx.so"),
        )
        assertNotEquals(
            AudioEffectsPaths.overlayLabel("system/vendor/etc/audio_effects.xml"),
            AudioEffectsPaths.libraryLabel("system/vendor/lib64/soundfx/libmaxfx.so"),
        )
    }

    @Test
    fun `a library plan is built only from a real source file and a known abi`() {
        val plan = audioEffectLibraryPlan(
            sourcePath = "/data/data/nd.max/files/libmaxfx.so",
            abi = "arm64-v8a",
            fileName = "libmaxfx.so",
        )!!
        assertEquals("libmaxfx.so", plan.fileName)
        assertEquals("/data/data/nd.max/files/libmaxfx.so", plan.sourcePath)
        assertEquals("/vendor/lib64/soundfx/libmaxfx.so", plan.devicePath)
        assertEquals("system/vendor/lib64/soundfx/libmaxfx.so", plan.moduleRelativePath)
        assertEquals(AudioEffectsPaths.VENDOR_FILE_LABEL, plan.label)
        assertEquals("644", plan.mode)
        // **وبلا مصدرٍ لا خطّة** — ولا مسار مظنون: الواجهة تقول «المكتبة غير مشحونة».
        assertNull(audioEffectLibraryPlan(null, "arm64-v8a", "libmaxfx.so"))
        assertNull(audioEffectLibraryPlan("  ", "arm64-v8a", "libmaxfx.so"))
        assertNull(audioEffectLibraryPlan("/data/app/x/lib/arm64", "mips", "libmaxfx.so"))
        // **ومصدرٌ لا ينتهي باسم المكتبة لا يُقبل:** ما سيُنسخ ليس المكتبة.
        assertNull(audioEffectLibraryPlan("/tmp/libother.so", "arm64-v8a", "libmaxfx.so"))
    }

    @Test
    fun `the scripts fix the library mode and label only when a library is shipped`() {
        val relative = AudioEffectsPaths.libraryModuleRelativePath(
            AudioEffectsPaths.libraryDevicePath("arm64-v8a", "libmaxfx.so")!!,
        )!!
        val withLibrary = AudioSystemModule.postFsDataScript(
            "system/vendor/etc/audio_effects.xml",
            relative,
        )
        // **وهو نفس عطب ملفّ التهيئة بنفس الأثر:** مكتبة ٠٦٠٠ تُركّب ولا يقرؤها المصنع.
        assertTrue(withLibrary.contains("chmod 0644 \"\$LIB\""))
        assertTrue(withLibrary.contains("chcon \"u:object_r:vendor_file:s0\" \"\$LIB\""))
        assertTrue(withLibrary.contains("LIB=\"\$MODDIR/$relative\""))
        // والغياب لا يُسقط الإقلاع: الحارس `[ -f ]` ثمّ `|| true` على كل سطر.
        assertTrue(withLibrary.contains("[ -f \"\$LIB\" ]"))
        assertTrue(withLibrary.contains("|| true"))

        val customize = AudioSystemModule.customizeScript(
            "system/vendor/etc/audio_effects.xml",
            relative,
        )
        assertTrue(
            customize.contains(
                "set_perm \"\$MODPATH/$relative\" 0 0 0644 u:object_r:vendor_file:s0",
            ),
        )
        // **والرقم واحد في السطرين وفي مقارنة التثبيت:** `LIBRARY_MODE` هي مصدره، فلا ينحرف
        // سكربتٌ عن قياس.
        assertTrue(withLibrary.contains("chmod 0$LIBRARY_MODE \"\$LIB\""))

        // **وبلا مكتبة لا كتلة ميْتة** — فلا يوهم قارئ السكربت بأنّ شيئًا نُسخ.
        val without = AudioSystemModule.postFsDataScript("system/vendor/etc/audio_effects.xml")
        assertFalse(without.contains("LIB="))
        assertFalse(without.contains("chmod 0644 \"\$LIB\""))
        assertFalse(
            AudioSystemModule.customizeScript("system/vendor/etc/audio_effects.xml")
                .contains("vendor_file"),
        )
        // ومع ذلك: **لا `cp` في السكربت** — النسخ يفعلُه التطبيق بالمحكِّم لا سكربتٌ أعمى.
        assertFalse(withLibrary.contains("cp "))
        assertFalse(customize.contains("cp "))
    }

    // ──────────────── ‏تكملة ٢٤١: **إخراج المكتبة من الحزمة** — `nativeLibraryDir` ليس مجلّدًا ────────────────

    /** مجلّد مؤقّت جديد — **بلا `createTempDir` المُهمَلة** فلا يُبنى على دالّةٍ ستُرفع. */
    private fun tempDir(): File = Files.createTempDirectory("maxfx-test").toFile()

    /** يبني حزمةً حقيقيّة (‏zip) فيها مدخل المكتبة — فيُقاس الاستخراج على ملفّ لا على توقّع. */
    private fun apkWith(entries: Map<String, ByteArray>): File {
        val apk = File.createTempFile("base", ".apk")
        ZipOutputStream(apk.outputStream()).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return apk
    }

    @Test
    fun `the staged entry is the one AGP writes and a wrong abi asks for nothing`() {
        assertEquals("lib/arm64-v8a/libmaxfx.so", AudioEffectLibraryStaging.entryName("arm64-v8a", "libmaxfx.so"))
        assertEquals("lib/armeabi-v7a/libmaxfx.so", AudioEffectLibraryStaging.entryName("armeabi-v7a", "libmaxfx.so"))
        assertNull(AudioEffectLibraryStaging.entryName("mips", "libmaxfx.so"))
        assertNull(AudioEffectLibraryStaging.entryName("arm64-v8a", "") )
        // واسمٌ فيه مسار يُرفض: المدخل داخل الحزمة يُبنى من اسمٍ مجرَّد لا من مسار.
        assertNull(AudioEffectLibraryStaging.entryName("arm64-v8a", "lib/arm64-v8a/libmaxfx.so"))
        assertFalse(AudioEffectLibraryStaging.knowsAbi("arm64-v8a " + "x"))
        assertTrue(AudioEffectLibraryStaging.knowsAbi(" arm64-v8a "))
    }

    @Test
    fun `the library is extracted out of the real apk zip, not read from nativeLibraryDir`() {
        val apk = apkWith(mapOf("lib/arm64-v8a/libmaxfx.so" to byteArrayOf(1, 2, 3, 4, 5)))
        val dir = tempDir()
        val staged = AudioEffectLibraryStaging.stage(
            apkPath = apk.absolutePath,
            abi = "arm64-v8a",
            fileName = "libmaxfx.so",
            targetDir = dir.absolutePath,
        )
        assertEquals("${dir.absolutePath}/libmaxfx.so", staged)
        assertEquals(5L, File(staged!!).length())
        // **والمؤقّت لا يُترك خلفنا:** من قطعه لا يترك «مكتبة» مبتورة تُنسخ إلى النظام.
        assertFalse(File(dir, "libmaxfx.so.tmp").exists())
        // واستخراجٌ ثانٍ يستبدل الأوّل بلا فشل (الزرّ يُضغط مرّتين).
        assertEquals(staged, AudioEffectLibraryStaging.stage(apk.absolutePath, "arm64-v8a", "libmaxfx.so", dir.absolutePath))
    }

    @Test
    fun `a missing entry, a foreign abi and a bad apk all return null instead of an empty library`() {
        val dir = tempDir()
        // حزمةٌ فيها مكتبة أخرى: **لا بحثٌ جزئيّ** — مكتبةٌ شبيهة ليست مكتبتنا.
        val other = apkWith(mapOf("lib/arm64-v8a/libother.so" to byteArrayOf(9)))
        assertNull(AudioEffectLibraryStaging.stage(other.absolutePath, "arm64-v8a", "libmaxfx.so", dir.absolutePath))
        // وحزمةٌ بمدخلٍ فارغ ليست مكتبة.
        val empty = apkWith(mapOf("lib/arm64-v8a/libmaxfx.so" to ByteArray(0)))
        assertNull(AudioEffectLibraryStaging.stage(empty.absolutePath, "arm64-v8a", "libmaxfx.so", dir.absolutePath))
        // وملفٌ ليس حزمةً (أو غائباً) يعود `null` لا استثناء.
        assertNull(AudioEffectLibraryStaging.stage("/data/app/does-not-exist/base.apk", "arm64-v8a", "libmaxfx.so", dir.absolutePath))
        val bogus = File.createTempFile("bogus", ".apk").apply { writeText("not a zip") }
        assertNull(AudioEffectLibraryStaging.stage(bogus.absolutePath, "arm64-v8a", "libmaxfx.so", dir.absolutePath))
        assertNull(AudioEffectLibraryStaging.stage(null, "arm64-v8a", "libmaxfx.so", dir.absolutePath))
        // ومجلّد هدفٍ غير موجود ⇒ لا كتابة في مكانٍ مجهول.
        assertNull(AudioEffectLibraryStaging.stage(other.absolutePath, "arm64-v8a", "libmaxfx.so", "/nope/nope"))
    }

    @Test
    fun `sourceFor prefers the apk and falls back to an already extracted directory`() {
        val apk = apkWith(mapOf("lib/arm64-v8a/libmaxfx.so" to byteArrayOf(7, 7)))
        val dir = tempDir()
        val dirWithLib = tempDir().also { File(it, "libmaxfx.so").writeBytes(byteArrayOf(3)) }
        // ١) الحزمة أوّلًا — **تعمل في الحالتين** (`extractNativeLibs` صحيحًا أو خاطئًا).
        assertEquals(
            "${dir.absolutePath}/libmaxfx.so",
            AudioEffectLibraryStaging.sourceFor(apk.absolutePath, dirWithLib.absolutePath, "arm64-v8a", "libmaxfx.so", dir.absolutePath),
        )
        // ٢) وحزمةٌ بلا المدخل ⇒ يقع على المجلّد المفكوك (أجهزة `extractNativeLibs=true`).
        val withoutEntry = apkWith(mapOf("classes.dex" to byteArrayOf(1)))
        assertEquals(
            "${dirWithLib.absolutePath}/libmaxfx.so",
            AudioEffectLibraryStaging.sourceFor(withoutEntry.absolutePath, dirWithLib.absolutePath, "arm64-v8a", "libmaxfx.so", dir.absolutePath),
        )
        // ٣) ولا حزمة ولا مجلّد ⇒ `null` — وهي حالة «المكتبة غير مشحونة» التي تُقال لا تُتجاهل.
        assertNull(AudioEffectLibraryStaging.sourceFor(withoutEntry.absolutePath, "/nope/lib/arm64", "arm64-v8a", "libmaxfx.so", dir.absolutePath))
        assertNull(AudioEffectLibraryStaging.sourceFor(null, null, "arm64-v8a", "libmaxfx.so", dir.absolutePath))
        // وملفٌ فارغ في المجلّد ليس مكتبة (يفشل فكّه فورًا على الجهاز).
        val emptyDir = tempDir().also { File(it, "libmaxfx.so").writeBytes(ByteArray(0)) }
        assertNull(AudioEffectLibraryStaging.sourceFor(null, emptyDir.absolutePath, "arm64-v8a", "libmaxfx.so", dir.absolutePath))
    }

    @Test
    fun `a declared source yields the whole plan and an undeclared one yields none`() {
        val apk = apkWith(mapOf("lib/arm64-v8a/libmaxfx.so" to byteArrayOf(1, 2, 3)))
        val dir = tempDir()
        val plan = AudioEffectLibrarySource(
            apkPath = apk.absolutePath,
            stagingDir = dir.absolutePath,
            abi = "arm64-v8a",
        ).planFor("libmaxfx.so")!!
        // **ونفس السلسلة المقيسة:** من مدخل الحزمة ⇐ مسار البحث الذي يقرؤه المصنع ⇐ مسار الطبقة.
        assertEquals("${dir.absolutePath}/libmaxfx.so", plan.sourcePath)
        assertEquals("/vendor/lib64/soundfx/libmaxfx.so", plan.devicePath)
        assertEquals("system/vendor/lib64/soundfx/libmaxfx.so", plan.moduleRelativePath)
        assertEquals(AudioEffectsPaths.VENDOR_FILE_LABEL, plan.label)

        // ولا شيء معلَن ⇒ لا خطّة: وهذه هي حالة «المكتبة غير مشحونة» التي تُقال بسببها الصريح.
        assertNull(AudioEffectLibrarySource().planFor("libmaxfx.so"))
        assertNull(AudioEffectLibrarySource(abi = "arm64-v8a").planFor("libmaxfx.so"))
    }

    // ──────────────── ‏تكملة ٢٤٢: **حالة المكتبة الخمس** — الحكم في نواةٍ نقيّة لا في Compose ────────────────

    @Test
    fun `the library state is one of five measured verdicts and the order is deliberate`() {
        // **الأسبق أوّلًا:** بلا خطّة لا معنى لقيس وجودٍ — فلا يُقال «غائبة» عن مكتبةٍ لم نشحنها.
        assertEquals(
            AudioEffectLibraryState.NOT_SHIPPED,
            audioEffectLibraryState(hasPlan = false, installed = true, mode = "644"),
        )
        // ومخطّطةٌ غائبة ⇒ «غير منسوخة»، **ولا تُقرأ صلاحية ملفّ غير موجود**.
        assertEquals(
            AudioEffectLibraryState.NOT_INSTALLED,
            audioEffectLibraryState(hasPlan = true, installed = false, mode = null),
        )
        // **والجهل ليس نفيًا (ADR-07):** `installed = null` (لم يُقَس، بلا جذر مثلًا) ⇒ «لم تُقَس»
        // لا «غير منسوخة» — وهو نفس درس المصفوفة: النفي يُعلن عن قياسنا لا عن الجهاز.
        assertEquals(
            AudioEffectLibraryState.UNMEASURED,
            audioEffectLibraryState(hasPlan = true, installed = null, mode = null),
        )
        // والموجودة المقروءة هي الشرط الكامل — **وهي الوحيدة التي تُعدّ إيجابيّة**.
        assertEquals(
            AudioEffectLibraryState.READABLE,
            audioEffectLibraryState(hasPlan = true, installed = true, mode = LIBRARY_MODE),
        )
        // **والعطب المقيس:** موجودة بصلاحية غير قراءة ⇒ «ممنوعة»، وهي التي تُطبع «can't find».
        assertEquals(
            AudioEffectLibraryState.NOT_READABLE,
            audioEffectLibraryState(hasPlan = true, installed = true, mode = "600"),
        )
        // وصلاحيةٌ لم تُقرأ ⇒ «لم تُقَس» لا «قراءة» ولا «منع».
        assertEquals(
            AudioEffectLibraryState.UNMEASURED,
            audioEffectLibraryState(hasPlan = true, installed = true, mode = null),
        )
        // **والحالات خمسٌ بأسماءٍ متمايزة** — فالرمز يُكتب في السجلّ ويُقارن.
        val tokens = AudioEffectLibraryState.values().map { it.token }
        assertEquals(5, tokens.size)
        assertEquals(tokens.size, tokens.toSet().size)
    }
}

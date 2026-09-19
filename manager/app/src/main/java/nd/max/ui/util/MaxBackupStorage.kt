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

import java.io.File

/**
 * أين يعيش الأرشيف.
 *
 * **المكان المطلوب** هو مجلد عام يراه المستخدم ومدير الملفات بلا جذر:
 *
 *     /storage/emulated/0/MaxManger/MaxBackup
 *
 * وهذا الملف يحمل **القرار**، لا العملية: هو لا يكتب ولا يقرأ ولا يلمس `Context`،
 * بل يقول أي جذر يُستعمل وأي جذور تُفحَص. سبب الفصل ليس التزيين: القرار هنا يُختبر
 * بلا جهاز وبلا Android، وهو القرار الوحيد الذي إن أخطأ ضاعت نسخ المستخدم في مكان
 * لا يُقرأ منه.
 *
 * **ولماذا جذران لا جذر:** أندرويد ١١+ يمنع تطبيقًا بلا صلاحية «الوصول لكل الملفات»
 * من الكتابة في جذر التخزين العام. فلو كتبنا إلى المسار العام وحده لكانت النتيجة
 * إما فشلًا صريحًا أو — أسوأ — نسخة تُعلن نجاحًا ولم تُكتب. لذلك: المسار العام إن
 * كان قابلًا للكتابة، وإلا مجلد التطبيق الخارجي، **والواجهة تقول أيّهما استُعمل**.
 * والسرد يمرّ على الجذرين معًا حتى لا تختفي نسخة أُخذت قبل هذا التغيير.
 */
object MaxBackupStorage {

    /** الوالد العام: مجلد باسم التطبيق في جذر التخزين الداخلي. */
    const val PUBLIC_PARENT = "/storage/emulated/0/MaxManger"

    /** مجلد الأرشيف داخل الوالد. */
    const val FOLDER = "MaxBackup"

    /**
     * ملف يُكتب ثم يُحذف لاختبار الكتابة فعلًا.
     * الاسم مخفي ومبدوء بنقطة كي لا يُرى في مدير ملفات المستخدم، ولا يُخلط بأرشيف.
     */
    const val PROBE_FILE = ".max-backup-probe"

    /** المسار المطلوب: `/storage/emulated/0/MaxManger/MaxBackup`. */
    fun requestedRoot(): File = File(PUBLIC_PARENT, FOLDER)

    /** جذر الاحتياط: مجلد التطبيق الخارجي، داخل `MaxBackup` بنفس الاسم. */
    fun fallbackRoot(externalFilesDir: File): File = File(externalFilesDir, FOLDER)

    /**
     * الجذر المُستخدَم.
     *
     * @param requestedUsable نتيجة **فحص كتابة فعلي** — لا نُمرّر «أظنّه يعمل»، ولا نعتمد
     *        على وجود المجلد وحده: مجلد موجود ومملوك لـroot يفشل عند أول كتابة من التطبيق.
     */
    fun choose(requested: File, fallback: File, requestedUsable: Boolean): File =
        if (requestedUsable) requested else fallback

    /**
     * الجذور التي يمرّ عليها السرد والحذف والتدقيق.
     *
     * الجذر الاحتياطي يُقرأ **دائمًا**، لا عند غياب العام فقط: مستخدم أخذ نسخًا بالأمس
     * ثم منح الصلاحية اليوم يجب أن يرى نسخه، لا أن يظنّ أنها فُقدت.
     */
    fun searchRoots(active: File, fallback: File): List<File> =
        listOf(active, fallback).distinctBy { it.absolutePath }

    /** مجلد تطبيق داخل جذر. */
    fun folderOf(root: File, pkg: String): File = File(root, pkg)

    /** هل هذا الجذر هو المسار العام المطلوب؟ (قرار بالمسار، لا بحالة القرص.) */
    fun isRequested(root: File): Boolean = root.absolutePath == requestedRoot().absolutePath
}

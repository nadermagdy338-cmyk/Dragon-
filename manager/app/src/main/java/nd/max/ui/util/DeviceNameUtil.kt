/*
 * Copyright (C) 2026-2027 Zexshia
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

package nd.max.ui.util


import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicReference

/**
 * الاسم من `Build` وحده — **بلا أيّ قراءة ملفّ ولا قاعدة بيانات ولا استعلام**.
 *
 * **ولماذا دالّة مستقلّة (عطب سرعة مُبلَّغ عنه: «جلب المعلومات في الشاشة الرئسية بطيء،
 * انتظر دقيقة»):** كان الاسم التسويقي يُقرأ بـ[getRealDeviceName] **داخل `remember { }`
 * في `HomeScreen`** — و`remember` يُنفَّذ **خلال التركيب على الخيط الرئيسي**. وتلك الدالّة
 * تنسخ `devices.db` من الأصول (**٤٫٢ ميغابايت** — مقيس) إلى مجلّد قواعد البيانات، ثم تفتح
 * SQLite وتستعلم بـ`LIKE` (وهو استعلام لا يستعمل فهرسًا: مسح كامل للجدول). فكان أوّل إطار
 * في الرئيسية ينتظر نسخة ٤ ميغابايت وقرصًا واستعلامًا — بدل أن يرسم.
 *
 * ⇒ فالاسم الذي **لا يكلّف قراءة واحدة** يُعرض أوّلًا، ثم يُستبدل بالاسم التسويقي حين
 * يصل. وكلاهما اسم حقيقيّ للجهاز نفسه؛ الفرق أن الثاني أَدَقّ في التسويق لا في الصحّة.
 */
fun fallbackDeviceName(): String {
    val mfg = Build.MANUFACTURER
    val model = Build.MODEL
    val device = Build.DEVICE

    val defaultName = if (model.contains(mfg, ignoreCase = true)) {
        model
    } else {
        "$mfg $model"
    }

    return if (defaultName.contains(device, ignoreCase = true)) {
        defaultName
    } else {
        "$defaultName ($device)"
    }
}

/**
 * الاسم التسويقي من جدول الأجهزة المرفق (`devices.db`) — **يُنادى من خيط خلفيّ فقط**.
 *
 * وثلاثة إصلاحات هنا، كلّها من نفس العطب المقيس:
 *
 * 1. **الحفظ في الذاكرة:** الاسم لا يتغيّر في عمر العملية (من `Build` ومن جدول مرفق بالنسخة)،
 *    فطلبُ جدول ٤ ميغابايت مرّتين — مرّة للرئيسية ومرّة لـ«معلومات الجهاز» — كان يضاعف
 *    الكلفة بلا معلومة جديدة. وبعد أوّل نداء يصير النداء الثاني مجّانيًّا.
 * 2. **نسخ ذرّي:** النسخة كانت تُكتب **على مسار الملفّ النهائي**، فانقطاعها (مقتل العملية،
 *    أو تخزين ممتلئ) يُنتج قاعدة **نصف مكتوبة** يظنّها الشرط `dbFile.exists()` تامّة، فيفشل
 *    الاستعلام **إلى الأبد** ويسقط الاسم إلى الاسم الافتراضي بلا سبب ظاهر. صارت تُكتب على
 *    `.tmp` ثم تُنقل باسمها — فلا يوجد «نصف ملفّ» أصلًا.
 * 3. **شفاء ذاتيّ:** فشل فتح القاعدة (ملفّ مقطوع من نسخة قديمة) **يحذف** الملفّ فيُعاد نسخه
 *    في المحاولة التالية، بدل أن يبقى الاسم ناقصًا ما دام التطبيق مثبّتًا.
 */
private val cachedRealName = AtomicReference<String?>(null)

fun getRealDeviceName(context: Context): String {
    cachedRealName.get()?.let { return it }
    val resolved = resolveRealDeviceName(context)
    cachedRealName.set(resolved)
    return resolved
}

private fun resolveRealDeviceName(context: Context): String {
    val mfg = Build.MANUFACTURER
    val model = Build.MODEL
    val device = Build.DEVICE

    val defaultName = if (model.contains(mfg, ignoreCase = true)) {
        model
    } else {
        "$mfg $model"
    }


    val cleanModel = model.replace(mfg, "", ignoreCase = true).trim(' ', '-', '_')
    val cleanDevice = device.replace(mfg, "", ignoreCase = true).trim(' ', '-', '_')

    val dbName = "devices.db"
    val dbFile = context.getDatabasePath(dbName)


    if (!dbFile.exists()) {
        try {
            dbFile.parentFile?.mkdirs()
            // نسخ على مسار مؤقّت ثم نقل: المسار النهائي لا يوجد إلا وملفّه تامّ (انظر ٢ أعلاه).
            val tmp = File(dbFile.absolutePath + ".tmp")
            context.assets.open(dbName).use { input ->
                FileOutputStream(tmp).use { output ->
                    input.copyTo(output)
                }
            }
            if (!tmp.renameTo(dbFile)) {
                tmp.copyTo(dbFile, overwrite = true)
                tmp.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()

            return if (defaultName.contains(device, ignoreCase = true)) defaultName else "$defaultName ($device)"
        }
    }

    var marketingName = ""
    var db: SQLiteDatabase? = null

    if (dbFile.exists()) {
        try {
            db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)


            val query = """
                SELECT name 
                FROM devices 
                WHERE model LIKE ? OR model LIKE ? 
                   OR device LIKE ? OR device LIKE ? 
                LIMIT 1
            """.trimIndent()

            db.rawQuery(query, arrayOf(model, cleanModel, device, cleanDevice)).use { cursor ->
                if (cursor.moveToFirst()) {

                    val nameFromDb = cursor.getString(0)


                    if (!nameFromDb.isNullOrBlank()) {

                        marketingName = if (nameFromDb.contains(mfg, ignoreCase = true)) {
                            nameFromDb.trim()
                        } else {
                            "$mfg $nameFromDb".trim()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // قاعدة لا تُفتح = ملفّ مقطوع لا قاعدة فارغة ⇒ يُحذف ليُنسخ الأصل في المحاولة
            // التالية. وحذفُه لا يُخسر شيئًا: نسخة الأصل مرفقة بالنسخة نفسها.
            runCatching { dbFile.delete() }
        } finally {
            db?.close()
        }
    }


    val finalName = marketingName.ifEmpty { defaultName }

    var cleanFinal = finalName.replace("($device)", "", ignoreCase = true).trim()
    cleanFinal = cleanFinal.replace("$device $device", device, ignoreCase = true).trim()

    return if (cleanFinal.contains(device, ignoreCase = true)) {
        cleanFinal
    } else {
        "$cleanFinal ($device)"
    }
}

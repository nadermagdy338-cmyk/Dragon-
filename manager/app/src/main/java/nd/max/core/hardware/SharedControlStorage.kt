/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.core.hardware

import android.content.Context
import java.io.File

/**
 * تخزين مستودع التحكّم المشترك — **قرارٌ واحد للعمليّتين**، وهو **المحميّ بالجهاز (DE)** لا المحميّ
 * باعتماد المستخدم (CE).
 *
 * **والعطب الذي وُجد هذا الملفّ لأجله — مقيسٌ من سجلّ جهاز حقيقيّ (٢٠٢٦-١٠-٠١):**
 * كانت العمليّتان (التطبيق، والرفيق الجذريّ `AppMonitor`) تبنيان المستودع في `filesDir` العاديّ —
 * أي `/data/user/0/nd.max/files`، وهو **التخزين المحميّ باعتماد المستخدم**، ولا يُفتح قبل أوّل فتحٍ
 * للشاشة. وسجلّ الجهاز نفسه يقول السلسلة كاملة:
 *
 * ```
 * 17:36:31.885  LockSettingsService: Not unlocking CE storage for user 0 yet because user is secured
 * 17:36:33.535  ContextImpl: Failed to ensure /data/user/0/nd.max/files: mkdir failed: ENOENT
 * 17:36:33.595  AndroidRuntime: java.lang.IllegalStateException: cannot-create-shared-control-directory
 * 17:36:48.555  LockSettingsService: Unlocked CE storage for secured user 0
 * 17:38:35.291  MaxManager: EVENT=JAVA_COMPANION_TIMEOUT checks=120 action=exit
 * ```
 *
 * فالرفيق — الذي يبدأه `service.sh` عند الإقلاع، **قبل** فتح تخزين المستخدم بخمس عشرة ثانية —
 * يموت، والخادم ينتظر ١٢٠ ثانية ثم يُغلق الوحدة كلها («Java companion daemon crashed or failed to
 * start»)، وكل ما بعد ذلك يفشل (`Atlas: all eligible routes failed`). **وهو تفسير «تعمل مرّة ولا تعمل
 * أخرى»:** العطب ليس في العتاد ولا في الإعداد، بل في **توقيت الإقلاع مقابل قفل الشاشة** — فمن لا قفل
 * له على شاشته لا يراه أبدًا، ومن له قفل يراه متقطّعًا.
 *
 * **ولماذا لا يُنتظر الفتح بدلًا من هذا:** الوحدة وظيفتها أن تعمل **من الإقلاع**، فانتظار فتح الشاشة
 * يخالف الغرض ويسقط من يشغّل هاتفه ثم يتركه. والمحميّ بالجهاز هو النوع الذي صنعه أندرويد لهذا الموضع
 * بعينه: **متاح قبل الفتح، وباقٍ بين الإقلاعات**.
 *
 * **وقاعدةٌ واحدة لأنّ الانقسام ممكن:** لو بنت إحدى العمليّتين مستودعها في CE والأخرى في DE لصار
 * **مستودعان لا يرى أحدهما الآخر** — فيكتب المستخدم قفلًا تراه الواجهة ولا يراه الرفيق. ولهذا القرار
 * في موضع واحد يُستدعى من الموضعين، لا في نسختين تتفرّقان يومًا.
 */
object SharedControlStorage {

    /**
     * مجلد المستودع المشترك: `filesDir` من سياق **محميّ بالجهاز**، والقرار محصورٌ في هذا السطر.
     *
     * **والفشل لا يُخفى:** من لا يستطيع بناء المجلد يُعلن ذلك باسمه (`SHARED_CONTROL_PLANE_*`) في
     * موضع النداء، ولا يمضي بمستودع مُعطَّل.
     */
    fun filesDir(context: Context): File = context.createDeviceProtectedStorageContext().filesDir
}

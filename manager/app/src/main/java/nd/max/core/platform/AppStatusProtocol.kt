/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.core.platform

/**
 * عقد ملف `app_status` — القناة الوحيدة من الرفيق إلى الخادم. **الصيغة تُعلَن هنا مرة واحدة**،
 * ويستهلكها الكاتب (`AppMonitor`) واختبار العقد معًا، فلا تتفرّع نسختان تتباعدان في صمت.
 *
 * لماذا وُجد هذا الملف
 * --------------------
 * كان الشكل مبنيًّا داخل `buildStatus()` ككتلة `buildString` لا يقرؤها إلا القارئ C
 * (`archdaemon/jni/src/AppLoader/StatusMonitor.c`). وأي تعديل عليها — إعادة ترتيب، أو مفتاح
 * يتبدّل حرفه — كان يمرّ **صامتًا**: لا مُصرّف يراه، ولا اختبار يقيسه، والخادم يتخطّى السطر
 * الذي لا يعرفه. وهي بالضبط «صيغة ملفات غير مُصدَّرة ⇒ كسر صامت محتمل» في مخاطر `ARCHITECTURE-AUDIT` §١٦.
 *
 * فالعقد صار شيئًا يُقاس: الاختبار يُنشئ النصّ من هنا ويقارنه بـ`fixtures/contracts/app_status.valid.txt`
 * **بحرفيّته**، والقارئ C يقرأ الملف نفسه ويقارن بالقيم المُعلَنة. فإن انحرف أحد الطرفين سقط اختباره.
 *
 * القانون المُثبَّت (مقيس من القارئ C)
 * -----------------------------------
 * - سطر لكل حقل: `key value` مفصولة بفراغ واحد، ونهاية LF.
 * - **القارئ يتخطّى ما لا يعرفه**: مطابقة بادئة لا مطابقة كاملة، فإضافة حقل **إضافة لا كسر**.
 * - ولهذا يُكتب سطر الإصدار `v 1` أولًا **ولا يقرؤه الخادم أصلًا** (`StatusMonitor.c` لا يطابق
 *   `v ` مع أي حقل) — فالمصافحة أحادية لا تحرسها بوّابة في C، وإنما وُضعت أولاها كي يستطيع قارئ
 *   مستقبليّ تخطّيها **قبل** أي حقل بيانات.
 * - وغياب حقل يُبقي **الافتراضي المُعلن** في الخادم (`app_name` = `Unknown`، `battery_level` = `-1`).
 *
 * ولا يُغيَّر ترتيب الحقول هنا عبثًا: الترتيب ليس جزءًا من العقد (القارئ مطابقة بادئة)، لكن
 * الـfixture يقارن بحرفيّته، فتغيير الترتيب يُعلَن ولا يُخفى.
 */
object AppStatusProtocol {

    /**
     * إصدار العقد. يُكتب في السطر الأول، **والقارئ في C لا يقرؤه أصلًا** (`StatusMonitor.c` لا
     * يطابق `v ` مع أي حقل) — فالمصافحة **أحادية بالتصميم**، وهي بالضبط ما يجعل الإضافة لا كسرًا:
     * القارئ يتخطّى ما لا يعرفه، ولا حاجة لتحديث الطرفين معًا.
     *
     * والزيادة تكون عند **تغيير كاسر** لا إضافي.
     *
     * ⚠️ **وفيها كان خطأ مفهوم مُقاس:** كان مكتوبًا هاهنا أن يقابله `MODULE_VERSION "V1"` في
     * `MaxManager.h`. وهذا غير صحيح: ذلك السطر **سلسلة إصدار البناء** (قيمة ملف `version`،
     * ويختمها `verify.sh` فيه مطابقةً لـ`module.prop`، ويقرؤها الخادم في `check_module_version()`)
     * لا رقم بروتوكول — فقيمة أي حزمة نُشحنها ليست `V1`. والحرس في
     * `AppStatusProtocolContractTest` يقيس اليوم **اتّفاق الرأس بملف `version`** ويمنع أن يعود
     * الخلط بإصدار البروتوكول.
     */
    const val VERSION = 1

    const val FIELD_VERSION = "v"
    const val FIELD_FOCUSED_APP = "focused_app"
    const val FIELD_SCREEN_AWAKE = "screen_awake"
    const val FIELD_BATTERY_SAVER = "battery_saver"
    const val FIELD_ZEN_MODE = "zen_mode"
    const val FIELD_BATTERY_LEVEL = "battery_level"
    const val FIELD_IS_CHARGING = "is_charging"
    const val FIELD_APP_NAME = "app_name"
    const val FIELD_REFRESH_RATE = "refresh_rate"
    const val FIELD_MAX_REFRESH_RATE = "max_refresh_rate"
    const val FIELD_SWITCH_ID = "switch_id"
    const val FIELD_PERAPP_ACTIVE = "perapp_active"

    /**
     * الحقول التي **يقرؤها الخادم** (بترتيب بادئاته في `StatusMonitor.c`) — مرجع للتدقيق لا
     * للكتابة. البقية (`refresh_rate` · `max_refresh_rate` · `perapp_active`) تُقرأ في Kotlin
     * ويتجاهلها الخادم، وتبقى في الملف لأن الكاتب واحد والملف واحد.
     */
    val DAEMON_CONSUMED_FIELDS: List<String> = listOf(
        FIELD_FOCUSED_APP, FIELD_SCREEN_AWAKE, FIELD_BATTERY_SAVER, FIELD_ZEN_MODE,
        FIELD_APP_NAME, FIELD_BATTERY_LEVEL, FIELD_IS_CHARGING, FIELD_SWITCH_ID,
    )

    /**
     * تُنتج نصّ `app_status` كاملًا — دالة **خالصة** بلا قراءة ملف ولا جذر، فتُقاس باختبار وحدة.
     * وهذا هو الشرط الذي يجعل العقد مرئيًّا: ما كان داخل `buildStatus()` لا يمكن استدعاؤه.
     *
     * @param focusedApp `pkg pid uid` كما يقرؤه الخادم بـ`sscanf(line+12, "%127s %d %d")`.
     * @param appName اسم معروض؛ قد يحوي فراغات، والخادم يقرأ بقيّة السطر فتبقى محفوظة.
     * @param switchId معرّف جلسة تبديل التطبيق، وتُقرأ بـ`%31s` فلا تتجاوز ٣١ محرفًا.
     */
    fun encode(
        focusedApp: String,
        screenAwake: Int,
        batterySaver: Int,
        zenMode: Int,
        batteryLevel: Int,
        isCharging: Int,
        appName: String,
        currentRefreshRate: Int,
        maxRefreshRate: Int,
        switchId: String,
        perAppOverridesActive: Boolean,
    ): String = buildString {
        appendLine("$FIELD_VERSION $VERSION")
        appendLine("$FIELD_FOCUSED_APP $focusedApp")
        appendLine("$FIELD_SCREEN_AWAKE $screenAwake")
        appendLine("$FIELD_BATTERY_SAVER $batterySaver")
        appendLine("$FIELD_ZEN_MODE $zenMode")
        appendLine("$FIELD_BATTERY_LEVEL $batteryLevel")
        appendLine("$FIELD_IS_CHARGING $isCharging")
        appendLine("$FIELD_APP_NAME $appName")
        appendLine("$FIELD_REFRESH_RATE $currentRefreshRate")
        appendLine("$FIELD_MAX_REFRESH_RATE $maxRefreshRate")
        appendLine("$FIELD_SWITCH_ID $switchId")
        appendLine("$FIELD_PERAPP_ACTIVE ${if (perAppOverridesActive) 1 else 0}")
    }
}

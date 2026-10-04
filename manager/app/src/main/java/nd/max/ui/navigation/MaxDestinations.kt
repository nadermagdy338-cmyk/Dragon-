/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AppSettingsAlt
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Cable
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.DataUsage
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.DisplaySettings
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Gamepad
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.automirrored.rounded.Launch
import androidx.compose.material.icons.automirrored.rounded.ListAlt
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PictureInPicture
import androidx.compose.material.icons.rounded.Power
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.ui.graphics.vector.ImageVector
import nd.max.R

/**
 * Risk classification for destinations that can wedge or brick a device.
 * Consumed by Control and any advanced-tool gate that explains device risk.
 */
enum class MaxRisk { Normal, Advanced, Dangerous }

/**
 * The single source of truth for every route in the app (ADR-02).
 *
 * Route strings exist ONLY here. Screens navigate through [MaxNavActions]
 * and reference these objects; navigate("literal") anywhere outside this
 * package is a static-test violation.
 *
 * @param parent the destination this one is reached from. Drives the
 *        Control hub membership and back-stack expectations.
 */
sealed class MaxDestination(
    val route: String,
    @StringRes val titleRes: Int,
    val icon: ImageVector,
    val parent: MaxDestination? = null,
    val risk: MaxRisk = MaxRisk.Normal,
    val isPrimary: Boolean = false,
) {
    /**
     * المسار الذي يُفتح به هذا العنصر من قائمة، بلا نيّة سابقة عن تطبيق بعينه.
     *
     * يختلف عن [route] في وجهات الاستعلام الاختياري (`?pkg={pkg}`): نقلُ [route]
     * كما هو يُطابق الوجهة ويُمرّر النمط `"{pkg}"` قيمةً للمعامل، فيقرأ المستهلك
     * نصًّا **غير فارغ** فينجو من فلترة الفراغ ويُفتح على تفصيل حزمة لا وجود لها.
     * التفصيل في `LaunchRoutes.kt`.
     */
    val launchRoute: String get() = launchRouteOf(route)

    /**
     * `true` حين يلزم معامل في المسار نفسه (`app_settings/{pkg}`) لا في استعلامه،
     * فلا يصحّ بثه من قائمة — وفتحُه كذلك خطأ برمجي لا حالة مشروعة.
     */
    val needsLaunchArgument: Boolean get() = launchRouteNeedsArgument(route)

    // Primary destinations (bottom bar / navigation rail)
    //
    // الترتيب هنا هو ترتيب الشريط حرفيًّا (طلب المالك): الرئيسية ← التطبيقات ← التحكم ←
    // الإعدادات. و`MaxAi` خرج من الشريط إلى **بطاقة في أول بطاقة بالشاشة الرئيسية**:
    // سطح يُفتح عند كل فتح للتطبيق لا يحتاج مقعدًا دائمًا في الشريط، والمقعد صار للإعدادات
    // التي كانت تُفتح من سهم في الشريط العلوي بلا موضع ثابت في التنقل.
    data object Now : MaxDestination("now", R.string.max_nav_now, Icons.Rounded.Home, isPrimary = true)
    data object Apps : MaxDestination("apps", R.string.max_nav_apps, Icons.Rounded.Apps, isPrimary = true)
    data object Control : MaxDestination("control", R.string.max_nav_control, Icons.Rounded.Tune, isPrimary = true)
    data object MaxAi : MaxDestination("max_ai", R.string.max_nav_max_ai, Icons.Rounded.AutoAwesome)

    /**
     * The live command centre: the running loop drawn as it happens (vitals,
     * thermal forecast vs actual, prediction-error trend, knowledge state,
     * exploration gate, loop counters, knob ownership). Replaces the static
     * "engine state" section that used to sit inside [MaxAi].
     */
    data object MaxLive : MaxDestination("max_live", R.string.max_live_title, Icons.Rounded.Timeline, MaxAi)

    /**
     * `Device Info` — مقعد **قراءة** لعتاد الجهاز: المعالج والذاكرة والبطارية والشاشة
     * والحرارة والمستشعرات والشبكة، كلٌّ في قسمه، ومع كل قسم بابٌ إلى الشاشة التي **تملك**
     * موضوعه.
     *
     * **ولا أبَ له بقصد:** ليس تفضيلًا (فلا موضع له في الإعدادات) ولا تحكّمًا (فلا صفّ له في
     * محاور `Control`)؛ وأقسامه ليست أبناءه في الشجرة بل **أقسام داخلية** يختارها شريط
     * التبويبات، فلو صار أبوه `Control` لَظهر اسمه في قائمة المحاور مكرّرًا بلا معنى. ومدخله
     * من الرئيسية (بطاقة معلومات الجهاز) ومن أي شاشة تفتحه من سجلّ المسارات.
     *
     * **ومساره ذو معامل اختياري في الاستعلام** (`?section=<key>`) — بنفس عقد `MaxBackup`
     * و`Permissions` حرفيًّا: الدخول **المجرّد** (`device_info` بعد نزع الاستعلام) يفتح
     * «نظرة عامة»، والدخول من قسم بعينه (زرّ الشاشة التي تملكه) يفتحه مباشرةً. ووجهةٌ
     * واحدة بمعامل لا إحدى عشرة وجهة: التبويبات أقسامٌ داخلها لا مقاعد في الشجرة (ADR-02).
     */
    data object DeviceInfo : MaxDestination("device_info?section={section}", R.string.device_info, Icons.Rounded.Info)

    // Settings root: مقعد دائم في الشريط السفلي (طلب المالك)، وكان يُفتح من الشريط العلوي فقط.
    data object Settings : MaxDestination("settings", R.string.max_nav_settings, Icons.Rounded.Settings, isPrimary = true)

    // Onboarding
    data object GetStarted : MaxDestination("get_started", R.string.max_title_get_started, Icons.Rounded.Home)

    // Control domain hubs (ADR-04)
    data object CpuHub : MaxDestination("hub_cpu", R.string.max_hub_cpu, Icons.Rounded.Memory, Control)
    data object GpuHub : MaxDestination("hub_gpu", R.string.max_hub_gpu, Icons.Rounded.Speed, Control)
    data object MemoryHub : MaxDestination("hub_memory", R.string.max_hub_memory, Icons.Rounded.Storage, Control)
    data object DisplayHub : MaxDestination("hub_display", R.string.max_hub_display, Icons.Rounded.DisplaySettings, Control)
    data object ResponsivenessHub : MaxDestination("hub_responsiveness", R.string.max_hub_responsiveness, Icons.Rounded.TouchApp, Control)
    data object ThermalHub : MaxDestination("hub_thermal", R.string.max_hub_thermal, Icons.Rounded.Thermostat, Control)
    data object PowerHub : MaxDestination("hub_power", R.string.max_hub_power, Icons.Rounded.BatteryChargingFull, Control)
    data object StorageHub : MaxDestination("hub_storage", R.string.max_hub_storage, Icons.Rounded.Storage, Control)
    data object NetworkHub : MaxDestination("hub_network", R.string.max_hub_network, Icons.Rounded.NetworkCheck, Control)

    /**
     * **العقدة العاشرة (`AU-01`، تكملة ٢٢٦):** الصوت عالم **نظام** كالطاقة والعرض، ومكانه مع
     * جيرانه الذين يقرأون العتاد لا مع التطبيقات. والخريطة كلها في `SOUND-SCREEN-PLAN`.
     *
     * **وحدّها المُعلن من الآن:** العقدة تُقرأ الآن (`AU-02` الأجهزة و`AU-04` المؤثرات)، ولا
     * صفوف فيها بعد لأنّ استوديو الصوت (`AU-05`) يُسجَّل في مرحلته — فلا تُبنى واجهة فوق بيانات
     * لا وجود لها، وهذا هو ترتيب الإغلاق الملزم المكتوب في الخطة.
     */
    data object AudioHub : MaxDestination("hub_audio", R.string.max_hub_audio, Icons.AutoMirrored.Rounded.VolumeUp, Control)

    /**
     * سطح تحكّم الصوت (`AU-05`): مستويات الدفقات تُقرأ وتُكتب، والتشخيص يسمّي حدوده.
     *
     * **وسببُ وجودها وجهةً لا جسمَ الحوز:** جرد ما يُعلنه الجهاز انتقل إلى قسم الصوت في
     * «معلومات الجهاز» (أمر المالك: «انقل ما صنعته إلى `device info` لكي لا يضيع الجهد»)، والحوز
     * صار فهرسَ مجالٍ لا سطحًا. وهي **ابنةُ `AudioHub`** لا شاشةً معلّقة، فيسلّطها `maxHubRows`
     * تلقائيًّا (تُقرأ `parent` لا قائمة مكتوبة بيد) — ويلتقطها `ControlLayoutModelTest` و`ScreenFinder`.
     */
    data object AudioStudio : MaxDestination("audio_studio", R.string.max_audio_studio_title, Icons.Rounded.GraphicEq, AudioHub)

    // Feature screens: CPU domain
    data object CpuCoreControl : MaxDestination("cpucorecontrol", R.string.cpu_core_control_title, Icons.Rounded.DeveloperBoard, CpuHub)
    data object GovernorSettings : MaxDestination("governorsettings", R.string.gov_settings, Icons.Rounded.Tune, CpuHub)
    data object PreferenceTweaks : MaxDestination("preferenced", R.string.prefs, Icons.Rounded.Tune, CpuHub)

    // Feature screens: GPU domain
    data object GpuStudio : MaxDestination("gpustudio", R.string.max_title_gpu_studio, Icons.Rounded.Speed, GpuHub)

    // Feature screens: Memory domain
    data object ZramManager : MaxDestination("zrammanager", R.string.zram_title, Icons.Rounded.Storage, MemoryHub)

    // Feature screens: Display domain
    data object DisplayStudio : MaxDestination("displaystudio", R.string.display_studio_title, Icons.Rounded.DisplaySettings, DisplayHub)
    data object Resolution : MaxDestination("resolutionscreen", R.string.resolution_title, Icons.Rounded.AspectRatio, DisplayHub)

    // Feature screens: Responsiveness domain
    data object TouchBoost : MaxDestination("touchboost", R.string.touch_boost_title, Icons.Rounded.TouchApp, ResponsivenessHub)
    data object FpsGo : MaxDestination("fpsgoscreen", R.string.str_fpsgo_settings, Icons.Rounded.Speed, ResponsivenessHub)
    data object Fas : MaxDestination("FasScreen", R.string.str_frame_aware_scheduling, Icons.Rounded.Schedule, ResponsivenessHub)

    /**
     * **انتقلت إلى الأدوات بأمر المالك** («انقل شاشة الأداء من شاشة الاستجابة إلى الأدوات»).
     *
     * والحجّة التي نقضها الأمر كانت حجّة **تصنيف** لا حجّة وصول: هي "شاشة" (HUD) تُعرض فوق
     * التطبيقات الأخرى لا داخل مجالٍ يُضبط، وتُفتح لتُرى لا لتُشغّل سلسلة استجابة. فبقاؤها مع
     * `FpsGo`/`Fas` كان يجعلها تُقرأ على أنها "ضبط معدل الإطارات" — وهو `FpsGo` نفسه.
     *
     * والأثر المقيس على الشجرة أوسع من موضع واحد: الخروج من `ResponsivenessHub` أسقطها من
     * صفوف الـhub تلقائيًّا (`maxHubRows` تقرأ `parent` لا قائمة مكتوبة بيد)، فلم تحتج الشاشة
     * ولا نموذج التخطيط أي تعديل آخر سوى إدخالها في `ControlToolDestinations`.
     */
    data object FpsOverlay : MaxDestination("fpsoverlay", R.string.fps_overlay_title, Icons.Rounded.PictureInPicture, Control)

    // Feature screens: Thermal domain
    data object ThermalDetail : MaxDestination("thermal_detail", R.string.thermal_title, Icons.Rounded.Thermostat, ThermalHub)

    // Feature screens: Power domain
    /**
     * Battery and charging in one place.
     *
     * `BatteryDetail` used to be a second destination reading the same pack with
     * its own poll loop and no controls. It was folded in here rather than kept
     * beside: one screen per question, and this question ("what is the battery
     * doing, and what may I change about it?") is a single one.
     */
    data object Charging : MaxDestination("chargingscreen", R.string.charging_title, Icons.Rounded.BatteryChargingFull, PowerHub)
    data object BypassCharging : MaxDestination("bypasschg", R.string.bcharging, Icons.Rounded.Cable, PowerHub)
    data object BypassChargingCheck : MaxDestination("bypasschg_check", R.string.max_title_bypass_check, Icons.Rounded.Power, PowerHub)
    data object DozeMode : MaxDestination("dozemode", R.string.dozemode_title, Icons.Rounded.Bedtime, PowerHub)

    // Feature screens: Storage & compiler domain
    data object Dex2oat : MaxDestination("dex2oat", R.string.dex2oat_title, Icons.Rounded.Science, StorageHub)
    data object StorageDetail : MaxDestination("storage_detail", R.string.detail_storage, Icons.Rounded.DataUsage, StorageHub)

    // Feature screens: Network domain
    data object NetworkScheduler : MaxDestination("networkscheduler", R.string.net_sched_title, Icons.Rounded.NetworkCheck, NetworkHub)
    data object NetworkDetail : MaxDestination("network_detail", R.string.detail_network, Icons.Rounded.Wifi, NetworkHub)

    // Feature screens: Apps destination
    //
    // مراقب المهام انتقل إلى `Control → Tools` بقرار المالك: هو **أداة** تُفتح على الجهاز لا
    // شاشة تخصّ تطبيقًا بعينه، ومكان الأدوات في شاشة التحكّم لا في مسار التشخيص داخل الإعدادات.
    data object ProcessManager : MaxDestination("processmanager", R.string.processmgr_title, Icons.Rounded.Timeline, Control)
    data object DebloatFreeze : MaxDestination("debloatfreeze", R.string.debloat_freeze_title, Icons.Rounded.CleaningServices, Apps)
    data object AppSettings : MaxDestination("app_settings/{pkg}", R.string.max_title_app_settings, Icons.Rounded.AppSettingsAlt, Apps)

    // Settings children
    data object Diagnostics : MaxDestination("diagnostics", R.string.section_diagnostics, Icons.Rounded.BugReport, Settings)

    /**
     * **السمة عادت إلى الإعدادات بأمر المالك.** كانت نُقلت إلى `Control → Tools` بحجّة أن
     * الألوان أداة لا تفضيل؛ والقياس الذي نقضها: من يريد تغيير السمة يذهب إلى الإعدادات
     * فعلًا، فيجد لغتها وبصمتها هناك ولا يجد **مظهرها** — أي أن الحجّة كانت عن التصنيف لا
     * عن الوصول. وصارت الصفّ الأول فوق بطاقة اللغة، فالمظهر واللغة يجلسان معًا كما هما في
     * ذهن المستخدم: تفضيلان يخصّان الواجهة نفسها.
     *
     * ووحدة السجل بقيت في الأدوات: هي أداة تشخيص تُطلب من مكان واحد، لا تفضيلًا يُقلَّب.
     *
     * ونقل الأب إلى `Settings` يُبقي `ControlLayoutModelTest` صادقًا: كل وجهة أبوها `Control`
     * لها صفّ في الصفحة، وما خرج من الأب خرج من القائمة معه.
     */
    // والمخاطرة تبقى `Normal`: التصنيف يخصّ ما قد يُربك الجهاز، وهذه الثلاثة تقرأ وتُظهر
    // ولا تكتب عتادًا. رفعها إلى `Advanced` كان سيصنّفها في بوّابة المخاطرة بلا سبب.
    data object ColorPalette : MaxDestination("color_palette", R.string.theme, Icons.Rounded.Palette, Settings)
    data object ColorScheme : MaxDestination("colorscheme", R.string.color_scheme, Icons.Rounded.ColorLens, Control)
    data object Logs : MaxDestination("logsviewer", R.string.logsviewer_title, Icons.AutoMirrored.Rounded.ListAlt, Control)
    data object ConfigBackup : MaxDestination("config_backup", R.string.max_nav_config_backup, Icons.Rounded.Backup, Settings)

    /**
     * `GAP-14` — عقد الطرف الثالث: الإضافات المركّبة، وما قُبل منها وما رُفض ولماذا.
     * تحت الإعدادات لأنها **حالة النظام** لا تحكّم أداء، ولأن نصّ العقد يجب أن يكون
     * في متناول من يكتب إضافة، لا مخفيًّا في صفحة مطوّرين.
     */
    data object Plugins : MaxDestination("plugins", R.string.max_plugins_title, Icons.Rounded.Extension, Settings)

    /**
     * `Max Backup` — نسخ التطبيقات احتياطيًّا وفحص سلامتها واسترجاعها.
     *
     * `pkg` **اختياري**: بلا معرّف تفتح الشاشة على منتقي التطبيقات (فهي شاشة قائمة بذاتها)،
     * وبه تُفتح على تطبيق واحد — وهو المدخل الموجود في شاشة إعدادات كل تطبيق.
     */
    data object MaxBackup : MaxDestination("max_backup?pkg={pkg}", R.string.max_backup_title, Icons.Rounded.Backup, Control, MaxRisk.Advanced)

    /**
     * `GAP-07` — الصلاحيات و`AppOps`: ما يُعلنه البيان، وما تسمح به المنصّة فعلًا، ومرجع مدوَّن
     * تعود إليه. `pkg` **اختياري**: بلا معرّف تفتح الشاشة على منتقي التطبيقات (فهي شاشة رئيسية
     * مستقلة بذاتها)، وبه تُفتح على تطبيق واحد — وهو مدخل شاشة إعدادات كل تطبيق.
     */
    data object Permissions : MaxDestination("max_perms?pkg={pkg}", R.string.max_perms_title, Icons.Rounded.Shield, Control, MaxRisk.Advanced)
    data object About : MaxDestination("aboutscreen", R.string.section_about, Icons.Rounded.Info, Settings)
    data object Privilege : MaxDestination("privilege", R.string.max_privilege_title, Icons.Rounded.Shield, Settings)
    data object ModuleHealth : MaxDestination("module_health", R.string.max_module_title, Icons.Rounded.Build, Settings)

    /**
     * لوبي الألعاب بالعرضيّ — **سطح اللعب لا قائمة إعدادات**.
     *
     * كانت إلى جانبها شاشة مكتبة عمودية (`GameSpace`) أُزيلت بأمر المالك؛ فهذه الوجهة هي السطح
     * الوحيد للألعاب: لوحة عرضية تُفتح من قائمة `Apps`، وتقود إلى التشغيل وملف اللعبة واللوحة
     * الجانبية وإدارة المكتبة. **والوسم الافتراضي ([MaxRisk.Normal]) مقصود:** هذه الشاشة لا تكتب عتادًا
     * (ADR-11)، و[MaxRisk.Advanced] محفوظ لأدوات الكتابة المحجوبة — فلا تُحجب لوحةُ اختيار لعبة.
     */
    data object GameLobby : MaxDestination("gamelobby", R.string.game_lobby_title, Icons.Rounded.Gamepad, Apps)

    /**
     * **باب اللوبي في قائمة `Apps`** — كان سطحًا لا يُوصل إليه إلا من شاشة المكتبة وحدها.
     *
     * وليس هذا تنظيمًا شكليًّا: قائمة `Apps` تسرد كل وجهة أبوها `Apps`، ولو دخل اللوبي بها لظهر
     * صفًّا عاديًّا إلى جانب أدوات مثل `SetEdit`. واللوبي **سطح لعب** لا أداة، فيُستثنى من السرد
     * ويُوضع له **صفّ بارز في رأس القائمة** (`ApplistScreen.LobbyBanner`) — نفس مبدأ «الأبواب التي
     * تهمّ في الصفّ الأول» (`ApplistScreen.GameLobbyDoor`).
     */
    data object EmulatorHub : MaxDestination("emulatorhub", R.string.emu_hub_title, Icons.Rounded.Apps, Apps)
    data object HmaCompanion : MaxDestination("hma_companion", R.string.hma_companion_title, Icons.Rounded.Shield, Control)

    data object SpoofStudio : MaxDestination("spoofstudio", R.string.spoof_title, Icons.Rounded.AppSettingsAlt, Control)

    // Control - Advanced tools (gated, not preferences)
    data object SetEdit : MaxDestination("setedit", R.string.max_title_setedit, Icons.Rounded.Edit, Control, MaxRisk.Advanced)
    data object ActivityLauncher : MaxDestination("activitylauncher", R.string.max_title_activity_launcher, Icons.AutoMirrored.Rounded.Launch, Control, MaxRisk.Advanced)

    /**
     * `GAP-09` — مدير الملفات بالجذر. أداة متقدّمة لا تفضيل: كل عملية تكتب على القرص
     * خارج نطاق إعداداتنا، فمكانها تحت `Control → Tools` مع تعليم المخاطرة لا في قائمة
     * تفضيلات تُقلَّب بلا انتباه.
     */
    data object FileManager : MaxDestination("filemanager", R.string.max_files_title, Icons.Rounded.Folder, Control, MaxRisk.Advanced)

    companion object {
        /**
         * The four bottom-bar / nav-rail destinations (ADR-03).
         *
         * Lazy on purpose: these lists are built from the very
         * `data object`s that make up this sealed class. If whichever
         * destination the app touches *first* — anywhere, e.g. a cold
         * start reading [Control] for the nav bar — happened to force this
         * list eagerly (a plain `val`), the JVM would already be partway
         * through initializing that same destination's class when the list
         * tried to read it back, and class-init recursion rules hand back
         * the not-yet-assigned singleton field as null instead of waiting.
         * `by lazy` defers evaluation until something actually asks for the
         * list, by which point that recursive window has closed. See
         * ControlLayoutModelTest for the regression this once caused.
         */
        val PrimaryDestinations: List<MaxDestination> by lazy { listOf(Now, Apps, Control, Settings) }

        /** Every destination registered in [MaxNavGraph]. */
        val All: List<MaxDestination> by lazy {
            listOf(
                GetStarted, Now, Control, Apps, MaxAi, MaxLive, DeviceInfo, Settings,
                CpuHub, GpuHub, MemoryHub, DisplayHub, ResponsivenessHub, ThermalHub,
                PowerHub, StorageHub, NetworkHub, AudioHub,
                // وسطح التحكّم الصوتيّ داخل المجال العاشر — وبدونه لا صفَّ له في التخطيط
                // ولا يُسلّط له بابٌ من قسم «معلومات الجهاز» (`AS-01`).
                AudioStudio,
                CpuCoreControl, GovernorSettings, PreferenceTweaks, GpuStudio,
                ZramManager, DisplayStudio, Resolution, TouchBoost, FpsGo, Fas, FpsOverlay,
                ThermalDetail, Charging, BypassCharging, BypassChargingCheck, DozeMode,
                Dex2oat, StorageDetail, NetworkScheduler, NetworkDetail,
                ProcessManager, DebloatFreeze, AppSettings, GameLobby, EmulatorHub, HmaCompanion,
                ColorPalette, ColorScheme, Diagnostics, Logs, ConfigBackup, Plugins, MaxBackup, Permissions, About,
                Privilege, ModuleHealth,
                SetEdit, ActivityLauncher, FileManager, SpoofStudio,
            )
        }

        /** Route ids of the primary destinations, for bar visibility checks. */
        val PrimaryRoutes: Set<String> by lazy { PrimaryDestinations.map { it.route }.toSet() }
    }
}

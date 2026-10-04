/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * Device Info — **النموذج النقيّ** لشاشة معلومات الجهاز.
 *
 * **لماذا نموذج منفصل عن الشاشة.** «أي حقل يُعرض، ومن أي مصدر، وهل يُقال عنه غير
 * معروف» قرارٌ لا يُقاس داخل `@Composable`: الرسم لا يُترجم ولا يُنفَّذ إلا على جهاز.
 * فالقرار كله هنا — دوالّ نقيّة على بيانات بسيطة — ويُقاس في اختبار JVM على القيم
 * الحقيقية (`DeviceInfoModelTest`)، وتبقى الشاشة طبقة **رسم** لا تحمل قاعدة.
 *
 * **والقاعدة الواحدة التي تحكم الملف كله:** `null` تعني «لا قيمة»، و**لا يُكتب صفر
 * مكان قيمة لم تُقرأ**. وهذا نصّ طلب المالك: «عدم إظهار قيم غير متاحة باعتبارها صفرًا»
 * و«عرض Unsupported أو Unavailable عندما يتعذر الحصول على المعلومة». ولذلك كل حقل
 * في [DeviceInfoSnapshot] اختياريّ، و[DeviceInfoFact.value] الاختياريّ هو ما يُترجَم في
 * الشاشة إلى `MaxDataTrust` المعروض («غير مقروء» لا «٠»).
 *
 * **وحدّ مُعلن:** هذا الملف لا يقرأ شيئًا. القراءة (`Build` · `sysfs` · `SensorManager`)
 * في الشاشة ومن المصادر القائمة (`DashboardState` و`CpuHardwareBackend` و`SensorInventory`)
 * — لا قارئ جديد، وهو شرط المالك: «لا تنشئ بنية موازية لما هو موجود بالفعل».
 */
package nd.max.ui.subscreens

import androidx.annotation.StringRes
import java.util.Locale
import nd.max.R
import nd.max.core.audio.AudioInventorySnapshot
import nd.max.core.platform.SensorInventory

/**
 * أقسام الشاشة — وهي **نفس أقسام شاشات التحكّم** حتى يجد المستخدم الفكرة في الموضع
 * نفسه في الشاشتين: من قرأ «المعالج» في التحكّم يقرؤه هنا في المقعد ذاته.
 */
enum class DeviceInfoSection(@StringRes val titleRes: Int, val wireKey: String) {
    Overview(R.string.devinfo_sec_overview, "overview"),
    Cpu(R.string.devinfo_sec_cpu, "cpu"),
    Gpu(R.string.devinfo_sec_gpu, "gpu"),
    Memory(R.string.devinfo_sec_memory, "memory"),
    Storage(R.string.devinfo_sec_storage, "storage"),
    Battery(R.string.devinfo_sec_battery, "battery"),
    Display(R.string.devinfo_sec_display, "display"),
    Thermal(R.string.devinfo_sec_thermal, "thermal"),
    Sensors(R.string.devinfo_sec_sensors, "sensors"),
    System(R.string.devinfo_sec_system, "system"),
    Network(R.string.devinfo_sec_network, "network"),
    // والكاميرا **قسمٌ بلا شاشة تحكّم مالكة** — وهو استثناء مُعلَن لا صدفة: المرجعان
    // اللذان أُرسلت لقطاتهما يحملان لقطات كاميرا كثيرة، وسؤال المالك نصًّا «معلومات أكثر
    // من الصور في كل قسم»، فصار القسم هنا وبُيته `Diagnostics` (بيت الجرد والتقارير).
    Camera(R.string.devinfo_sec_camera, "camera"),
    // والصوت (`AS-01`): ما تُعلنه المنصّة عن المخرج والأجهزة والمؤثرات — **نقلٌ من حوز الصوت**
    // بأمر المالك («انقل ما صنعته إلى `device info` لكي لا يضيع الجهد»)، وبُيته سطح التحكّم
    // `AudioStudio` الذي يملك صفّه الحوز — فالقسم له شاشة تحكّم مالكة، لا استثناء جديدًا.
    Audio(R.string.devinfo_sec_audio, "audio"),
}

/**
 * مفتاح القسم ← القسم. و**المجهول يسقط إلى «نظرة عامة»** لا إلى «لا شيء»: مسار يُفتح
 * بلا قسم (أو بمفتاح من نسخة أخرى) يجب أن يُنتج شاشة، لا شاشة فارغة — وهذا هو الفرق
 * بين غياب معلن (نظرة عامة) وشاشة تنتظر معاملًا لا يأتي.
 *
 * والدالّة صافية (نصّ ← قسم) فتُقاس على JVM: كل مفتاح في [DeviceInfoSection] يعدّ
 * قسمه، و`null` و`""` و`"bogus"` تعدّ `Overview`.
 */
fun deviceInfoSectionOf(wireKey: String?): DeviceInfoSection =
    DeviceInfoSection.entries.firstOrNull { it.wireKey == wireKey } ?: DeviceInfoSection.Overview

/**
 * صفحة التقليب التي يفتحها مفتاح المسار (`DI-01`).
 *
 * **وهي ترتيب القسم نفسه**: الصفحات تُبنى من [DeviceInfoSection] بالترتيب المعلن
 * (`deviceInfoSections` تعدّ الأقسام كلها بالترتيب)، فالرقم ورقمُ التعداد شيء واحد — ودالّة
 * باسمها تُفصح عن الاعتماد ولا تُخفي `ordinal` في وسيط `initialPage`.
 *
 * والمجهول يسقط إلى «نظرة عامة» كما في [deviceInfoSectionOf]، فلا تُفتح صفحة لا وجود لها.
 */
fun deviceInfoPageOf(wireKey: String?): Int = deviceInfoSectionOf(wireKey).ordinal

/**
 * مستوى الثقة — **أربعة لا خامسة**، تناظر `MaxDataTrust` في نظام التصميم واحدًا بواحد:
 *
 * * [Live] — قُرِئ الآن ومصدره حيّ.
 * * [Snapshot] — قيمة إقلاع لا تتغيّر (مساحة، بناء، شهادة أمان): صحيحة لكنها ليست «قياسًا».
 * * [Unreadable] — العتاد موجود ولم تُقرأ عقدته.
 * * [Unsupported] — **الجهاز لا يملك هذه الخاصية أصلًا**؛ وهذا ليس عطبًا في التطبيق
 *   ولا في الجهاز، ولا يجوز عرضه كعطب (وهو نصّ الطلب: «عدم الادعاء بوجود دعم لخاصية
 *   غير موجودة»).
 */
enum class DeviceInfoTrust { Live, Snapshot, Unreadable, Unsupported }

/**
 * حقل واحد معروض.
 *
 * @param value **مُهيّأ للعرض وبلا وحدة**، أو `null` حين لم تُقرأ القيمة. ولا يُمرَّر "0"
 *        بمعنى «مجهول» — الصفر قراءة، والمجهول غياب.
 * @param source من أين قُرِئت (`sysfs` أو `Build` أو `dumpsys`) — يُطبع مع الحقل فيُسمّى
 *        الدليل لا يُدّعى (ADR-07).
 * @param noteRes سبب الغياب حين يكون معلومًا، وإلا `null`.
 */
data class DeviceInfoFact(
    @StringRes val label: Int,
    val value: String?,
    val unit: String? = null,
    val trust: DeviceInfoTrust = DeviceInfoTrust.Live,
    val source: String? = null,
    @StringRes val noteRes: Int? = null,
    /**
     * القيمة **كموارد نصّية** حين تكون مترجَمة («مدعوم» · «USB» · «جيدة»): النموذج لا يعرف
     * لغة، فالترجمة تبقى في الموارد وتُقرأ في الشاشة (`asMetric` يقدّمها على `value`).
     * والقيمتان لا تجتمعان: مُمرَّرةٌ واحدةٌ منهما فقط، والمختارة هي الحقيقة لا البديل.
     */
    @StringRes val valueRes: Int? = null,
)

/** قسم معروض: عنوانه وحقوله المعلنة. والقائمة **قد تكون كلها بلا قيم** — وهذا معلَن لا مخفيّ. */
data class DeviceInfoSectionModel(
    val section: DeviceInfoSection,
    val facts: List<DeviceInfoFact>,
    /** صفوف بعنوانها **من الجهاز** (اسم مستشعر أو منطقة حرارية) لا من مورد. */
    val rows: List<DeviceInfoRow> = emptyList(),
    /** بطاقات إضافية داخل القسم نفسه (مناطق · نقاط تخفيف · أجهزة تبريد). */
    val cards: List<DeviceInfoCard> = emptyList(),
    /** صفوف خصائص العتاد (شبكة) — ومصيرُها وسمٌ لا قيمة. */
    val chips: List<DeviceInfoChipRow> = emptyList(),
    /** رأس القسم: المقياس الدائريّ وبلاطات الإحصاء — قرارٌ مبنيّ في `heroOf` ومقيس. */
    val hero: DeviceInfoHero = DeviceInfoHero(),
)

/**
 * صفّ معروض: عنوانه نصّ **من الجهاز** وتحته سطر تفاصيل مُهيّأ. ولماذا نوعٌ ثانٍ غير
 * `DeviceInfoFact`: عنوان ذاك موردٌ مترجَم، وأمّا اسم مستشعر أو تسمية منطقة فهي من العتاد فلا
 * تُترجَم. و`detail = null` تعني «لا قراءة» ولا تُكمَّل بصفر.
 */
data class DeviceInfoRow(val title: String, val detail: String? = null)

/** بطاقة داخل قسم: عنوانها مورد، ومحتواها حقول وصفوف. */
data class DeviceInfoCard(
    @StringRes val titleRes: Int,
    val facts: List<DeviceInfoFact> = emptyList(),
    val rows: List<DeviceInfoRow> = emptyList(),
)

// ── رأس القسم (الجولة الجديدة) ──────────────────────────────────────────
// لماذا نوعٌ للرأس: «ما يُبرز» قرارٌ عرضيّ مبنيّ من اللقطة، فلو بُني في `@Composable`
// لصار غير قابل للقياس — والقاعدة واحدة: لا قيمة بلا قراءة، ولا جزء بلا مصدر.

/**
 * حالة خاصية عتادية — **ثلاث لا اثنتان**: المنصّة أعلنتها، أو لم تُعلنها، أو لم تُسأل.
 * والفرق بين الثانية والثالثة فرقٌ بين «جهازك لا يملكها» و«لم نعرف» — وهذا هو ADR-07
 * في اتجاه الغياب (لا يُدَّعى غيابٌ لم يُقَس).
 */
enum class CapabilityState { Supported, NotDeclared, Unreadable }

/** صفّ خاصية: عنوانه موردٌ مترجَم، وحالته **تصريح المنصّة** لا انطباعٌ عن عمل الجهاز. */
data class DeviceInfoChipRow(
    @StringRes val labelRes: Int,
    val state: CapabilityState,
)

/**
 * مقياس دائريّ في رأس القسم — **ولا يُبنى إلا من كسرٍ حقيقيّ** (نسبة أو تردّد من سقفه)،
 * فلا يُرسم قوسٌ على تخمين: غياب القراءة يعني غياب المقياس لا قوسًا فارغًا.
 */
data class DeviceInfoGauge(
    @StringRes val titleRes: Int,
    /** الرقم كما سيُقرأ وسط القوس — مُهيّأ و**بلا وحدة**. */
    val valueText: String,
    val unitText: String,
    val fraction: Float,
    val subtitle: String? = null,
    val live: Boolean = false,
)

/**
 * بلاطة إحصاء في الرأس: تسمية مورد وقيمة مُهيّأة — و`null` معها حالةُ ثقة لا صفر.
 * و[valueRes] نظيره في `DeviceInfoFact`: القيمة المترجَمة («مُفعّل») حين لا تكون رقماً.
 */
data class DeviceInfoTile(
    @StringRes val captionRes: Int,
    val value: String?,
    val unit: String? = null,
    val trust: DeviceInfoTrust = DeviceInfoTrust.Live,
    @StringRes val valueRes: Int? = null,
)

/** رأس القسم: هويّة (في النظرة العامة وحدها) ثم مقياسٌ ثم بلاطات. */
data class DeviceInfoHero(
    val title: String? = null,
    val subtitle: String? = null,
    val gauges: List<DeviceInfoGauge> = emptyList(),
    val tiles: List<DeviceInfoTile> = emptyList(),
)

// ── الكاميرا ────────────────────────────────────────────────────────────

/** جهة الكاميرا كما تُعلنه `CameraCharacteristics.LENS_FACING` — بلا نوع Android هنا. */
enum class CameraFacing { Front, Back, External }

/**
 * كاميرا كما ستُعرض. **وكل حقلٍ اختياريّ**: عدسة قد تُعلن فتحة ولا تُعلن طولًا بؤريًّا،
 * ومستشعرٌ قد لا يُعلن ترتيب مرشّحه — فلا تُكمَّل البطاقة بما لم يُقرأ.
 *
 * والتحويل من `CameraCharacteristics` إلى هذه القيم يقع في طبقة الجمع (`DeviceInfoCollect`)
 * — فالعرض يُقاس على JVM بلا كاميرا ولا جهاز.
 */
data class DeviceCameraInfo(
    val facing: CameraFacing,
    /** المستوى العتادي كما تُسمّيه المنصّة (`LEVEL_3` · `FULL`) — رمزٌ لا يُترجَم. */
    val hardwareLevel: String? = null,
    val apertures: List<Float> = emptyList(),
    val focalLengthsMm: List<Float> = emptyList(),
    val sensorSizeMm: Pair<Float, Float>? = null,
    val pixelArray: Pair<Int, Int>? = null,
    val sensorOrientation: Int? = null,
    val maxDigitalZoom: Float? = null,
    val flashAvailable: Boolean? = null,
    /** أقصى دقة JPEG وأعداد الدقات المعلنة — فالقائمة كاملة سطرٌ لا يُقرأ في صفٍّ واحد. */
    val jpegMax: Pair<Int, Int>? = null,
    val jpegCount: Int? = null,
    /** قدرات المنصّة (`BURST_CAPTURE` · `LOGICAL_MULTI_CAMERA`…) — رموزٌ لاتينية كما أعلنت. */
    val capabilities: List<String> = emptyList(),
    /** ترتيب مرشّح الألوان كما يُعلنه العتاد (رقمه) — واسمه يُشتق في الصياغة الصافية. */
    val colorFilterArrangement: Int? = null,
)

/** قسم تخزين كما يُعرض: مساره (`/data`) ومقاساه — والمعدوم منها `null` لا صفر. */
data class DeviceInfoPartitionRow(
    val path: String,
    val usedGb: Float?,
    val totalGb: Float?,
)

/**
 * منطقة حرارية كما ستُعرض (تُحوَّل في الشاشة فلا يعرف النموذج `ThermalUtil`). و`celsius = null` أو
 * صفر = **لا قراءة**، و`enabled = false` = **مطفأة**: حالة ثالثة غير «لا قراءة» وغير «قُرئت».
 */
data class ThermalZoneRow(
    val label: String,
    val category: String,
    val celsius: Int?,
    val enabled: Boolean = true,
    val trips: List<ThermalTripRow> = emptyList(),
)

/** نقطة تخفيف أو إغلاق لمنطقة: الحرارة وطبيعتها كما تعلنها النواة. */
data class ThermalTripRow(val celsius: Int, val kind: String)

/** جهاز تبريد وحالته الحالية من أقصاها (كما تُعلنهما عُقده). */
data class CoolingRow(val label: String, val current: Int?, val max: Int?)

/**
 * جهاز تبريد ← صفّ. **القاعدة:** `cur_state = -1` = «لا يُعرف الحال»، و`max_state = 0` = «لم يُعلن
 * سقف» — وكلتاهما «لا قيمة» لا صفرًا (`ADR-07`)؛ وكانتا في الشاشة بلا مقياس فصارتا دالّة صافية.
 */
fun coolingRow(label: String, currentState: Int, maxState: Int): CoolingRow = CoolingRow(
    label = label,
    current = currentState.takeIf { it >= 0 },
    max = maxState.takeIf { it > 0 },
)

/** حالة جهاز التبريد كما تُعرض: `3/10` أو `null` (فيُكتب `—`). وتُعيد التصفية على `Int?` لأن
 * الصفّ نوع مفتوح، فصفٌّ بُني بيدٍ بـ`-1/0` كان يُعرض «-1/0» — قراءة مختلقة من قيمة تعني «لا حال». */
fun coolingStateOf(current: Int?, max: Int?): String? {
    val live = current?.takeIf { it >= 0 } ?: return null
    val ceiling = max?.takeIf { it > 0 } ?: return null
    return "$live/$ceiling"
}

/**
 * لقطة الجهاز — **كل حقل اختياريّ** و`null` هي «لم تُقرأ»؛ بلا أنواع Android فيها، فيبقى النموذج
 * قابلًا للقياس على JVM بلا جهاز.
 */
data class DeviceInfoSnapshot(
    // ── الهوية ──
    val deviceName: String? = null,
    val manufacturer: String? = null,
    val chipset: String? = null,
    val chipsetVendor: String? = null,
    val android: String? = null,
    val sdk: Int? = null,
    val kernel: String? = null,
    val abis: String? = null,
    val fingerprint: String? = null,
    val securityPatch: String? = null,
    val selinux: String? = null,
    val appVersion: String? = null,
    // ── المعالج ──
    val cpuCores: Int? = null,
    /** الأنوية **المتصلة** الآن — وهي غير العدد الكلي: نواة قابلة للقراءة وقد تكون مُطفأة. */
    val cpuCoresOnline: Int? = null,
    /** سطر لكل نواة (المُعرّف · الحالة · الحالي/الأقصى · العنقود · الحاكم) — مقروءة فقط. */
    val cpuCoreLines: List<String> = emptyList(),
    val cpuClusters: List<String> = emptyList(),
    val cpuLoadPercent: Int? = null,
    val cpuFreqMhz: Int? = null,
    val cpuCeilingMhz: Int? = null,
    // ── الرسوم ──
    val gpuLoadPercent: Int? = null,
    val gpuFreqMhz: Int? = null,
    val gpuCeilingMhz: Int? = null,
    val gpuMaxSupportedMhz: Int? = null,
    // ── الذاكرة ──
    val ramUsedMb: Int? = null,
    val ramTotalMb: Int? = null,
    val swapUsedMb: Int? = null,
    val swapTotalMb: Int? = null,
    // ── التخزين ──
    val storageUsedGb: Float? = null,
    val storageTotalGb: Float? = null,
    // ── البطارية ──
    val batteryPercent: Int? = null,
    val batteryStatus: String? = null,
    val batteryTempC: Float? = null,
    val batteryVoltageV: Float? = null,
    val batteryCurrentMa: Int? = null,
    val powerWatt: Float? = null,
    // ── الشاشة ──
    val displayWidth: Int? = null,
    val displayHeight: Int? = null,
    val displayDensityDpi: Int? = null,
    val displayRefreshHz: Int? = null,
    // ── الحرارة ──
    val cpuTempC: Int? = null,
    val gpuTempC: Int? = null,
    val skinTempC: Int? = null,
    val thermalZoneCount: Int? = null,
    /** المناطق بأسمائها وحالتها — فالخريطة الحرارية كانت رقمًا واحدًا لا خريطة. */
    val thermalZones: List<ThermalZoneRow> = emptyList(),
    /** كلمة «مطفأة» بلغة الواجهة لحالة منطقة أعلنتها النواة ولا تقرأ. */
    val thermalOffLabel: String? = null,
    val coolingDevices: List<CoolingRow> = emptyList(),
    // ── المستشعرات ──
    val sensorCount: Int? = null,
    val sensorWakeUpCount: Int? = null,
    val sensorKindCount: Int? = null,
    /** الجرد كما أعلنته المنصّة — يُعرض **صفًّا لكل مستشعر**، لا عدًّا فقط. */
    val sensorItems: List<SensorInventory.Item> = emptyList(),
    /** الأصناف الحاضرة فعلًا — وذكرها هو ما يجعل الغائب ظاهرًا بلا ادّعاء. */
    val sensorKinds: List<SensorInventory.Kind> = emptyList(),
    /** أسماء الأصناف بلغة الواجهة — تُبنى في الشاشة، فالنموذج لا يعرف `R`. */
    val sensorKindLabels: Map<SensorInventory.Kind, String> = emptyMap(),
    /** وسم الإيقاظ بلغة الواجهة، يُلحق بصفّ المستشعر المُوقظ. */
    val sensorWakeUpLabel: String? = null,
    /** قراءة الضوء وحالتها الثلاثية (`REPORTED` · `ABSENT` · `UNREADABLE`). */
    val sensorLight: SensorInventory.LightReading? = null,
    // ── الشبكة ──
    val downloadKbps: Long? = null,
    val uploadKbps: Long? = null,
    // ── المعالج: البنية التي تُقرأ من النواة نفسها (`uname` و`/proc/cpuinfo` و`cpu0/cache`) ──
    val cpuArch: String? = null,
    val cpuMinMhz: Int? = null,
    val cpuCache: String? = null,
    val cpuFeatures: String? = null,
    // ── الرسوم: واجهة الرسم المعلَنة (OpenGL ES) — تُقرأ من المنصّة لا من تخمين الاسم ──
    val gpuGles: String? = null,
    // ── البطارية: ما تُعلنه عُقد الشحن، و«غير معلَن» هنا كثير فلا يُخترع ──
    val batteryHealthPercent: Int? = null,
    val batteryCycleCount: Int? = null,
    val batteryTechnology: String? = null,
    // ── الشاشة: الأنماط التي تُعلنها المنصّة بالهرتز ──
    val displaySupportedHz: List<Int> = emptyList(),
    // ══ حقول الجولة الجديدة (تكملة ٢٢٥) — كلها اختيارية و`null` = لم تُقرأ ════════
    // ── الهوية والبناء ──
    val brand: String? = null,
    val model: String? = null,
    val deviceCodename: String? = null,
    val board: String? = null,
    val hardware: String? = null,
    val bootloader: String? = null,
    val baseband: String? = null,
    val buildId: String? = null,
    val buildIncremental: String? = null,
    val buildType: String? = null,
    val buildMillis: Long? = null,
    val androidCodename: String? = null,
    /** عمر التشغيل بالثواني — والصفر ليس عمرًا فيُترجَم إلى `null` في الصياغة. */
    val uptimeSeconds: Long? = null,
    /** لحظة الإقلاع (ملّي ثانية) — تُحسب من الساعة مقابل مهلة التشغيل. */
    val bootMillis: Long? = null,
    val timeZoneId: String? = null,
    // ── النظام الموسّع ──
    val localeName: String? = null,
    /** الآلة الافتراضية كما تُعلنها الخصائص (`2.1.0 · Dalvik`) — نصٌّ واحد لا حقلان. */
    val vmLabel: String? = null,
    val webViewVersion: String? = null,
    val playServicesVersion: String? = null,
    /** ثلاث حالات العتاد: مدعوم، غير مدعوم، لم يُقرأ (`null`). */
    val treble: Boolean? = null,
    val seamlessUpdates: Boolean? = null,
    val dynamicPartitions: Boolean? = null,
    // ── DRM ──
    val drmSecurityLevel: String? = null,
    val drmVendor: String? = null,
    val drmVersion: String? = null,
    val drmAlgorithms: String? = null,
    val drmHdcp: String? = null,
    /** «لا مخرج رقميّ» حالةٌ معلنة غير المستوى (`-1` في `MediaDrm`) فلها سطرُها. */
    val drmNoDigitalOutput: Boolean = false,
    // ── المعالج: تفاصيل `/proc/cpuinfo` التي لا تُقرأ من غيره ──
    val cpuImplementer: String? = null,
    val cpuPart: String? = null,
    val cpuRevision: String? = null,
    // ── الرسوم: الاسم الحقيقيّ المُعلن لا المُستنتج من الشريحة ──
    val gpuRenderer: String? = null,
    val gpuRendererVendor: String? = null,
    val gpuDriver: String? = null,
    val vulkanVersion: String? = null,
    val vulkanLevel: String? = null,
    // ── الذاكرة الموسّع ──
    val ramCachedMb: Int? = null,
    val ramBuffersMb: Int? = null,
    val javaHeapMaxMb: Int? = null,
    // ── التخزين: أقسامه المُعلنة ──
    val storagePartitions: List<DeviceInfoPartitionRow> = emptyList(),
    // ── البطارية الموسّع: الرموز كما تُعلنها المنصّة وتُترجم في الصياغة ──
    val batteryHealthCode: Int? = null,
    val batteryPluggedCode: Int? = null,
    val batteryDesignCapacityMah: Int? = null,
    val batteryChargeCounterMah: Int? = null,
    // ── الشاشة الموسّع ──
    /** الشقّ العلويّ بالبكسل — وصفره **قراءة** (لا شقّ) والغياب وحده عدم قراءة. */
    val displayCutoutTopPx: Int? = null,
    val displayHdrTypes: List<Int> = emptyList(),
    val displayWideGamut: Boolean? = null,
    val displayBrightnessPercent: Int? = null,
    val displayAutoBrightness: Boolean? = null,
    /** مهلة الإطفاء بالدقائق — **كسريّ** لأن أجهزة تُعلن ٣٠ ثانية، والصفر ليس مهلة. */
    val displayTimeoutMinutes: Float? = null,
    val fontScale: Float? = null,
    val orientationCode: Int? = null,
    // ── الشبكة: حالة التطوير والقدرات المُعلنة ──
    val adbEnabled: Boolean? = null,
    val developerOptions: Boolean? = null,
    val capabilities: List<DeviceInfoChipRow> = emptyList(),
    // ── الكاميرا ──
    val cameras: List<DeviceCameraInfo> = emptyList(),

    // ── الصوت (`AS-01`) ──
    /**
     * لقطة الجرد كما قرأها `AudioInventory` **نفسه** — لا قارئ ثانٍ ولا نسخة ثانية من الحقيقة.
     * و`null` غياب قراءة لا «لا صوت»: الجرد لم يُقرأ، وهذا ما يُقال في الحقل نفسه.
     */
    val audio: AudioInventorySnapshot? = null,
)

/**
 * صفر العدّ ليس قيمة: هذه هي البوّابة الوحيدة بين قراءة العتاد (`Int` صفريّ حين يعجز)
 * وبين `null` الصريحة التي تعني «غير مقروء».
 *
 * **ولا تُطبَّق على كل حقل:** صفر البطارية ونسبة حمل المعالج **قراءتان حقيقيتان**،
 * فيُمرَّران كما هما. ولذلك البوّابة دالّة تُنادى عند الحاجة لا قاعدة تُطبَّق على اللقطة.
 */
fun countable(value: Int): Int? = value.takeIf { it > 0 }

/** نظيرها للعشرية: الصفر و«سالب صفر» ليسا قياسًا. */
fun countable(value: Float): Float? = value.takeIf { it > 0f }

/**
 * أقسام الشاشة مبنيّة من اللقطة — **بالترتيب المعلن في [DeviceInfoSection] لا بترتيب
 * الحقول**، فلا يتغيّر ترتيب الأقسام بتغيّر ترتيب البناء.
 */
fun deviceInfoSections(snapshot: DeviceInfoSnapshot): List<DeviceInfoSectionModel> =
    DeviceInfoSection.entries.map { section ->
        DeviceInfoSectionModel(
            section = section,
            facts = factsOf(section, snapshot),
            rows = rowsOf(section, snapshot),
            cards = cardsOf(section, snapshot),
            chips = chipsOf(section, snapshot),
            hero = heroOf(section, snapshot),
        )
    }

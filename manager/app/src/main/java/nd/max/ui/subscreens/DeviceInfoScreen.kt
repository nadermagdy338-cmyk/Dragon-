/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * Device Info — **شاشة معلومات الجهاز**.
 *
 * الغاية: أن يجد المستخدم ما يُعلنه جهازه في مقعد واحد، بأقسام متجاورة يُتنقّل بينها
 * أفقيًّا (`MaxTabStrip` — شريط التطبيق واحد، فلا يظهر هنا شريط بمقاس مختلف).
 *
 * **ولا بابَ في آخر قسم (بأمر المالك، تكملة ١٥١):** كانت الأقسام الأحد عشر تُغلق كلّها بصفّ
 * تنقّل إلى شاشة الموضوع (`MaxNavigationRow` ← `maxDeviceInfoSource`)، والأمر: «أزِل
 * الاختصارات التي في شاشة Device Info التي تكون غالبًا في آخر كل قسم». فأُزيلت، وصار المدخل
 * من الجهة التي **يُعمل** فيها لا من جهة القراءة: سطر `More info` في الشاشة المالكة للموضوع
 * نفسها (`MaxDeviceInfoShortcut` ← `deviceInfoShortcutSection`) — تسعة مواضع مقيسة في
 * `DeviceInfoShortcutOrdersTest`. وصفحات المحور التسع **لا** ترسم بابًا (الجولة ٢٠١): الصفحة
 * نفسها فهرس أبواب، والباب مقصده الشاشة التي تعمل على الموضوع.
 *
 * **وقاعدة هذه الشاشة أنها لا تحمل قاعدة.** كل قرار (أي حقل، وبأي وحدة، ومتى يُقال «غير
 * مقروء») في `DeviceInfoModel` — مقيس في اختبار JVM. وهنا **الرسم والقراءة فقط**، والقراءة
 * من المصادر القائمة نفسها: `DashboardState` (‏دورة الرئيسية) و`CpuHardwareBackend` و
 * `SensorMonitorUtil` و`ThermalUtil` و`ChipsetIdentity`. فلا قارئ جديد ولا دورة قراءة
 * ثانية، وهو شرط المالك: «لا تنشئ بنية موازية لما هو موجود».
 *
 * **ولا صفّ واحد هنا يُبنى بيد:** القسم يُبنى من `deviceInfoSections`، فيُضاف قسم جديد في
 * النموذج وحده ويُقرأ في الشاشة بلا لمس هذه الطبقة.
 */
package nd.max.ui.subscreens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nd.max.R
import nd.max.core.platform.SensorInventory
import nd.max.core.platform.SensorMonitorUtil
import nd.max.core.platform.ThermalUtil
import nd.max.ui.component.sensorKindText
import nd.max.ui.design.MAX_VALUE_UNAVAILABLE
import nd.max.ui.design.MaxDataTrust
import nd.max.ui.design.MaxGroup
import nd.max.ui.design.MaxGroupDivider
import nd.max.ui.design.MaxRow
import nd.max.ui.design.MaxHelpAction
import nd.max.ui.design.MaxListScreen
import nd.max.ui.design.MaxMetric
import nd.max.ui.design.MaxMetricLine
import nd.max.ui.design.MaxSection
import nd.max.ui.design.MaxSpace
import nd.max.ui.design.MaxTab
import nd.max.ui.design.MaxTabStrip
import nd.max.ui.mainscreens.SectionLoadingIndicator
import nd.max.ui.navigation.MaxDestination
import nd.max.ui.navigation.MaxNavActions
import nd.max.ui.viewmodel.HomeDashboardViewModel

@Composable
fun DeviceInfoScreen(
    navController: NavHostController,
    /**
     * مفتاح القسم الذي **دخل منه** المستخدم، أو `null` حين دخل دخولًا مجرّدًا (من الرئيسية
     * أو من قائمة): فيقع على «نظرة عامة».
     *
     * وهو **بذرة التبويب لا حالته**: يُقرأ مرّة عند أوّل تركيب فيُختار قسمه، ثم يملك المستخدم
     * الاختيار — **ويحفظه التقليب نفسه**: `rememberPagerState` يُنشئ `PagerState` بمُحفظٍ داخليّ
     * (فلا حاجة لـ`rememberSaveable` ثانٍ فوقه)، فتعود الصفحة التي اختارها المستخدم بعد إعادة
     * التركيب لا بذرتُها. ولذلك لا يُعاد فتح القسم المطلوب لو عاد المسار نفسه إلى التركيب —
     * وهذا هو السلوك المقصود: الضغط يعني «خُذني إلى هناك الآن» لا «ثبّتني عليه للأبد».
     */
    sectionKey: String? = null,
    dashboardViewModel: HomeDashboardViewModel = viewModel(),
) {
    val context = LocalContext.current
    val actions = remember(navController) { MaxNavActions(navController) }
    val dashboard by dashboardViewModel.dashboardState.collectAsStateWithLifecycle()
    // **التقليب `DI-01`: حالة واحدة لا اثنتان.** `PagerState` هو المصدر، والشريط يقرأه ولا
    // يملك نسخة منه — فلم يبقَ ممكنًا أن يتخلّف التبويب عن الصفحة (أو العكس)، وهو ما كان يقع
    // حتمًا لو بقي رقم التبويب حالةً ثانية.
    //
    // والبذرة من مفتاح المسار: قسمٌ معلوم يفتح نفسه، ومجهول (أو غائب) يسقط إلى النظرة العامة
    // (`deviceInfoPageOf` ← `deviceInfoSectionOf` — صافيتان ومقيستان على JVM) فلا تُفتح شاشة
    // على تبويب لا وجود له.
    //
    // و**عدد الصفحات ثابت** (‏[DeviceInfoSection] كلها · ١١) لا `sections.size`: فالأقسام
    // معلنة قبل قراءتها، وربط العدّ بما نُجح في قراءته كان يجعل التقليب يعمل بعد وصول
    // البيانات فقط — والصفحة التي لم تُقرأ بعد تُعرض لها حالة تحميل.
    val pagerState = rememberPagerState(initialPage = deviceInfoPageOf(sectionKey)) {
        DeviceInfoSection.entries.size
    }
    val scope = rememberCoroutineScope()

    // ودورة القراءة **حاضرةً فقط**: تبدأ مع ظهور الشاشة وتقف بخروجها، فلا تُقاس
    // حرارة وتُقرأ عقد كلّ ثانيتين من شاشة نُظر إليها مرّة (نصّ الطلب: «إيقاف أو تخفيض
    // التحديثات عند خروج الشاشة من الواجهة»).
    LifecycleStartEffect(dashboardViewModel) {
        dashboardViewModel.setPollingActive(true)
        onStopOrDispose { dashboardViewModel.setPollingActive(false) }
    }

    // ما لا يتغيّر في عمر الشاشة يُقرأ مرّة واحدة: هويّة البناء، وعناقيد التردّد، وقائمة
    // المناطق الحرارية — **وعلى خيط خلفيّ لا داخل التركيب**.
    //
    // **ولماذا (عطب سرعة مُبلَّغ عنه: «فتح الشاشات يأخذ ١٥ ثانية إلى دقيقة»):** كانت الثلاثة
    // داخل `remember { … }`، و`remember` يُنفَّذ **خلال التركيب على خيط الواجهة** — فتحصُل
    // `deviceInfoStatics` (‏`uname` · `/proc/cpuinfo` · سياسة SELinux · صحة البطارية · المخبأ ·
    // معدّلات الشاشة المعلَنة) و`cpuClusterInfo` (‏`CpuHardwareBackend.policies()` +
    // `CpuTopologyUtil.detectClusters()`) و`ThermalUtil.readThermalZones()` — وهي قراءات عقد
    // ومعاملات IPC — **فيحجب فتح الشاشة أوّل إطار** حتى تنتهي، ولا يُرسم شيء قبلها.
    // ⇒ صارت في `LaunchedEffect` داخل `withContext(Dispatchers.IO)`، فترسم الشاشة أوّلًا ثم
    // تُملأ. وما لم يُقرأ بعد يُقال عنه **«جارٍ التحميل»** لا «غير مقروء»: الثانية ادّعاء عن
    // الجهاز، والأولى عنّا (ADR-07).
    var statics by remember { mutableStateOf<DeviceInfoStatics?>(null) }
    var clusters by remember { mutableStateOf<CpuClusterInfo?>(null) }
    // وكلمة «مطفأة» بلغة الواجهة: النموذج لا يعرف لغة، والدالّة الصافية تُقاس بمعاملها.
    val offlineCoreLabel = stringResource(R.string.cpu_core_row_offline)
    // والأسماء التي يحتاجها النموذج — تُبنى هنا لأن النموذج لا يعرف `R` (وهو ما يُبقيه قابلًا
    // للقياس على JVM)، وتُمرَّر مع اللقطة فتُبنى الصفوف في مكان واحد.
    val sensorKindLabels = SensorInventory.Kind.entries.associateWith { sensorKindText(it) }
    val sensorWakeUpLabel = stringResource(R.string.max_sensor_wakeup_flag)
    val thermalOffLabel = stringResource(R.string.devinfo_thermal_zone_off)
    var thermal by remember { mutableStateOf<ThermalRead?>(null) }
    var sensors by remember { mutableStateOf<SensorInventory.Report?>(null) }
    LaunchedEffect(context) {
        // اللقطة الساكنة والهويّة في نداء واحد على خيط الخلفية — لا ثلاث رحلات متتابعة
        // تُطيل زمن ما قبل أوّل إطار.
        val identity = withContext(Dispatchers.IO) { deviceInfoStatics(context) to cpuClusterInfo() }
        statics = identity.first
        clusters = identity.second
        // والمناطق الحرارية تُقرأ **كاملة** لا عدًّا: أسماءها وأجهزتها ونقاط تخفيفها كانت
        // مقروءة أصلًا وتُهمل بعد قراءة عددها (`DI-03`).
        thermal = withContext(Dispatchers.IO) {
            runCatching {
                val zones = ThermalUtil.readThermalZones()
                val cooling = ThermalUtil.readCoolingDevices()
                // ونقاط التخفيف **للمناطق الحيّة وحدها**: عُقدها هي الأكثر في هذا الجهاز
                // (٦٦ منطقة في بصمة مقيسة)، وقراءتها كلها عند الفتح تُؤخّر أوّل إطار — وهي
                // في الشاشة الحرارية تُقرأ مرّة واحدة كذلك.
                val trips = zones
                    .filter { it.isEnabled && it.temperatureC > 0 }
                    .associate { it.sysfsPath to ThermalUtil.readTripPoints(it.sysfsPath) }
                ThermalRead(zones = zones, cooling = cooling, trips = trips)
            }.getOrNull()
        }
        // والمستشعرات مُهلة بطبعها (`SensorMonitorUtil`) — وتُقرأ خارج خيط الواجهة أيضًا:
        // تسجيل مُستمع والانتظار عليه عمل لا يخصّ الرسم.
        sensors = withContext(Dispatchers.IO) {
            runCatching { SensorMonitorUtil.report(context) }.getOrNull()
        }
    }

    // والنموذج يُبنى **بعد** أن تُقرأ اللقطة وحدها: `null` هنا تعني «لم تُقرأ بعد»، ولا تُمرَّر
    // إلى النموذج لأنّه يترجمها إلى «غير مقروء» — وهي حكاية ثانية (‏`DeviceInfoFact`).
    val sections = remember(
        dashboard,
        statics,
        clusters,
        offlineCoreLabel,
        sensorKindLabels,
        sensorWakeUpLabel,
        thermalOffLabel,
        thermal,
        sensors,
    ) {
        val readStatics = statics
        val readClusters = clusters
        if (readStatics == null || readClusters == null) {
            emptyList()
        } else {
            deviceInfoSections(
                deviceInfoSnapshotOf(
                    dashboard = dashboard,
                    statics = readStatics,
                    cpuClusters = readClusters,
                    offlineCoreLabel = offlineCoreLabel,
                    thermal = thermal,
                    sensors = sensors,
                    sensorKindLabels = sensorKindLabels,
                    sensorWakeUpLabel = sensorWakeUpLabel,
                    thermalOffLabel = thermalOffLabel,
                )
            )
        }
    }
    // **«يُقرأ الآن» أم «لا قراءة» (تكملة ٢٠٥):** طابع أول دورة اكتملت يأتي من النموذج
    // (`DashboardState.readingsAtMs`)، وصفر تعني «لم تُقرأ بعد» — وهي الحالة التي رآها
    // المالك مكتوبةً «لا قراءة» دقيقةً كاملة قبل أن تظهر الأرقام.
    val pendingReadings = dashboard.readingsAtMs == 0L

    MaxListScreen(
        title = stringResource(R.string.device_info),
        subtitle = stringResource(R.string.devinfo_subtitle),
        onBack = actions::back,
        accentIcon = Icons.Rounded.Info,
        actions = {
            MaxHelpAction(
                title = stringResource(R.string.devinfo_help_title),
                body = stringResource(R.string.devinfo_help_desc),
            )
        },
        header = {
            // والشريط يقرأ **حالة التقليب نفسها** ولا يملك واحدة: النقر ينقل الصفحة والسحب
            // يحرّك الشريط، بلا مزامنة يدوية بين اثنتين.
            //
            // ويظهر **فورًا** لا مع البيانات: عناوين الأقسام معلنة في [DeviceInfoSection] لا
            // مقروءة من الجهاز، وشريطٌ بلا عنوان هو ما كان يُخشى — وهو هنا مأمون. وحالة
            // التحميل تُقال مرّةً واحدة **داخل الصفحة** لا إحدى عشرة مرّة فوقها.
            MaxTabStrip(
                tabs = DeviceInfoSection.entries.map { MaxTab(label = stringResource(it.titleRes)) },
                selectedIndex = pagerState.currentPage,
                onSelect = { page -> scope.launch { pagerState.animateScrollToPage(page) } },
            )
        },
    ) {
        // **التقليب (`DI-01`):** كل الأقسام في عنصر واحد بارتفاع الشاشة، وداخله صفحة لكل قسم
        // تُمرَّر بنفسها (قائمتها الكسولة) — فالتمرير الرأسي داخل القسم، والتقليب أفقيّ بينها.
        item(key = "devinfo_pager") {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillParentMaxHeight(),
                // ومفتاح ثابت لكل صفحة (مفتاح مسارها): يحفظ موضع كل قسم عند التقليب بعيدًا.
                key = { page -> DeviceInfoSection.entries[page].wireKey },
            ) { page ->
                DeviceInfoPage(
                    section = sections.getOrNull(page),
                    pending = pendingReadings,
                )
            }
        }
    }
}

/**
 * صفحة قسم واحد: **قائمتها الكسولة الخاصة** فتمرّ رأسيًّا داخل القسم بينما التقليب أفقيّ بينه.
 *
 * والقائمة الكسولة **شرط لا زينة** بعد `DI-03`: صفحة المستشعرات تحمل صفًّا لكل مستشعر
 * (٣٤ مستشعرًا في بصمة مقيسة) وصفحة الحرارة صفًّا لكل منطقة حرارية مع بطاقتين — وبناءً مقدَّمًا
 * في `Column` كان يُعيد العطب الذي فُرضت القائمة الكسولة على هذه الشاشة بسببه أصلًا.
 *
 * و`section = null` تعني **لم تُقرأ بعد**: تُعرض حالة تحميل واحدة في موضع البطاقة نفسه، فلا
 * تظهر بطاقة بأصفار (وهي حكاية عن الجهاز لم تُقَس — `ADR-07`).
 */
@Composable
private fun DeviceInfoPage(section: DeviceInfoSectionModel?, pending: Boolean) {
    if (section == null) {
        Box(
            modifier = Modifier.fillMaxSize().padding(vertical = 48.dp),
            contentAlignment = Alignment.Center,
        ) { SectionLoadingIndicator() }
        return
    }

    // **والفراغ السفلي لا يُضاف هنا:** القائمة الحاوية (`MaxListScreen`) تحمل أدناه
    // `pageBottom + شريط التنقّل` بعد هذا العنصر نفسه، فإضافة مثله داخل الصفحة كانت تجعل
    // آخر صفّ يبتعد عن أسفل الشاشة بفراغين — أي قاعدة واحدة لفراغ واحد، كسابقتها.
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(MaxSpace.md),
    ) {
        // **الرأس أولًا** (تكملة ٢٢٥): المقياس وبلاطات الإحصاء فوق التفاصيل — فيقرأ القارئ
        // «ما جهازي؟» في ثانيةٍ ثم ينزل إلى الحقول إن أراد التفصيل.
        item(key = "devinfo_hero_${section.section.wireKey}") {
            DeviceInfoHeroView(hero = section.hero, pending = pending)
        }
        item(key = "devinfo_card_${section.section.wireKey}") {
            DeviceInfoCardBlock(
                title = stringResource(section.section.titleRes),
                facts = section.facts,
                rows = section.rows,
                pending = pending,
            )
        }
        // وشرائح القدرات (الشبكة): حكم المنصّة لكل خاصية — لا كسطور قيم مُسطّحة.
        if (section.chips.isNotEmpty()) {
            item(key = "devinfo_chips_${section.section.wireKey}") {
                DeviceInfoChipGroup(
                    chips = section.chips,
                    title = stringResource(R.string.devinfo_network_caps_title),
                )
            }
        }
        // والبطاقات الإضافية: كل فكرة في بطاقتها لا كومةً في نهاية الأولى.
        section.cards.forEachIndexed { position, card ->
            item(key = "devinfo_card_${section.section.wireKey}_$position") {
                DeviceInfoCardBlock(
                    title = stringResource(card.titleRes),
                    facts = card.facts,
                    rows = card.rows,
                    pending = pending,
                )
            }
        }
    }
}

/**
 * بطاقة واحدة: عنوانها، ثم حقولها، ثم صفوفها.
 *
 * **ولماذا الصفوف بـ`MaxRow` لا بحقل `MaxMetricLine`:** عنوان الصفّ هنا **نصّ من الجهاز**
 * (اسم مستشعر أو منطقة) لا مورد، وقيمة `MaxMetricLine` سطرٌ واحد (`maxLines = 1`) فتُقتطع
 * فيه حقول المستشعر السبعة — وأمّا `MaxRow` فسطراه (`subtitle` بسطرين) تتّسعان لها. وصفٌّ بلا
 * قراءة يُكتب بـ`—` التي يكتبها نظام التصميم نفسه لحقل بلا قيمة.
 */
@Composable
private fun DeviceInfoCardBlock(
    title: String,
    facts: List<DeviceInfoFact>,
    rows: List<DeviceInfoRow>,
    pending: Boolean,
) {
    MaxSection(title = title) {
        MaxGroup {
            facts.forEachIndexed { position, fact ->
                if (position > 0) MaxGroupDivider()
                // **وحالة الانتظار تُمرَّر إلى المقياس (تكملة ٢٠٥):** قبل انتهاء
                // أوّل دورة قياس تكون القيم الحيّة أصفارًا، وكانت تُوسم «لا قراءة»
                // — وهو حكمٌ على الجهاز لم يُقس بعد. ومعها تصير «يُقرأ…» حتى
                // تصل القراءة الأولى، ثم تُستبدل بالأرقام أو بـ«لا قراءة» الحقيقية.
                MaxMetricLine(metric = fact.asMetric(pending = pending))
            }
            rows.forEachIndexed { position, row ->
                // والفاصل مطلوب بين آخر حقل وأوّل صفّ كذلك، وإلّا التصق صفٌّ بحقلٍ.
                if (facts.isNotEmpty() || position > 0) MaxGroupDivider()
                MaxRow(title = row.title, subtitle = row.detail ?: MAX_VALUE_UNAVAILABLE)
            }
        }
    }
}

/**
 * الحقل → `MaxMetric`. والمقياس هو ما يقرّر هل تُطبع القيمة أصلًا: قيمة بلا ثقة **لا
 * تُطبع** ويُقال «غير مقروء» بدل صفر (وهي بوابة نظام التصميم نفسها، فلا تُكرَّر هنا).
 */
@Composable
private fun DeviceInfoFact.asMetric(pending: Boolean): MaxMetric = MaxMetric(
    label = stringResource(label),
    // والقيمة المترجَمة (`valueRes`: «مدعوم» · «USB» · «جيدة») تُقرأ من الموارد **هنا** —
    // فالنموذج لا يعرف لغة، وبلا هذا السطر تُطبع «—» مكان كلمةٍ معلومة أصلًا.
    value = value ?: valueRes?.let { stringResource(it) },
    unit = unit,
    trust = trust.asMaxTrust(pending),
    source = source,
    note = noteRes?.let { stringResource(it) },
)

/**
 * تحويل وسم قسم «معلومات الجهاز» إلى وسم نظام التصميم — [pending] تعني: **الدورة الأولى
 * لم تكتمل بعد**. وهي المعنى الوحيد الذي يفرّق بين «لا قراءة» (حكم على مصدر) و«يُقرأ…»
 * (حكم على سؤال لم يُطرح). وما عدا ذلك تحويلٌ مباشر بلا اجتهاد.
 */
internal fun DeviceInfoTrust.asMaxTrust(pending: Boolean): MaxDataTrust = when (this) {
    DeviceInfoTrust.Live -> MaxDataTrust.Live
    DeviceInfoTrust.Snapshot -> MaxDataTrust.Snapshot
    DeviceInfoTrust.Unreadable -> if (pending) MaxDataTrust.Loading else MaxDataTrust.Unreadable
    DeviceInfoTrust.Unsupported -> MaxDataTrust.Unsupported
}

/**
 * الشاشة التي **تملك** موضوع القسم — الخريطة التي يقابلها [deviceInfoShortcutSection].
 *
 * **ولم تُرسم بعد الآن (أمر المالك، تكملة ١٥١):** كانت أبواب الأقسام تُبنى من هذه الخريطة
 * (`MaxNavigationRow` يُغلق كلّ قسم)، والأمر: «أزِل الاختصارات التي في شاشة Device Info التي
 * تكون غالبًا في آخر كل قسم». فذهب الرسم، وبقيت الخريطة **مكتوبة** لأنها الطرف الذي تُقاس
 * عليه الخريطة العكسية: البوّابة تُثبت أن لكل قسم شاشةً تملكه (هنا)، وأن تلك الشاشة تُعيد
 * القسم نفسه (`deviceInfoShortcutSection`) — ذهابًا وعودةً. ولو حُذفت ونُسخت بيدٍ في الاختبار
 * لصار للسؤال الواحد مصدران، والثاني بلا مستهلك حقيقي. **ومستهلكها اليوم هو الاختبار** —
 * وهذا مُعلَن لا مسكوت عنه.
 *
 * والدالة **غير قابلة للعدم** (`MaxDestination` لا `MaxDestination?`) لأن الحدّ الذي مُنع هو
 * نفسه «بابٌ إلى لا شيء»: قسمٌ بلا شاشة تُعرَض بياناته فيها = قسم لا يجب أن يوجد.
 *
 * وأقسام ثلاثة تفتح `Diagnostics` (النظرة العامة والنظام والمستشعرات) لأنها **بيت الثلاثة**
 * حقيقةً: فيه تقرير الجهاز وجرد المستشعرات (`SensorInventoryCard`) وجرد الأطلس — وهو أفضل
 * شاشة موجودة منطقيًّا لهذه الفكرة، وليس شاشة مُكرّرة تُبنى لتحقيق شرط.
 */
internal fun maxDeviceInfoSource(section: DeviceInfoSection): MaxDestination = when (section) {
    // النظرة العامة = الجهاز كله في سطور؛ وتقريره الكامل في `Diagnostics`.
    DeviceInfoSection.Overview -> MaxDestination.Diagnostics
    DeviceInfoSection.Cpu -> MaxDestination.CpuCoreControl
    DeviceInfoSection.Gpu -> MaxDestination.GpuStudio
    DeviceInfoSection.Memory -> MaxDestination.ZramManager
    DeviceInfoSection.Storage -> MaxDestination.StorageDetail
    DeviceInfoSection.Battery -> MaxDestination.Charging
    DeviceInfoSection.Display -> MaxDestination.DisplayStudio
    DeviceInfoSection.Thermal -> MaxDestination.ThermalDetail
    // والمستشعرات شاشتها هي `Diagnostics` — **وفيها بطاقة الجرد فعلًا**
    // (`SensorInventoryCard`)؛ فلا تُبنى شاشة مستشعرات ثانية لها البطاقة نفسها.
    DeviceInfoSection.Sensors -> MaxDestination.Diagnostics
    DeviceInfoSection.System -> MaxDestination.Diagnostics
    DeviceInfoSection.Network -> MaxDestination.NetworkDetail
    // والكاميرا **قسمٌ بلا شاشة تحكّم مالكة** (استثناءٌ مُعلَن، كُتب في التعداد نفسه):
    // أُضيفت بأمر المالك «معلومات أكثر من الصور في كل قسم» بعد أن كانت لا تُقرأ عتادها
    // أصلًا، وبُيتها `Diagnostics` — بيت الجرد والتقارير، وأقرب بيتٍ موجود لا بيتٌ مُختلَق.
    DeviceInfoSection.Camera -> MaxDestination.Diagnostics
    // والصوت (`AS-01`): له شاشة تحكّم مالكة فعلًا — سطح الصوت `AudioStudio` الذي يُقرأ فيه ويُكتب
    // (مستويات الدفقات). فلا استثناء هنا، بل الحالة العاديّة: قسمٌ يشير إلى الشاشة التي تعمل على
    // موضوعه، وهي صفّ الحوز نفسه (`maxHubRows`) — وهو ما يقيسه `DeviceInfoControlConvergenceTest`.
    DeviceInfoSection.Audio -> MaxDestination.AudioStudio
}

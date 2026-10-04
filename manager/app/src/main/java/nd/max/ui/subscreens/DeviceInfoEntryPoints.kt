/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * Device Info — **مداخل الشاشات** (الاتجاه العكسي لـ[nd.max.ui.subscreens.maxDeviceInfoSource]).
 *
 * `maxDeviceInfoSource` يجيب: «قسمٌ في معلومات الجهاز — أيّ شاشة تملك موضوعه؟» (من الشاشة
 * إلى القسم). وهذه الإجابة **معكوسة**: «شاشةٌ تملك موضوعًا — أيّ قسم يقابلها في معلومات
 * الجهاز؟» (من القسم إلى الشاشة) — فيدخل المستخدم المتقدّم من الشاشة التي يعمل فيها إلى
 * القسم الذي يشرحها، بضغطة واحدة وبلا تنقّل في أحد عشر تبويبًا.
 *
 * **والتعاكس ليس تلقائيًّا، والدليل أنّه لا يمكن أن يكون:** `Diagnostics` **وحدها تملك
 * ثلاثة أقسام** (النظرة العامة والنظام والمستشعرات)، فالمعكوس عليها واحد والمالكون ثلاثة.
 * ولذلك تُكتب الخريطة صراحةً هنا، ويقول الاختبار أين تتقارب مع الأصل وأين تفترق — بدل
 * أن تُستنتج انعكاسًا فينحرف المعنى بصمت.
 *
 * **والاختيار الوحيد الذي ليس ١:١ مُعلَن بالاسم:** شاشة التشخيص تفتح **المستشعرات** —
 * لأنها البطاقة التي فيها (`SensorInventoryCard`) وهي أقرب ما في الشاشة إلى «معلومات
 * الجهاز»، وهو نصّ طلب المالك («في شاشة التشخيص يكون هناك زر يدخلك على قسم المستشعرات»).
 *
 * **و`null` ليست فراغًا بل حكم:** شاشة لا موضوع لها في معلومات الجهاز لا تُرسم لها ضغطة.
 * و`ResponsivenessHub` هي الحالة الوحيدة اليوم: الأقسام الأحد عشر **لا تحمل قسمًا
 * للاستجابة** (لمسة/إطارات)، وقسمٌ لها يستلزم بنية بيانات جديدة لا نقل صفٍّ قائم. فغياب
 * الزرّ هناك خبر صادق، ووجوده بلا قسم يقصده كان سيُنتج زرًّا يفتح نظرة عامة عامةً.
 */
package nd.max.ui.subscreens

import nd.max.ui.navigation.MaxDestination

/**
 * القسم الذي يفتحه مدخل هذه الشاشة في «معلومات الجهاز»، أو `null` حين لا قسم يقابلها.
 *
 * والدالّة صافية (وجهة ← قسم) فلا تحتاج جهازًا ولا Compose، وتُقاس في اختبار JVM.
 */
fun deviceInfoShortcutSection(destination: MaxDestination): DeviceInfoSection? = when (destination) {
    // ── شاشات يملك قسمها الواحد معناها ──
    MaxDestination.CpuCoreControl -> DeviceInfoSection.Cpu
    MaxDestination.GpuStudio -> DeviceInfoSection.Gpu
    MaxDestination.ZramManager -> DeviceInfoSection.Memory
    MaxDestination.StorageDetail -> DeviceInfoSection.Storage
    MaxDestination.Charging -> DeviceInfoSection.Battery
    MaxDestination.DisplayStudio -> DeviceInfoSection.Display
    MaxDestination.ThermalDetail -> DeviceInfoSection.Thermal
    MaxDestination.NetworkDetail -> DeviceInfoSection.Network
    // ── التشخيص: تملك ثلاثة أقسام، والمقصود منها هنا المستشعرات (أعلاه) ──
    MaxDestination.Diagnostics -> DeviceInfoSection.Sensors
    // ── صفحات المجالات: الباب إلى قسم المجال نفسه ──
    MaxDestination.CpuHub -> DeviceInfoSection.Cpu
    MaxDestination.GpuHub -> DeviceInfoSection.Gpu
    MaxDestination.MemoryHub -> DeviceInfoSection.Memory
    MaxDestination.DisplayHub -> DeviceInfoSection.Display
    MaxDestination.ThermalHub -> DeviceInfoSection.Thermal
    MaxDestination.PowerHub -> DeviceInfoSection.Battery
    MaxDestination.StorageHub -> DeviceInfoSection.Storage
    MaxDestination.NetworkHub -> DeviceInfoSection.Network
    // ── والصوت (`AS-01`): القسم يملكه **سطح التحكّم** لا الحوز ──
    // والحوز فهرسُ بابٍ لا شاشةً تعمل على الموضوع (قاعدة الجولة ٢٠١: لا باب في صفحة المحور)،
    // فالباب يُرسم في `AudioStudio` ويقود إلى قسم الصوت — وذهابًا وعودةً يقيسه `DeviceInfoControlConvergenceTest`.
    MaxDestination.AudioHub -> DeviceInfoSection.Audio
    MaxDestination.AudioStudio -> DeviceInfoSection.Audio
    // ولا قسم لـ`ResponsivenessHub` (أعلاه)، ولا لوجهة لا موضوع لها في الشاشة.
    else -> null
}

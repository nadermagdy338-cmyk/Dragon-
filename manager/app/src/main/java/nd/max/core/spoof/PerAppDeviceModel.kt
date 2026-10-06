/* Copyright (C) 2026 Nader Magdy. All rights reserved. */
package nd.max.core.spoof

/**
 * منطق شاشة «مزيّف الأجهزة لكل تطبيق» — نقيّ: لا موارد ولا Compose، فالتسميات تمرّ من الواجهة.
 *
 * الغرض المُعلَن: أن يكون **الجهاز** أوّل ما يراه المستخدم وأوّل ما يفعله في تبويب التزييف — لا سياسة
 * الوراثة (عام/مخصّص/متوقف) التي كانت في المقدّمة فبدت الشاشة شاشة إعدادات لمحرّك COPG، وهو ما رفضه
 * المالك صراحةً («يشبه COPG وليس device_faker»). السياسة تبقى، لكن في مجموعة «متقدّم».
 *
 * وقاعدة الأمان نفسها: كل ما لا دليل عليه لا يُدَّعى — لا قيمة تُخترع، ولا تغيير يُعلن بلا رصد (ADR-07).
 */
object PerAppDeviceModel {
    /** بادئة معرّف الملفّ المأخوذ من عيّنة الكتالوج (`sample_<key>`) — انظر [SampleDevice.profileId]. */
    const val SAMPLE_PREFIX = "sample_"

    /**
     * جهاز هذا التطبيق كما يُحلّ فعلًا: ملفّه المخصّص إن كان وضعه مخصّصًا، وإلا القالب العام إن كان وارثًا،
     * وإلا `null` (يرى جهازك الحقيقي). ومرجع مفقود لا يُختلق: `null` أصدق من ملفّ وهمي.
     */
    fun effective(workspace: SpoofWorkspace, pkg: String): SpoofProfile? = when (workspace.appPolicy(pkg).mode) {
        SpoofInheritanceMode.CUSTOM -> workspace.profiles.firstOrNull { it.id == workspace.bindings[pkg] }
        SpoofInheritanceMode.GLOBAL -> workspace.profiles.firstOrNull { it.id == workspace.globalProfileId }
        SpoofInheritanceMode.DISABLED -> null
    }

    /**
     * مفتاح العيّنة من معرّف ملفّ، أو `null` لملفّ حُرّ/منسوخ (`copy_…`) فلا تُعلَّم بطاقة بالخطأ
     * على أنه «المستخدم الآن» وهو ليس كذلك.
     */
    fun sampleKey(profileId: String?): String? = profileId
        ?.takeIf { it.startsWith(SAMPLE_PREFIX) }
        ?.removePrefix(SAMPLE_PREFIX)
        ?.takeIf { it.isNotBlank() }

    /**
     * ترتيب صفّ الأجهزة: بطاقة جهاز هذا التطبيق أوّلًا (فتُرى بلا تمرير)، ثم بقيّة الكتالوج بترتيبه.
     * لا حذف ولا تكرار ولا إعادة ترتيب لغير ذلك.
     */
    fun rowOrder(devices: List<SampleDevice>, currentProfileId: String?): List<SampleDevice> {
        val key = sampleKey(currentProfileId) ?: return devices
        val chosen = devices.filter { it.key == key }
        if (chosen.isEmpty()) return devices
        return chosen + devices.filterNot { it.key == key }
    }

    /**
     * كم حقلًا سيغيّره هذا الجهاز عن جهازك — **من القيم المرصودة فقط**.
     * قيمة لم تُرصد لا تُحتسب تغييرًا، فالعدّ لا يدّعي ما لا دليل عليه؛ و`null` (بلا تزييف) صفر.
     */
    fun changedFieldCount(device: SpoofProfile?, observed: Map<SpoofField, String>): Int {
        if (device == null) return 0
        fun differs(field: SpoofField, value: String?): Boolean =
            value != null && observed[field] != null && observed[field] != value
        return listOf(
            differs(SpoofField.BRAND, device.brand),
            differs(SpoofField.MODEL, device.model),
            differs(SpoofField.DEVICE, device.device),
            differs(SpoofField.PRODUCT, device.product),
            differs(SpoofField.FINGERPRINT, device.fingerprint),
            differs(SpoofField.SDK_INT, device.sdkInt?.toString()),
        ).count { it }
    }
}
